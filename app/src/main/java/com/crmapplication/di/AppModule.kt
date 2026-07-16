package com.crmapplication.di

import android.content.Context
import androidx.room.Room
import com.crmapplication.LeadDetailVM.local.CrmDatabase
import com.crmapplication.LeadDetailVM.local.LeadDao
import com.crmapplication.LeadDetailVM.local.NoteDao
import com.crmapplication.LeadDetailVM.local.StatusHistoryDao
import com.crmapplication.LeadDetailVM.remote.ApiService
import com.crmapplication.LeadDetailVM.remote.FakeApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideApiService(): ApiService = FakeApiService()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CrmDatabase =
        Room.databaseBuilder(context, CrmDatabase::class.java, "crm_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideLeadDao(db: CrmDatabase): LeadDao = db.leadDao()
    @Provides fun provideNoteDao(db: CrmDatabase): NoteDao = db.noteDao()
    @Provides fun provideStatusHistoryDao(db: CrmDatabase): StatusHistoryDao = db.statusHistoryDao()
}
// nothing