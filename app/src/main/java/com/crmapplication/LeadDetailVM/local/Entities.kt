package com.crmapplication.LeadDetailVM.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "leads")
data class LeadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val totalDial: Int = 0,
    val connected: Int = 0,
    val talkTime: String = "",
    val firstCall: String? = null,
    val lastCall: String? = null,
    val labels: List<String> = emptyList(),
    val status: String = "Fresh Leads",

    val product: String? = null,

    val source: String? = null,

    // Agent-editable lead fields (PUT api/leads/{id}). Free-form string, because the backend schema
    // stores it that way — usually `yyyy-MM-dd`, but the web dashboard can write a formatted date.
    val travelDate: String? = null,

    // Party size. Null means "not set", matching the backend default — distinct from 0.
    val numberOfPersons: Int? = null,

    val statusChangedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,

    // Cutoff for call-log analytics: the moment this lead first landed locally for this agent.
    // Set once on first sync (see LeadsRepository.syncLeads), never overwritten. Calls before
    // this instant belong to a prior owner and are excluded from history, counts, and pushes.
    val assignedAt: Long? = null,
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = LeadEntity::class,
        parentColumns = ["id"],
        childColumns = ["leadId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("leadId")]
)
data class NoteEntity(

    @PrimaryKey val id: String,
    val leadId: String,
    val text: String,

    val timestamp: Long = System.currentTimeMillis(),

    val authorName: String? = null,

    val authorId: String? = null,

    val imageUrl: String? = null,

    val timeLabel: String? = null,
)

@Entity(
    tableName = "status_history",
    foreignKeys = [ForeignKey(
        entity = LeadEntity::class,
        parentColumns = ["id"],
        childColumns = ["leadId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("leadId")]
)
data class StatusHistoryEntity(
    @PrimaryKey val id: String,
    val leadId: String,
    val previousStatus: String?,
    val newStatus: String,
    val changedBy: String,
    val changedAt: Long = System.currentTimeMillis(),
)

/**
 * An agent-filed bug report. Deliberately not tied to a lead, so no foreign key.
 *
 * Reports are visible to every logged-in agent via `GET api/bugs`. [isSynced] false means this row
 * exists only on this device because the push failed — the UI says so rather than implying the team
 * has seen it.
 *
 * Id convention matches notes: a local report gets a UUID (contains '-'), a server one won't, which
 * is what lets [BugReportDao.replaceServerReports] refresh server rows without touching unsent ones.
 */
@Entity(tableName = "bug_reports")
data class BugReportEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val reporterName: String,
    val reporterId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    /**
     * Server-owned triage state. Stored as free text, not an enum, so a status added on the backend
     * shows up instead of failing to parse. Literal default mirrors `BugStatus.OPEN` — spelled out
     * to keep this Room layer free of remote-package imports.
     */
    val status: String = "Open",
)
