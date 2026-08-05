package com.crmapplication

import com.crmapplication.LeadDetailVM.remote.updateReminderBody
import com.crmapplication.utils.formatIso8601Utc
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Guards the reminder request body (`PUT /api/leads/:id/reminder`).
 *
 * The clear case is the fragile one: it only works if the `reminderDate` key reaches the server
 * carrying an explicit null. A missing key means "leave unchanged" to the backend, so losing the
 * null turns Clear into a silent no-op — the failure mode is invisible from the app.
 */
class UpdateReminderBodyTest {

    private fun bodyText(iso: String?): String =
        Buffer().also { updateReminderBody(iso).writeTo(it) }.readUtf8()

    @Test
    fun `setting a reminder sends the instant`() {
        assertEquals(
            """{"reminderDate":"2026-08-16T15:33:00.000Z"}""",
            bodyText("2026-08-16T15:33:00.000Z"),
        )
    }

    @Test
    fun `clearing sends an explicit null, not an empty object`() {
        assertEquals("""{"reminderDate":null}""", bodyText(null))
    }

    /**
     * Why [updateReminderBody] pre-serializes instead of returning a data class or [JsonObject].
     *
     * This is the behaviour of the shared Retrofit's converter (`GsonConverterFactory.create()`,
     * default `serializeNulls = false`). If this assertion ever starts failing, the workaround is
     * obsolete and the body can go back to being a plain data class.
     */
    @Test
    fun `default Gson would drop the null key - the reason for the workaround`() {
        val withJsonNull = JsonObject().apply { add("reminderDate", JsonNull.INSTANCE) }
        assertEquals("{}", Gson().toJson(withJsonNull))

        data class Naive(val reminderDate: String?)
        assertEquals("{}", Gson().toJson(Naive(null)))
    }

    /**
     * The body's timestamp shape has to match what the endpoint documents. Round-trips the exact
     * example from the API doc: wire string → epoch millis (what the app stores) → wire string.
     */
    @Test
    fun `reminder instant round-trips as UTC with millis`() {
        val documented = "2026-08-16T15:33:00.000Z"
        val stored = Instant.parse(documented).toEpochMilli()
        assertEquals(documented, formatIso8601Utc(stored))
        assertEquals("""{"reminderDate":"$documented"}""", bodyText(formatIso8601Utc(stored)))
    }
}
