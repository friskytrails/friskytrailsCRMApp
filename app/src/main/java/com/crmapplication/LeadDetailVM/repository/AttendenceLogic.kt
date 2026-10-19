package com.crmapplication.LeadDetailVM.repository

import android.content.Context
import android.util.Log
import com.crmapplication.LeadDetailVM.remote.AgentsApi
import com.crmapplication.LeadDetailVM.remote.AttendanceTodayDto
import com.crmapplication.LeadDetailVM.remote.PushAttendanceRequest
import com.crmapplication.utils.SessionCleaner
import com.crmapplication.utils.SessionScopedState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Talk-time attendance (see attendance-api-spec-simple.md).
 *
 *   under 60 min      -> "A"  Absent
 *   60 to under 100   -> "H"  Half Day
 *   100 min or more   -> "P"  Full Day
 *   Sunday            -> "WO" Week Off, never counted
 *
 * The phone works the status out from today's talk time, so the progress bar is instant. It only
 * tells the server when the status goes UP (H, then P). The server keeps one status per agent per day
 * and counts finished days into the monthly P / A / H totals.
 */

const val ATTENDANCE_ABSENT = "A"
const val ATTENDANCE_HALF = "H"
const val ATTENDANCE_FULL = "P"
const val ATTENDANCE_WEEK_OFF = "WO"

/** Defaults, used until the server sends its own `rules`. */
data class AttendanceRules(
    val halfDaySeconds: Long = 3600L,
    val fullDaySeconds: Long = 6000L,
    /** 0 = Sunday, 1 = Monday ... 6 = Saturday. */
    val weekOffDays: Set<Int> = setOf(0),
)

/** Everything the Dashboard's attendance card shows. */
data class AttendanceSnapshot(
    val date: String,                    // yyyy-MM-dd, IST
    val talkSeconds: Long,
    val status: String,                  // "A", "H", "P" or "WO"
    val progress: Float,                 // 0..1 of the full-day target
    val halfTick: Float,                 // where the half-day mark sits on the bar (0.6 with the defaults)
    val halfDaySeconds: Long,
    val fullDaySeconds: Long,
    val nextStatus: String?,             // "H" or "P", or null when there is nothing left to reach
    val secondsToNext: Long?,
    val isWeekOff: Boolean,
    /** The status is higher than anything the server has acknowledged yet. */
    val pendingSync: Boolean,
)

/** Higher is better. Anything that is not P or H ranks as absent. */
fun attendanceRank(status: String?): Int = when (status?.trim()?.uppercase()) {
    ATTENDANCE_FULL -> 2
    ATTENDANCE_HALF -> 1
    else -> 0
}

fun attendanceStatusFor(talkSeconds: Long, rules: AttendanceRules): String = when {
    talkSeconds >= rules.fullDaySeconds -> ATTENDANCE_FULL
    talkSeconds >= rules.halfDaySeconds -> ATTENDANCE_HALF
    else -> ATTENDANCE_ABSENT
}

@Singleton
class AttendanceRepository @Inject constructor(
    private val agentsApi: AgentsApi,
    @ApplicationContext context: Context,
    sessionCleaner: SessionCleaner,
) : SessionScopedState {

    init {
        sessionCleaner.register(this)
    }

    private val prefs = context.getSharedPreferences("attendance_push", Context.MODE_PRIVATE)
    private val pushMutex = Mutex()

    private val _snapshot = MutableStateFlow<AttendanceSnapshot?>(null)
    val snapshot: StateFlow<AttendanceSnapshot?> = _snapshot.asStateFlow()

    @Volatile private var rules = AttendanceRules()
    @Volatile private var serverToday: AttendanceTodayDto? = null
    @Volatile private var lastTalkSeconds = 0L

    /** Set when the backend answers 404/405, so an older backend isn't hit on every refresh. */
    @Volatile private var endpointMissing = false

    override fun resetSessionState() {
        _snapshot.value = null
        rules = AttendanceRules()
        serverToday = null
        lastTalkSeconds = 0L
        endpointMissing = false
    }

    /** Today's date and weekday in IST (0 = Sunday), whatever time zone the phone is set to. */
    private fun istToday(): Pair<String, Int> {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(tz)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        return fmt.format(cal.time) to (cal.get(Calendar.DAY_OF_WEEK) - 1)
    }

    /**
     * Works out today's attendance from [talkSeconds] (the phone's own call log), never going below
     * what the server already holds, and publishes it for the Dashboard.
     */
    fun update(talkSeconds: Long, agentId: String?): AttendanceSnapshot {
        lastTalkSeconds = talkSeconds
        val (today, weekday) = istToday()
        val r = rules
        val server = serverToday?.takeIf { it.date == today }
        val serverStatus = server?.status?.trim()?.uppercase()

        // Another phone, or an admin correction, may have put more talk time or a higher status on the server.
        val talk = maxOf(talkSeconds, (server?.talkTimeSeconds ?: 0).toLong())
        val weekOff = weekday in r.weekOffDays

        val computed = attendanceStatusFor(talk, r)
        val status = when {
            weekOff -> ATTENDANCE_WEEK_OFF
            attendanceRank(serverStatus) > attendanceRank(computed) -> serverStatus ?: computed
            else -> computed
        }

        val nextStatus: String?
        val secondsToNext: Long?
        when {
            weekOff -> { nextStatus = null; secondsToNext = null }
            status == ATTENDANCE_ABSENT -> {
                nextStatus = ATTENDANCE_HALF
                secondsToNext = (r.halfDaySeconds - talk).coerceAtLeast(0L)
            }
            status == ATTENDANCE_HALF -> {
                nextStatus = ATTENDANCE_FULL
                secondsToNext = (r.fullDaySeconds - talk).coerceAtLeast(0L)
            }
            else -> { nextStatus = null; secondsToNext = null }
        }

        val acknowledged = maxOf(attendanceRank(serverStatus), sentRank(agentId, today))
        val snap = AttendanceSnapshot(
            date = today,
            talkSeconds = talk,
            status = status,
            progress = (talk.toFloat() / r.fullDaySeconds).coerceIn(0f, 1f),
            halfTick = r.halfDaySeconds.toFloat() / r.fullDaySeconds,
            halfDaySeconds = r.halfDaySeconds,
            fullDaySeconds = r.fullDaySeconds,
            nextStatus = nextStatus,
            secondsToNext = secondsToNext,
            isWeekOff = weekOff,
            pendingSync = !weekOff && attendanceRank(status) > acknowledged,
        )
        _snapshot.value = snap
        return snap
    }

    /** Reads what the server already holds for today (and its rules), so nothing is sent twice. */
    suspend fun loadServerToday(agentId: String, bearer: String) {
        if (endpointMissing) return
        runCatching { agentsApi.getAttendanceToday(id = agentId, authorization = bearer) }
            .onSuccess { dto ->
                serverToday = dto
                dto.rules?.let { rr ->
                    rules = AttendanceRules(
                        halfDaySeconds = rr.halfDayMinSeconds?.toLong() ?: rules.halfDaySeconds,
                        fullDaySeconds = rr.fullDayMinSeconds?.toLong() ?: rules.fullDaySeconds,
                        weekOffDays = rr.weekOffDays?.toSet() ?: rules.weekOffDays,
                    )
                }
            }
            .onFailure { noteFailure("today", it) }
    }

    /**
     * Tells the server the status has gone up. Sends nothing for Absent (no record means Absent) or on
     * a week off, and nothing that was already sent. A failed send is simply retried on the next refresh.
     */
    suspend fun pushIfRaised(agentId: String, bearer: String) {
        val snap = _snapshot.value ?: return
        if (endpointMissing || snap.isWeekOff || snap.status == ATTENDANCE_ABSENT) return

        pushMutex.withLock {
            val serverRank = attendanceRank(serverToday?.takeIf { it.date == snap.date }?.status)
            val acknowledged = maxOf(serverRank, sentRank(agentId, snap.date))
            if (attendanceRank(snap.status) <= acknowledged) return

            runCatching {
                agentsApi.putAttendanceToday(
                    id = agentId,
                    authorization = bearer,
                    body = PushAttendanceRequest(
                        date = snap.date,
                        status = snap.status,
                        talkTimeSeconds = snap.talkSeconds.toInt(),
                    ),
                )
            }
                .onSuccess { dto ->
                    val savedStatus = dto.status ?: snap.status
                    markSent(agentId, snap.date, maxOf(attendanceRank(savedStatus), attendanceRank(snap.status)))
                    serverToday = AttendanceTodayDto(
                        date = snap.date,
                        status = savedStatus,
                        talkTimeSeconds = dto.talkTimeSeconds ?: snap.talkSeconds.toInt(),
                    )
                    update(lastTalkSeconds, agentId)      // clears the "not synced yet" note
                }
                .onFailure { noteFailure("push", it) }
        }
    }

    private fun noteFailure(what: String, e: Throwable) {
        if (e is CancellationException) throw e
        if (e is HttpException && (e.code() == 404 || e.code() == 405)) {
            endpointMissing = true
            Log.w(TAG, "attendance/$what is not available on this backend (HTTP ${e.code()}); using the on-phone status only")
        } else {
            Log.w(TAG, "attendance $what failed; will retry on the next refresh", e)
        }
    }

    private fun sentRank(agentId: String?, date: String): Int {
        if (agentId == null) return 0
        return if (prefs.getString("${agentId}_date", null) == date) prefs.getInt("${agentId}_rank", 0) else 0
    }

    private fun markSent(agentId: String, date: String, rank: Int) {
        prefs.edit()
            .putString("${agentId}_date", date)
            .putInt("${agentId}_rank", rank)
            .apply()
    }

    private companion object {
        const val TAG = "Attendance"
    }
}