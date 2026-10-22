package com.crmapplication.LeadDetailVM.repository

import com.crmapplication.utils.formatApiDate

/**
 * The bank accounts a transaction can be paid into, as `POST /api/bookings` spells them.
 *
 * [apiValue] is sent verbatim — the backend validates against this exact list, so a re-worded label
 * here would fail server-side validation rather than just look different.
 */
enum class PaymentMode(val apiValue: String) {
    KALPANA_BOI("Kalpana BOI"),
    KALPANA_PNB("Kalpana PNB"),
    BABITA_AU("Babita AU"),
    HARI_MOHAN_BOB("Hari Mohan BOB"),
    FT_HDFC("FT HDFC"),
    PRATYUSH_SBI("Pratyush SBI"),
    ;

    companion object {
        /**
         * Every booking made from this app is paid into the HDFC account, so the form fixes the mode
         * rather than offering a choice — see the read-only Payment Mode field in `BookingDetailsDialog`.
         *
         * The other entries stay in this enum because they remain valid server-side: a booking created
         * elsewhere and synced back can legitimately carry any of them.
         */
        val DEFAULT = FT_HDFC
    }
}

/**
 * What the agent types into the Add Booking form before it becomes a `POST /api/bookings` body.
 *
 * Amounts and counts are held as **strings**, not numbers: they're backed by text fields, and an empty
 * field has to stay distinguishable from a deliberate `0` so [validate] can say "required" instead of
 * silently booking a ₹0 trip. Dates are epoch millis because that's what the Material date picker
 * hands back; they're only formatted to `yyyy-MM-dd` at the wire boundary in [toFormFields].
 *
 * The screenshot is held as a **URI string** rather than an `android.net.Uri` for the same reason the
 * rest of this file avoids Android types: [validate] has to be unit-testable, and the project has only
 * JUnit on the test classpath — no Robolectric. The bytes are read at the repository boundary.
 *
 * Note there is no `dueAmount` field. The backend recalculates due from total minus paid on save (and
 * recalculates paid itself from *verified* payments), so an editable due here would be a number the
 * agent could change and the server would ignore. It's derived for display via [impliedDueAmount].
 */
data class BookingForm(
    val fullName: String = "",
    val emailId: String = "",
    val contactNumber: String = "",
    val emergencyContactNumber: String = "",
    val adults: String = "",
    val children: String = "",
    val packageName: String = "",
    val location: String = "",
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val totalAmount: String = "",
    val gstAmount: String = "",
    val paidAmount: String = "",
    val transactionId: String = "",
    val paymentMode: PaymentMode = PaymentMode.DEFAULT,
    val screenshotUri: String? = null,
    val screenshotName: String? = null,
) {
    /**
     * Total minus paid, floored at zero, or null when either isn't a usable number yet. Drives the
     * read-only Due Amount field. Display only — see the class note on why it isn't sent.
     */
    val impliedDueAmount: Long?
        get() {
            val total = totalAmount.toAmountOrNull() ?: return null
            val paid = paidAmount.toAmountOrNull() ?: return null
            return (total - paid).coerceAtLeast(0L)
        }

    /** Party size as the old single-count field expressed it. Null until both counts parse. */
    val totalTravellers: Int?
        get() {
            val adultCount = adults.toCountOrNull() ?: return null
            val childCount = children.toCountOrNull() ?: return null
            return adultCount + childCount
        }

    val hasScreenshot: Boolean get() = !screenshotUri.isNullOrBlank()
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
    val adults: String? = null,
    val children: String? = null,
    val packageName: String? = null,
    val location: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val totalAmount: String? = null,
    val gstAmount: String? = null,
    val paidAmount: String? = null,
    val transactionId: String? = null,
    val screenshot: String? = null,
) {
    val isValid: Boolean
        get() = fullName == null && emailId == null && contactNumber == null &&
            emergencyContactNumber == null && adults == null && children == null &&
            packageName == null && location == null && startDate == null && endDate == null &&
            totalAmount == null && paidAmount == null && transactionId == null &&
            screenshot == null
}

/**
 * Every field the API requires is mandatory here too. Two reasons beyond mirroring the contract: a
 * booking that reached the server half-empty would still flip the lead to `Booked` — and the status
 * can't be changed back from this app — and the transaction screenshot is what an admin verifies the
 * payment against, so a booking without one is unverifiable.
 *
 * Rules follow the API doc, with one deliberate tightening: total amount must be **more than** zero
 * where the backend allows zero. A ₹0 total is the mockup's untouched default, never a real trip.
 */
fun BookingForm.validate(): BookingFormErrors = BookingFormErrors(
    fullName = fullName.requiredText("Full name"),
    emailId = when {
        emailId.isBlank() -> "Email ID is required"
        !EMAIL_REGEX.matches(emailId.trim()) -> "Enter a valid email address"
        else -> null
    },
    contactNumber = contactNumber.validateIndianMobile("Phone number"),
    // Not sent to the booking API, so it isn't held to the backend's 10-digit rule — kept permissive
    // so an agent can record an international next-of-kin number.
    emergencyContactNumber = when {
        emergencyContactNumber.isBlank() -> "Emergency contact number is required"
        emergencyContactNumber.digits().length !in LOOSE_PHONE_DIGIT_RANGE ->
            "Enter a valid phone number (10-15 digits)"
        else -> null
    },
    // The combined "at least 1" check is reported on Adults because that's the field an agent would
    // fix; Children legitimately stays 0 on most bookings.
    adults = when (val count = adults.toCountOrNull()) {
        null -> if (adults.isBlank()) "Adults is required" else "Enter a whole number"
        else -> {
            val childCount = children.toCountOrNull()
            if (childCount != null && count + childCount < 1) "At least 1 traveller is required"
            else null
        }
    },
    children = when (children.toCountOrNull()) {
        null -> if (children.isBlank()) "Children is required" else "Enter a whole number"
        else -> null
    },
    packageName = packageName.requiredText("Package name"),
    location = location.requiredText("Start / end location"),
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
    gstAmount = when (val gst = gstAmount.toAmountOrNull()) {
        null -> if (gstAmount.isBlank()) null else "Enter a valid GST amount"
        else -> null
    },
    paidAmount = when (val paid = paidAmount.toAmountOrNull()) {
        null -> if (paidAmount.isBlank()) "Paid amount is required" else "Enter a valid amount"
        else -> {
            val total = totalAmount.toAmountOrNull()
            if (total != null && paid > total) "Paid can't be more than the total" else null
        }
    },
    transactionId = when {
        transactionId.isBlank() -> "Transaction ID is required"
        !TRANSACTION_ID_REGEX.matches(transactionId.trim()) ->
            "Use only letters, numbers, underscore or hyphen"
        else -> null
    },
    screenshot = if (hasScreenshot) null else "Attach the transaction screenshot",
)

/**
 * Form → multipart text fields. Call only on a form that [validate] accepted; the fallbacks exist so
 * this can't throw, not as a licence to skip validation.
 *
 * The file itself isn't here — the caller adds the `screenshot` part. Keys match the API doc exactly.
 *
 * Deliberately absent: `dueAmount` (server-derived), and every field the doc marks server-owned —
 * `bookingId`, `paymentId`, `createdBy`, `status`, `tripStatus`. Sending a client-chosen `status`
 * would fight the backend's "create always starts as Pending" rule.
 *
 * @param leadId the CRM lead's Mongo `_id`. Sent so the backend syncs that lead to `Booked`; omitted
 *   when blank, since an empty value would read as a request to update a lead with no id.
 */
fun BookingForm.toFormFields(leadId: String? = null): Map<String, String> = buildMap {
    put("travellerName", fullName.trim())
    put("travellerEmail", emailId.trim().lowercase())
    put("travellerPhone", contactNumber.toIndianMobileDigits())
    put("adults", (adults.toCountOrNull() ?: 0).toString())
    put("children", (children.toCountOrNull() ?: 0).toString())
    put("packageName", packageName.trim())
    put("location", location.trim())
    put("startDate", startDateMillis?.let(::formatApiDate).orEmpty())
    put("endDate", endDateMillis?.let(::formatApiDate).orEmpty())
    put("totalAmount", (totalAmount.toAmountOrNull() ?: 0L).toString())
    put("paidAmount", (paidAmount.toAmountOrNull() ?: 0L).toString())
    put("transactionId", transactionId.trim())
    put("paymentMode", paymentMode.apiValue)

    // Not part of the documented contract — the backend ignores unknown fields. Sent anyway so a
    // number the agent took the trouble to collect isn't dropped on the floor if the field is added.
    emergencyContactNumber.trim().takeIf { it.isNotEmpty() }
        ?.let { put("emergencyContactNumber", it) }

    gstAmount.toAmountOrNull()
        ?.let { put("gstAmount", it.toString()) }

    leadId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("leadId", it) }
}

private fun String.requiredText(label: String): String? =
    if (isBlank()) "$label is required" else null

private fun String.digits(): String = filter(Char::isDigit)

/**
 * Reduces anything an agent (or a synced lead record) might hold to the bare 10-digit subscriber
 * number: `+91 98765-43210`, `091…`, and `9876543210` all collapse to the same thing.
 *
 * This normalisation is load-bearing rather than cosmetic. `contactNumber` is pre-filled from the
 * lead, which commonly stores a `+91` prefix, and the backend rejects anything that isn't exactly 10
 * digits — so without this the form would open already invalid on a perfectly good number.
 */
internal fun String.toIndianMobileDigits(): String {
    var digits = digits().trimStart('0')
    if (digits.length == 12 && digits.startsWith(INDIA_COUNTRY_CODE)) {
        digits = digits.removePrefix(INDIA_COUNTRY_CODE)
    }
    return digits
}

/** The API's rule: exactly 10 digits, first digit 6-9. */
private fun String.validateIndianMobile(label: String): String? {
    if (isBlank()) return "$label is required"
    val digits = toIndianMobileDigits()
    return when {
        digits.length != 10 -> "$label must be 10 digits"
        digits.first() !in '6'..'9' -> "$label must start with 6, 7, 8 or 9"
        else -> null
    }
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

/** A pax count: a non-negative whole number, or null if it isn't one yet. */
private fun String.toCountOrNull(): Int? = trim().toIntOrNull()?.takeIf { it >= 0 }

/** Deliberately permissive — enough to catch a typo, not to police exotic-but-valid addresses. */
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

/** The API's rule: letters, digits, underscore and hyphen only. */
private val TRANSACTION_ID_REGEX = Regex("^[A-Za-z0-9_-]+$")

private const val INDIA_COUNTRY_CODE = "91"

/** For the emergency number only, which no backend validates. */
private val LOOSE_PHONE_DIGIT_RANGE = 10..15
