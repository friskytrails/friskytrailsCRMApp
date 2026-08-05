package com.crmapplication.LeadDetailVM.repository

import com.crmapplication.LeadDetailVM.local.BugReportEntity
import com.crmapplication.LeadDetailVM.local.LeadEntity
import com.crmapplication.LeadDetailVM.local.NoteEntity
import com.crmapplication.LeadDetailVM.local.StatusHistoryEntity
import com.crmapplication.LeadDetailVM.remote.ApiLeadDto
import com.crmapplication.LeadDetailVM.remote.ApiNoteDto
import com.crmapplication.LeadDetailVM.remote.BugReportDto
import com.crmapplication.LeadDetailVM.remote.BugStatus
import com.crmapplication.LeadDetailVM.remote.AuthUser
import com.crmapplication.LeadDetailVM.remote.LeadDto
import com.crmapplication.LeadDetailVM.remote.MeResponse
import com.crmapplication.LeadDetailVM.remote.NoteDto
import java.time.Instant

data class Lead(
    val id: String,
    val name: String,
    val phone: String,
    val totalDial: Int = 0,
    val connected: Int = 0,
    val talkTime: String = "",
    val firstCall: String? = null,
    val lastCall: String? = null,
    val labels: List<String> = emptyList(),
    val status: String = DEFAULT_LEAD_STATUS,

    val product: String? = null,

    val source: String? = null,

    /**
     * When the party travels, as the server stores it — a free-form string, usually `yyyy-MM-dd`.
     * Kept verbatim rather than parsed to millis: the web dashboard may write an already-formatted
     * date, and re-formatting a string we can't parse would lose what the agent typed.
     */
    val travelDate: String? = null,

    /** Party size. Null means "not set" (the backend default), which is distinct from 0. */
    val numberOfPersons: Int? = null,

    val statusChangedAt: Long? = null,
    val createdAt: Long,
    val dueDate: Long?,
    val assignedAt: Long? = null,
    val notes: List<Note> = emptyList(),
)

/**
 * Status a lead falls back to when the server sends none. Stays a compile-time constant rather than
 * following `GET /api/config`: it's used in the DTO → domain mapping, which has no config access.
 *
 * The live status list lives in `LeadsUiState.statuses` (see [DEFAULT_LEAD_STATUSES] for the
 * pre-sync fallback).
 */
const val DEFAULT_LEAD_STATUS = "Fresh Leads"

/**
 * The one status that isn't reachable from the plain status dropdown: it requires the booking form
 * (`PUT api/leads/{id}/book`), and once a lead is here this app won't let the status change again.
 */
const val BOOKED_STATUS = "Booked"

/** True when the lead is booked, so its status is locked and the booking form is closed. */
fun Lead.isBooked(): Boolean = status.equals(BOOKED_STATUS, ignoreCase = true)

data class Note(
    val id: String,
    val leadId: String,
    val text: String,
    val timestamp: Long,

    val authorName: String? = null,

    val authorId: String? = null,

    val imageUrl: String? = null,

    val timeLabel: String? = null,
) {

    val hasAttachment: Boolean get() = !imageUrl.isNullOrBlank()

    val isDocument: Boolean
        get() {
            val url = imageUrl?.substringBefore('?')?.lowercase() ?: return false
            return url.endsWith(".pdf") || url.endsWith(".doc") || url.endsWith(".docx") ||
                (!IMAGE_EXTENSIONS.any { url.endsWith(it) } && url.contains("/raw/upload/"))
        }
}

private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")

data class Profile(
    val id: String,
    val name: String,
    val email: String,
    val isAdmin: Boolean = false,
    val isVerified: Boolean = false,
)

data class StatusChange(
    val id: String,
    val leadId: String,
    val previousStatus: String?,
    val newStatus: String,
    val changedBy: String,
    val changedAt: Long,
)

/**
 * A bug report filed by an agent. [isSynced] is false while the report exists only on this device —
 * the UI surfaces that so an agent isn't left thinking a report reached the team when it hasn't.
 *
 * [status] is whatever the backend currently says (see `BugStatus`), so an agent can tell a fixed
 * report from one nobody has picked up.
 */
data class BugReport(
    val id: String,
    val title: String,
    val description: String,
    val reporterName: String,
    val reporterId: String? = null,
    val createdAt: Long,
    val isSynced: Boolean = false,
    val status: String = BugStatus.OPEN,
)

data class DashboardStats(
    val date: String,
    val totalDials: Int,
    val totalTalktime: String,
    val connectedCalls: Int,
    val uniqueCalls: Int,
    val callMoreThan: Int,
    val firstCall: String?,
    val lastCall: String?,
    val idleTime: String,
    val attendance: String,
)

data class MonthlyStats(
    val month: String,
    val monthlyTarget: String,
    val bookingCount: String,
    val totalSaleAmount: String,
    val attendance: String,
)

data class DashboardData(
    val daily: DashboardStats,
    val monthly: MonthlyStats,
)

fun LeadDto.toEntity() = LeadEntity(
    id = id,
    name = name,
    phone = phone,
    createdAt = createdAt,
    dueDate = dueDate,
)

fun LeadEntity.toDomain() = Lead(
    id = id,
    name = name,
    phone = phone,
    totalDial = totalDial,
    connected = connected,
    talkTime = talkTime,
    firstCall = firstCall,
    lastCall = lastCall,
    labels = labels,
    status = status,
    product = product,
    source = source,
    travelDate = travelDate,
    numberOfPersons = numberOfPersons,
    statusChangedAt = statusChangedAt,
    createdAt = createdAt,
    dueDate = dueDate,
    assignedAt = assignedAt,
)

fun ApiLeadDto.toEntity(): LeadEntity {
    val stableId = id ?: mongoId ?: leadId?.toString() ?: phone.orEmpty()
    return LeadEntity(
        id = stableId,
        name = name?.trim().orEmpty().ifBlank { phone.orEmpty().ifBlank { "Unknown" } },
        phone = phone.orEmpty(),
        totalDial = booking?.totalDial ?: 0,
        connected = booking?.connected ?: 0,
        talkTime = booking?.talkTime.orEmpty(),
        firstCall = booking?.firstCall,
        lastCall = booking?.lastCall,
        labels = labels.orEmpty(),
        status = status?.takeIf { it.isNotBlank() } ?: DEFAULT_LEAD_STATUS,
        product = product?.trim()?.takeIf { it.isNotBlank() },
        source = leadSource?.trim()?.takeIf { it.isNotBlank() },
        // The backend defaults travelDate to "", so blank and absent both mean "not set" here.
        travelDate = travelDate?.trim()?.takeIf { it.isNotBlank() },
        numberOfPersons = numberOfPersons?.takeIf { it > 0 },
        createdAt = createdAt.toEpochMillisOrNow(),
        // `dates.reminderDate` is the server's copy of the reminder (date + time, set via
        // PUT /api/leads/:id/reminder). Deliberately NOT `dates.dueDate`: that one is date-only and
        // the call-log sync rewrites it on every refresh, so it would stomp the agent's reminder.
        // Null here means "no reminder on the server" — LeadsRepository.syncLeads decides whether
        // that outranks the locally stored value.
        dueDate = dates?.reminderDate.toEpochMillisOrNull(),
    )
}

private fun String?.toEpochMillisOrNow(): Long =
    toEpochMillisOrNull() ?: System.currentTimeMillis()

/** Parses an ISO-8601 instant, or null if absent/unparseable. */
private fun String?.toEpochMillisOrNull(): Long? =
    this?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

fun NoteDto.toEntity() = NoteEntity(id, leadId, text, timestamp)

fun NoteEntity.toDomain() = Note(
    id = id,
    leadId = leadId,
    text = text,
    timestamp = timestamp,
    authorName = authorName,
    authorId = authorId,
    imageUrl = imageUrl,
    timeLabel = timeLabel,
)

fun ApiNoteDto.toEntity(leadId: String): NoteEntity {
    val noteId = id ?: mongoId ?: return NoteEntity(
        id = "srv-${System.nanoTime()}", leadId = leadId, text = text.orEmpty(),
    )
    return NoteEntity(
        id = noteId,
        leadId = leadId,
        text = text.orEmpty(),
        timestamp = objectIdToEpochMillis(noteId) ?: System.currentTimeMillis(),
        authorName = author?.trim()?.takeIf { it.isNotBlank() },
        authorId = authorId?.trim()?.takeIf { it.isNotBlank() },
        imageUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() },
        timeLabel = null,
    )
}

private fun objectIdToEpochMillis(id: String): Long? {
    if (id.length < 8) return null
    return runCatching { id.substring(0, 8).toLong(16) * 1000L }.getOrNull()
}

fun BugReportEntity.toDomain() = BugReport(
    id = id,
    title = title,
    description = description,
    reporterName = reporterName,
    reporterId = reporterId,
    createdAt = createdAt,
    isSynced = isSynced,
    status = status,
)

/**
 * Server report → local row.
 *
 * The fallback id for a response missing both `id` and `_id` deliberately has **no hyphen**. The
 * hyphen is load-bearing: `BugReportDao.deleteServerReports` keys off `id NOT LIKE '%-%'` to tell
 * server rows from unsent local ones (which get UUIDs). A `srv-…` id would contain a hyphen, so it
 * would be misread as local, survive every `replaceServerReports`, and pile up a duplicate per sync.
 */
fun BugReportDto.toEntity(): BugReportEntity {
    val reportId = id ?: mongoId ?: "srv${System.nanoTime()}"
    return BugReportEntity(
        id = reportId,
        title = title?.trim().orEmpty().ifBlank { "(no title)" },
        description = description?.trim().orEmpty(),
        reporterName = reporterName?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown agent",
        // `reportedBy` on the wire — not `reporterId`, and not the `authorId` that notes use.
        reporterId = reportedBy?.trim()?.takeIf { it.isNotBlank() },
        createdAt = createdAt.toEpochMillisOrNow(),
        // Came from the server, so by definition every agent can see it.
        isSynced = true,
        // A response without a status is treated as untriaged rather than blank, so the badge can't
        // render empty.
        status = status?.trim()?.takeIf { it.isNotBlank() } ?: BugStatus.OPEN,
    )
}

fun MeResponse.toDomain() = Profile(
    id = id.orEmpty(),
    name = name.orEmpty(),
    email = email.orEmpty(),
    isAdmin = isAdmin,
    isVerified = isVerified,
)

fun AuthUser.toProfile() = Profile(
    id = id.orEmpty(),
    name = name.orEmpty(),
    email = email.orEmpty(),
    isAdmin = isAdmin,
    isVerified = isVerified,
)

fun StatusHistoryEntity.toDomain() =
    StatusChange(id, leadId, previousStatus, newStatus, changedBy, changedAt)
