package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.bookingFromCalls
import com.crmapplication.LeadDetailVM.repository.callLabelFor
import com.crmapplication.LeadDetailVM.repository.datesFromCalls
import com.crmapplication.LeadDetailVM.repository.mergeLabels
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallType
import com.crmapplication.calllog.callStats
import com.crmapplication.calllog.normalizedPhoneKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogBookingTest {

    private val number = "9001622113"

    private val dialed = CallLogEntry(
        id = 1L,
        number = "+91 90016 22113",
        type = CallType.OUTGOING,
        dateMillis = 1_700_000_000_000L,
        durationSeconds = 120L,
    )
    private val incoming = CallLogEntry(
        id = 2L,
        number = "9001622113",
        type = CallType.INCOMING,
        dateMillis = 1_700_100_000_000L,
        durationSeconds = 45L,
    )
    private val calls = listOf(dialed, incoming)

    @Test
    fun `same number matches across dialer formats`() {
        val key = number.normalizedPhoneKey()
        assertEquals(key, dialed.number.normalizedPhoneKey())
        assertEquals(key, incoming.number.normalizedPhoneKey())
    }

    @Test
    fun `dialed and incoming are distinct directions with their own labels`() {
        assertEquals("Dialed", dialed.type.label)
        assertEquals("Incoming", incoming.type.label)
        assertTrue(dialed.type != incoming.type)
    }

    @Test
    fun `booking body counts today's dials, connections and talk time`() {
        // bookingFromCalls is today-scoped to match the Dashboard performance card, so build
        // two calls that both fall on now's day.
        val now = 1_700_100_000_000L
        val out = dialed.copy(id = 20L, dateMillis = now - 2_000L, durationSeconds = 120L)
        val inc = incoming.copy(id = 21L, dateMillis = now - 1_000L, durationSeconds = 45L)

        val body = bookingFromCalls(listOf(out, inc), now = now)
        assertNotNull(body)
        body!!
        assertEquals("one outgoing plus one answered inbound", 2, body.totalDial)
        assertEquals("today-scoped: totalDial equals dailyDial", body.totalDial, body.dailyDial)
        assertEquals("both today's calls connected (duration > 0)", 2, body.connected)
        assertEquals("120s + 45s = 165s → 2:45", "2:45", body.talkTime)
        assertEquals("today-scoped: talkTime equals dailyTalkTime", body.talkTime, body.dailyTalkTime)
    }

    /**
     * A dial is any call the agent actually handled, not just an outgoing one — so an answered
     * inbound call counts. Counting only [CallType.OUTGOING] was what made the app under-report
     * against the web historical report (observed: app 2 dials vs web 5 for the same day).
     */
    @Test
    fun `an answered inbound-only day still reports dials`() {
        val now = 1_700_100_000_000L
        val inc = incoming.copy(id = 30L, dateMillis = now - 1_000L, durationSeconds = 45L)

        val body = bookingFromCalls(listOf(inc), now = now)!!
        assertEquals("an inbound lead call is a dial", 1, body.totalDial)
    }

    /**
     * A missed call took no effort from the agent — the lead rang and nobody picked up — so counting
     * it would inflate the figure with calls the agent never handled.
     */
    @Test
    fun `missed calls do not count toward dials`() {
        val now = 1_700_100_000_000L
        val missed = dialed.copy(
            id = 31L,
            type = CallType.MISSED,
            dateMillis = now - 1_000L,
            durationSeconds = 0L,
        )

        val body = bookingFromCalls(listOf(missed), now = now)!!
        assertEquals("a missed call is not a dial", 0, body.totalDial)
        assertEquals("a missed call never connected", 0, body.connected)
    }

    /**
     * An unanswered inbound call is a missed call by another name: the provider sometimes files it as
     * INCOMING with a zero duration rather than MISSED, so the rule keys off the duration instead of
     * trusting the type alone.
     */
    @Test
    fun `an unanswered inbound call does not count`() {
        val now = 1_700_100_000_000L
        val unanswered = incoming.copy(id = 34L, dateMillis = now - 1_000L, durationSeconds = 0L)

        val body = bookingFromCalls(listOf(unanswered), now = now)!!
        assertEquals(0, body.totalDial)
    }

    /**
     * Outgoing is the asymmetric case: it counts even at zero duration, because the agent made the
     * attempt regardless of whether the lead picked up.
     */
    @Test
    fun `an unanswered outgoing call still counts as a dial`() {
        val now = 1_700_100_000_000L
        val unanswered = dialed.copy(id = 35L, dateMillis = now - 1_000L, durationSeconds = 0L)

        val body = bookingFromCalls(listOf(unanswered), now = now)!!
        assertEquals("the agent still tried", 1, body.totalDial)
        assertEquals("but nobody talked", 0, body.connected)
    }

    /**
     * Connected can no longer exceed dials. Under the outgoing-only rule an answered inbound call
     * produced connected=1 with dials=0 — a card reading "0 dials · 1 connected".
     */
    @Test
    fun `connected never exceeds dials`() {
        val now = 1_700_100_000_000L
        val out = dialed.copy(id = 32L, dateMillis = now - 3_000L, durationSeconds = 0L)
        val inc = incoming.copy(id = 33L, dateMillis = now - 1_000L, durationSeconds = 45L)

        val body = bookingFromCalls(listOf(out, inc), now = now)!!
        assertEquals(2, body.totalDial)
        assertEquals(1, body.connected)
        assertTrue("connected <= dials", body.connected <= body.totalDial)
    }

    /**
     * The lead-detail directional breakdown is deliberately unaffected: it shows Outgoing and
     * Incoming side by side, so its outgoing figure must stay direction-filtered or the two chips
     * would double-count the same call.
     */
    @Test
    fun `the directional breakdown still separates outgoing from incoming`() {
        val stats = callStats(calls)
        assertEquals("one outgoing", 1, stats.dialedCount)
        assertEquals("one incoming", 1, stats.incomingCount)
        assertEquals("two calls in total", 2, stats.totalCalls)
    }

    @Test
    fun `first and last call are today's oldest and newest, in that order`() {
        val now = 1_700_100_000_000L
        val older = dialed.copy(id = 20L, dateMillis = now - 5_000L)
        val newer = dialed.copy(id = 21L, dateMillis = now - 1_000L)

        val body = bookingFromCalls(listOf(older, newer), now = now)!!
        assertNotNull(body.firstCall)
        assertNotNull(body.lastCall)

        assertTrue(body.firstCall!! <= body.lastCall!!)
    }

    @Test
    fun `no calls means nothing to push`() {
        assertEquals(null, bookingFromCalls(emptyList()))
    }

    @Test
    fun `booking counts only calls on the day of now`() {

        val now = 1_700_100_000_000L
        val todayA = dialed.copy(id = 10L, dateMillis = now - 1_000L, durationSeconds = 60L)
        val todayB = dialed.copy(id = 11L, dateMillis = now - 2_000L, durationSeconds = 30L)
        val yesterday = dialed.copy(id = 12L, dateMillis = now - 26L * 60 * 60 * 1000, durationSeconds = 999L)

        val body = bookingFromCalls(listOf(todayA, todayB, yesterday), now = now)!!

        assertEquals("today-scoped: yesterday's call is excluded", 2, body.totalDial)
        assertEquals("only today's calls", 2, body.dailyDial)
        assertEquals("today's talk time 60s + 30s = 90s → 1:30", "1:30", body.dailyTalkTime)
        assertEquals("talkTime is today-scoped too", "1:30", body.talkTime)
    }

    @Test
    fun `nothing to push when no call falls on now's day`() {
        // No calls today -> return null so an idle day never overwrites stored booking with zeros.
        assertNull(bookingFromCalls(calls, now = 1_900_000_000_000L))
    }

    @Test
    fun `dates body maps first call to startDate and last call to dueDate`() {
        val body = datesFromCalls(calls)
        assertNotNull(body)
        body!!

        assertEquals(formatExpected(dialed.dateMillis), body.startDate)
        assertEquals(formatExpected(incoming.dateMillis), body.dueDate)
        assertTrue(body.startDate!! <= body.dueDate!!)
    }

    @Test
    fun `no calls means no dates to push`() {
        assertNull(datesFromCalls(emptyList()))
    }

    @Test
    fun `label is Connected when any call has duration`() {

        assertEquals("Connected", callLabelFor(calls))
    }

    @Test
    fun `label is Dialed when no call connected`() {
        val missed = dialed.copy(type = CallType.MISSED, durationSeconds = 0L)
        val outgoingNoAnswer = dialed.copy(durationSeconds = 0L)
        assertEquals("Dialed", callLabelFor(listOf(missed, outgoingNoAnswer)))
    }

    @Test
    fun `no calls means no label`() {
        assertNull(callLabelFor(emptyList()))
    }

    @Test
    fun `merging keeps other labels and replaces a stale outcome label`() {
        val existing = listOf("Hot Lead", "Dialed", "VIP")
        val merged = mergeLabels(existing, "Connected")

        assertEquals(listOf("Hot Lead", "VIP", "Connected"), merged)
    }

    @Test
    fun `merging a null label just strips outcome labels`() {
        val existing = listOf("VIP", "Connected")
        assertEquals(listOf("VIP"), mergeLabels(existing, null))
    }

    @Test
    fun `callStats splits counts and talk time by direction`() {

        val stats = callStats(calls)
        assertEquals(2, stats.totalCalls)
        assertEquals(1, stats.dialedCount)
        assertEquals(1, stats.incomingCount)
        assertEquals(0, stats.missedCount)
        assertEquals(120L, stats.outgoingDurationSeconds)
        assertEquals(45L, stats.incomingDurationSeconds)
        assertEquals(165L, stats.totalDurationSeconds)
    }

    @Test
    fun `callStats counts missed calls separately and ignores their duration`() {
        val missed = dialed.copy(id = 3L, type = CallType.MISSED, durationSeconds = 0L)
        val stats = callStats(listOf(dialed, incoming, missed))
        assertEquals(1, stats.missedCount)
        assertEquals(3, stats.totalCalls)

        assertEquals(165L, stats.totalDurationSeconds)
    }

    private fun formatExpected(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(epochMs))
}
