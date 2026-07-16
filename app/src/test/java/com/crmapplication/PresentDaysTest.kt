package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.presentDayCount
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PresentDaysTest {

    private fun callOn(id: Long, year: Int, month0: Int, day: Int, hour: Int = 12): CallLogEntry {
        val millis = Calendar.getInstance().apply {
            set(year, month0, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return CallLogEntry(id, "9001622113", CallType.OUTGOING, millis, 60L)
    }

    @Test
    fun `no calls means zero present days`() {
        assertEquals(0, presentDayCount(emptyList()))
    }

    @Test
    fun `multiple calls on the same day count as one present day`() {
        val calls = listOf(
            callOn(1, 2026, Calendar.JUNE, 26, hour = 9),
            callOn(2, 2026, Calendar.JUNE, 26, hour = 14),
            callOn(3, 2026, Calendar.JUNE, 26, hour = 18),
        )
        assertEquals(1, presentDayCount(calls))
    }

    @Test
    fun `calls on different days count separately`() {
        val calls = listOf(
            callOn(1, 2026, Calendar.JUNE, 1),
            callOn(2, 2026, Calendar.JUNE, 2),
            callOn(3, 2026, Calendar.JUNE, 2),
            callOn(4, 2026, Calendar.JUNE, 15),
        )
        assertEquals(3, presentDayCount(calls))
    }
}
