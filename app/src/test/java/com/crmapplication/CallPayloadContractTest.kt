package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.callStatusFor
import com.crmapplication.LeadDetailVM.repository.isValidObjectId
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the POST /api/calls body to the contract in call_logs_api_docs.md: `leadId` must be castable
 * to an ObjectId, and `status` must be one of the four enum values the backend accepts.
 */
class CallPayloadContractTest {

    @Test
    fun `a real 24-char hex ObjectId is accepted in either case`() {
        assertTrue(isValidObjectId("64f1a2b3c4d5e6f7a8b9c0d1"))
        assertTrue(isValidObjectId("64F1A2B3C4D5E6F7A8B9C0D1"))
    }

    @Test
    fun `the lead-id fallbacks that are not ObjectIds are rejected`() {
        // Models.kt stableId falls back to a numeric leadId then the phone number, so both shapes
        // reach the call site. Sending either would make the backend throw a CastError.
        assertFalse(isValidObjectId("1042"))
        assertFalse(isValidObjectId("9001622113"))
        assertFalse(isValidObjectId(""))
    }

    @Test
    fun `near-miss ids are rejected rather than sent hopefully`() {
        assertFalse(isValidObjectId("64f1a2b3c4d5e6f7a8b9c0d"))   // 23 chars
        assertFalse(isValidObjectId("64f1a2b3c4d5e6f7a8b9c0d12"))  // 25 chars
        assertFalse(isValidObjectId("64f1a2b3c4d5e6f7a8b9c0dg"))   // 24 chars, 'g' is not hex
        assertFalse(isValidObjectId("64f1a2b3-c4d5-e6f7-a8b9"))    // uuid-ish
    }

    @Test
    fun `every emitted status is one of the four the backend accepts`() {
        val accepted = setOf("Connected", "Missed", "Failed", "Voicemail")
        // Both duration branches, since status depends on it for incoming and outgoing.
        val emitted = CallType.entries.flatMap { type ->
            listOf(0L, 42L).map { duration ->
                callStatusFor(
                    CallLogEntry(
                        id = 1L,
                        number = "9001622113",
                        type = type,
                        dateMillis = 1_800_000_000_000L,
                        durationSeconds = duration,
                    )
                )
            }
        }
        // null means "don't post at all" (blocked/unknown), which never reaches the request body.
        val statuses = emitted.filterNotNull().toSet()
        assertTrue("unexpected status values: ${statuses - accepted}", accepted.containsAll(statuses))
        assertTrue("some accepted statuses are unreachable", statuses == accepted)
    }
}
