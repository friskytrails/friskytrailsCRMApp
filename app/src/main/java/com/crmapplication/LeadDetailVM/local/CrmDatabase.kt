package com.crmapplication.LeadDetailVM.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LeadEntity::class, NoteEntity::class, StatusHistoryEntity::class],

    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CrmDatabase : RoomDatabase() {
    abstract fun leadDao(): LeadDao
    abstract fun noteDao(): NoteDao
    abstract fun statusHistoryDao(): StatusHistoryDao
}
