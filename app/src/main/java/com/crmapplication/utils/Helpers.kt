package com.crmapplication.utils

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

fun timeAgo(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val mins = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    val weeks = days / 7
    return when {
        mins < 60   -> "$mins min ago"
        hours < 24  -> "$hours hr${if (hours > 1) "s" else ""} ago"
        days < 7    -> "$days day${if (days > 1) "s" else ""} ago"
        weeks < 4   -> "$weeks week${if (weeks > 1) "s" else ""} ago"
        else        -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMs))
    }
}

fun formatDate(epochMs: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMs))

fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(epochMs))

fun getDueDateStatus(dueMs: Long): String {
    val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val due = Calendar.getInstance().apply { timeInMillis = dueMs; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return when {
        due.before(now) -> "overdue"
        due == now || due.time == now.time -> "today"
        else -> "upcoming"
    }
}

fun formatWhatsAppUrl(phone: String): String {
    val clean = phone.replace(Regex("[^0-9]"), "")
    return "https://wa.me/$clean"
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (h > 0 || m > 0) append("${m}m ")
        append("${s}s")
    }.trim()
}

fun formatCallTime(epochMs: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(epochMs))

fun formatBookingDateTime(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null

    val zone = ZoneId.systemDefault()
    val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dateOnlyFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    runCatching { Instant.parse(value) }.getOrNull()?.let {
        return dateTimeFmt.format(Date(it.toEpochMilli()))
    }

    runCatching { OffsetDateTime.parse(value) }.getOrNull()?.let {
        return dateTimeFmt.format(Date(it.toInstant().toEpochMilli()))
    }

    runCatching { LocalDateTime.parse(value) }.getOrNull()?.let {
        return dateTimeFmt.format(Date(it.atZone(zone).toInstant().toEpochMilli()))
    }

    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) }.getOrNull()?.let {
        return dateOnlyFmt.format(it)
    }

    return value
}

fun formatTalkTime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatTalkTimeClock(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

fun formatApiDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))

fun formatIso8601(epochMs: Long): String =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).toString()

fun formatDashboardDate(epochMs: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMs))

fun formatMonthLabel(epochMs: Long): String =
    SimpleDateFormat("MMMM yy", Locale.getDefault()).format(Date(epochMs))

fun formatClockTime(epochMs: Long): String =
    SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(epochMs))

fun formatTalkTimeWords(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0 minutes"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"}"
    return when {
        h > 0 && m > 0 -> "${plural(h, "hour")} ${plural(m, "minute")}"
        h > 0          -> plural(h, "hour")
        m > 0          -> plural(m, "minute")
        else           -> plural(totalSeconds, "second")
    }
}

fun formatIdleTime(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0m"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
