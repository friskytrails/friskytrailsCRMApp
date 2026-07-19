package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.isPermanentCallLogError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogErrorTest {

    @Test
    fun `body rejections are permanent and get skipped`() {
        // 400 covers both documented cases: missing required fields and a duplicate clientCallId.
        assertTrue(isPermanentCallLogError(400))
        assertTrue(isPermanentCallLogError(409))
        assertTrue(isPermanentCallLogError(422))
    }

    @Test
    fun `auth and routing failures are retried, not skipped`() {
        // Skipping these would silently discard calls that succeed once the token or config is fixed.
        assertFalse(isPermanentCallLogError(401))
        assertFalse(isPermanentCallLogError(403))
        assertFalse(isPermanentCallLogError(404))
    }

    @Test
    fun `throttling and server errors are retried`() {
        assertFalse(isPermanentCallLogError(429))
        assertFalse(isPermanentCallLogError(500))
        assertFalse(isPermanentCallLogError(502))
        assertFalse(isPermanentCallLogError(503))
    }
}
