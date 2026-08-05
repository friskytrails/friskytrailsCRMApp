package com.crmapplication.LeadDetailVM.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LeadEntity::class, NoteEntity::class, StatusHistoryEntity::class, BugReportEntity::class],

    // 12 → 13: leads gained `travelDate` and `numberOfPersons`, now that PUT api/leads/{id} lets
    // agents edit them. AppModule still uses fallbackToDestructiveMigration(), so this bump drops
    // local tables once on the next launch. Leads, notes and bug reports all re-sync from the server
    // and due dates survive in DueDateStore; an unsent local-only note or report is the one thing
    // that is lost.
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CrmDatabase : RoomDatabase() {
    abstract fun leadDao(): LeadDao
    abstract fun noteDao(): NoteDao
    abstract fun statusHistoryDao(): StatusHistoryDao
    abstract fun bugReportDao(): BugReportDao
}
