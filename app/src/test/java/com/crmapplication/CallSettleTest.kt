package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.CALL_SETTLE_MS
import com.crmapplication.LeadDetailVM.repository.isDurationUnsettled
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSettleTest {

    private val now = 1_800_000_000_000L

    private fun entry(
        type: CallType,
        durationSeconds: Long,
        ageMs: Long,
    ) = CallLogEntry(
        id = 1L,
        number = "9001622113",
        type = type,
        dateMillis = now - ageMs,
        durationSeconds = durationSeconds,
    )

    @Test
    fun `fresh in-progress call with no duration yet is held back`() {
        assertTrue(isDurationUnsettled(entry(CallType.OUTGOING, 0, ageMs = 2_000), now))
        assertTrue(isDurationUnsettled(entry(CallType.INCOMING, 0, ageMs = 2_000), now))
    }

    @Test
    fun `a duration already written means the row is final regardless of age`() {
        // Android rewrites DURATION on hang-up, so any non-zero value is settled. A short call must
        // post immediately — waiting out the window would strand it until the next sync trigger.
        assertFalse(isDurationUnsettled(entry(CallType.OUTGOING, 5, ageMs = 500), now))
    }

    @Test
    fun `missed and rejected calls are legitimately zero-duration and post at once`() {
        assertFalse(isDurationUnsettled(entry(CallType.MISSED, 0, ageMs = 100), now))
        assertFalse(isDurationUnsettled(entry(CallType.REJECTED, 0, ageMs = 100), now))
        assertFalse(isDurationUnsettled(entry(CallType.VOICEMAIL, 0, ageMs = 100), now))
    }

    @Test
    fun `an old zero-duration dial has stopped changing and posts as failed`() {
        assertFalse(isDurationUnsettled(entry(CallType.OUTGOING, 0, ageMs = CALL_SETTLE_MS + 1), now))
    }

    @Test
    fun `a future-stamped row is treated as unsettled rather than posted`() {
        // Device clock skew. Posting a future timestamp would make the server compute negative idle.
        assertTrue(isDurationUnsettled(entry(CallType.OUTGOING, 0, ageMs = -60_000), now))
    }
}
