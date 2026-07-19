package com.crmapplication

import com.crmapplication.utils.formatIso8601Utc
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the wire format of the `timestamp` sent in POST /api/calls. The backend derives
 * live-status idle and live-activity first/last-call bucketing from this string, so it must be
 * canonical UTC with seconds + milliseconds + 'Z' — never a local offset, never dropped fields.
 */
class CallTimestampTest {

    @Test
    fun `timestamp is canonical UTC with millis and Z`() {
        // 1_700_000_000_000 ms == 2023-11-14T22:13:20 UTC
        assertEquals("2023-11-14T22:13:20.000Z", formatIso8601Utc(1_700_000_000_000L))
    }

    @Test
    fun `seconds and millis are never dropped on an exact minute`() {
        // 1_700_000_040_000 ms == 2023-11-14T22:14:00 UTC — OffsetDateTime.toString() would emit
        // "…T22:14Z" (no seconds); the canonical formatter must keep ":00.000".
        assertEquals("2023-11-14T22:14:00.000Z", formatIso8601Utc(1_700_000_040_000L))
    }
}
