package com.crmapplication.calllog

import android.provider.CallLog

data class CallLogEntry(
    val id: Long,
    val number: String,
    val type: CallType,
    val dateMillis: Long,
    val durationSeconds: Long,
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    VOICEMAIL,
    REJECTED,
    BLOCKED,
    UNKNOWN;

    val label: String
        get() = when (this) {
            INCOMING -> "Incoming"
            OUTGOING -> "Dialed"
            MISSED -> "Missed"
            VOICEMAIL -> "Voicemail"
            REJECTED -> "Rejected"
            BLOCKED -> "Blocked"
            UNKNOWN -> "Call"
        }

    val icon: String
        get() = when (this) {
            INCOMING -> "📥"
            OUTGOING -> "📤"
            MISSED -> "📵"
            VOICEMAIL -> "📨"
            REJECTED -> "🚫"
            BLOCKED -> "⛔"
            UNKNOWN -> "📞"
        }

    companion object {
        fun fromProviderType(value: Int): CallType = when (value) {
            CallLog.Calls.INCOMING_TYPE -> INCOMING
            CallLog.Calls.OUTGOING_TYPE -> OUTGOING
            CallLog.Calls.MISSED_TYPE -> MISSED
            CallLog.Calls.VOICEMAIL_TYPE -> VOICEMAIL
            CallLog.Calls.REJECTED_TYPE -> REJECTED
            CallLog.Calls.BLOCKED_TYPE -> BLOCKED
            else -> UNKNOWN
        }
    }
}

data class NumberCallStats(
    val totalCalls: Int,
    val dialedCount: Int,
    val incomingCount: Int,
    val missedCount: Int,
    val totalDurationSeconds: Long,
    val outgoingDurationSeconds: Long,
    val incomingDurationSeconds: Long,
) {
    val hasCalls: Boolean get() = totalCalls > 0
}

fun callStats(calls: List<CallLogEntry>): NumberCallStats = NumberCallStats(
    totalCalls = calls.size,
    dialedCount = calls.count { it.type == CallType.OUTGOING },
    incomingCount = calls.count { it.type == CallType.INCOMING },
    missedCount = calls.count { it.type == CallType.MISSED },
    totalDurationSeconds = calls.sumOf { it.durationSeconds },
    outgoingDurationSeconds = calls.filter { it.type == CallType.OUTGOING }.sumOf { it.durationSeconds },
    incomingDurationSeconds = calls.filter { it.type == CallType.INCOMING }.sumOf { it.durationSeconds },
)
