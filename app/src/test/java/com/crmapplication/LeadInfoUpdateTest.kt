package com.crmapplication

import com.crmapplication.LeadDetailVM.remote.ApiLeadDto
import com.crmapplication.LeadDetailVM.remote.UpdateLeadInfoRequest
import com.crmapplication.LeadDetailVM.repository.toEntity
import com.crmapplication.utils.formatTravelDate
import com.crmapplication.utils.parseTravelDateMillis
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Guards travel date and party size on a lead (`PUT api/leads/{id}`).
 *
 * The fragile part is the *partial* update: the backend reads an absent key as "leave unchanged", so
 * which keys reach the wire decides whether editing one field silently wipes another. Gson's
 * `serializeNulls = false` (the default, and what the shared Retrofit uses) is what makes that work,
 * so these assertions pin the serializer's behaviour, not just the data class.
 */
class LeadInfoUpdateTest {

    private val gson = Gson()

    private fun json(request: UpdateLeadInfoRequest): String = gson.toJson(request)

    // region request body

    @Test
    fun `editing one field sends only that field`() {
        assertEquals("""{"travelDate":"2026-09-15"}""", json(UpdateLeadInfoRequest(travelDate = "2026-09-15")))
        assertEquals("""{"numberOfPersons":4}""", json(UpdateLeadInfoRequest(numberOfPersons = 4)))
        assertEquals("""{"name":"Saheli"}""", json(UpdateLeadInfoRequest(name = "Saheli")))
    }

    @Test
    fun `both fields can go together`() {
        assertEquals(
            """{"travelDate":"2026-09-15","numberOfPersons":4}""",
            json(UpdateLeadInfoRequest(travelDate = "2026-09-15", numberOfPersons = 4)),
        )
    }

    /**
     * The reason a clear sends `""` / `0` rather than null: a null field is dropped from the JSON
     * entirely, which the backend reads as "leave unchanged" — the clear would be a silent no-op.
     */
    @Test
    fun `null fields are omitted, so a clear must send a value`() {
        assertEquals("{}", json(UpdateLeadInfoRequest()))
        assertEquals("""{"travelDate":""}""", json(UpdateLeadInfoRequest(travelDate = "")))
        assertEquals("""{"numberOfPersons":0}""", json(UpdateLeadInfoRequest(numberOfPersons = 0)))
    }

    @Test
    fun `isEmpty marks a request with nothing to send`() {
        assertTrue(UpdateLeadInfoRequest().isEmpty)
        assertFalse(UpdateLeadInfoRequest(travelDate = "").isEmpty)
        assertFalse(UpdateLeadInfoRequest(numberOfPersons = 0).isEmpty)
        assertFalse(UpdateLeadInfoRequest(name = "Saheli").isEmpty)
    }

    // endregion

    // region response parsing

    @Test
    fun `the documented response shape parses`() {
        val dto = gson.fromJson(
            """
            {
              "id": "66af4d90e1f3a29001b2a75c",
              "name": "Saheli",
              "phone": "8918885347",
              "travelDate": "2026-09-15",
              "numberOfPersons": 4,
              "status": "Interested Leads"
            }
            """.trimIndent(),
            ApiLeadDto::class.java,
        )
        assertEquals("2026-09-15", dto.travelDate)
        assertEquals(4, dto.numberOfPersons)
    }

    /** The backend's documented legacy alias for the same value. */
    @Test
    fun `noOfPax parses into numberOfPersons`() {
        val dto = gson.fromJson("""{"noOfPax": 6}""", ApiLeadDto::class.java)
        assertEquals(6, dto.numberOfPersons)
    }

    @Test
    fun `absent fields parse as null, not zero`() {
        val dto = gson.fromJson("""{"name": "Saheli"}""", ApiLeadDto::class.java)
        assertNull(dto.travelDate)
        assertNull(dto.numberOfPersons)
    }

    // endregion

    // region dto to entity

    @Test
    fun `both fields survive the mapping to the local entity`() {
        val entity = ApiLeadDto(
            id = "1",
            name = "Saheli",
            phone = "8918885347",
            travelDate = "2026-09-15",
            numberOfPersons = 4,
        ).toEntity()

        assertEquals("2026-09-15", entity.travelDate)
        assertEquals(4, entity.numberOfPersons)
    }

    /**
     * The backend defaults `travelDate` to `""` and leaves persons null, so both "absent" and
     * "explicitly empty" have to land as null locally — otherwise the UI would print a blank value
     * or a meaningless 0 instead of the not-set dash.
     */
    @Test
    fun `empty and zero from the server become not-set`() {
        val entity = ApiLeadDto(
            id = "1",
            name = "Saheli",
            phone = "8918885347",
            travelDate = "   ",
            numberOfPersons = 0,
        ).toEntity()

        assertNull(entity.travelDate)
        assertNull(entity.numberOfPersons)
    }

    // endregion

    // region display formatting

    @Test
    fun `an api date is shown in a readable form`() {
        // Locale-independent: build the expectation the same way the app formats it.
        val expected = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-09-15")!!)
        assertEquals(expected, formatTravelDate("2026-09-15"))
    }

    /**
     * The field is free-form in the backend schema, so the web dashboard may have written something
     * that isn't `yyyy-MM-dd`. Showing it verbatim beats showing an error or a wrong date.
     */
    @Test
    fun `an unparseable date is shown as stored`() {
        assertEquals("Mid September", formatTravelDate("Mid September"))
        assertEquals("15/09/2026", formatTravelDate("15/09/2026"))
        // Real calendar dates only — 2026-02-31 is not one, so it can't be silently rolled forward.
        assertEquals("2026-02-31", formatTravelDate("2026-02-31"))
    }

    @Test
    fun `blank and absent dates have nothing to show`() {
        assertNull(formatTravelDate(null))
        assertNull(formatTravelDate(""))
        assertNull(formatTravelDate("   "))
    }

    @Test
    fun `a stored date round-trips back to the picker`() {
        val millis = parseTravelDateMillis("2026-09-15")!!
        assertEquals("2026-09-15", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(millis))
    }

    @Test
    fun `the picker falls back when the stored date is not an api date`() {
        assertNull(parseTravelDateMillis("Mid September"))
        assertNull(parseTravelDateMillis(null))
        assertNull(parseTravelDateMillis(""))
    }

    // endregion
}
