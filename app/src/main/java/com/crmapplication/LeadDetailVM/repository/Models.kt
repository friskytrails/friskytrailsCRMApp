package com.crmapplication.LeadDetailVM.repository

import com.crmapplication.LeadDetailVM.local.LeadEntity
import com.crmapplication.LeadDetailVM.local.NoteEntity
import com.crmapplication.LeadDetailVM.local.StatusHistoryEntity
import com.crmapplication.LeadDetailVM.remote.ApiLeadDto
import com.crmapplication.LeadDetailVM.remote.ApiNoteDto
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

    val statusChangedAt: Long? = null,
    val createdAt: Long,
    val dueDate: Long?,
    val notes: List<Note> = emptyList(),
)

val LEAD_STATUSES = listOf(
    "Fresh Leads",
    "Interested Leads",
    "Pre Prospect Leads",
    "Prospect Leads",
    "Booked",
    "Rejected Leads",
)

const val DEFAULT_LEAD_STATUS = "Fresh Leads"

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
    statusChangedAt = statusChangedAt,
    createdAt = createdAt,
    dueDate = dueDate,
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
        createdAt = createdAt.toEpochMillisOrNow(),
        dueDate = null,
    )
}

private fun String?.toEpochMillisOrNow(): Long =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

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
        timeLabel = timestamp?.trim()?.takeIf { it.isNotBlank() },
    )
}

private fun objectIdToEpochMillis(id: String): Long? {
    if (id.length < 8) return null
    return runCatching { id.substring(0, 8).toLong(16) * 1000L }.getOrNull()
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
