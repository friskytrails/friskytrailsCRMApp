package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.computeIdleSeconds
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallType
import org.junit.Assert.assertEquals
import org.junit.Test

class IdleTimeTest {

    private fun call(id: Long, startSeconds: Long, durationSeconds: Long) = CallLogEntry(
        id = id,
        number = "9001622113",
        type = CallType.OUTGOING,
        dateMillis = startSeconds * 1000,
        durationSeconds = durationSeconds,
    )

    @Test
    fun `single call has no idle time`() {
        assertEquals(0L, computeIdleSeconds(listOf(call(1, 0, 120))))
    }

    @Test
    fun `empty list has no idle time`() {
        assertEquals(0L, computeIdleSeconds(emptyList()))
    }

    @Test
    fun `idle is the gap between one call ending and the next starting`() {

        val calls = listOf(call(1, 0, 120), call(2, 200, 60))
        assertEquals(80L, computeIdleSeconds(calls))
    }

    @Test
    fun `idle sums across multiple gaps`() {

        val calls = listOf(call(1, 0, 100), call(2, 150, 50), call(3, 500, 60))
        assertEquals(350L, computeIdleSeconds(calls))
    }

    @Test
    fun `back-to-back and overlapping calls contribute no negative idle`() {

        val calls = listOf(call(1, 0, 100), call(2, 80, 20), call(3, 100, 60))
        assertEquals(0L, computeIdleSeconds(calls))
    }

    @Test
    fun `overnight gap counts the whole stretch as idle`() {
        val hour = 3600L

        val prev = call(1, 18 * hour - 60, 60)
        val next = call(2, 33 * hour, 120)
        assertEquals(15 * hour, computeIdleSeconds(listOf(prev, next)))
    }
}
