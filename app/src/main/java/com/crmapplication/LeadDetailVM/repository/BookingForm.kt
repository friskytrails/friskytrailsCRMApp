package com.crmapplication.LeadDetailVM.repository

import com.crmapplication.LeadDetailVM.remote.BookLeadRequest
import com.crmapplication.LeadDetailVM.remote.BookingDetailsDto
import com.crmapplication.utils.formatApiDate

/**
 * What the agent types into the Booking Details form before it becomes a
 * [BookLeadRequest].
 *
 * Amounts and pax are held as **strings**, not numbers: they're backed by text fields, and an empty
 * field has to stay distinguishable from a deliberate `0` so [validate] can say "required" instead of
 * silently booking a ₹0 trip. Dates are epoch millis because that's what the Material date picker
 * hands back; they're only formatted to `yyyy-MM-dd` at the wire boundary in [toRequest].
 *
 * Deliberately plain Kotlin (no Android types) so [validate] is unit-testable — the project has only
 * JUnit on the test classpath, no Robolectric.
 */
data class BookingForm(
    val fullName: String = "",
    val emailId: String = "",
    val contactNumber: String = "",
    val emergencyContactNumber: String = "",
    val packageName: String = "",
    val noOfPax: String = "",
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val totalAmount: String = "",
    val paidAmount: String = "",
    val dueAmount: String = "",
) {
    /**
     * Total minus paid, or null when either isn't a usable number yet. Drives the auto-filled Due
     * Amount field; the agent can still overwrite it (a discount makes `paid + due < total` legal),
     * which is why [validate] doesn't insist the three agree.
     */
    val impliedDueAmount: Long?
        get() {
            val total = totalAmount.toAmountOrNull() ?: return null
            val paid = paidAmount.toAmountOrNull() ?: return null
            return (total - paid).coerceAtLeast(0L)
        }
}

/**
 * One message per field, all null when the form is good. A data class rather than a map so the
 * Composable reads `errors.emailId` and can't typo a key.
 */
data class BookingFormErrors(
    val fullName: String? = null,
    val emailId: String? = null,
    val contactNumber: String? = null,
    val emergencyContactNumber: String? = null,
    val packageName: String? = null,
    val noOfPax: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val totalAmount: String? = null,
    val paidAmount: String? = null,
    val dueAmount: String? = null,
) {
    val isValid: Boolean
        get() = fullName == null && emailId == null && contactNumber == null &&
            emergencyContactNumber == null && packageName == null && noOfPax == null &&
            startDate == null && endDate == null && totalAmount == null &&
            paidAmount == null && dueAmount == null
}

/**
 * Every field is mandatory here even though the backend defaults most of them — a booking that
 * reached the server half-empty would still flip the lead to `Booked` and increment the agent's
 * booking count, and the status can't be changed back from this app afterwards. Better to block the
 * submit than to record an unfixable booking.
 */
fun BookingForm.validate(): BookingFormErrors = BookingFormErrors(
    fullName = fullName.requiredText("Full name"),
    emailId = when {
        emailId.isBlank() -> "Email ID is required"
        !EMAIL_REGEX.matches(emailId.trim()) -> "Enter a valid email address"
        else -> null
    },
    contactNumber = contactNumber.validatePhone("Contact number"),
    emergencyContactNumber = emergencyContactNumber.validatePhone("Emergency contact number"),
    packageName = packageName.requiredText("Package name"),
    noOfPax = when (val pax = noOfPax.trim().toIntOrNull()) {
        null -> if (noOfPax.isBlank()) "No. of pax is required" else "Enter a whole number"
        // The mockup pre-fills 0, which is never a real booking — catch it rather than send it.
        else -> if (pax < 1) "At least 1 pax is required" else null
    },
    startDate = if (startDateMillis == null) "Start date is required" else null,
    endDate = when {
        endDateMillis == null -> "End date is required"
        startDateMillis != null && endDateMillis < startDateMillis ->
            "End date can't be before the start date"
        else -> null
    },
    totalAmount = when (val total = totalAmount.toAmountOrNull()) {
        null -> if (totalAmount.isBlank()) "Total amount is required" else "Enter a valid amount"
        else -> if (total <= 0L) "Total amount must be more than 0" else null
    },
    paidAmount = when (val paid = paidAmount.toAmountOrNull()) {
        null -> if (paidAmount.isBlank()) "Paid amount is required" else "Enter a valid amount"
        else -> {
            val total = totalAmount.toAmountOrNull()
            if (total != null && paid > total) "Paid can't be more than the total" else null
        }
    },
    dueAmount = when (dueAmount.toAmountOrNull()) {
        null -> if (dueAmount.isBlank()) "Due amount is required" else "Enter a valid amount"
        else -> null
    },
)

/**
 * Form → wire body. Call only on a form that [validate] accepted; the `?: 0` fallbacks exist so this
 * can't throw, not as a licence to skip validation.
 *
 * `tripId`/`tripIndex` are intentionally omitted: without them the backend appends a new trip and
 * generates its own `TRIP-XXXXXX`. Sending `tripIndex = 0` would silently overwrite the lead's first
 * trip instead, which is wrong for a fresh booking.
 */
fun BookingForm.toRequest(): BookLeadRequest = BookLeadRequest(
    bookingDetails = BookingDetailsDto(
        fullName = fullName.trim(),
        emailId = emailId.trim(),
        contactNumber = contactNumber.trim(),
        emergencyContactNumber = emergencyContactNumber.trim(),
        packageName = packageName.trim(),
        noOfPax = noOfPax.trim().toIntOrNull() ?: 1,
        startDate = startDateMillis?.let(::formatApiDate).orEmpty(),
        endDate = endDateMillis?.let(::formatApiDate).orEmpty(),
        totalAmount = totalAmount.toAmountOrNull() ?: 0L,
        paidAmount = paidAmount.toAmountOrNull() ?: 0L,
        dueAmount = dueAmount.toAmountOrNull() ?: 0L,
    )
)

private fun String.requiredText(label: String): String? =
    if (isBlank()) "$label is required" else null

private fun String.validatePhone(label: String): String? {
    if (isBlank()) return "$label is required"
    val digits = filter(Char::isDigit)
    return if (digits.length !in PHONE_DIGIT_RANGE) "Enter a valid $PHONE_HINT" else null
}

/**
 * Whole rupees only, so a stray separator or symbol pasted from a quote ("₹1,50,000") still reads as
 * a number instead of failing validation. Returns null for blank/negative/non-numeric input.
 */
private fun String.toAmountOrNull(): Long? {
    val cleaned = trim().removePrefix("₹").replace(",", "").replace(" ", "")
    if (cleaned.isBlank()) return null
    return cleaned.toLongOrNull()?.takeIf { it >= 0L }
}

/** Deliberately permissive — enough to catch a typo, not to police exotic-but-valid addresses. */
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

/** 10 digits for an Indian mobile, up to 15 to allow a country code (E.164's ceiling). */
private val PHONE_DIGIT_RANGE = 10..15
private const val PHONE_HINT = "phone number (10-15 digits)"
