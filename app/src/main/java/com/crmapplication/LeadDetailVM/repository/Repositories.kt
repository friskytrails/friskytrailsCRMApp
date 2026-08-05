package com.crmapplication.LeadDetailVM.repository

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.crmapplication.LeadDetailVM.local.BugReportDao
import com.crmapplication.LeadDetailVM.local.BugReportEntity
import com.crmapplication.LeadDetailVM.local.LeadDao
import com.crmapplication.LeadDetailVM.local.LeadEntity
import com.crmapplication.LeadDetailVM.local.NoteDao
import com.crmapplication.LeadDetailVM.local.NoteEntity
import com.crmapplication.LeadDetailVM.local.StatusHistoryDao
import com.crmapplication.LeadDetailVM.local.StatusHistoryEntity
import com.crmapplication.LeadDetailVM.remote.AddLeadNoteRequest
import com.crmapplication.LeadDetailVM.remote.AddNoteRequest
import com.crmapplication.LeadDetailVM.remote.AgentMetricsDto
import com.crmapplication.LeadDetailVM.remote.AgentsApi
import com.crmapplication.LeadDetailVM.remote.AttendanceLogDto
import com.crmapplication.LeadDetailVM.remote.MonthlyAttendanceDto
import com.crmapplication.LeadDetailVM.remote.ApiConfig
import com.crmapplication.LeadDetailVM.remote.ApiNoteDto
import com.crmapplication.LeadDetailVM.remote.AuthApi
import com.crmapplication.LeadDetailVM.remote.AuthLoginRequest
import com.crmapplication.LeadDetailVM.remote.BugReportApi
import com.crmapplication.LeadDetailVM.remote.BugStatus
import com.crmapplication.LeadDetailVM.remote.CreateBugReportRequest
import com.crmapplication.LeadDetailVM.remote.UpdateBugStatusRequest
import com.crmapplication.LeadDetailVM.remote.UpdateLeadInfoRequest
import com.crmapplication.LeadDetailVM.remote.CallsApi
import com.crmapplication.LeadDetailVM.remote.ConfigApi
import com.crmapplication.LeadDetailVM.remote.HistoricalReportDto
import com.crmapplication.LeadDetailVM.remote.LiveActivityDto
import com.crmapplication.LeadDetailVM.remote.LiveStatusDto
import com.crmapplication.LeadDetailVM.remote.LogCallRequest
import com.crmapplication.LeadDetailVM.remote.LongCallDto
import com.crmapplication.LeadDetailVM.remote.CreateLeadRequest
import com.crmapplication.LeadDetailVM.remote.ForgotPasswordRequest
import com.crmapplication.LeadDetailVM.remote.LeadsApi
import com.crmapplication.LeadDetailVM.remote.RegisterRequest
import com.crmapplication.LeadDetailVM.remote.ResendOtpRequest
import com.crmapplication.LeadDetailVM.remote.ResetPasswordRequest
import com.crmapplication.LeadDetailVM.remote.StatusResponse
import com.crmapplication.LeadDetailVM.remote.UpdateProfileRequest
import com.crmapplication.LeadDetailVM.remote.UpdateBookingRequest
import com.crmapplication.LeadDetailVM.remote.UpdateDatesRequest
import com.crmapplication.LeadDetailVM.remote.UpdateLabelsRequest
import com.crmapplication.LeadDetailVM.remote.UpdateMetricsRequest
import com.crmapplication.LeadDetailVM.remote.UpdateStatusRequest
import com.crmapplication.LeadDetailVM.remote.updateReminderBody
import com.crmapplication.LeadDetailVM.remote.UploadApi
import com.crmapplication.LeadDetailVM.remote.UploadResponse
import com.crmapplication.LeadDetailVM.remote.VerifyEmailRequest
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallLogReader
import com.crmapplication.calllog.CallType
import com.crmapplication.calllog.countsAsDial
import com.crmapplication.calllog.normalizedPhoneKey
import com.crmapplication.utils.CallSyncStore
import com.crmapplication.utils.DocumentPartFactory
import com.crmapplication.utils.DueDateStore
import com.crmapplication.utils.ProductCatalogStore
import com.crmapplication.utils.SessionManager
import com.crmapplication.utils.StatusCatalogStore
import com.crmapplication.utils.formatApiDate
import com.crmapplication.utils.formatClockTime
import com.crmapplication.utils.formatDashboardDate
import com.crmapplication.utils.formatIdleTime
import com.crmapplication.utils.formatIso8601
import com.crmapplication.utils.formatIso8601Utc
import com.crmapplication.utils.formatMonthLabel
import com.crmapplication.utils.formatTalkTimeClock
import com.crmapplication.utils.formatTalkTimeWords
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val session: SessionManager,
) {

    suspend fun register(name: String, email: String, password: String): Result<Boolean> = runCatching {
        val response = authApi.register(
            RegisterRequest(name = name.trim(), email = email.trim(), password = password)
        )
        response.emailFailed
    }.recoverCatching { e ->
        if (e !is HttpException) throw e

        val serverError = e.serverError()
        if (serverError != null && serverError.contains("pending", ignoreCase = true)) {
            throw PendingVerificationException(serverError)
        }
        throw Exception(friendlyApiMessage(serverError, e.code()))
    }

    suspend fun verifyEmail(email: String, otp: String): Result<Unit> = runCatching {
        authApi.verifyEmail(VerifyEmailRequest(email = email.trim(), otp = otp.trim()))
        Unit
    }.mapApiError()

    suspend fun resendOtp(email: String): Result<Unit> = runCatching {
        authApi.resendOtp(ResendOtpRequest(email = email.trim()))
        Unit
    }.mapApiError()

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val response = authApi.login(AuthLoginRequest(email = email.trim(), password = password))
        val token = response.token
            ?: throw IllegalStateException("Login succeeded but no token was returned")
        session.saveToken(token)
        val name = response.user?.name?.takeIf { it.isNotBlank() }
            ?: response.user?.email
            ?: email.trim()
        session.saveAgentName(name)

        session.saveAgentEmail(response.user?.email?.takeIf { it.isNotBlank() } ?: email.trim())

        response.user?.id?.takeIf { it.isNotBlank() }?.let(session::saveAgentId)
        name
    }.recoverCatching { e ->
        if (e !is HttpException) throw e

        val serverError = e.serverError()
        if (serverError != null && serverError.contains("verify", ignoreCase = true)) {
            throw EmailNotVerifiedException(serverError)
        }
        throw Exception(friendlyApiMessage(serverError, e.code()))
    }

    suspend fun requestPasswordReset(email: String): Result<Unit> = runCatching {
        authApi.forgotPassword(ForgotPasswordRequest(email = email.trim()))
        Unit
    }.mapApiError()

    suspend fun verifyResetOtp(email: String, otp: String): Result<Unit> =
        if (otp.isBlank()) Result.failure(Exception("Enter the code from your email"))
        else Result.success(Unit)

    suspend fun resetPassword(email: String, otp: String, newPassword: String): Result<Unit> = runCatching {
        authApi.resetPassword(
            ResetPasswordRequest(email = email.trim(), otp = otp.trim(), newPassword = newPassword)
        )
        Unit
    }.mapApiError()

    suspend fun getProfile(): Result<Profile> = runCatching {
        val bearer = session.getToken().bearerOrThrow()
        authApi.getProfile(bearer).toDomain()
    }.mapApiError()

    suspend fun updateProfile(name: String, email: String): Result<Profile?> = runCatching {
        val bearer = session.getToken().bearerOrThrow()
        val response = authApi.updateProfile(bearer, UpdateProfileRequest(name = name.trim(), email = email.trim()))
        val user = response.user

        session.saveAgentName(user?.name?.takeIf { it.isNotBlank() } ?: name.trim())
        session.saveAgentEmail(user?.email?.takeIf { it.isNotBlank() } ?: email.trim())
        user?.toProfile()
    }.mapApiError()

    fun logout() = session.clear()
    fun isLoggedIn() = session.getToken() != null
    fun getAgentName() = session.getAgentName()
    fun getAgentEmail() = session.getAgentEmail()

    private fun String?.bearerOrThrow(): String {
        val token = this?.trim()?.takeIf { it.isNotEmpty() } ?: error("Not logged in.")
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }
}

class PendingVerificationException(message: String) : Exception(message)

class EmailNotVerifiedException(message: String) : Exception(message)

private fun HttpException.serverError(): String? = runCatching {
    response()?.errorBody()?.string()?.let { body ->
        Gson().fromJson(body, StatusResponse::class.java)?.error
    }
}.getOrNull()

private fun parseUploadError(body: String?): String? = runCatching {
    body?.takeIf { it.isNotBlank() }?.let {
        val parsed = Gson().fromJson(it, UploadResponse::class.java)
        val message = parsed?.message?.takeIf { m -> m.isNotBlank() }
        val detail = parsed?.error?.takeIf { e -> e.isNotBlank() }
        when {
            message != null && detail != null -> "$message: $detail"
            else -> message ?: detail
        }
    }
}.getOrNull()

private fun friendlyApiMessage(serverError: String?, code: Int): String =
    serverError
        ?: if (code == 429) "Too many attempts. Please wait a few minutes and try again."
        else "Request failed ($code)"

/**
 * Shown instead of OkHttp's raw transport text. Agents were seeing
 * "Failed to connect to <host>/<ip>:443", which reads like an app crash and says nothing actionable.
 */
private const val NETWORK_ERROR_MESSAGE =
    "Couldn't reach the server. Check your connection and try again."

private fun <T> Result<T>.mapApiError(): Result<T> = recoverCatching { e ->
    if (e is HttpException) {
        throw Exception(friendlyApiMessage(e.serverError(), e.code()))
    }
    // Every transport failure lands here: no DNS, no route, refused socket, timeout.
    if (e is IOException) {
        throw Exception(NETWORK_ERROR_MESSAGE)
    }
    throw e
}

@Singleton
class DashboardRepository @Inject constructor(
    private val leadDao: LeadDao,
    private val callLogReader: CallLogReader,
    private val agentsApi: AgentsApi,
    private val session: SessionManager,
) {

    /** yyyy-MM-dd of the day we last successfully pushed "Present"; guards against re-pushing every refresh. */
    private var lastAttendancePush: String? = null

    /**
     * Last computed dashboard, kept in-memory on this Singleton so it survives ViewModel
     * recreation. Re-entering the dashboard can paint this instantly instead of a blank spinner
     * while a fresh compute runs. Holds the local-only stage until the network stage replaces it.
     */
    @Volatile
    var lastData: DashboardData? = null
        private set

    fun hasCallLogPermission(): Boolean = callLogReader.hasPermission()

    fun observeCallLogChanges(): Flow<Unit> = callLogReader.observeChanges()

    private data class AgentAuth(val id: String, val bearer: String)

    private fun resolveAgentAuth(): AgentAuth? {
        val token = session.getToken()?.takeIf { it.isNotBlank() } ?: return null
        val id = session.getAgentId()?.takeIf { it.isNotBlank() }
            ?: agentIdFromToken(token)
            ?: return null
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        return AgentAuth(id, bearer)
    }

    private suspend fun fetchAgentMetrics(): AgentMetricsDto? {
        val auth = resolveAgentAuth() ?: return null
        return runCatching { agentsApi.getMetrics(id = auth.id, authorization = auth.bearer) }.getOrNull()
    }

    /** Best-effort: admin-set daily attendance logs (P/A per date). Empty on any failure. */
    private suspend fun fetchAttendanceLogs(): List<AttendanceLogDto> {
        val auth = resolveAgentAuth() ?: return emptyList()
        return runCatching { agentsApi.getAttendance(id = auth.id, authorization = auth.bearer) }
            .getOrNull().orEmpty()
    }

    /** Best-effort: server-side authoritative monthly P/A counts. Null on any failure. */
    private suspend fun fetchMonthlyAttendance(month: Int, year: Int): MonthlyAttendanceDto? {
        val auth = resolveAgentAuth() ?: return null
        return runCatching {
            agentsApi.getMonthlyAttendance(
                id = auth.id,
                month = month,
                year = year,
                authorization = auth.bearer,
            )
        }.onSuccess {
            Log.d(TAG, "monthlyAttendance id=${auth.id} $month/$year -> present=${it.present} absent=${it.absent}")
        }.onFailure {
            Log.w(TAG, "monthlyAttendance id=${auth.id} $month/$year FAILED, falling back to local count", it)
        }.getOrNull()
    }

    /**
     * Best-effort: if the agent has any call activity today, mark them Present on the server.
     * Runs at most once per day (in-memory guard), only flips [lastAttendancePush] on success so
     * a failed push retries on the next refresh. Never surfaces errors — attendance is derived,
     * not user-entered, so a failure here must not break the dashboard.
     */
    private suspend fun syncTodayAttendance(
        allCalls: List<CallLogEntry>,
        startOfDay: Long,
        endOfDay: Long,
    ) {
        val today = formatApiDate(startOfDay)
        if (lastAttendancePush == today) return

        val presentToday = allCalls.any { it.dateMillis in startOfDay until endOfDay }
        if (!presentToday) return

        updateMetrics(attendance = "P", attendanceDate = today)
            .onSuccess { lastAttendancePush = today }
    }

    suspend fun updateMetrics(
        attendance: String? = null,
        attendanceDate: String? = null,
        monthlyTarget: Int? = null,
        targetCompleted: Int? = null,
    ): Result<AgentMetricsDto> = runCatching {
        val auth = resolveAgentAuth() ?: error("Not logged in.")
        agentsApi.updateMetrics(
            id = auth.id,
            authorization = auth.bearer,
            body = UpdateMetricsRequest(
                attendance = attendance,
                attendanceDate = attendanceDate,
                monthlyTarget = monthlyTarget,
                targetCompleted = targetCompleted,
            ),
        )
    }.mapApiError()

    private fun agentIdFromToken(token: String): String? = runCatching {
        val payload = token.removePrefix("Bearer ").trim().split(".").getOrNull(1) ?: return null
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        Gson().fromJson(json, JsonObject::class.java)?.get("userId")?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Two-stage dashboard load, emitted local-first so the UI paints without waiting on the network:
     *  1. **Local stage** — call log + local Room leads only, with placeholder target/attendance.
     *     Emitted immediately; this is the fast path and covers offline.
     *  2. **Network stage** — best-effort agent metrics + attendance (all fetched concurrently),
     *     merged in and emitted as an updated [DashboardData].
     * Each stage is cached into [lastData] so a re-entry can paint instantly before this runs again.
     * Network failures are swallowed inside the fetch* helpers, so the local stage always survives.
     */
    fun getDashboard(): Flow<DashboardData> = flow {
        check(callLogReader.hasPermission()) { "Call-log permission not granted" }

        val now = Calendar.getInstance()
        val startOfDay = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + DAY_MILLIS

        val startOfMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // --- Stage 1: local-only (no network) ---
        val allCalls = callLogReader.readAll()
        val leads = leadDao.getAllLeads().first()

        // Per-number assignment cutoff (earliest stamp wins if a number is shared; null =
        // no restriction). Dashboard stays today-scoped but drops any of today's calls that
        // predate assignment, so a lead assigned today can't pull in earlier-today calls.
        val assignedByKey: Map<String, Long?> = leads
            .mapNotNull { lead ->
                val key = lead.phone.normalizedPhoneKey().ifEmpty { null } ?: return@mapNotNull null
                key to lead.assignedAt
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> if (times.any { it == null }) null else times.filterNotNull().min() }

        val localDaily = buildDailyStats(allCalls, startOfDay, endOfDay, now.timeInMillis, todayStatus = null, assignedByKey)
        val localMonthly = buildMonthlyStats(startOfMonth, presentCount = 0, absentCount = 0, metrics = null, leads)
        DashboardData(daily = localDaily, monthly = localMonthly)
            .also { lastData = it }
            .let { emit(it) }

        // --- Stage 2: network (best-effort, all concurrent) ---
        coroutineScope {
            val metricsDeferred = async { fetchAgentMetrics() }
            val attendanceLogsDeferred = async { fetchAttendanceLogs() }
            val monthlyAttendanceDeferred = async {
                fetchMonthlyAttendance(
                    month = now.get(Calendar.MONTH) + 1,
                    year = now.get(Calendar.YEAR),
                )
            }
            launch { syncTodayAttendance(allCalls, startOfDay, endOfDay) }

            val metrics = metricsDeferred.await()
            val attendanceLogs = attendanceLogsDeferred.await()
            val monthlyAttendance = monthlyAttendanceDeferred.await()

            val todayKey = formatApiDate(startOfDay)
            val monthPrefix = todayKey.take(7)

            val todayStatus = attendanceLogs.firstOrNull { it.date == todayKey }
                ?.status?.trim()?.uppercase()
                ?: metrics?.attendance?.trim()?.uppercase()

            val monthLogs = attendanceLogs.filter { it.date.orEmpty().startsWith(monthPrefix) }
            val presentCount = monthlyAttendance?.present
                ?: monthLogs.count { it.status?.trim()?.uppercase() == "P" }
            val absentCount = monthlyAttendance?.absent
                ?: monthLogs.count { it.status?.trim()?.uppercase() == "A" }

            val daily = buildDailyStats(allCalls, startOfDay, endOfDay, now.timeInMillis, todayStatus, assignedByKey)
            val monthly = buildMonthlyStats(startOfMonth, presentCount, absentCount, metrics, leads)

            DashboardData(daily = daily, monthly = monthly)
                .also { lastData = it }
                .let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildDailyStats(
        allCalls: List<CallLogEntry>,
        startOfDay: Long,
        endOfDay: Long,
        nowMillis: Long,
        todayStatus: String?,
        assignedByKey: Map<String, Long?>,
    ): DashboardStats {

        // A call belongs to a lead only if it's to an assigned number AND at/after that lead's
        // assignment cutoff (null cutoff = no restriction). Excludes a prior owner's pre-assignment calls.
        val leadCalls = allCalls.filter { call ->
            val key = call.number.normalizedPhoneKey()
            if (!assignedByKey.containsKey(key)) return@filter false
            val assignedAt = assignedByKey[key]
            assignedAt == null || call.dateMillis >= assignedAt
        }

        val today = leadCalls
            .filter { it.dateMillis in startOfDay until endOfDay }
            .sortedBy { it.dateMillis }

        // Idle time = the gap between the two most recent calls today only. No synthetic "now"
        // marker (that inflated idle with time-since-last-call, which read as login time), and
        // no cross-day carryover. Fewer than two calls today -> no idle to show.
        val idleSeconds = if (today.size >= 2) computeIdleSeconds(today.takeLast(2)) else null

        val totalTalk = today.sumOf { it.durationSeconds }
        // Dials include answered inbound calls, not just outgoing ones — see `countsAsDial` for the
        // rule. Counting outgoing alone was what made this tile report 2 against the web historical
        // report's 5 for the same day.
        val dials = today.count { it.countsAsDial }
        // Connected is a subset of the dialled calls, so it can never print higher than Total Dial.
        val connected = today.count { it.countsAsDial && it.durationSeconds > 0 }
        val callsPerNumber = today.groupingBy { it.number.normalizedPhoneKey() }.eachCount()
        val unique = callsPerNumber.size
        val callMoreThan = callsPerNumber.count { it.value >= 2 }

        return DashboardStats(
            date = formatDashboardDate(startOfDay),
            totalDials = dials,
            totalTalktime = formatTalkTimeWords(totalTalk),
            connectedCalls = connected,
            uniqueCalls = unique,
            callMoreThan = callMoreThan,
            firstCall = today.firstOrNull()?.dateMillis?.let(::formatClockTime),
            lastCall = today.lastOrNull()?.dateMillis?.let(::formatClockTime),
            idleTime = idleSeconds?.let(::formatIdleTime) ?: "—",

            attendance = when (todayStatus) {
                "P" -> "Present"
                "A" -> "Absent"
                else -> "—"
            },
        )
    }

    private fun buildMonthlyStats(
        startOfMonth: Long,
        presentCount: Int,
        absentCount: Int,
        metrics: AgentMetricsDto?,
        leads: List<LeadEntity>,
    ): MonthlyStats {

        val bookedLeads = leads.count { it.status.equals("Booked", ignoreCase = true) }

        val monthlyTarget = if (metrics?.monthlyTarget != null || metrics?.targetCompleted != null) {
            "${metrics.targetCompleted ?: 0} - ${metrics.monthlyTarget ?: 0}"
        } else {
            "0 - 0"
        }

        return MonthlyStats(
            month = formatMonthLabel(startOfMonth),
            monthlyTarget = monthlyTarget,
            bookingCount = "$bookedLeads / ${leads.size}",
            totalSaleAmount = "0 / 0",
            attendance = "${presentCount}P / ${absentCount}A",
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val TAG = "DashboardRepo"
    }
}

@Singleton
class LeadsRepository @Inject constructor(
    private val leadsApi: LeadsApi,
    private val uploadApi: UploadApi,
    private val documentPartFactory: DocumentPartFactory,
    private val leadDao: LeadDao,
    private val noteDao: NoteDao,
    private val statusHistoryDao: StatusHistoryDao,
    private val session: SessionManager,
    private val dueDateStore: DueDateStore,
) {

    fun currentAgentId(): String? = session.getAgentId()

    // Dedupe redundant network syncs. Leads + Dashboard + Add Lead each own a ViewModel that fires
    // syncLeads() on entry; without this, opening Add Lead right after Leads re-hits the network for
    // no benefit (Room's Flow already drives the UI). A short throttle collapses those; manual
    // refresh / post-create pass force = true to bypass it. The mutex also folds concurrent callers
    // onto one in-flight request.
    private val syncMutex = Mutex()
    @Volatile private var lastSyncAt: Long = 0L

    fun observeLeads(): Flow<List<Lead>> = leadDao.getAllLeads().map { list ->
        list.map { it.toDomain() }
    }

    fun observeNotes(leadId: String): Flow<List<Note>> = noteDao.getNotesForLead(leadId).map { list ->
        list.map { it.toDomain() }
    }

    fun observeStatusHistory(leadId: String): Flow<List<StatusChange>> =
        statusHistoryDao.getHistoryForLead(leadId).map { list -> list.map { it.toDomain() } }

    suspend fun syncLeads(force: Boolean = false): Result<Unit> = syncMutex.withLock {
      runCatching {
        // Throttle: skip a redundant network round-trip if we synced very recently and the caller
        // didn't force it. Room's Flow already keeps the UI live, so a skipped sync isn't a data gap.
        if (!force && System.currentTimeMillis() - lastSyncAt < SYNC_THROTTLE_MS) {
            return@runCatching
        }
        check(ApiConfig.isConfigured) {
            "Leads API is not configured. Fill ApiConfig.BASE_URL and ApiConfig.LEADS_ENDPOINT."
        }
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot sync leads.")
        val dtos = leadsApi.getLeads(
            endpoint = ApiConfig.LEADS_ENDPOINT,
            authorization = token.toBearerOrNull(),
        )

        val existing = leadDao.getAllLeads().first().associateBy { it.id }
        // Durable due dates, read once. These outlive the `leads` table, so this is what puts a
        // saved reminder back after a destructive migration wiped the row it used to live in.
        val savedDueDates = dueDateStore.all()
        val now = System.currentTimeMillis()
        val entities = dtos.map { dto ->
            val entity = dto.toEntity()
            val prior = existing[entity.id]
            val locallyChangedStatus = prior?.statusChangedAt != null
            entity.copy(
                // Reminder precedence. `entity.dueDate` now carries the server's `dates.reminderDate`,
                // so it wins when set — that's what makes a reminder set from the web dashboard show
                // up here. The store is the fallback and still the only copy that survives an app
                // update (destructive migration); `prior` is the warm-path fallback.
                //
                // A *null* from the server deliberately does NOT clear a stored reminder: null is
                // ambiguous between "cleared on the web" and "our push failed, so the server never
                // got it". Preferring the local value loses a web-side clear (the agent can redo it)
                // rather than a reminder the agent set on this device, which exists nowhere else.
                dueDate = entity.dueDate ?: savedDueDates[entity.id] ?: prior?.dueDate,
                statusChangedAt = prior?.statusChangedAt ?: entity.statusChangedAt,
                status = if (locallyChangedStatus) prior!!.status else entity.status,
                // Agent-editable fields (PUT api/leads/{id}), same precedence as dueDate above and
                // for the same reason: the server wins when it actually sent a value, and a null is
                // ambiguous between "cleared elsewhere" and "our push never landed" — so it must not
                // silently discard an edit the agent just made on this device.
                //
                // Without these two lines the detail screen's 25s poll rebuilt the row from the
                // server response and wiped every edit within seconds of saving it.
                travelDate = entity.travelDate ?: prior?.travelDate,
                numberOfPersons = entity.numberOfPersons ?: prior?.numberOfPersons,
                // Set once: stamp the first time this lead appears for this agent, then preserve.
                // This instant is the call-log cutoff — calls before it aren't this agent's work.
                assignedAt = prior?.assignedAt ?: now,
            )
        }

        if (entities.isEmpty()) leadDao.deleteAll()
        else leadDao.deleteLeadsNotIn(entities.map { it.id })
        leadDao.upsertLeads(entities)
        // Mirror the prune into the durable store so it doesn't accumulate keys for reassigned
        // leads. Intentionally NOT called when entities is empty — see DueDateStore.retainOnly.
        dueDateStore.retainOnly(entities.map { it.id })

        // Back the resolved reminder into the durable store, which in practice means picking up ones
        // that arrived from the server (e.g. set on the web dashboard). Room alone isn't enough — a
        // schema bump destructively migrates — so this keeps the store the union of every reminder
        // we know about, leaving the precedence chain above something to fall back on after a wipe.
        // Writes only on a difference, so a locally-set reminder that already matches is a no-op.
        entities.forEach { entity ->
            val resolved = entity.dueDate ?: return@forEach
            if (savedDueDates[entity.id] != resolved) dueDateStore.set(entity.id, resolved)
        }

        dtos.forEach { dto ->
            val id = dto.id ?: dto.mongoId ?: dto.leadId?.toString() ?: dto.phone.orEmpty()
            if (id.isNotBlank()) reconcileNotes(id, dto.notes)
        }
        lastSyncAt = System.currentTimeMillis()
      }
    }

    suspend fun createLead(request: CreateLeadRequest): Result<Unit> = runCatching {
        check(ApiConfig.isConfigured) { "Leads API is not configured." }
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot create a lead.")
        leadsApi.createLead(
            authorization = token.toBearerOrNull(),
            body = request,
        )

        runCatching { syncLeads(force = true) }
        Unit
    }.mapApiError()

    suspend fun addNote(leadId: String, text: String, imageUrl: String? = null): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val optimisticId = UUID.randomUUID().toString()
            noteDao.insertNote(
                NoteEntity(
                    id = optimisticId,
                    leadId = leadId,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    authorName = session.getAgentName().ifBlank { "Me" },
                    authorId = session.getAgentId(),
                    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
                )
            )
            val updated = try {
                leadsApi.addNote(
                    id = leadId,
                    authorization = session.getToken()?.toBearerOrNull(),
                    body = AddLeadNoteRequest(text = text, imageUrl = imageUrl?.takeIf { it.isNotBlank() }),
                )
            } catch (e: Throwable) {
                // Roll the optimistic row back. reconcileNotes only clears server-side ids
                // (`id NOT LIKE '%-%'`), and this id is a UUID, so without this the unsent note
                // would sit in "Previous Notes" forever and read as saved when it never reached
                // the server. Dropping it keeps the list honest; the composer keeps the text so
                // the agent can send again.
                noteDao.deleteNoteById(optimisticId)
                throw e
            }

            reconcileNotes(leadId, updated.notes)
            noteDao.deleteNoteById(optimisticId)
        }
    }.mapApiError()

    suspend fun deleteNote(leadId: String, noteId: String): Result<Unit> = runCatching {
        val isLocalOnly = noteId.contains('-')
        if (isLocalOnly) {
            noteDao.deleteNoteById(noteId)
            return@runCatching
        }
        val updated = leadsApi.deleteNote(
            id = leadId,
            noteId = noteId,
            authorization = session.getToken()?.toBearerOrNull(),
        )
        reconcileNotes(leadId, updated.notes)
    }.mapApiError()

    suspend fun refreshLeadNotes(leadId: String): Result<Unit> = runCatching {
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot refresh notes.")
        val dto = leadsApi.getLead(id = leadId, authorization = token.toBearerOrNull())
        reconcileNotes(leadId, dto.notes)
    }

    private suspend fun reconcileNotes(leadId: String, apiNotes: List<ApiNoteDto>?) {
        if (apiNotes == null) return
        noteDao.replaceServerNotes(leadId, apiNotes.map { it.toEntity(leadId) })
    }

    suspend fun uploadDocument(leadId: String, uri: Uri): Result<String> = runCatching {
        val token = session.getToken()?.toBearerOrNull()
            ?: error("Not logged in — cannot upload.")
        val (part, meta) = documentPartFactory.build(uri)
            ?: error("Couldn't read the selected file.")

        val response = uploadApi.upload(authorization = token, file = part)
        if (!response.isSuccessful) {

            val serverMessage = parseUploadError(response.errorBody()?.string())
            error(serverMessage ?: "Upload failed (HTTP ${response.code()}).")
        }
        val body = response.body()
        val fileUrl = body?.fileUrl?.takeIf { it.isNotBlank() }
            ?: error(body?.error ?: body?.message ?: "Upload failed — no file URL returned.")

        addNote(leadId, text = meta.fileName, imageUrl = fileUrl).getOrThrow()
        fileUrl
    }

    /**
     * Sets, or with a null [dueDate] clears, the lead's reminder date **and time**.
     *
     * Offline-first, in the repo's usual order: [DueDateStore] first (the durable copy — Room's row
     * is disposable, since any schema bump destructively migrates and `deleteLeadsNotIn` runs on
     * every sync), then Room so the list and detail Flows repaint, then the server.
     *
     * The push goes to `PUT /api/leads/:id/reminder` — the dedicated endpoint that stores date *and*
     * time. Not `/dates`: that field is date-only and [updateDates] rewrites it from the call log on
     * every refresh, which would erase whatever the agent set here.
     *
     * A push failure is returned as a failure but the local write **stays** — the reminder still
     * fires on this device and the next sync reconciles. This used to be a fire-and-forget call to
     * the fake `ApiService` with the error swallowed, so a reminder never actually left the device
     * and nothing said so.
     */
    suspend fun setDueDate(leadId: String, dueDate: Long?): Result<Unit> = runCatching {
        dueDateStore.set(leadId, dueDate)
        leadDao.updateDueDate(leadId, dueDate)

        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — the reminder is saved on this device only.")
        leadsApi.updateReminder(
            id = leadId,
            authorization = token.toBearerOrNull(),
            body = updateReminderBody(dueDate?.let(::formatIso8601Utc)),
        )
        Unit
    }.mapApiError()

    suspend fun updateStatus(leadId: String, status: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()

        val previousStatus = leadDao.getLeadById(leadId)?.status
        leadDao.updateStatus(leadId, status, now)

        if (previousStatus != status) {
            statusHistoryDao.insert(
                StatusHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    leadId = leadId,
                    previousStatus = previousStatus,
                    newStatus = status,
                    changedBy = session.getAgentName().ifBlank { "Unknown" },
                    changedAt = now,
                )
            )
        }
        leadsApi.updateStatus(
            id = leadId,
            authorization = session.getToken()?.toBearerOrNull(),
            body = UpdateStatusRequest(status),
        )
        Unit
    }

    /**
     * Partial update of the agent-editable lead fields (`PUT api/leads/{id}`), offline-first like
     * [updateStatus]: Room is written first so the detail screen repaints immediately through
     * `observeLeads`, then the change is pushed.
     *
     * Each parameter is null when this edit doesn't touch that field, so only what actually changed
     * is sent — editing the party size can't clobber a name changed on the web dashboard meanwhile.
     * To *clear* a value pass `""` for [travelDate] or `0` for [numberOfPersons]: the request then
     * carries `""` / `0` instead of omitting the key, which is what tells the backend to reset it.
     * A blank [name] is ignored rather than treated as a clear — a lead has to keep a display name.
     *
     * A failed push leaves the local change in place and surfaces the error; the next successful
     * [syncLeads] reconciles against the server, matching how the rest of this repository behaves.
     */
    suspend fun updateLeadInfo(
        leadId: String,
        name: String? = null,
        travelDate: String? = null,
        numberOfPersons: Int? = null,
    ): Result<Unit> = runCatching {
        val current = leadDao.getLeadById(leadId)
            ?: error("This lead is no longer available on this device.")

        val resolvedName = if (name == null) current.name else name.trim().ifEmpty { current.name }
        val resolvedTravelDate =
            if (travelDate == null) current.travelDate else travelDate.trim().takeIf { it.isNotEmpty() }
        val resolvedPersons = when {
            numberOfPersons == null -> current.numberOfPersons
            numberOfPersons <= 0 -> null
            else -> numberOfPersons
        }

        val request = UpdateLeadInfoRequest(
            name = if (name != null) resolvedName else null,
            // Empty string / 0 rather than an omitted key: an absent key means "leave unchanged" to
            // the backend, so a clear has to send a value. (Gson here has serializeNulls = false, so
            // an explicit JSON null isn't available — see UpdateLeadInfoRequest.)
            travelDate = if (travelDate != null) resolvedTravelDate.orEmpty() else null,
            numberOfPersons = if (numberOfPersons != null) (resolvedPersons ?: 0) else null,
        )
        if (request.isEmpty) return@runCatching

        leadDao.updateLeadInfo(
            leadId = leadId,
            name = resolvedName,
            travelDate = resolvedTravelDate,
            numberOfPersons = resolvedPersons,
        )
        leadsApi.updateLeadInfo(
            id = leadId,
            authorization = session.getToken()?.toBearerOrNull(),
            body = request,
        )
        Unit
    }

    /**
     * Submits the agent's booking form to `PUT api/leads/{id}/book`, then records `Booked` locally.
     *
     * **Server-first, unlike every other write here.** Leads and notes are offline-first — write Room,
     * push, let a failed push reconcile later — but booking can't be: reaching `Booked` locks the
     * status dropdown, and this app offers no way back. An optimistic local write followed by a failed
     * push would leave the lead locked with no booking on the server and no way for the agent to
     * retry. So nothing is written until the server has the booking.
     *
     * The response DTO is intentionally **not** upserted: it carries no `assignedAt` and its
     * `dates.reminderDate` round-trip would risk the local-only reminder, both of which
     * [syncLeads] guards. The next sync picks up the `name`/`product` the backend rewrites from
     * `fullName`/`packageName`.
     */
    suspend fun bookLead(leadId: String, form: BookingForm): Result<Unit> = runCatching {
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot book this lead.")

        leadsApi.bookLead(
            id = leadId,
            authorization = token.toBearerOrNull(),
            body = form.toRequest(),
        )

        val now = System.currentTimeMillis()
        val previousStatus = leadDao.getLeadById(leadId)?.status
        leadDao.updateStatus(leadId, BOOKED_STATUS, now)
        if (previousStatus != BOOKED_STATUS) {
            statusHistoryDao.insert(
                StatusHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    leadId = leadId,
                    previousStatus = previousStatus,
                    newStatus = BOOKED_STATUS,
                    changedBy = session.getAgentName().ifBlank { "Unknown" },
                    changedAt = now,
                )
            )
        }
        Unit
    }.mapApiError()

    suspend fun updateBooking(leadId: String, calls: List<CallLogEntry>): Result<Unit> = runCatching {
        // Lead-detail push: [calls] is already the post-assignment history (VM filters by assignedAt),
        // so book the cumulative window — every dial since assignment, not just today's.
        val body = bookingFromCalls(calls, todayOnly = false) ?: return@runCatching
        leadsApi.updateBooking(
            id = leadId,
            authorization = session.getToken()?.toBearerOrNull(),
            body = body,
        )
        Unit
    }

    suspend fun updateDates(leadId: String, calls: List<CallLogEntry>): Result<Unit> = runCatching {
        val body = datesFromCalls(calls) ?: return@runCatching
        leadsApi.updateDates(
            id = leadId,
            authorization = session.getToken()?.toBearerOrNull(),
            body = body,
        )
        Unit
    }

    suspend fun updateLabels(
        leadId: String,
        existingLabels: List<String>,
        calls: List<CallLogEntry>,
    ): Result<Unit> = runCatching {
        val callLabel = callLabelFor(calls) ?: return@runCatching
        val merged = mergeLabels(existingLabels, callLabel)
        leadsApi.updateLabels(
            id = leadId,
            authorization = session.getToken()?.toBearerOrNull(),
            body = UpdateLabelsRequest(merged),
        )
        Unit
    }

    private fun String.toBearerOrNull(): String? {
        val token = trim().takeIf { it.isNotEmpty() } ?: return null
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }

    private companion object {
        /** Skip a network sync if the last one finished within this window (unless forced). */
        const val SYNC_THROTTLE_MS = 30_000L
    }
}

/**
 * Global settings from `GET /api/config`: the lead statuses and the product catalog. Admins edit
 * both on the backend and the app follows — a new status reaches the filter chips and every status
 * dropdown, and a new product reaches Add Lead, with no app release.
 *
 * Read-through cache: the `observe*` Flows emit from DataStore immediately (so chips and dropdowns
 * are populated on first frame and offline), [syncConfig] refreshes them and the writes push back
 * through the same Flows, updating open UI live.
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val configApi: ConfigApi,
    private val catalogStore: ProductCatalogStore,
    private val statusStore: StatusCatalogStore,
    private val session: SessionManager,
) {

    /** Cached list, falling back to [DEFAULT_PRODUCTS] until the first successful sync lands. */
    fun observeProducts(): Flow<List<String>> = catalogStore.products.map { cached ->
        cached.ifEmpty { DEFAULT_PRODUCTS }
    }

    /** Cached list, falling back to [DEFAULT_LEAD_STATUSES] until the first successful sync lands. */
    fun observeStatuses(): Flow<List<String>> = statusStore.statuses.map { cached ->
        cached.ifEmpty { DEFAULT_LEAD_STATUSES }
    }

    @Volatile private var lastSyncAt: Long = 0L

    /**
     * Fetches both lists in one request and caches them. [force] bypasses the throttle that
     * collapses the near simultaneous calls from ViewModel init, screen entry, and the lead poll
     * into one request.
     *
     * A failure leaves the caches untouched, so callers should not surface it as a user-facing
     * error — slightly stale chips are the correct degraded behaviour.
     */
    suspend fun syncConfig(force: Boolean = false): Result<Unit> = runCatching {
        if (!force && System.currentTimeMillis() - lastSyncAt < CATALOG_THROTTLE_MS) {
            return@runCatching
        }
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot load configuration.")
        val dto = configApi.getConfig(authorization = token.toBearerOrNull())

        // Each list is guarded on its own: an empty (or absent) array would blank the UI it drives,
        // so treat it as "nothing to apply" and keep the previous cache rather than leaving the
        // agent with no products to pick or no chips to filter by. A response carrying only one of
        // the two still applies that one.
        val products = sanitizeConfigList(dto.products)
        if (products.isNotEmpty()) {
            catalogStore.save(products)
        }
        val statuses = sanitizeConfigList(dto.statuses)
        if (statuses.isNotEmpty()) {
            statusStore.save(statuses)
        }
        lastSyncAt = System.currentTimeMillis()
    }

    private fun String.toBearerOrNull(): String? {
        val token = trim().takeIf { it.isNotEmpty() } ?: return null
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }

    private companion object {
        const val CATALOG_THROTTLE_MS = 15_000L
    }
}

/**
 * Shown until the first `GET /api/config` succeeds on a fresh install. Mirrors the server's
 * documented default list; the server's copy always wins once fetched.
 */
val DEFAULT_PRODUCTS: List<String> = listOf(
    "Meghalaya Package",
    "Hampta Pass Trek",
    "Rishikesh Activities",
    "Spiti Package",
    "Ladakh Package",
    "Kerala Trip",
    "Adventure Activities",
    "Others",
)

/**
 * Lead statuses shown until the first `GET /api/config` succeeds on a fresh install. Mirrors the
 * server's documented default list, in the server's order; the server's copy always wins once
 * fetched.
 *
 * [BOOKED_STATUS] must stay in here: the booking form and the status lock key off that exact name,
 * so a fresh install has to be able to recognise a booked lead before any sync lands.
 */
val DEFAULT_LEAD_STATUSES: List<String> = listOf(
    "Fresh Leads",
    "Interested Leads",
    "Pre Prospect Leads",
    "Prospect Leads",
    BOOKED_STATUS,
    "Rejected Leads",
)

/**
 * Cleans one of the `GET /api/config` string lists (statuses or products): drops blanks and
 * case-insensitive duplicates while preserving the server's ordering — that order is the admin's
 * choice, so it is not sorted. For statuses the order is doubly load-bearing: it's the order the
 * filter chips and the status dropdown render in, i.e. the pipeline's own progression.
 */
fun sanitizeConfigList(values: List<String>?): List<String> {
    val seen = HashSet<String>()
    return values.orEmpty()
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .filter { seen.add(it.lowercase()) }
}

/**
 * Agent-filed bug reports, backed by `api/bugs` and offline-first like leads and notes: Room is
 * written first so the list repaints immediately, then the report is pushed.
 *
 * Reports are team-wide — [syncReports] pulls every agent's, so a report filed on one device shows up
 * on all of them, and [BugReport.status] reflects the backend's triage state.
 */
@Singleton
class BugReportRepository @Inject constructor(
    private val bugReportApi: BugReportApi,
    private val bugReportDao: BugReportDao,
    private val session: SessionManager,
) {

    fun observeReports(): Flow<List<BugReport>> =
        bugReportDao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Files a report: saves locally, then pushes to `POST api/bugs`.
     *
     * On a failed push the local row is deliberately **kept** (marked unsynced) rather than rolled
     * back the way `LeadsRepository.addNote` rolls back an unsent note. A note lives beside a
     * server-owned lead that will re-sync, so a stale local copy there is misleading; a bug report
     * has no server counterpart, and silently discarding text an agent just typed is worse than
     * showing it with a "not yet visible to other agents" marker.
     */
    suspend fun submitReport(title: String, description: String): Result<Unit> = runCatching {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        require(cleanTitle.isNotEmpty()) { "Please enter a short title." }
        require(cleanDescription.isNotEmpty()) { "Please describe the bug." }

        val localId = UUID.randomUUID().toString()
        bugReportDao.insert(
            BugReportEntity(
                id = localId,
                title = cleanTitle,
                description = cleanDescription,
                reporterName = session.getAgentName().ifBlank { "Me" },
                reporterId = session.getAgentId(),
                isSynced = false,
            )
        )

        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot file a report.")
        val created = bugReportApi.createBugReport(
            authorization = token.toBearerOrNull(),
            body = CreateBugReportRequest(title = cleanTitle, description = cleanDescription),
        )
        // Swap the optimistic row for the server's copy. Insert before delete so the Flow never
        // emits a list with neither, which would flicker the report out of the list and back.
        bugReportDao.insert(created.toEntity())
        bugReportDao.deleteById(localId)
    }.mapApiError()

    /**
     * Pulls every agent's reports from `GET api/bugs` and replaces the server-side rows, leaving
     * unsent local ones in place so an offline report can't be swept away before it's filed.
     */
    suspend fun syncReports(): Result<Unit> = runCatching {
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot load reports.")
        val dtos = bugReportApi.getBugReports(authorization = token.toBearerOrNull())
        bugReportDao.replaceServerReports(dtos.map { it.toEntity() })
    }.mapApiError()

    /**
     * Moves a report to a new [BugStatus] via `PUT api/bugs/{id}/status`.
     *
     * No UI calls this yet — see [BugReportApi.updateBugStatus] for why. Rejects unsent local
     * reports, whose UUID ids mean nothing to the server.
     */
    suspend fun updateStatus(reportId: String, status: String): Result<Unit> = runCatching {
        require(reportId.isNotBlank() && !reportId.contains('-')) {
            "This report hasn't reached the server yet."
        }
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot update this report.")
        val updated = bugReportApi.updateBugStatus(
            id = reportId,
            authorization = token.toBearerOrNull(),
            body = UpdateBugStatusRequest(status = status),
        )
        bugReportDao.insert(updated.toEntity())
    }.mapApiError()

    private fun String.toBearerOrNull(): String? {
        val token = trim().takeIf { it.isNotEmpty() } ?: return null
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }
}

@Singleton
class CallLogSyncRepository @Inject constructor(
    private val callsApi: CallsApi,
    private val callLogReader: CallLogReader,
    private val callSyncStore: CallSyncStore,
    private val session: SessionManager,
    private val leadDao: LeadDao,
) {

    // Serializes syncNewCalls. The call-log observer fires it repeatedly for one call (Android
    // writes the row, then updates duration on hang-up), so overlapping runs could both read the
    // same old watermark before either advanced it — and POST the same call twice, inflating the
    // backend's COUNT(*)-based totalDials. The lock makes each run see the prior run's watermark.
    private val syncMutex = Mutex()

    suspend fun syncNewCalls(): Result<Int> = syncMutex.withLock {
      runCatching {
        if (!callLogReader.hasPermission()) return@runCatching 0
        val token = session.getToken()?.takeIf { it.isNotBlank() } ?: return@runCatching 0
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"

        val all = callLogReader.readAll()
        if (all.isEmpty()) return@runCatching 0

        if (!callSyncStore.hasBaseline()) {
            callSyncStore.setWatermark(all.maxOf { it.id })
            return@runCatching 0
        }

        // Only post calls that (a) happened today and (b) are to an assigned lead. The backend
        // counts every posted call as a "dial", so sending the agent's whole call history
        // (incoming/spam/non-lead + past days) inflated the Historical report. Non-qualifying
        // calls still advance the watermark so they're never reprocessed.
        // Per-number assignment cutoff: a call only counts if it happened at/after the lead was
        // assigned to this agent. Keyed by last-10-digit phone key; if several leads share a number
        // the earliest stamp wins (most inclusive). A null stamp means "no restriction".
        val leads = leadDao.getAllLeads().first()
        val assignedByKey: Map<String, Long?> = leads
            .mapNotNull { lead ->
                val key = lead.phone.normalizedPhoneKey().ifEmpty { null } ?: return@mapNotNull null
                key to lead.assignedAt
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> if (times.any { it == null }) null else times.filterNotNull().min() }
        // Phone key -> lead id, so a logged call can be tied to its lead. When several leads share a
        // number, prefer the earliest-assigned (matches the most-inclusive cutoff in assignedByKey).
        val leadIdByKey: Map<String, String> = leads
            .mapNotNull { lead ->
                val key = lead.phone.normalizedPhoneKey().ifEmpty { null } ?: return@mapNotNull null
                Triple(key, lead.id, lead.assignedAt)
            }
            .groupBy { it.first }
            .mapValues { (_, rows) -> rows.minBy { it.third ?: Long.MAX_VALUE }.second }
        // Stable per-install id; combined with the device call-log id it makes clientCallId
        // deterministic, so a retried POST can't create a duplicate row (guide: idempotency).
        val installId = callSyncStore.getInstallId()
        val now = System.currentTimeMillis()
        // Reporting window. Previously this was today-only, which silently destroyed calls: a
        // non-matching entry still advanced the watermark, so a call placed at 23:58 and synced at
        // 00:01 — or any call from a day the agent never opened the app — was dropped forever, and
        // live-activity lost the real first/last call for that day. The lookback lets those late
        // arrivals through while still excluding genuinely old history from the Historical report.
        val windowStart = startOfDayMillis(now) - (REPORTING_LOOKBACK_DAYS - 1) * DAY_MS
        val windowEnd = startOfDayMillis(now) + DAY_MS

        val watermark = callSyncStore.getWatermark()
        val newCalls = all.filter { it.id > watermark }.sortedBy { it.id }
        if (newCalls.isEmpty()) return@runCatching 0

        var logged = 0
        for (entry in newCalls) {
            val status = callStatusFor(entry)

            // Stop before a row whose duration is still in flux (see [isDurationUnsettled]). Break,
            // not continue, so the watermark stays behind it and the next sync re-reads it with the
            // settled duration. Rows are ascending by id, so everything after this is newer still.
            if (isDurationUnsettled(entry, now)) break

            val key = entry.number.normalizedPhoneKey()
            val isInWindow = entry.dateMillis in windowStart until windowEnd
            val isLeadCall = assignedByKey.containsKey(key)
            // Exclude calls before the lead was assigned to this agent (a prior owner's history).
            val assignedAt = assignedByKey[key]
            val afterAssignment = assignedAt == null || entry.dateMillis >= assignedAt
            if (status == null || !isInWindow || !isLeadCall || !afterAssignment) {
                callSyncStore.setWatermark(entry.id)
                continue
            }
            val response = callsApi.logCall(
                authorization = bearer,
                body = LogCallRequest(
                    status = status,
                    clientCallId = "$installId-${entry.id}",
                    // Only a real ObjectId may go out: the backend casts this field and throws on
                    // anything else, and lead ids fall back to a numeric id or a phone number
                    // (Models.kt stableId). Dropping it keeps the call loggable — leadId is
                    // optional and contactNumber still lets the backend attribute it.
                    leadId = leadIdByKey[key]?.takeIf { isValidObjectId(it) },
                    duration = entry.durationSeconds,
                    contactNumber = entry.number.takeIf { it.isNotBlank() && it != "Unknown" },
                    // Canonical UTC (…Z) so the backend's live-status idle and live-activity
                    // first/last-call bucketing stay correct regardless of device timezone.
                    timestamp = formatIso8601Utc(entry.dateMillis),
                ),
            )

            if (!response.isSuccessful) {
                // A permanent rejection (duplicate clientCallId, malformed body) will fail
                // identically forever. Advancing past it keeps one poison row from blocking every
                // newer call behind it — which would silently freeze all reporting. Transient
                // failures (5xx, auth, throttling) stop the run so the calls are retried intact.
                if (!isPermanentCallLogError(response.code())) break
                callSyncStore.setWatermark(entry.id)
                continue
            }
            callSyncStore.setWatermark(entry.id)
            logged++
        }
        logged
      }
    }

    /**
     * Aggregated historical performance from GET /api/calls/historical. An agent's token scopes
     * the result to their own row(s); admins get all agents. Dates are inclusive ISO strings.
     */
    suspend fun getHistoricalReport(
        startDate: String? = null,
        endDate: String? = null,
        team: String? = null,
    ): Result<List<HistoricalReportDto>> = runCatching {
        callsApi.getHistorical(
            authorization = bearerOrThrow(),
            startDate = startDate,
            endDate = endDate,
            team = team,
        )
    }.mapApiError()

    /** Live idle metrics (last call + idle ms) from GET /api/calls/live-status. */
    suspend fun getLiveStatus(): Result<List<LiveStatusDto>> = runCatching {
        callsApi.getLiveStatus(authorization = bearerOrThrow())
    }.mapApiError()

    /** Today's first/last call bounds and talk time per agent from GET /api/calls/live-activity. */
    suspend fun getLiveActivity(): Result<List<LiveActivityDto>> = runCatching {
        callsApi.getLiveActivity(authorization = bearerOrThrow())
    }.mapApiError()

    /**
     * The per-call rows behind the aggregates, from GET /api/calls/long-calls. [metric] selects
     * "connected" (every connected call) or "longCalls" — calls of [LONG_CALL_THRESHOLD_SECONDS] or
     * more, which is what the backend uses when the parameter is omitted.
     */
    suspend fun getLongCalls(
        metric: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        agentId: String? = null,
    ): Result<List<LongCallDto>> = runCatching {
        callsApi.getLongCalls(
            authorization = bearerOrThrow(),
            agentId = agentId,
            startDate = startDate,
            endDate = endDate,
            metric = metric,
        )
    }.mapApiError()

    private fun bearerOrThrow(): String {
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Not signed in")
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }
}

/**
 * True when POST /api/calls failed in a way that retrying the identical body can never fix, so the
 * call should be skipped rather than blocking the queue behind it.
 *
 * Only body-level rejections qualify: 400 (missing/invalid fields, or the duplicate-clientCallId
 * case the API guide documents) and the 409/422 variants a stricter backend may use for the same
 * conditions. Everything else keeps the current call queued: 401/403 mean the token needs
 * attention, 404 means the endpoint is misconfigured, 429 means slow down, and 5xx is the server's
 * problem — skipping any of those would discard real calls that would have succeeded later.
 */
fun isPermanentCallLogError(code: Int): Boolean = code == 400 || code == 409 || code == 422

/**
 * True when [value] can be cast to a MongoDB ObjectId — exactly 24 hex characters.
 *
 * POST /api/calls casts `leadId` server-side and throws a CastError on anything else, which comes
 * back as a 5xx. Since 5xx is treated as transient, an unqualified id would stall the sync queue on
 * that call forever, so a lead id is only sent when it passes this check.
 */
fun isValidObjectId(value: String): Boolean =
    value.length == 24 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

fun callStatusFor(entry: CallLogEntry): String? = when (entry.type) {
    CallType.VOICEMAIL -> "Voicemail"
    CallType.MISSED -> "Missed"
    CallType.INCOMING -> if (entry.durationSeconds > 0) "Connected" else "Missed"
    CallType.OUTGOING -> if (entry.durationSeconds > 0) "Connected" else "Failed"
    CallType.REJECTED -> "Failed"
    CallType.BLOCKED, CallType.UNKNOWN -> null
}

fun presentDayCount(calls: List<CallLogEntry>): Int =
    calls.map { formatApiDate(it.dateMillis) }.distinct().size

fun computeIdleSeconds(callsOldestFirst: List<CallLogEntry>): Long {
    if (callsOldestFirst.size < 2) return 0L
    var idle = 0L
    for (i in 0 until callsOldestFirst.size - 1) {
        val current = callsOldestFirst[i]
        val next = callsOldestFirst[i + 1]
        val currentEndMs = current.dateMillis + current.durationSeconds * 1000
        val gapSeconds = (next.dateMillis - currentEndMs) / 1000
        if (gapSeconds > 0) idle += gapSeconds
    }
    return idle
}

fun bookingFromCalls(
    calls: List<CallLogEntry>,
    now: Long = System.currentTimeMillis(),
    todayOnly: Boolean = true,
): UpdateBookingRequest? {
    if (calls.isEmpty()) return null
    // Two windows share this builder:
    //  - todayOnly = true  -> Dashboard-style "today's calls" (the historical default).
    //  - todayOnly = false -> the lead-detail cumulative window. Callers pre-filter [calls] to
    //    the lead's post-assignment history (CallLogReader.callsForNumber sinceMillis), so here we
    //    count the whole list: every dial/call since the lead was assigned, across all days.
    // Either way, an empty window returns null so an idle lead never overwrites stored values with zeros.
    val scoped = if (todayOnly) {
        val dayStart = startOfDayMillis(now)
        val dayEnd = dayStart + 24L * 60 * 60 * 1000
        calls.filter { it.dateMillis in dayStart until dayEnd }
    } else {
        calls
    }.sortedBy { it.dateMillis }
    if (scoped.isEmpty()) return null
    val talkSeconds = scoped.sumOf { it.durationSeconds }
    // Same `countsAsDial` rule the Dashboard uses, which is what stops the lead card and lead detail
    // from printing a different dial count than the Dashboard for the very same calls.
    val dials = scoped.count { it.countsAsDial }
    return UpdateBookingRequest(
        totalDial = dials,
        dailyDial = dials,
        connected = scoped.count { it.countsAsDial && it.durationSeconds > 0 },
        talkTime = formatTalkTimeClock(talkSeconds),
        dailyTalkTime = formatTalkTimeClock(talkSeconds),
        firstCall = scoped.first().dateMillis.let(::formatIso8601),
        lastCall = scoped.last().dateMillis.let(::formatIso8601),
    )
}

internal const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * How many days back `syncNewCalls` will still post a call, counting today as day 1. Wider than
 * today-only so a call made near midnight, or on a day the app was never opened / had no network,
 * still reaches the server instead of being dropped when the watermark passes it.
 */
internal const val REPORTING_LOOKBACK_DAYS = 3L

/**
 * How recent a call-log row must be before we treat its `duration` as still in flux. Android writes
 * the row at call start and rewrites DURATION on hang-up, so a row younger than this may be an
 * in-progress call whose duration is 0. Posting that would record it as Failed/Missed with no talk
 * time, and the watermark would move past it before the real duration ever landed.
 */
internal const val CALL_SETTLE_MS = 15_000L

/**
 * True when [entry] is too fresh to trust its duration, so posting it now would send a wrong
 * `status`/`duration` pair that the watermark then makes permanent.
 *
 * Only zero-duration rows wait: a row already carrying a duration has been rewritten on hang-up, so
 * it is final no matter how recent. A missed or rejected call is legitimately zero-duration and
 * would otherwise wait out the window for nothing, so those settle immediately too — the ambiguous
 * case is an in-progress incoming/outgoing call, which is exactly what this holds back.
 */
fun isDurationUnsettled(entry: CallLogEntry, now: Long): Boolean {
    if (entry.durationSeconds > 0) return false
    val inProgressCandidate = entry.type == CallType.OUTGOING || entry.type == CallType.INCOMING
    if (!inProgressCandidate) return false
    val age = now - entry.dateMillis
    // A negative age means the row is stamped in the future (device clock moved); treat it as
    // unsettled rather than posting a timestamp that would corrupt the server's idle computation.
    return age < CALL_SETTLE_MS
}

private fun startOfDayMillis(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun datesFromCalls(calls: List<CallLogEntry>): UpdateDatesRequest? {
    if (calls.isEmpty()) return null
    return UpdateDatesRequest(
        startDate = calls.minByOrNull { it.dateMillis }?.dateMillis?.let(::formatApiDate),
        dueDate = calls.maxByOrNull { it.dateMillis }?.dateMillis?.let(::formatApiDate),
    )
}

val CALL_OUTCOME_LABELS = listOf("Dialed", "Connected")

fun callLabelFor(calls: List<CallLogEntry>): String? {
    if (calls.isEmpty()) return null
    return if (calls.any { it.durationSeconds > 0 }) "Connected" else "Dialed"
}

fun mergeLabels(existing: List<String>, callLabel: String?): List<String> {
    val kept = existing.filter { it !in CALL_OUTCOME_LABELS }
    return if (callLabel == null) kept else kept + callLabel
}
