package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.BOOKED_STATUS
import com.crmapplication.LeadDetailVM.repository.DEFAULT_LEAD_STATUSES
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.sanitizeConfigList
import com.crmapplication.viewModel.LeadFilter
import com.crmapplication.viewModel.LeadsUiState
import com.crmapplication.viewModel.shortStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the server-owned lead statuses from `GET /api/config` — the list that drives the filter
 * chips and every status dropdown.
 *
 * The point of these is that a status added on the backend reaches the whole app without a release,
 * and that the app degrades sanely when the config is stale, partial, or missing entries the app
 * depends on.
 */
class LeadStatusConfigTest {

    private fun lead(name: String, status: String) = Lead(
        id = name,
        name = name,
        phone = "9876543210",
        status = status,
        createdAt = 0L,
        dueDate = null,
    )

    // region defaults

    @Test
    fun `the offline fallback matches the documented server default, in order`() {
        assertEquals(
            listOf(
                "Fresh Leads",
                "Interested Leads",
                "Pre Prospect Leads",
                "Prospect Leads",
                "Booked",
                "Rejected Leads",
            ),
            DEFAULT_LEAD_STATUSES,
        )
    }

    /**
     * The booking form is only reachable by picking this status, so a fresh install has to offer it
     * before any sync lands.
     */
    @Test
    fun `the fallback includes the booked status`() {
        assertTrue(DEFAULT_LEAD_STATUSES.contains(BOOKED_STATUS))
    }

    @Test
    fun `the fallback survives sanitizing unchanged`() {
        assertEquals(DEFAULT_LEAD_STATUSES, sanitizeConfigList(DEFAULT_LEAD_STATUSES))
    }

    // endregion

    // region chip labels

    @Test
    fun `the trailing Leads is dropped for chip labels`() {
        assertEquals("Fresh", shortStatusLabel("Fresh Leads"))
        assertEquals("Pre Prospect", shortStatusLabel("Pre Prospect Leads"))
        assertEquals("Rejected", shortStatusLabel("Rejected Leads"))
    }

    @Test
    fun `names without the suffix are left alone`() {
        assertEquals("Booked", shortStatusLabel("Booked"))
        assertEquals("Follow Up", shortStatusLabel("Follow Up"))
        // Case-insensitive match on the suffix, since the admin types these by hand.
        assertEquals("Walk In", shortStatusLabel("Walk In LEADS"))
    }

    @Test
    fun `a bare Leads keeps its name rather than becoming empty`() {
        assertEquals("Leads", shortStatusLabel("Leads"))
        assertEquals("Leads", shortStatusLabel("  Leads  "))
    }

    // endregion

    // region filter derivation

    @Test
    fun `chips are All plus the server statuses in the server's order`() {
        val state = LeadsUiState(statuses = listOf("Fresh Leads", "Walk In", "Booked"))
        assertEquals(
            listOf("All", "Fresh", "Walk In", "Booked"),
            state.filters.map { it.label },
        )
        assertEquals(
            listOf(null, "Fresh Leads", "Walk In", "Booked"),
            state.filters.map { it.statusMatch },
        )
    }

    /** A backend-added status has to become a chip with no app change — the whole point of this. */
    @Test
    fun `a status added on the backend appears as a chip`() {
        val state = LeadsUiState(statuses = DEFAULT_LEAD_STATUSES + "Negotiating Leads")
        assertTrue(state.filters.any { it.statusMatch == "Negotiating Leads" })
        assertEquals("Negotiating", state.filters.last().label)
    }

    /**
     * Dropping the chip would leave [LeadsUiState.visibleLeads] still filtering by a status the
     * agent can no longer see or clear.
     */
    @Test
    fun `an active filter whose status left the config is kept as a chip`() {
        val removed = LeadFilter.forStatus("Retired Leads")
        val state = LeadsUiState(statuses = DEFAULT_LEAD_STATUSES, activeFilter = removed)
        assertEquals(removed, state.filters.last())
        assertEquals(DEFAULT_LEAD_STATUSES.size + 2, state.filters.size)
    }

    @Test
    fun `an active filter still in the config is not duplicated`() {
        val state = LeadsUiState(
            statuses = DEFAULT_LEAD_STATUSES,
            activeFilter = LeadFilter.forStatus("Prospect Leads"),
        )
        assertEquals(1, state.filters.count { it.statusMatch == "Prospect Leads" })
        assertEquals(DEFAULT_LEAD_STATUSES.size + 1, state.filters.size)
    }

    /** Server casing can drift; a re-selected chip shouldn't double up because of it. */
    @Test
    fun `staleness is decided case-insensitively`() {
        val state = LeadsUiState(
            statuses = listOf("prospect leads"),
            activeFilter = LeadFilter.forStatus("Prospect Leads"),
        )
        assertEquals(2, state.filters.size)
    }

    @Test
    fun `the All chip survives an empty status list`() {
        val state = LeadsUiState(statuses = emptyList())
        assertEquals(listOf(LeadFilter.All), state.filters)
    }

    // endregion

    // region dropdown options

    @Test
    fun `booked is always offered, since it is the only route to the booking form`() {
        val withoutBooked = listOf("Fresh Leads", "Interested Leads")
        val state = LeadsUiState(statuses = withoutBooked)
        assertEquals(withoutBooked + BOOKED_STATUS, state.statusOptions)
    }

    @Test
    fun `booked is not duplicated when the server already sends it`() {
        val state = LeadsUiState(statuses = DEFAULT_LEAD_STATUSES)
        assertEquals(DEFAULT_LEAD_STATUSES, state.statusOptions)
        assertEquals(1, state.statusOptions.count { it.equals(BOOKED_STATUS, ignoreCase = true) })
    }

    @Test
    fun `a differently-cased booked from the server is not doubled up`() {
        val state = LeadsUiState(statuses = listOf("Fresh Leads", "BOOKED"))
        assertEquals(listOf("Fresh Leads", "BOOKED"), state.statusOptions)
    }

    // endregion

    // region counts and visibility

    @Test
    fun `All counts open leads only, not closed ones`() {
        val state = LeadsUiState(
            leads = listOf(
                lead("a", "Fresh Leads"),
                lead("b", "Prospect Leads"),
                lead("c", BOOKED_STATUS),
                lead("d", "Rejected Leads"),
            ),
            statuses = DEFAULT_LEAD_STATUSES,
        )
        assertEquals(2, state.countFor(LeadFilter.All))
        assertEquals(1, state.countFor(LeadFilter.forStatus(BOOKED_STATUS)))
        assertEquals(1, state.countFor(LeadFilter.forStatus("Fresh Leads")))
        assertEquals(0, state.countFor(LeadFilter.forStatus("Walk In")))
    }

    @Test
    fun `closed leads stay out of All but return on their own chip`() {
        val leads = listOf(lead("open", "Fresh Leads"), lead("won", BOOKED_STATUS))
        val base = LeadsUiState(leads = leads, statuses = DEFAULT_LEAD_STATUSES)

        assertEquals(listOf("open"), base.visibleLeads.map { it.name })
        assertEquals(
            listOf("won"),
            base.copy(activeFilter = LeadFilter.forStatus(BOOKED_STATUS)).visibleLeads.map { it.name },
        )
    }

    @Test
    fun `a search finds a closed lead regardless of the active chip`() {
        val state = LeadsUiState(
            leads = listOf(lead("open", "Fresh Leads"), lead("won", BOOKED_STATUS)),
            statuses = DEFAULT_LEAD_STATUSES,
            searchQuery = "won",
        )
        assertEquals(listOf("won"), state.visibleLeads.map { it.name })
    }

    /**
     * Statuses are matched case-insensitively throughout: the app compares names it got from two
     * independent sources (the lead's own status and the config list), so exact casing can't be
     * assumed on either side.
     */
    @Test
    fun `filtering matches a lead's status case-insensitively`() {
        val state = LeadsUiState(
            leads = listOf(lead("a", "prospect leads")),
            statuses = DEFAULT_LEAD_STATUSES,
            activeFilter = LeadFilter.forStatus("Prospect Leads"),
        )
        assertEquals(listOf("a"), state.visibleLeads.map { it.name })
        assertEquals(1, state.countFor(LeadFilter.forStatus("Prospect Leads")))
    }

    // endregion
}
