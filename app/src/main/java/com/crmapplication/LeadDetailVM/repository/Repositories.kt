package com.crmapplication.LeadDetailVM.repository

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.crmapplication.LeadDetailVM.local.BugReportDao
import com.crmapplication.LeadDetailVM.local.BugReportEntity
import com.crmapplication.LeadDetailVM.local.CallDao
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
import com.crmapplication.LeadDetailVM.remote.BookingEnvelopeDto
import com.crmapplication.LeadDetailVM.remote.BookingsApi
import com.crmapplication.LeadDetailVM.remote.BugReportApi
import com.crmapplication.LeadDetailVM.remote.BugStatus
import com.crmapplication.LeadDetailVM.remote.CreateBugReportRequest
import com.crmapplication.LeadDetailVM.remote.UpdateBugStatusRequest
import com.crmapplication.LeadDetailVM.remote.UpdateLeadInfoRequest
import com.crmapplication.LeadDetailVM.remote.CallsApi
import com.crmapplication.LeadDetailVM.remote.CloudinaryApi
import com.crmapplication.LeadDetailVM.remote.CloudinaryUploadDto
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
import com.crmapplication.LeadDetailVM.remote.UploadSignatureDto
import com.crmapplication.LeadDetailVM.remote.VerifyEmailRequest
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallLogReader
import com.crmapplication.calllog.CallType
import com.crmapplication.calllog.countsAsConnected
import com.crmapplication.calllog.countsAsDial
import com.crmapplication.calllog.normalizedPhoneKey
import com.crmapplication.calllog.talkTimeSeconds
import com.crmapplication.utils.CallSyncStore
import com.crmapplication.utils.cloudinaryPublicId
import com.crmapplication.utils.DocumentPartFactory
import com.crmapplication.utils.DueDateStore
import com.crmapplication.utils.ProductCatalogStore
import com.crmapplication.utils.SessionCleaner
import com.crmapplication.utils.SessionManager
import com.crmapplication.utils.SessionScopedState
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.util.UUID
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val session: SessionManager,
    private val sessionCleaner: SessionCleaner,
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

    /**
     * Ends the session: forgets the credentials, then erases everything the agent left on the device.
     *
     * Token first, data second, and the order is load-bearing. Clearing the token immediately makes any
     * sync that starts from here fail with "Not logged in", so nothing can re-populate the tables
     * behind the wipe. Wiping first would leave a window where an in-flight request still holds a valid
     * token and writes the old agent's leads back into an emptied database.
     *
     * Suspending rather than fire-and-forget so the caller can wait: the login screen must not be able
     * to accept a new agent while the previous one's rows are still on disk. See
     * `AuthViewModel.logout`.
     *
     * One narrow window remains — a `syncLeads` already past its token read when logout begins can
     * still land afterwards. The next sync's `deleteLeadsNotIn` prunes those rows, and the mutex in
     * `LeadsRepository` keeps it to a single request rather than a pile.
     */
    suspend fun logout() {
        session.clear()
        sessionCleaner.clearForLogout()
    }
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

/** Our own backend's flat `{"error": "..."}` / `{"message": "..."}`, from the signature endpoint. */
internal fun parseSignatureError(body: String?): String? = runCatching {
    body?.takeIf { it.isNotBlank() }?.let {
        val parsed = Gson().fromJson(it, UploadSignatureDto::class.java)
        parsed?.error?.takeIf { e -> e.isNotBlank() }
            ?: parsed?.message?.takeIf { m -> m.isNotBlank() }
    }
}.getOrNull()

/** Cloudinary's nested `{"error": {"message": "..."}}`. */
/**
 * Pulls the booking API's own `error` string out of a failed response.
 *
 * Worth surfacing verbatim: its messages name the offending field ("Phone Number must be exactly 10
 * digits starting with 6, 7, 8, or 9", "transaction ID already exists"), which is exactly what the
 * agent needs to fix the form. A generic "Request failed (400)" would send them guessing.
 */
internal fun parseBookingError(body: String?): String? = runCatching {
    body?.takeIf { it.isNotBlank() }?.let {
        Gson().fromJson(it, BookingEnvelopeDto::class.java)?.error?.takeIf { e -> e.isNotBlank() }
    }
}.getOrNull()

/** Only reached when the response carried no parseable `error` of its own. */
internal fun friendlyBookingFailure(code: Int): String = when (code) {
    // Not a bad payload: the route isn't there. Worth its own wording because the agent's form was
    // fine and re-typing it won't help — `ApiConfig.BOOKING_BASE_URL` needs to point at the booking
    // service. Without this the message read "Couldn't save the booking (HTTP 404)", which invites
    // exactly the pointless retry it should prevent.
    404 -> "The booking service isn't reachable at its configured address. " +
        "Nothing was saved and retrying won't help — please report this, it needs a backend fix."
    401, 403 -> "You're not authorised to create this booking. Try signing in again."
    409 -> "A booking with that transaction ID already exists."
    413 -> "The screenshot is too large. The maximum is ${DocumentPartFactory.MAX_SCREENSHOT_SIZE_MB} MB."
    else -> "Couldn't save the booking (HTTP $code)."
}

internal fun parseCloudinaryError(body: String?): String? = runCatching {
    body?.takeIf { it.isNotBlank() }?.let {
        Gson().fromJson(it, CloudinaryUploadDto::class.java)?.error?.message?.takeIf { m -> m.isNotBlank() }
    }
}.getOrNull()

/**
 * Turns a Cloudinary upload failure into something an agent can act on.
 *
 * Two cases get rewritten because Cloudinary's own wording sends the reader in the wrong direction:
 * its size error quotes the account limit without saying what to do, and its signature error reads
 * like a client bug when in practice it means the signed parameters and the sent parameters
 * disagreed — a backend-side mismatch the agent cannot fix by retrying.
 */
internal fun cloudinaryFailureMessage(cloudinaryMessage: String?, code: Int): String {
    val message = cloudinaryMessage?.takeIf { it.isNotBlank() }
        ?: return "Upload failed (HTTP $code)."
    return when {
        message.contains("File size too large", ignoreCase = true) ||
            message.contains("too large", ignoreCase = true) ->
            "That file is too large to upload. The maximum is ${DocumentPartFactory.MAX_FILE_SIZE_MB} MB."
        message.contains("Invalid Signature", ignoreCase = true) ->
            "Upload was rejected as unauthorised. Please try again, and report it if it keeps failing."
        message.contains("Invalid extension", ignoreCase = true) ||
            message.contains("Unsupported", ignoreCase = true) ->
            "That file type isn't supported."
        else -> message
    }
}

/** A multipart form field. No content type: these are plain scalars, and Cloudinary expects them bare. */
private fun String.toFormField(): RequestBody = toRequestBody()

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
    // Only for the agent's voicemail marks: the Dashboard computes from the device call log, but that
    // log can't express "reached a machine", so the marks have to come from here.
    private val callDao: CallDao,
    private val attendanceRepo: AttendanceRepository,     // new
    sessionCleaner: SessionCleaner,
) : SessionScopedState {

    /** Today's attendance for the Dashboard card. */
    val attendance: StateFlow<AttendanceSnapshot?> get() = attendanceRepo.snapshot

    init {
        // This Singleton outlives a logout, so its caches have to be told to forget the agent.
        sessionCleaner.register(this)
    }

    /** yyyy-MM-dd of the day we last successfully pushed "Present"; guards against re-pushing every refresh. */
    private var lastAttendancePush: String? = null

    /**
     * Last values fetched in the network stage, so the local stage can build with them instead of
     * nulls.
     *
     * Without this the local stage always emitted `metrics = null` and zero attendance, so every
     * refresh flashed "₹0 / ₹0" and "0P / 0A" on the Monthly card before the network stage replaced
     * them a moment later. Seeding from the last known values means the card only ever changes when a
     * fetch actually returns something different.
     *
     * It is also what makes [NETWORK_THROTTLE_MS] safe: a skipped network stage keeps the real figures
     * on screen rather than reverting them to placeholders.
     */
    @Volatile private var cachedMetrics: AgentMetricsDto? = null
    @Volatile private var cachedPresent: Int = 0
    @Volatile private var cachedAbsent: Int = 0
    @Volatile private var cachedHalf: Int = 0

    /** Admin-set "P"/"A" for today, from the attendance log or metrics. Null = not known yet. */
    @Volatile private var cachedTodayStatus: String? = null

    /** When the network stage last completed. Guards the duplicate load on first composition. */
    @Volatile private var lastNetworkAt: Long = 0L

    /**
     * Last computed dashboard, kept in-memory on this Singleton so it survives ViewModel
     * recreation. Re-entering the dashboard can paint this instantly instead of a blank spinner
     * while a fresh compute runs. Holds the local-only stage until the network stage replaces it.
     */
    @Volatile
    var lastData: DashboardData? = null
        private set

    /**
     * Drops the cached dashboard and the attendance guard on logout.
     *
     * [lastData] is the most visible part of the leak this fixes: `DashboardViewModel.init` paints it
     * immediately and deliberately, so without this the next agent's first frame showed the previous
     * agent's dials, talk time and booking figures.
     */
    override fun resetSessionState() {
        lastData = null
        lastAttendancePush = null
        // Per-agent figures: the monthly target and the P/A counts belong to whoever was signed in.
        // Leaving them would show the previous agent's target on the next agent's first frame.
        cachedMetrics = null
        cachedPresent = 0
        cachedAbsent = 0
        cachedTodayStatus = null
        lastNetworkAt = 0L
    }

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
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + DAY_MILLIS
        val startOfMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // --- Stage 1: local only ---
        val allCalls = callLogReader.readAll().withVoicemailMarks(callDao.getVoicemailMarkedIds())
        val leads = leadDao.getAllLeads().first()
        val assignedByKey = assignedCutoffs(leads)
        val auth = resolveAgentAuth()

        // Today's talk time decides today's attendance, straight from the phone's call log.
        val talkSeconds = leadCallsToday(allCalls, startOfDay, endOfDay, assignedByKey)
            .sumOf { it.talkTimeSeconds.toLong() }
        val localAttendance = attendanceRepo.update(talkSeconds, auth?.id)

        val localDaily = buildDailyStats(
            allCalls, startOfDay, endOfDay, now.timeInMillis, localAttendance.status, assignedByKey,
        )
        val localMonthly = buildMonthlyStats(startOfMonth, cachedPresent, cachedHalf, cachedAbsent, cachedMetrics)
        DashboardData(daily = localDaily, monthly = localMonthly)
            .also { lastData = it }
            .let { emit(it) }

        if (System.currentTimeMillis() - lastNetworkAt < NETWORK_THROTTLE_MS) return@flow

        // --- Stage 2: network (best effort, all at once) ---
        coroutineScope {
            val metricsDeferred = async { fetchAgentMetrics() }
            val attendanceLogsDeferred = async { fetchAttendanceLogs() }
            val monthlyDeferred = async {
                fetchMonthlyAttendance(
                    month = now.get(Calendar.MONTH) + 1,
                    year = now.get(Calendar.YEAR),
                )
            }
            val serverTodayDeferred = async {
                if (auth != null) attendanceRepo.loadServerToday(auth.id, auth.bearer)
            }

            val metrics = metricsDeferred.await()
            val attendanceLogs = attendanceLogsDeferred.await()
            val monthlyAttendance = monthlyDeferred.await()
            serverTodayDeferred.await()

            // Recompute with whatever the server held, then send the status if it has gone up.
            attendanceRepo.update(talkSeconds, auth?.id)
            if (auth != null) attendanceRepo.pushIfRaised(auth.id, auth.bearer)
            val finalStatus = attendanceRepo.snapshot.value?.status ?: localAttendance.status

            // Monthly P / A / H from the server; the old admin-set log is only a fallback.
            val monthPrefix = formatApiDate(startOfDay).take(7)
            val monthLogs = attendanceLogs.filter { it.date.orEmpty().startsWith(monthPrefix) }
            val presentCount = monthlyAttendance?.fullDay ?: monthlyAttendance?.present
            ?: monthLogs.count { it.status?.trim()?.uppercase() == "P" }
            val halfCount = monthlyAttendance?.halfDay ?: 0
            val absentCount = monthlyAttendance?.absent
                ?: monthLogs.count { it.status?.trim()?.uppercase() == "A" }

            val effectiveMetrics = metrics ?: cachedMetrics      // a failed call must not blank the card

            val daily = buildDailyStats(allCalls, startOfDay, endOfDay, now.timeInMillis, finalStatus, assignedByKey)
            val monthly = buildMonthlyStats(startOfMonth, presentCount, halfCount, absentCount, effectiveMetrics)

            cachedMetrics = effectiveMetrics
            cachedPresent = presentCount
            cachedHalf = halfCount
            cachedAbsent = absentCount
            lastNetworkAt = System.currentTimeMillis()

            DashboardData(daily = daily, monthly = monthly)
                .also { lastData = it }
                .let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    private fun assignedCutoffs(leads: List<LeadEntity>): Map<String, Long?> = leads
        .mapNotNull { lead ->
            val key = lead.phone.normalizedPhoneKey().ifEmpty { null } ?: return@mapNotNull null
            key to lead.assignedAt
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, times) -> if (times.any { it == null }) null else times.filterNotNull().min() }

    /** Today's calls to the agent's own leads, oldest first. Shared by the Dashboard stats and attendance. */
    private fun leadCallsToday(
        allCalls: List<CallLogEntry>,
        startOfDay: Long,
        endOfDay: Long,
        assignedByKey: Map<String, Long?>,
    ): List<CallLogEntry> = allCalls
        .filter { call ->
            val key = call.number.normalizedPhoneKey()
            if (!assignedByKey.containsKey(key)) return@filter false
            val assignedAt = assignedByKey[key]
            assignedAt == null || call.dateMillis >= assignedAt
        }
        .filter { it.dateMillis in startOfDay until endOfDay }
        .sortedBy { it.dateMillis }

    /** Background path (the call-sync job): works out today's talk time and sends a raised status, without building the dashboard. */
    suspend fun syncAttendanceOnly() {
        if (!callLogReader.hasPermission()) return
        val auth = resolveAgentAuth() ?: return
        val startOfDay = startOfDayMillis(System.currentTimeMillis())
        val allCalls = callLogReader.readAll().withVoicemailMarks(callDao.getVoicemailMarkedIds())
        val assignedByKey = assignedCutoffs(leadDao.getAllLeads().first())
        val talk = leadCallsToday(allCalls, startOfDay, startOfDay + DAY_MILLIS, assignedByKey)
            .sumOf { it.talkTimeSeconds.toLong() }

        attendanceRepo.loadServerToday(auth.id, auth.bearer)
        attendanceRepo.update(talk, auth.id)
        attendanceRepo.pushIfRaised(auth.id, auth.bearer)
    }

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
        val today = leadCallsToday(allCalls, startOfDay, endOfDay, assignedByKey)

        // Idle = how long the agent has been off the phone right now: last call ended → now.
        val idleSeconds = idleSecondsSinceLastCall(today, nowMillis)

        // Talk time, not raw duration: a voicemail carries a real length but was never answered, so
        // it must not be credited as time the agent spent talking. See `talkTimeSeconds`.
        val totalTalk = today.sumOf { it.talkTimeSeconds }
        // Dials include answered inbound calls, not just outgoing ones — see `countsAsDial` for the
        // rule. Counting outgoing alone was what made this tile report 2 against the web historical
        // report's 5 for the same day.
        val dials = today.count { it.countsAsDial }
        // Connected is a subset of the dialled calls, so it can never print higher than Total Dial.
        // Uses countsAsConnected, so an agent-marked voicemail drops out of this too — it kept a
        // duration but reached a machine. The dial itself still counts; the agent did place the call.
        val connected = today.count { it.countsAsConnected }
        val callsPerNumber = today.groupingBy { it.number.normalizedPhoneKey() }.eachCount()
        val unique = callsPerNumber.size
        // "Call more than 5 min" — counts individual long calls by duration, matching the backend's
        // `longCalls` metric (GET api/calls/long-calls, ≥ LONG_CALL_THRESHOLD_SECONDS) so this tile and
        // the web historical report agree. It previously counted *numbers dialled twice or more*, which
        // measured repeat attempts rather than call length and disagreed with the server for the same
        // day. Uses talkTimeSeconds, so a long voicemail recording is not a long call.
        val callMoreThan = today.count { it.talkTimeSeconds >= LONG_CALL_THRESHOLD_SECONDS }

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
                "P" -> "Full Day"
                "H" -> "Half Day"
                "A" -> "Absent"
                "WO" -> "Week Off"
                else -> "—"
            },
        )
    }

    private fun buildMonthlyStats(
        startOfMonth: Long,
        presentCount: Int,
        halfCount: Int,
        absentCount: Int,
        metrics: AgentMetricsDto?,
    ): MonthlyStats {
        val target = metrics?.monthlyTarget ?: 0
        val completed = metrics?.targetCompleted ?: 0
        val bookings = metrics?.bookingCount ?: 0
        val bookingTarget = metrics?.targetBookingCount ?: 0

        return MonthlyStats(
            month = formatMonthLabel(startOfMonth),
            monthlyTarget = "${formatIndianAmount(completed.toLong())} / ${formatIndianAmount(target.toLong())}",
            bookingCount = "$bookings / $bookingTarget",
            totalSaleAmount = formatIndianAmount(completed.toLong()),
            // Full days / absent / half days, finished days only. Today is the progress bar.
            attendance = "${presentCount}P / ${absentCount}A / ${halfCount}H",
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val TAG = "DashboardRepo"

        /**
         * How long the network stage is suppressed after it completes.
         *
         * Sized to collapse the duplicate load on screen open (`DashboardViewModel.init` and the
         * screen's `ON_RESUME` observer fire milliseconds apart), not to cache aggressively — the
         * dashboard has no manual refresh, so a long window here would show stale attendance. The
         * local stage is never throttled, so returning to the screen always recomputes today's call
         * figures from the device log.
         */
        const val NETWORK_THROTTLE_MS = 10_000L
    }
}

/**
 * When this lead's booking happened, or null if it never was booked.
 *
 * [LeadEntity.bookedAt] is the real stamp, written by `bookLead`. The fallback exists for leads booked
 * **before** that column shipped (schema 16): those have no `bookedAt`, and the booking service has no
 * GET route to recover one, so their status-change time is the only trace of when the sale happened.
 * For a booked lead that proxy is sound — reaching `Booked` locks the status, so nothing moves
 * `statusChangedAt` afterwards.
 *
 * The fallback is gated on the status for exactly that reason: on any other lead `statusChangedAt` is
 * just "when the agent last changed the status", which is not a booking at all.
 */
private fun LeadEntity.bookedInstant(): Long? =
    bookedAt ?: statusChangedAt?.takeIf { status.equals(BOOKED_STATUS, ignoreCase = true) }

/**
 * The bookings that belong to the month starting at [startOfMonth] — the shared basis for both monthly
 * booking figures, so the count and the amount can never disagree about which sales they cover.
 *
 * Scoped by booking instant, not by current status: once money is booked in a month it belongs to that
 * month, even if an admin later moves the lead out of `Booked`. There is no upper bound because
 * [startOfMonth] is always the current month — a booking can't be in the future.
 */
private fun bookedThisMonth(leads: List<LeadEntity>, startOfMonth: Long): List<LeadEntity> =
    leads.filter { (it.bookedInstant() ?: return@filter false) >= startOfMonth }

/**
 * Total value of the bookings made this month, summed from the local `bookedAmount` stamps.
 *
 * On-device because the booking service offers no way to ask: `BookingsApi` has only `createBooking`,
 * and the leads payload carries no amount. So the figure covers bookings this device recorded — it
 * starts from ₹0 on a fresh install, and a booking made on another phone isn't in it.
 *
 * A booking with no stored amount (either confirmed without a total, or made before schema 16) counts
 * as zero rupees while still being a real booking — [monthlyBookingCount] is what reflects it.
 */
internal fun monthlySaleAmount(leads: List<LeadEntity>, startOfMonth: Long): Long =
    bookedThisMonth(leads, startOfMonth).sumOf { it.bookedAmount ?: 0L }

/**
 * How many leads were booked this month.
 *
 * Month-scoped on purpose: this sits on the **Monthly** card, so like the sale amount it resets to 0
 * when the month rolls over. It previously counted every `Booked` lead ever held, over the total lead
 * count — a lifetime figure on a monthly card, which never reset and grew with every reassignment.
 */
internal fun monthlyBookingCount(leads: List<LeadEntity>, startOfMonth: Long): Int =
    bookedThisMonth(leads, startOfMonth).size

/**
 * Formats whole rupees the Indian way — last three digits, then pairs: `150000` → `₹1,50,000`.
 *
 * Grouped by hand rather than with `NumberFormat`: the JDK's `en-IN` grouping has shifted between
 * versions, and this has to match what an agent sees in the backend regardless of which JVM the unit
 * tests run on. No decimals, because every amount in the booking form is a whole-rupee `Long`.
 */
internal fun formatIndianAmount(amount: Long): String {
    val digits = kotlin.math.abs(amount).toString()
    val grouped = if (digits.length <= 3) digits else {
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        // Pairs, right to left: "10000" → "1,00,00" (so 10000000 reads ₹1,00,00,000 — one crore).
        val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
        "$pairs,$last3"
    }
    return if (amount < 0) "-₹$grouped" else "₹$grouped"
}

@Singleton
class LeadsRepository @Inject constructor(
    private val leadsApi: LeadsApi,
    private val bookingsApi: BookingsApi,
    private val uploadApi: UploadApi,
    private val cloudinaryApi: CloudinaryApi,
    private val documentPartFactory: DocumentPartFactory,
    private val leadDao: LeadDao,
    private val noteDao: NoteDao,
    private val statusHistoryDao: StatusHistoryDao,
    private val session: SessionManager,
    private val dueDateStore: DueDateStore,
    sessionCleaner: SessionCleaner,
) : SessionScopedState {

    init {
        sessionCleaner.register(this)
    }

    /**
     * Forgets that a sync ever happened, so the new agent's first `syncLeads` actually hits the
     * network. Without this the 30s throttle below would skip it — and since logout has just emptied
     * Room, the new agent would sit looking at an empty list until the throttle expired.
     */
    override fun resetSessionState() {
        lastSyncAt = 0L
    }

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
                // Booking value: purely local, so `prior` is the ONLY source — there is no server
                // field to fall back to and `dto.toEntity()` always leaves these null. Carried
                // unconditionally rather than with the `entity.x ?: prior?.x` precedence used above,
                // because an incoming null here means "the payload doesn't carry this", never
                // "cleared". Omitting these two lines would zero the dashboard's monthly sale figure
                // on the next refresh.
                bookedAmount = prior?.bookedAmount,
                bookedAt = prior?.bookedAt,
                // Call-log cutoff: calls before this instant aren't this agent's work.
                //
                // Sourced from the server's `createdAt`, NOT from the moment this lead was first seen
                // on this device. Room uses fallbackToDestructiveMigration(), so a first-sight stamp
                // was re-set to "now" on every schema bump and every reinstall — which moved the
                // cutoff forward and made the lead's entire call history disappear from the detail
                // screen. `createdAt` comes from the payload, so it survives both.
                //
                // The backend exposes no assignment date (ApiLeadDto has createdAt/updatedAt only),
                // so creation is the proxy. It can be earlier than the real assignment, which is
                // harmless here: calls made before this agent held the lead were made by someone
                // else on another device and so aren't in this device's call log at all.
                //
                // Takes the EARLIEST of stored and incoming, unlike the server-wins precedence used
                // for the fields above. This cutoff may only ever move backwards — `createdAt`
                // degrades to `now` when the server omits it (Models.kt `toEpochMillisOrNow`), and
                // letting that overwrite an earlier stored stamp would hide history all over again.
                assignedAt = listOfNotNull(prior?.assignedAt, entity.createdAt).min(),
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
              // The list endpoint may send an empty notes array; trusting it would wipe every note on the
              // phone. The lead screen pulls the authoritative copy itself via refreshLeadNotes.
              if (id.isNotBlank() && !dto.notes.isNullOrEmpty()) reconcileNotes(id, dto.notes)
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

            // Same lock as syncLeads: a background refresh can no longer start before this save and
            // finish after it, which put the old notes list back and made the new note vanish.
            syncMutex.withLock {
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

                val serverNotes = updated.notes
                if (serverNotes != null) {
                    reconcileNotes(leadId, serverNotes)
                } else {
                    // The server saved the note but didn't send the list back. Pull it instead of
                    // deleting the temporary row and leaving the agent with a note that "disappeared".
                    runCatching { pullNotes(leadId) }
                }
                noteDao.deleteNoteById(optimisticId)
            }
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

    /** Fetches one lead's notes and replaces the stored server copy. Caller must hold [syncMutex]. */
    private suspend fun pullNotes(leadId: String) {
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot refresh notes.")
        val dto = leadsApi.getLead(id = leadId, authorization = token.toBearerOrNull())
        reconcileNotes(leadId, dto.notes)
    }

    private suspend fun reconcileNotes(leadId: String, apiNotes: List<ApiNoteDto>?) {
        if (apiNotes == null) return
        noteDao.replaceServerNotes(leadId, apiNotes.map { it.toEntity(leadId) })
    }

    /**
     * Attaches a file to a lead by uploading it, then writing a note that carries the resulting URL.
     *
     * The upload goes **straight to Cloudinary**, in three hops: ask our backend to sign a set of
     * upload parameters, POST the file to Cloudinary with that signature, then note the `secure_url`
     * it returns. The file never passes through our own API, which is the point — our host caps a
     * request body at 4.5 MB, so routing a 10 MB attachment through it could not work regardless of
     * timeouts. It also means one network hop instead of two.
     *
     * Only the transport changed. The note-writing half is untouched: [addNote] still owns the
     * optimistic Room insert and its rollback, so an attachment that fails to attach leaves no
     * phantom note behind.
     */
    suspend fun uploadDocument(leadId: String, uri: Uri): Result<String> = runCatching {
        val token = session.getToken()?.toBearerOrNull()
            ?: error("Not logged in — cannot upload.")
        val (part, meta) = documentPartFactory.build(uri)
            ?: error("Couldn't read the selected file.")

        // The same id has to reach the backend (to be signed) and Cloudinary (as a form field), so it
        // is generated once here rather than derived twice from the file name.
        val publicId = cloudinaryPublicId(meta.fileName)
        val signature = requestUploadSignature(token, publicId)

        val fileUrl = if (signature != null) {
            uploadToCloudinary(part, signature)
        } else {
            // Older backend without api/upload/signature — see legacyUpload.
            legacyUpload(token, part)
        }

        addNote(leadId, text = meta.fileName, imageUrl = fileUrl).getOrThrow()
        fileUrl
    }

    /**
     * Fetches signed Cloudinary credentials, or returns null when this backend has no such endpoint.
     *
     * Null means "not deployed here", and only that: a 404/405/501 is the shape of a route that does
     * not exist. Every other failure is reported, because they are all things the agent or the
     * backend team needs to see rather than silently work around — a 401 means the session expired
     * (the legacy path would reject it too), and a 500 means the backend is missing its Cloudinary
     * credentials, which no amount of client retrying fixes.
     */
    private suspend fun requestUploadSignature(
        token: String,
        publicId: String,
    ): UploadSignatureDto? {
        val response = uploadApi.uploadSignature(authorization = token, publicId = publicId)
        if (!response.isSuccessful) {
            if (response.code() in ENDPOINT_ABSENT_CODES) {
                Log.i(TAG, "No api/upload/signature (HTTP ${response.code()}); using legacy upload.")
                return null
            }
            val serverMessage = parseSignatureError(response.errorBody()?.string())
            error(serverMessage ?: "Couldn't prepare the upload (HTTP ${response.code()}).")
        }
        val body = response.body()
            ?: error("Couldn't prepare the upload — the server sent an empty response.")

        // Validated together rather than defaulted individually: a signature is only usable if every
        // part of it arrived, and Cloudinary's reply to a partial one is an opaque 401.
        if (body.signature.isNullOrBlank() ||
            body.apiKey.isNullOrBlank() ||
            body.cloudName.isNullOrBlank() ||
            body.timestamp == null
        ) {
            error(
                body.error?.takeIf { it.isNotBlank() }
                    ?: body.message?.takeIf { it.isNotBlank() }
                    ?: "Couldn't prepare the upload — the server sent incomplete credentials."
            )
        }
        return body
    }

    /**
     * Posts the file to Cloudinary with the signed parameters and returns its `secure_url`.
     *
     * The form fields are built from what the signature response actually contained, never from what
     * we asked for. Cloudinary recomputes the signature over the exact parameters it receives, so a
     * field the backend did not sign — even a correct-looking one — fails the upload wholesale with
     * "Invalid Signature" rather than being ignored.
     */
    private suspend fun uploadToCloudinary(
        part: MultipartBody.Part,
        signature: UploadSignatureDto,
    ): String {
        val fields = buildMap {
            put("api_key", signature.apiKey!!.toFormField())
            put("timestamp", signature.timestamp!!.toString().toFormField())
            put("signature", signature.signature!!.toFormField())
            signature.folder?.takeIf { it.isNotBlank() }?.let { put("folder", it.toFormField()) }
            signature.publicId?.takeIf { it.isNotBlank() }?.let { put("public_id", it.toFormField()) }
        }

        val response = cloudinaryApi.upload(
            url = "$CLOUDINARY_UPLOAD_BASE/${signature.cloudName}/auto/upload",
            file = part,
            fields = fields,
        )
        if (!response.isSuccessful) {
            val message = parseCloudinaryError(response.errorBody()?.string())
            error(cloudinaryFailureMessage(message, response.code()))
        }
        val body = response.body()
        return body?.secureUrl?.takeIf { it.isNotBlank() }
            ?: error(
                cloudinaryFailureMessage(body?.error?.message, response.code())
            )
    }

    /**
     * The previous transport: multipart to our own `api/upload`, which relayed to Cloudinary.
     *
     * Retained only so a build running against a backend that predates the signature endpoint still
     * uploads. It inherits that host's 4.5 MB body limit, so a file between that and our 10 MB cap
     * will fail here with the server's own error — correctly, and with a message from the server
     * rather than a mystery timeout.
     */
    private suspend fun legacyUpload(token: String, part: MultipartBody.Part): String {
        val response = uploadApi.upload(authorization = token, file = part)
        if (!response.isSuccessful) {
            val serverMessage = parseUploadError(response.errorBody()?.string())
            error(serverMessage ?: "Upload failed (HTTP ${response.code()}).")
        }
        val body = response.body()
        return body?.fileUrl?.takeIf { it.isNotBlank() }
            ?: error(body?.error ?: body?.message ?: "Upload failed — no file URL returned.")
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
     * Creates the booking record via `POST api/bookings`, then records `Booked` locally.
     *
     * The multipart body carries the form's text fields plus the transaction screenshot, and a
     * `leadId` — that last field is what makes the backend sync this lead to `Booked`, and it is the
     * only link between the booking database (`ft_booking_system`) and the CRM lead.
     *
     * **Server-first, unlike every other write here.** Leads and notes are offline-first — write Room,
     * push, let a failed push reconcile later — but booking can't be: reaching `Booked` locks the
     * status dropdown, and this app offers no way back. An optimistic local write followed by a failed
     * push would leave the lead locked with no booking on the server and no way for the agent to
     * retry. So nothing is written until the server has the booking.
     *
     * The local status write is kept rather than left to the next sync: it's what closes the dialog on
     * a lead the UI already shows, and it stays correct even if the backend's lead-sync step is the
     * part that failed.
     *
     * Note this no longer calls `PUT api/leads/{id}/book`. That endpoint also incremented the agent's
     * monthly booking count and rewrote the lead's `name`/`product` from the form — neither of which
     * `POST api/bookings` does, so those two side effects no longer happen on booking.
     */
    suspend fun bookLead(leadId: String, form: BookingForm): Result<BookingReceipt> = runCatching {
        val token = session.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Not logged in — cannot book this lead.")

        val screenshotUri = form.screenshotUri?.takeIf { it.isNotBlank() }
            ?: error("Attach the transaction screenshot before booking.")

        // Throws with an agent-readable message if the file is too big or the wrong type; returns null
        // only when the URI can't be opened, which usually means the picker's grant already lapsed.
        val (screenshotPart, _) = documentPartFactory.buildScreenshot(Uri.parse(screenshotUri))
            ?: error("Couldn't read the selected screenshot. Please pick it again.")

        val response = bookingsApi.createBooking(
            authorization = token.toBearerOrNull(),
            fields = form.toFormFields(leadId).mapValues { (_, value) -> value.toFormField() },
            screenshot = screenshotPart,
        )
        if (!response.isSuccessful) {
            error(
                parseBookingError(response.errorBody()?.string())
                    ?: friendlyBookingFailure(response.code())
            )
        }
        val booking = response.body()?.data
            ?: error(
                response.body()?.error?.takeIf { it.isNotBlank() }
                    ?: "The booking may not have been saved — the server sent an empty response."
            )

        val now = System.currentTimeMillis()
        val previousStatus = leadDao.getLeadById(leadId)?.status
        leadDao.updateStatus(leadId, BOOKED_STATUS, now)
        // The device's only record of what this sale was worth — it feeds the dashboard's monthly
        // amount. Written from the server's confirmed figure rather than the form, and only after the
        // booking is accepted, so the total can never count a booking the backend rejected.
        leadDao.setBookedAmount(leadId, booking.totalAmount, now)
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

        BookingReceipt(
            bookingId = booking.bookingId?.takeIf { it.isNotBlank() },
            // Server-derived, and deliberately read back rather than echoed from the form: the backend
            // recalculates paid from *verified* payments, so a deposit that's still awaiting
            // verification legitimately reads as 0 here.
            totalAmount = booking.totalAmount,
            paidAmount = booking.paidAmount,
            dueAmount = booking.dueAmount,
        )
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

        const val TAG = "LeadsRepo"

        const val CLOUDINARY_UPLOAD_BASE = "https://api.cloudinary.com/v1_1"

        /**
         * Responses that mean "this route isn't deployed here" rather than "the request was bad".
         *
         * 405 and 501 are included alongside 404 because a host that routes every `api` path to one
         * handler can answer an unknown sub-path with a method error instead of a not-found.
         */
        val ENDPOINT_ABSENT_CODES = setOf(404, 405, 501)
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
    private val callDao: CallDao,
) {

    // Serializes syncNewCalls. The call-log observer fires it repeatedly for one call (Android
    // writes the row, then updates duration on hang-up), so overlapping runs could both read the
    // same old watermark before either advanced it — and POST the same call twice, inflating the
    // backend's COUNT(*)-based totalDials. The lock makes each run see the prior run's watermark.
    private val syncMutex = Mutex()

    /**
     * A lead's stored call history, newest first, as a live Flow — the screen's source of truth.
     *
     * Reading from Room rather than the call-log provider is what makes history survive closing the
     * dialog, leaving the screen, an app restart and being offline; it also updates on its own as
     * [ingestDeviceCalls] writes new rows, so the UI needs no refresh call of its own.
     *
     * [since] is the lead's `assignedAt` cutoff — calls before it belong to a prior owner. Null means
     * no restriction, matching the maps in [leadIndex].
     */
    fun observeCallsForLead(phone: String, since: Long?): Flow<List<CallLogEntry>> {
        val key = phone.normalizedPhoneKey()
        if (key.isEmpty()) return flowOf(emptyList())
        return callDao.observeCallsForNumber(key, since ?: 0L)
            .map { rows -> rows.map { it.toDomain() } }
    }

    /**
     * Fills a lead's history from `GET api/calls/long-calls` when this device has nothing stored for
     * it — the fresh-install and new-device case, where the call log holds none of the agent's earlier
     * calls but the backend does.
     *
     * Returns 0 and makes no request when rows already exist, so this never competes with the device
     * log. That is the intended precedence: the device is the complete source (it has missed and
     * failed calls too), the server is the fallback.
     *
     * **Known gaps, both inherent to the endpoint rather than incidental:**
     * - `metric` accepts only `connected` or `longCalls` (≥300s), so **missed and failed calls cannot
     *   be recovered from the server at all**. A backfilled history is therefore answered calls only.
     *   `connected` is requested because long calls are a subset of it.
     * - `LongCallDto` carries no `clientCallId`, so server rows can't be matched to device rows by id.
     *   Overlap is suppressed by number + instant within [SERVER_CALL_DEDUPE_TOLERANCE_MS]; a device
     *   row that lands later for the same call can still duplicate a backfilled one, which is why this
     *   only ever runs into an empty history.
     */
    suspend fun backfillCallsForLead(
        phone: String,
        leadId: String?,
        since: Long?,
    ): Result<Int> = runCatching {
        val key = phone.normalizedPhoneKey()
        if (key.isEmpty()) return@runCatching 0
        val cutoff = since ?: 0L
        if (callDao.countForNumber(key, cutoff) > 0) return@runCatching 0

        val rows = callsApi.getLongCalls(
            authorization = bearerOrThrow(),
            metric = "connected",
        )
        // The endpoint is agent-scoped by the token but not number-scoped, so filter to this lead.
        val existing = callDao.getCallsForNumber(key, 0L)
        val entities = rows
            .mapNotNull { it.toEntity(fallbackLeadId = leadId) }
            .filter { it.phoneKey == key && it.dateMillis >= cutoff }
            .filter { candidate ->
                existing.none { stored ->
                    abs(stored.dateMillis - candidate.dateMillis) <= SERVER_CALL_DEDUPE_TOLERANCE_MS
                }
            }
        if (entities.isEmpty()) return@runCatching 0
        callDao.replaceServerCalls(key, entities)
        entities.size
    }.mapApiError()

    /**
     * Marks (or unmarks) one call as having reached a voicemail machine rather than a person.
     *
     * The agent is the only possible source for this: Android's call log records an answered machine
     * exactly like an answered human. Applies to a single call, never to the number — the same lead can
     * go to voicemail once and pick up the next time.
     *
     * Takes effect immediately everywhere, because the write lands in the `calls` table that the lead
     * screen observes and the Dashboard re-reads marks from on every compute. The call's talk time and
     * its connected status drop; the dial still counts, since the agent did place it.
     *
     * Only device-sourced calls can be marked — [CallEntity.id] is recoverable from a device row's
     * numeric id, whereas a server-backfilled row's domain id is synthetic. Returns false for those
     * rather than writing to a guessed id.
     */
    suspend fun setCallVoicemail(call: CallLogEntry, isVoicemail: Boolean): Result<Boolean> =
        runCatching {
            if (call.id < 0) return@runCatching false
            callDao.setVoicemail(deviceCallId(call.id), isVoicemail)
            true
        }

    /** One-shot read of the same rows [observeCallsForLead] streams. */
    suspend fun getStoredCallsForLead(phone: String, since: Long?): List<CallLogEntry> {
        val key = phone.normalizedPhoneKey().ifEmpty { return emptyList() }
        return callDao.getCallsForNumber(key, since ?: 0L).map { it.toDomain() }
    }

    /**
     * Mirrors this device's call log into the `calls` table so a lead's history is stored locally
     * instead of being re-read from the provider every time the dialog opens.
     *
     * Deliberately **not** bounded by the watermark or by [REPORTING_LOOKBACK_DAYS], unlike
     * [syncNewCalls]: those bounds exist to keep the *backend's* dial counts honest, whereas this is
     * the agent's full local history — every call to an assigned lead from that lead's `assignedAt`
     * onward, however old. The upsert is keyed on the device call-log id, so re-ingesting the same
     * row corrects a duration that settled after the first read rather than duplicating the call.
     *
     * Calls to a number with no assigned lead are skipped rather than stored unattributed — this
     * table must not accumulate the agent's personal and spam calls. That does **not** lose a call
     * placed before its lead had synced: because this re-reads the entire log rather than resuming
     * from a cursor, the next run after the lead lands picks that call up and stores it. Re-reading
     * everything is therefore load-bearing, not just simpler. [CallDao.attachLeadId] then covers the
     * residual case of rows already stored against a lead whose id changed.
     */
    suspend fun ingestDeviceCalls(): Result<Int> = runCatching {
        if (!callLogReader.hasPermission()) return@runCatching 0
        val all = callLogReader.readAll()
        if (all.isEmpty()) return@runCatching 0

        val leads = leadDao.getAllLeads().first()
        val index = leadIndex(leads)
        if (index.assignedByKey.isEmpty()) return@runCatching 0

        // Read the agent's voicemail marks BEFORE building the rows. The device log has no such field,
        // so this table is their only copy — upserting without carrying them forward would silently
        // erase every mark on the next call-log change.
        val voicemailMarked = deviceCallLogIds(callDao.getVoicemailMarkedIds())

        val entities = all.mapNotNull { entry ->
            val key = entry.number.normalizedPhoneKey().ifEmpty { null } ?: return@mapNotNull null
            if (!index.assignedByKey.containsKey(key)) return@mapNotNull null
            // Same cutoff the backend push uses: a null stamp means no restriction.
            val assignedAt = index.assignedByKey[key]
            if (assignedAt != null && entry.dateMillis < assignedAt) return@mapNotNull null
            entry.toEntity(
                leadId = index.leadIdByKey[key],
                isVoicemail = entry.id in voicemailMarked,
            )
        }
        if (entities.isEmpty()) return@runCatching 0

        callDao.upsertCalls(entities)
        // Adopt rows stored before their lead existed locally.
        leads.forEach { lead ->
            val key = lead.phone.normalizedPhoneKey()
            if (key.isNotEmpty()) callDao.attachLeadId(key, lead.id)
        }
        entities.size
    }

    /**
     * Phone-key views of the agent's leads, shared by [ingestDeviceCalls] and [syncNewCalls] so the
     * two can't disagree about which calls belong to a lead.
     *
     * Both maps key off the last-10-digit phone key. When several leads share a number the earliest
     * assignment wins, which is the most inclusive choice; a null stamp means "no restriction".
     */
    private fun leadIndex(leads: List<LeadEntity>): LeadCallIndex {
        val assignedByKey: Map<String, Long?> = leads
            .mapNotNull { lead ->
                val key = lead.phone.normalizedPhoneKey().ifEmpty { null } ?: return@mapNotNull null
                key to lead.assignedAt
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> if (times.any { it == null }) null else times.filterNotNull().min() }
        val leadIdByKey: Map<String, String> = leads
            .mapNotNull { lead ->
                val key = lead.phone.normalizedPhoneKey().ifEmpty { null } ?: return@mapNotNull null
                Triple(key, lead.id, lead.assignedAt)
            }
            .groupBy { it.first }
            .mapValues { (_, rows) -> rows.minBy { it.third ?: Long.MAX_VALUE }.second }
        return LeadCallIndex(assignedByKey, leadIdByKey)
    }

    private data class LeadCallIndex(
        val assignedByKey: Map<String, Long?>,
        val leadIdByKey: Map<String, String>,
    )

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

        // Only post calls that (a) fall in the reporting window and (b) are to an assigned lead, at or
        // after that lead's assignment cutoff. The backend counts every posted call as a "dial", so
        // sending the agent's whole call history (incoming/spam/non-lead + past days) inflated the
        // Historical report. Shares `leadIndex` with ingestDeviceCalls so the two agree on which
        // calls belong to a lead.
        val leads = leadDao.getAllLeads().first()
        val index = leadIndex(leads)
        val assignedByKey = index.assignedByKey
        val leadIdByKey = index.leadIdByKey
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

        // The watermark means "examined up to here"; the pending set means "examined but still owed to
        // the server". Splitting the two is what stopped calls from being destroyed: previously a
        // single cursor was advanced past every non-qualifying call, so "skipped because its lead
        // hadn't synced yet" was indistinguishable from "already sent" and the call was never
        // reconsidered. Candidates are therefore everything new plus everything still owed.
        val watermark = callSyncStore.getWatermark()
        val pending = callSyncStore.getPending().toMutableSet()
        // Drop owed ids whose row has left the device log (the agent cleared their call history).
        // Nothing in the loop below can reach them — they aren't in `all`, so they never become
        // candidates — and without this they would sit in the set until MAX_PENDING evicted them.
        // Runs before the early return below, which would otherwise skip the prune for good.
        val deviceIds = all.mapTo(HashSet()) { it.id }
        if (pending.retainAll(deviceIds)) callSyncStore.setPending(pending)

        val candidates = all.filter { it.id > watermark || it.id in pending }.sortedBy { it.id }
        if (candidates.isEmpty()) return@runCatching 0

        var logged = 0
        var seenUpTo = watermark
        for (entry in candidates) {
            val key = entry.number.normalizedPhoneKey()
            val action = callSyncAction(
                entry = entry,
                now = now,
                windowStart = windowStart,
                windowEnd = windowEnd,
                assignedAt = assignedByKey[key],
                isLeadCall = assignedByKey.containsKey(key),
            )
            // Every branch advances `seenUpTo`: the watermark records what was examined, while
            // `pending` separately records what is still owed.
            seenUpTo = maxOf(seenUpTo, entry.id)
            when (action) {
                CallSyncAction.DISCARD -> {
                    pending.remove(entry.id)
                    continue
                }
                CallSyncAction.RETRY_LATER -> {
                    pending.add(entry.id)
                    continue
                }
                CallSyncAction.POST -> Unit
            }
            // Non-null for POST — callSyncAction discards a row without an acceptable status.
            val status = callStatusFor(entry) ?: continue
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
                if (isPermanentCallLogError(response.code())) {
                    // A body-level rejection (duplicate clientCallId, malformed body) fails
                    // identically forever, so stop owing it — otherwise one poison row would be
                    // retried on every sync for as long as it stayed in the window.
                    pending.remove(entry.id)
                    continue
                }
                // Transient (5xx, auth, throttling): keep it owed and end the run rather than
                // hammering the server once per remaining call. Everything examined so far is still
                // persisted below, so the break costs nothing but the retry.
                pending.add(entry.id)
                break
            }
            pending.remove(entry.id)
            logged++
        }
        // Persisted once, after the loop, so an early break still records everything examined. The
        // watermark only ever moves forward over rows this run actually looked at.
        if (seenUpTo > watermark) callSyncStore.setWatermark(seenUpTo)
        callSyncStore.setPending(pending)
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

/** What [CallLogSyncRepository.syncNewCalls] should do with one device call-log row. */
enum class CallSyncAction {
    /** Send it now. */
    POST,

    /** Not eligible yet, but could be later — keep it owed and reconsider on a future run. */
    RETRY_LATER,

    /** Can never be posted; stop tracking it. */
    DISCARD,
}

/**
 * Decides the fate of one call-log row, kept as a pure function so the three-way split is testable
 * without a Retrofit/DataStore/ContentProvider stack (this project has JUnit only — no MockK).
 *
 * The distinction between [CallSyncAction.RETRY_LATER] and [CallSyncAction.DISCARD] is the whole
 * point: a single "seen" cursor used to collapse them, so a call skipped because its lead hadn't
 * synced yet was treated exactly like one already sent, and was silently lost.
 *
 * [assignedAt] is the lead's cutoff (null = no restriction) and [isLeadCall] whether the number
 * belongs to an assigned lead at all.
 */
fun callSyncAction(
    entry: CallLogEntry,
    now: Long,
    windowStart: Long,
    windowEnd: Long,
    assignedAt: Long?,
    isLeadCall: Boolean,
): CallSyncAction {
    // No status the backend's enum accepts (blocked/unknown), so it is unpostable by nature.
    if (callStatusFor(entry) == null) return CallSyncAction.DISCARD
    // Older than the reporting window, which only ever moves forward — it can never re-enter. This is
    // also what bounds the pending set: an id that never becomes eligible ages out instead of
    // accumulating forever.
    if (entry.dateMillis < windowStart) return CallSyncAction.DISCARD

    // The lead may still arrive (a call placed moments after adding a lead syncs later), and the
    // cutoff itself can still move backwards because it takes the earliest value ever seen.
    if (!isLeadCall) return CallSyncAction.RETRY_LATER
    if (assignedAt != null && entry.dateMillis < assignedAt) return CallSyncAction.RETRY_LATER
    // Stamped beyond the window's upper bound (skewed device clock). Comes back into range as the
    // bound advances, so it waits rather than being discarded.
    if (entry.dateMillis >= windowEnd) return CallSyncAction.RETRY_LATER
    // Duration still being written; posting now would record a real call as zero-length.
    if (isDurationUnsettled(entry, now)) return CallSyncAction.RETRY_LATER

    return CallSyncAction.POST
}

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

/**
 * How long the agent has been off the phone **right now**: from the end of their most recent call
 * today to [nowMillis]. This is the live "not calling" stretch the Dashboard's Idle Time tile shows.
 *
 * Replaces an earlier metric that summed the gaps *between* today's calls. That answered a different
 * question — total downtime already accumulated — so the tile sat frozen at the same value while the
 * agent stopped calling, which is exactly when idle should be climbing.
 *
 * Null when there are no calls today, so the tile reads "—" rather than implying an idle stretch that
 * was never measured. Scoping to today is what keeps this honest: an agent who hasn't called since
 * yesterday shows "—", not a huge overnight figure that really means "hasn't started yet" (the reason
 * a previous version of this tile was accused of reporting login time).
 *
 * The end of a call is `dateMillis + talkTimeSeconds`, not raw duration, so a voicemail recording
 * doesn't count as time on the phone — consistent with talk time. Clamped at 0, so a call in progress
 * or a skewed device clock reads "0m" instead of a negative figure. Uses the latest end rather than
 * the latest start, so a long call that began before a short one can't be treated as ending first.
 */
fun idleSecondsSinceLastCall(todaysCalls: List<CallLogEntry>, nowMillis: Long): Long? {
    val lastCallEndMs = todaysCalls
        .maxOfOrNull { it.dateMillis + it.talkTimeSeconds * 1000 }
        ?: return null
    return ((nowMillis - lastCallEndMs) / 1000).coerceAtLeast(0L)
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

    //Today's calls
    val dayStart = startOfDayMillis(now)
    val nextMidnight = java.util.Calendar.getInstance().apply {
        timeInMillis = dayStart
        add(java.util.Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    val todaysCalls = calls.filter { it.dateMillis in dayStart until nextMidnight }

    val scoped = if (todayOnly) {
        todaysCalls
    } else {
        calls
    }.sortedBy { it.dateMillis }
    if (scoped.isEmpty()) return null
    // Excludes voicemail (see `talkTimeSeconds`), so the talk time pushed to the backend matches the
    // dial and connected counts below, which already ignore it.
    // Same `countsAsDial` rule the Dashboard uses, which is what stops the lead card and lead detail
    // from printing a different dial count than the Dashboard for the very same calls.

    // Strictly today's totals (will be 0 if no calls were made today)
    val dailyDials = todaysCalls.count {
        it.countsAsDial
    }

    val dailyTalkSeconds = todaysCalls.sumOf {
        it.talkTimeSeconds
    }

    // Cumulative totals (across all days)
    val totalDials = scoped.count {
        it.countsAsDial
    }

    val totalTalkSeconds = scoped.sumOf {
        it.talkTimeSeconds
    }
    return UpdateBookingRequest(
        totalDial = totalDials,
        dailyDial = dailyDials,
        // Excludes agent-marked voicemails, matching the Dashboard's Connected Calls tile.
        connected = scoped.count { it.countsAsConnected },
        talkTime = formatTalkTimeClock(totalTalkSeconds),
        dailyTalkTime = formatTalkTimeClock(dailyTalkSeconds),
        firstCall = scoped.first().dateMillis.let(::formatIso8601Utc),
        lastCall = scoped.last().dateMillis.let(::formatIso8601Utc),
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
 * How far apart two calls to the same number may be and still be treated as the same call when
 * backfilling from the server.
 *
 * Needed because `LongCallDto` carries no `clientCallId` (see [CallLogSyncRepository.backfillCallsForLead]),
 * so a server row can only be matched to a stored one by number and instant. A minute absorbs the
 * clock skew between the device stamp and the server's copy while staying far below the gap between
 * two genuinely distinct calls to the same lead.
 */
internal const val SERVER_CALL_DEDUPE_TOLERANCE_MS = 60_000L

/**
 * How recent a call-log row must be before we treat its `duration` as still in flux. Android writes
 * the row at call start and rewrites DURATION on hang-up, so a row younger than this may be an
 * in-progress call whose duration is 0. Posting that would record it as Failed/Missed with no talk
 * time, and the watermark would move past it before the real duration ever landed.
 */
internal const val CALL_SETTLE_MS = 15_000L

/**
 * How long a single call must last to count as a "long call" — five minutes.
 *
 * Matches the backend's `longCalls` metric (`GET api/calls/long-calls` treats ≥300s as long, and uses
 * it as that endpoint's default), so the Dashboard's "Call more than" tile and the web historical
 * report count the same calls for the same day.
 */
const val LONG_CALL_THRESHOLD_SECONDS = 300L

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

/**
 * The lead's outcome label: "Connected" once any call actually reached a person, else "Dialed".
 *
 * Uses [countsAsConnected] rather than a raw duration check, so a call the agent marked as voicemail
 * doesn't label the lead Connected. This label is pushed to the backend, so leaving it on raw duration
 * would contradict the Connected Calls figure on both the app's dashboard and the web one.
 */
fun callLabelFor(calls: List<CallLogEntry>): String? {
    if (calls.isEmpty()) return null
    return if (calls.any { it.countsAsConnected }) "Connected" else "Dialed"
}

fun mergeLabels(existing: List<String>, callLabel: String?): List<String> {
    val kept = existing.filter { it !in CALL_OUTCOME_LABELS }
    return if (callLabel == null) kept else kept + callLabel
}
