package com.crmapplication.calllog

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun callsForNumber(number: String): List<CallLogEntry> {
        if (!hasPermission()) return emptyList()
        val targetKey = number.normalizedPhoneKey()
        if (targetKey.isEmpty()) {
            android.util.Log.w(TAG, "Lead number '$number' has no digits — cannot match.")
            return emptyList()
        }
        val all = readAll(context.contentResolver)
        val matched = all.filter { it.number.normalizedPhoneKey() == targetKey }
        if (matched.isEmpty()) {

            val sample = all.take(15).joinToString { "${it.number}→${it.number.normalizedPhoneKey()}" }
            android.util.Log.w(
                TAG,
                "No match for lead '$number' (key=$targetKey). " +
                    "${all.size} calls on device. First numbers: [$sample]",
            )
        } else {
            android.util.Log.d(TAG, "Matched ${matched.size} call(s) for lead '$number' (key=$targetKey).")
        }
        return matched
    }

    suspend fun readAll(): List<CallLogEntry> {
        if (!hasPermission()) return emptyList()
        return readAll(context.contentResolver)
    }

    fun observeChanges(): Flow<Unit> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,  true, observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private suspend fun readAll(contentResolver: ContentResolver): List<CallLogEntry> =
        withContext(Dispatchers.IO) {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                PROJECTION,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { it.toEntries() } ?: emptyList()
        }

    private fun Cursor.toEntries(): List<CallLogEntry> {
        val idIdx = getColumnIndexOrThrow(CallLog.Calls._ID)
        val numberIdx = getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val typeIdx = getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIdx = getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durationIdx = getColumnIndexOrThrow(CallLog.Calls.DURATION)

        val result = ArrayList<CallLogEntry>(count)
        while (moveToNext()) {
            result += CallLogEntry(
                id = getLong(idIdx),
                number = getString(numberIdx).orEmpty().ifBlank { "Unknown" },
                type = CallType.fromProviderType(getInt(typeIdx)),
                dateMillis = getLong(dateIdx),
                durationSeconds = getLong(durationIdx),
            )
        }
        return result
    }

    private companion object {
        const val TAG = "CallLogReader"

        val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
    }
}

fun String.normalizedPhoneKey(): String {
    val digits = filter(Char::isDigit)
    return if (digits.length <= MATCH_DIGITS) digits else digits.takeLast(MATCH_DIGITS)
}

private const val MATCH_DIGITS = 10
