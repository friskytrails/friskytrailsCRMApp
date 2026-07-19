package com.crmapplication.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.callSyncDataStore by preferencesDataStore(name = "call_sync")

@Singleton
class CallSyncStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private val WATERMARK_KEY = longPreferencesKey("last_logged_call_id")
        private val INSTALL_ID_KEY = stringPreferencesKey("install_id")
        const val NO_WATERMARK = -1L
    }

    suspend fun getWatermark(): Long =
        context.callSyncDataStore.data.first()[WATERMARK_KEY] ?: NO_WATERMARK

    suspend fun setWatermark(id: Long) {
        context.callSyncDataStore.edit { it[WATERMARK_KEY] = id }
    }

    suspend fun hasBaseline(): Boolean =
        context.callSyncDataStore.data.first()[WATERMARK_KEY] != null

    /**
     * A stable UUID for this install, generated once and persisted. Combined with a device
     * call-log id it yields a [clientCallId] that stays identical across retries, so a
     * timed-out POST /api/calls can be safely re-sent without creating a duplicate.
     */
    suspend fun getInstallId(): String {
        context.callSyncDataStore.data.first()[INSTALL_ID_KEY]?.let { return it }
        val generated = UUID.randomUUID().toString()
        context.callSyncDataStore.edit { it[INSTALL_ID_KEY] = generated }
        return generated
    }
}
