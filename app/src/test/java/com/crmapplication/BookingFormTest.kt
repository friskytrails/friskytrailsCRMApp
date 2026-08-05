package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.BookingForm
import com.crmapplication.LeadDetailVM.repository.toRequest
import com.crmapplication.LeadDetailVM.repository.validate
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Guards the booking form (`PUT /api/leads/:id/book`).
 *
 * Worth testing despite being "just a form": submitting is the only route to `Booked`, and once a lead
 * is booked this app won't let the status change again. A validation hole therefore produces an
 * unfixable record — an empty booking, or a ₹0 trip, that the agent can't take back.
 */
class BookingFormTest {

    /** Local midnight for a given date, matching what the date picker hands the form. */
    private fun dateMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun validForm() = BookingForm(
        fullName = "John Doe",
        emailId = "john.doe@example.com",
        contactNumber = "9876543210",
        emergencyContactNumber = "9123456780",
        packageName = "Bali Honeymoon Package",
        noOfPax = "2",
        startDateMillis = dateMillis(2026, 8, 15),
        endDateMillis = dateMillis(2026, 8, 20),
        totalAmount = "150000",
        paidAmount = "50000",
        dueAmount = "100000",
    )

    @Test
    fun `a fully filled form validates`() {
        assertTrue(validForm().validate().isValid)
    }

    @Test
    fun `an empty form reports every field`() {
        val errors = BookingForm().validate()
        assertFalse(errors.isValid)
        listOf(
            errors.fullName, errors.emailId, errors.contactNumber, errors.emergencyContactNumber,
            errors.packageName, errors.noOfPax, errors.startDate, errors.endDate,
            errors.totalAmount, errors.paidAmount, errors.dueAmount,
        ).forEach { assertNotNull("every blank field should report an error", it) }
    }

    @Test
    fun `malformed email is rejected`() {
        assertNotNull(validForm().copy(emailId = "john.doe").validate().emailId)
        assertNotNull(validForm().copy(emailId = "john@doe").validate().emailId)
        assertNotNull(validForm().copy(emailId = "john doe@x.com").validate().emailId)
        assertNull(validForm().copy(emailId = "j.d+tag@sub.example.co.in").validate().emailId)
    }

    @Test
    fun `phone numbers need 10 to 15 digits`() {
        assertNotNull(validForm().copy(contactNumber = "98765").validate().contactNumber)
        assertNotNull(
            validForm().copy(emergencyContactNumber = "1234567890123456").validate()
                .emergencyContactNumber
        )
        // Formatting shouldn't matter — only the digits do.
        assertNull(validForm().copy(contactNumber = "+91 98765-43210").validate().contactNumber)
    }

    /**
     * The mockup pre-fills `0` for pax and both amounts. Those are the values most likely to be
     * submitted untouched, so they're the ones that must not pass.
     */
    @Test
    fun `the mockup's zero defaults do not pass`() {
        assertNotNull(validForm().copy(noOfPax = "0").validate().noOfPax)
        assertNotNull(validForm().copy(totalAmount = "0").validate().totalAmount)
    }

    @Test
    fun `paid above total is rejected`() {
        val errors = validForm().copy(totalAmount = "50000", paidAmount = "60000").validate()
        assertNotNull(errors.paidAmount)
    }

    @Test
    fun `a fully paid booking is allowed`() {
        val errors = validForm()
            .copy(totalAmount = "150000", paidAmount = "150000", dueAmount = "0")
            .validate()
        assertTrue(errors.isValid)
    }

    @Test
    fun `end date before start date is rejected`() {
        val errors = validForm().copy(
            startDateMillis = dateMillis(2026, 8, 20),
            endDateMillis = dateMillis(2026, 8, 15),
        ).validate()
        assertNotNull(errors.endDate)
    }

    @Test
    fun `a same-day trip is allowed`() {
        val day = dateMillis(2026, 8, 15)
        val errors = validForm().copy(startDateMillis = day, endDateMillis = day).validate()
        assertTrue(errors.isValid)
    }

    @Test
    fun `amounts tolerate rupee formatting pasted from a quote`() {
        val errors = validForm().copy(totalAmount = "₹1,50,000", paidAmount = "50,000").validate()
        assertNull(errors.totalAmount)
        assertNull(errors.paidAmount)
    }

    @Test
    fun `due amount is total minus paid, floored at zero`() {
        assertEquals(
            100_000L,
            validForm().copy(totalAmount = "150000", paidAmount = "50000").impliedDueAmount,
        )
        // Over-payment is caught by validation; the helper still must not return a negative.
        assertEquals(
            0L,
            validForm().copy(totalAmount = "50000", paidAmount = "60000").impliedDueAmount,
        )
        assertNull(validForm().copy(totalAmount = "", paidAmount = "50000").impliedDueAmount)
    }

    /**
     * The wire shape, against the documented example. Two things this pins down:
     * dates go out as `yyyy-MM-dd` (not the `dd-MM-yyyy` the form displays), and the payload is
     * wrapped in `bookingDetails`.
     */
    @Test
    fun `request matches the documented payload`() {
        val json = Gson().toJsonTree(validForm().toRequest()).asJsonObject
        val details = json.getAsJsonObject("bookingDetails")
        assertNotNull("payload must be wrapped in bookingDetails", details)

        assertEquals("John Doe", details.get("fullName").asString)
        assertEquals("john.doe@example.com", details.get("emailId").asString)
        assertEquals("9876543210", details.get("contactNumber").asString)
        assertEquals("9123456780", details.get("emergencyContactNumber").asString)
        assertEquals("Bali Honeymoon Package", details.get("packageName").asString)
        assertEquals(2, details.get("noOfPax").asInt)
        assertEquals("2026-08-15", details.get("startDate").asString)
        assertEquals("2026-08-20", details.get("endDate").asString)
        assertEquals(150000L, details.get("totalAmount").asLong)
        assertEquals(50000L, details.get("paidAmount").asLong)
        assertEquals(100000L, details.get("dueAmount").asLong)
    }

    /**
     * Omitting both trip keys is what makes the backend append a new trip and generate its own
     * `TRIP-XXXXXX`. Sending `tripIndex` would overwrite an existing trip instead — a silent data loss
     * that no UI would reveal, so it's pinned here.
     */
    @Test
    fun `request sends no tripId or tripIndex`() {
        val details = Gson().toJsonTree(validForm().toRequest())
            .asJsonObject.getAsJsonObject("bookingDetails")
        assertFalse(details.has("tripId"))
        assertFalse(details.has("tripIndex"))
    }

    @Test
    fun `whitespace is trimmed before sending`() {
        val details = Gson()
            .toJsonTree(validForm().copy(fullName = "  John Doe  ", emailId = " a@b.com ").toRequest())
            .asJsonObject.getAsJsonObject("bookingDetails")
        assertEquals("John Doe", details.get("fullName").asString)
        assertEquals("a@b.com", details.get("emailId").asString)
    }
}
