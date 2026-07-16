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

    val statusChangedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
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
