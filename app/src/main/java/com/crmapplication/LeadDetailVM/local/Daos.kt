package com.crmapplication.LeadDetailVM.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {
    @Query("SELECT * FROM leads ORDER BY createdAt DESC")
    fun getAllLeads(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun getLeadById(id: String): LeadEntity?

    @Upsert
    suspend fun upsertLeads(leads: List<LeadEntity>)

    @Upsert
    suspend fun upsertLead(lead: LeadEntity)

    @Query("UPDATE leads SET dueDate = :dueDate WHERE id = :leadId")
    suspend fun updateDueDate(leadId: String, dueDate: Long?)

    @Query("UPDATE leads SET status = :status, statusChangedAt = :changedAt WHERE id = :leadId")
    suspend fun updateStatus(leadId: String, status: String, changedAt: Long)

    @Query("DELETE FROM leads WHERE id NOT IN (:keepIds)")
    suspend fun deleteLeadsNotIn(keepIds: List<String>)

    @Query("DELETE FROM leads")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getNotesForLead(leadId: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM notes WHERE leadId = :leadId AND id NOT LIKE '%-%'")
    suspend fun deleteServerNotesForLead(leadId: String)

    @Transaction
    suspend fun replaceServerNotes(leadId: String, serverNotes: List<NoteEntity>) {
        deleteServerNotesForLead(leadId)
        if (serverNotes.isNotEmpty()) insertNotes(serverNotes)
    }
}

@Dao
interface StatusHistoryDao {

    @Query("SELECT * FROM status_history WHERE leadId = :leadId ORDER BY changedAt DESC")
    fun getHistoryForLead(leadId: String): Flow<List<StatusHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: StatusHistoryEntity)
}
