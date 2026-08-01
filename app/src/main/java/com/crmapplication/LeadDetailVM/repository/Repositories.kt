package com.crmapplication.LeadDetailVM.repository

import android.net.Uri
import android.util.Base64
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
import com.crmapplication.LeadDetailVM.remote.ApiConfig
import com.crmapplication.LeadDetailVM.remote.ApiNoteDto
import com.crmapplication.LeadDetailVM.remote.ApiService
import com.crmapplication.LeadDetailVM.remote.AuthApi
import com.crmapplication.LeadDetailVM.remote.AuthLoginRequest
import com.crmapplication.LeadDetailVM.remote.CallsApi
import com.crmapplication.LeadDetailVM.remote.LogCallRequest
import com.crmapplication.LeadDetailVM.remote.CreateLeadRequest
import com.crmapplication.LeadDetailVM.remote.ForgotPasswordRequest
import com.crmapplication.LeadDetailVM.remote.LeadsApi
import com.crmapplication.LeadDetailVM.remote.RegisterRequest
import com.crmapplication.LeadDetailVM.remote.ResendOtpRequest
import com.crmapplication.LeadDetailVM.remote.ResetPasswordRequest
import com.crmapplication.LeadDetailVM.remote.SetDueDateRequest
import com.crmapplication.LeadDetailVM.remote.StatusResponse
import com.crmapplication.LeadDetailVM.remote.UpdateProfileRequest
import com.crmapplication.LeadDetailVM.remote.UpdateBookingRequest
import com.crmapplication.LeadDetailVM.remote.UpdateDatesRequest
import com.crmapplication.LeadDetailVM.remote.UpdateLabelsRequest
import com.crmapplication.LeadDetailVM.remote.UpdateStatusRequest
import com.crmapplication.LeadDetailVM.remote.UploadApi
import com.crmapplication.LeadDetailVM.remote.UploadResponse
import com.crmapplication.LeadDetailVM.remote.VerifyEmailRequest
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallLogReader
import com.crmapplication.calllog.CallType
import com.crmapplication.calllog.normalizedPhoneKey
import com.crmapplication.utils.CallSyncStore
import com.crmapplication.utils.DocumentPartFactory
import com.crmapplication.utils.SessionManager
import com.crmapplication.utils.formatApiDate
import com.crmapplication.utils.formatClockTime
import com.crmapplication.utils.formatDashboardDate
import com.crmapplication.utils.formatIdleTime
import com.crmapplication.utils.formatIso8601
import com.crmapplication.utils.formatMonthLabel
import com.crmapplication.utils.formatTalkTimeClock
import com.crmapplication.utils.formatTalkTimeWords
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

private fun <T> Result<T>.mapApiError(): Result<T> = recoverCatching { e ->
    if (e is HttpException) {
        throw Exception(friendlyApiMessage(e.serverError(), e.code()))
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

    fun hasCallLogPermission(): Boolean = callLogReader.hasPermission()

    fun observeCallLogChanges(): Flow<Unit> = callLogReader.observeChanges()

    private suspend fun fetchAgentMetrics(): AgentMetricsDto? {
        val token = session.getToken()?.takeIf { it.isNotBlank() } ?: return null

        val id = session.getAgentId()?.takeIf { it.isNotBlank() }
            ?: agentIdFromToken(token)
            ?: return null
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        return runCatching { agentsApi.getMetrics(id = id, authorization = bearer) }.getOrNull()
    }

    private fun agentIdFromToken(token: String): String? = runCatching {
        val payload = token.removePrefix("Bearer ").trim().split(".").getOrNull(1) ?: return null
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        Gson().fromJson(json, JsonObject::class.java)?.get("userId")?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()

    suspend fun getDashboard(): Result<DashboardData> = runCatching {
        check(callLogReader.hasPermission()) { "Call-log permission not granted" }

        val now = Calendar.getInstance()
        val startOfDay = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + DAY_MILLIS

        val startOfMonthCal = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = startOfMonthCal.timeInMillis
        val daysInMonth = startOfMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val allCalls = callLogReader.readAll()

        val metrics = fetchAgentMetrics()

        val leads = leadDao.getAllLeads().first()
        val leadKeys = leads
            .mapNotNull { it.phone.normalizedPhoneKey().ifEmpty { null } }
            .toSet()

        val daily = buildDailyStats(allCalls, startOfDay, endOfDay, now.timeInMillis, metrics, leadKeys)
        val monthly = buildMonthlyStats(allCalls, startOfMonth, endOfDay, daysInMonth, metrics, leads)

        DashboardData(daily = daily, monthly = monthly)
    }

    private fun buildDailyStats(
        allCalls: List<CallLogEntry>,
        startOfDay: Long,
        endOfDay: Long,
        nowMillis: Long,
        metrics: AgentMetricsDto?,
        leadKeys: Set<String>,
    ): DashboardStats {

        val leadCalls = allCalls.filter { it.number.normalizedPhoneKey() in leadKeys }

        val today = leadCalls
            .filter { it.dateMillis in startOfDay until endOfDay }
            .sortedBy { it.dateMillis }

        val previousCall = leadCalls
            .filter { it.dateMillis < startOfDay }
            .maxByOrNull { it.dateMillis }

        val nowMarker = today.lastOrNull()?.copy(dateMillis = nowMillis, durationSeconds = 0)
        val idleBasis = buildList {
            if (previousCall != null && today.isNotEmpty()) add(previousCall)
            addAll(today)
            if (nowMarker != null) add(nowMarker)
        }

        val totalTalk = today.sumOf { it.durationSeconds }
        val dials = today.count { it.type == CallType.OUTGOING }
        val connected = today.count { it.durationSeconds > 0 }
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
            idleTime = formatIdleTime(computeIdleSeconds(idleBasis)),

            attendance = when (metrics?.attendance?.trim()?.uppercase()) {
                "P" -> "Present"
                "A" -> "Absent"
                else -> if (today.isNotEmpty()) "Present" else "—"
            },
        )
    }

    private fun buildMonthlyStats(
        allCalls: List<CallLogEntry>,
        startOfMonth: Long,
        endOfDay: Long,
        daysInMonth: Int,
        metrics: AgentMetricsDto?,
        leads: List<LeadEntity>,
    ): MonthlyStats {

        val monthCalls = allCalls.filter { it.dateMillis in startOfMonth until endOfDay }
        val presentDays = presentDayCount(monthCalls)

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
            attendance = "$presentDays / $daysInMonth",
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

@Singleton
class LeadsRepository @Inject constructor(
    private val api: ApiService,
    private val leadsApi: LeadsApi,
    private val uploadApi: UploadApi,
    private val documentPartFactory: DocumentPartFactory,
    private val leadDao: LeadDao,
    private val noteDao: NoteDao,
    private val statusHistoryDao: StatusHistoryDao,
    private val session: SessionManager,
) {

    fun currentAgentId(): String? = session.getAgentId()

    fun observeLeads(): Flow<List<Lead>> = leadDao.getAllLeads().map { list ->
        list.map { it.toDomain() }
    }

    fun observeNotes(leadId: String): Flow<List<Note>> = noteDao.getNotesForLead(leadId).map { list ->
        list.map { it.toDomain() }
    }

    fun observeStatusHistory(leadId: String): Flow<List<StatusChange>> =
        statusHistoryDao.getHistoryForLead(leadId).map { list -> list.map { it.toDomain() } }

    suspend fun syncLeads(): Result<Unit> = runCatching {
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
        val entities = dtos.map { dto ->
            val entity = dto.toEntity()
            val prior = existing[entity.id]
            val locallyChangedStatus = prior?.statusChangedAt != null
            entity.copy(
                dueDate = prior?.dueDate ?: entity.dueDate,
                statusChangedAt = prior?.statusChangedAt ?: entity.statusChangedAt,
                status = if (locallyChangedStatus) prior!!.status else entity.status,
            )
        }

        if (entities.isEmpty()) leadDao.deleteAll()
        else leadDao.deleteLeadsNotIn(entities.map { it.id })
        leadDao.upsertLeads(entities)

        dtos.forEach { dto ->
            val id = dto.id ?: dto.mongoId ?: dto.leadId?.toString() ?: dto.phone.orEmpty()
            if (id.isNotBlank()) reconcileNotes(id, dto.notes)
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

        runCatching { syncLeads() }
        Unit
    }.mapApiError()

    suspend fun addNote(leadId: String, text: String, imageUrl: String? = null): Result<Unit> = runCatching {
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
        val updated = leadsApi.addNote(
            id = leadId,
            authorization = session.getToken()?.toBearerOrNull(),
            body = AddLeadNoteRequest(text = text, imageUrl = imageUrl?.takeIf { it.isNotBlank() }),
        )

        reconcileNotes(leadId, updated.notes)
        noteDao.deleteNoteById(optimisticId)
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

    suspend fun setDueDate(leadId: String, dueDate: Long?): Result<Unit> = runCatching {
        leadDao.updateDueDate(leadId, dueDate)
        runCatching {
            api.setDueDate("Bearer ${session.getToken()}", leadId,
                SetDueDateRequest(leadId, dueDate)
            )
        }
    }

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

    suspend fun updateBooking(leadId: String, calls: List<CallLogEntry>): Result<Unit> = runCatching {
        val body = bookingFromCalls(calls) ?: return@runCatching
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
}

@Singleton
class CallLogSyncRepository @Inject constructor(
    private val callsApi: CallsApi,
    private val callLogReader: CallLogReader,
    private val callSyncStore: CallSyncStore,
    private val session: SessionManager,
) {

    suspend fun syncNewCalls(): Result<Int> = runCatching {
        if (!callLogReader.hasPermission()) return@runCatching 0
        val token = session.getToken()?.takeIf { it.isNotBlank() } ?: return@runCatching 0
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"

        val all = callLogReader.readAll()
        if (all.isEmpty()) return@runCatching 0

        if (!callSyncStore.hasBaseline()) {
            callSyncStore.setWatermark(all.maxOf { it.id })
            return@runCatching 0
        }

        val watermark = callSyncStore.getWatermark()
        val newCalls = all.filter { it.id > watermark }.sortedBy { it.id }
        if (newCalls.isEmpty()) return@runCatching 0

        var logged = 0
        for (entry in newCalls) {
            val status = callStatusFor(entry)

            if (status == null) {
                callSyncStore.setWatermark(entry.id)
                continue
            }
            val response = callsApi.logCall(
                authorization = bearer,
                body = LogCallRequest(
                    status = status,
                    duration = entry.durationSeconds,
                    contactNumber = entry.number.takeIf { it.isNotBlank() && it != "Unknown" },
                    timestamp = formatIso8601(entry.dateMillis),
                ),
            )

            if (!response.isSuccessful) break
            callSyncStore.setWatermark(entry.id)
            logged++
        }
        logged
    }
}

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
): UpdateBookingRequest? {
    if (calls.isEmpty()) return null
    val dayStart = startOfDayMillis(now)
    val dayEnd = dayStart + 24L * 60 * 60 * 1000
    val today = calls.filter { it.dateMillis in dayStart until dayEnd }
    return UpdateBookingRequest(
        totalDial = calls.count { it.type == CallType.OUTGOING },
        dailyDial = today.count { it.type == CallType.OUTGOING },
        connected = calls.count { it.durationSeconds > 0 },
        talkTime = formatTalkTimeClock(calls.sumOf { it.durationSeconds }),
        dailyTalkTime = formatTalkTimeClock(today.sumOf { it.durationSeconds }),
        firstCall = calls.minByOrNull { it.dateMillis }?.dateMillis?.let(::formatApiDate),
        lastCall = calls.maxByOrNull { it.dateMillis }?.dateMillis?.let(::formatApiDate),
    )
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
