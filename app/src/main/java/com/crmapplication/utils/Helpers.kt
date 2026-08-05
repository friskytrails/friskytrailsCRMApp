package com.crmapplication.utils

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

/**
 * Calling code assumed when a lead's number carries none. Leads are entered in national
 * (10-digit) form far more often than international form, and WhatsApp cannot resolve a number
 * without a country code.
 */
private const val DEFAULT_COUNTRY_CODE = "91"

/** Length of a national (country-code-less) number for [DEFAULT_COUNTRY_CODE]. */
private const val NATIONAL_NUMBER_DIGITS = 10

/** Longest number E.164 allows; anything above this is malformed data, not a real number. */
private const val MAX_E164_DIGITS = 15

/**
 * Normalises a lead's phone number to the digits-only international form WhatsApp expects:
 * country code + national number, no `+`, no `00` prefix, no trunk `0`.
 *
 * Stripping non-digits alone is not enough — that leaves `9876543210` (no country code),
 * `09876543210` (trunk prefix) and `919198765432` shapes that all resolve to "number is not on
 * WhatsApp". A number that already declares its country code (leading `+` or `00`) is trusted as
 * given, so international leads keep working; only the ambiguous shapes get [DEFAULT_COUNTRY_CODE].
 */
fun toWhatsAppNumber(phone: String): String {
    val trimmed = phone.trim()
    val digits = trimmed.filter(Char::isDigit)
    if (digits.isEmpty()) return ""

    // A leading '+' or '00' means the number states its own country code, so the default must
    // not be prepended. The rest of the cleanup still applies — a declared code can be malformed.
    val statesCountryCode = trimmed.startsWith("+") || digits.startsWith("00")

    // Drop dial-out '00' and any trunk/stray leading zeros ("0 98765 43210", "091 98765 43210").
    val bare = (if (digits.startsWith("00")) digits.drop(2) else digits).trimStart('0')
    if (bare.isEmpty()) return ""

    val cc = DEFAULT_COUNTRY_CODE
    val ccLength = cc.length
    return when {
        // Country code typed twice ("+91 91 98765 43210", "9191 98765 43210").
        bare.length == 2 * ccLength + NATIONAL_NUMBER_DIGITS &&
            bare.startsWith(cc.repeat(2)) -> bare.drop(ccLength)
        // Already well-formed "91XXXXXXXXXX".
        bare.length == ccLength + NATIONAL_NUMBER_DIGITS && bare.startsWith(cc) -> bare
        // Bare national number — the common case, and the one that used to fail.
        !statesCountryCode && bare.length == NATIONAL_NUMBER_DIGITS -> cc + bare
        // Another country's code + subscriber number — leave it alone.
        bare.length <= MAX_E164_DIGITS -> bare
        // Beyond E.164: keep the trailing subscriber digits, which is the identifying part.
        else -> cc + bare.takeLast(NATIONAL_NUMBER_DIGITS)
    }
}

fun formatWhatsAppUrl(phone: String): String = "https://wa.me/${toWhatsAppNumber(phone)}"

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

/**
 * Displays a lead's `travelDate`. The backend stores it as a free-form string — usually
 * `yyyy-MM-dd`, but the web dashboard can write an already-formatted date — so a value that doesn't
 * parse is shown as the agent typed it rather than replaced with an error or a wrong date.
 */
fun formatTravelDate(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
    }.getOrNull()
    return parsed?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it) } ?: value
}

/**
 * Parses a lead's `travelDate` back to epoch millis for the date picker's initial selection, or null
 * when it isn't a `yyyy-MM-dd` value (in which case the picker just opens on today).
 */
fun parseTravelDateMillis(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)?.time
    }.getOrNull()
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

/**
 * Canonical UTC timestamp for the calls API, matching the backend contract exactly:
 * `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` (e.g. "2026-07-21T17:00:00.000Z"). Unlike [formatIso8601],
 * this always emits UTC with seconds + milliseconds, never a local offset and never dropping
 * fields. The backend buckets "today" (live-activity) and computes idle (live-status) from this
 * value, so a canonical UTC string keeps both reports correct regardless of device timezone.
 */
private val ISO_8601_UTC: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

fun formatIso8601Utc(epochMs: Long): String =
    ISO_8601_UTC.format(Instant.ofEpochMilli(epochMs))

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
