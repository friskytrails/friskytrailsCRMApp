package com.crmapplication.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.callSyncDataStore by preferencesDataStore(name = "call_sync")

@Singleton
class CallSyncStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private val WATERMARK_KEY = longPreferencesKey("last_logged_call_id")
        const val NO_WATERMARK = -1L
    }

    suspend fun getWatermark(): Long =
        context.callSyncDataStore.data.first()[WATERMARK_KEY] ?: NO_WATERMARK

    suspend fun setWatermark(id: Long) {
        context.callSyncDataStore.edit { it[WATERMARK_KEY] = id }
    }

    suspend fun hasBaseline(): Boolean =
        context.callSyncDataStore.data.first()[WATERMARK_KEY] != null
}
