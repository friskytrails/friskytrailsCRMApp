package com.crmapplication

import com.crmapplication.LeadDetailVM.remote.BugReportDto
import com.crmapplication.LeadDetailVM.remote.BugStatus
import com.crmapplication.LeadDetailVM.repository.toEntity
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards `GET api/bugs` → [BugReportDto] → `BugReportEntity`.
 *
 * The wire field names are the fragile part: they were guessed before the endpoint existed and two
 * were wrong. A rename here doesn't fail the build or throw — Gson leaves the field null and the
 * report just loses its reporter or status silently. So the first test parses the documented sample
 * verbatim rather than constructing a DTO by hand.
 */
class BugReportMappingTest {

    /** Sample response copied from the Bug Reports API doc, trimmed only in prose length. */
    private val documentedResponse = """
        {
          "id": "6a6f21472d9ddcb87a22e248",
          "title": "File preview not working in comments section",
          "description": "When uploading a PDF file in lead comments, the preview fails.",
          "reportedBy": "6a246ddd382949a328c9313b",
          "reporterName": "Admin User",
          "reporterEmail": "admin@friskytrails.com",
          "status": "Open",
          "createdAt": "2026-08-02T10:51:51.151Z",
          "updatedAt": "2026-08-02T10:51:51.151Z"
        }
    """.trimIndent()

    @Test
    fun `documented response maps to a synced entity with every field kept`() {
        val entity = Gson().fromJson(documentedResponse, BugReportDto::class.java).toEntity()

        assertEquals("6a6f21472d9ddcb87a22e248", entity.id)
        assertEquals("File preview not working in comments section", entity.title)
        // `reportedBy`, not `reporterId` — the whole point of this test.
        assertEquals("6a246ddd382949a328c9313b", entity.reporterId)
        assertEquals("Admin User", entity.reporterName)
        assertEquals(BugStatus.OPEN, entity.status)
        assertTrue("came from the server, so it is visible to everyone", entity.isSynced)

        // 2026-08-02T10:51:51.151Z — parsed, not defaulted to "now".
        assertEquals(1_785_667_911_151L, entity.createdAt)
    }

    /**
     * Server ids must not contain a hyphen. `BugReportDao.deleteServerReports` keys off
     * `id NOT LIKE '%-%'` to tell server rows from unsent local ones (which get UUIDs), so a
     * hyphenated fallback id would survive every sync and duplicate the report each time.
     */
    @Test
    fun `fallback id for a response missing both ids has no hyphen`() {
        val entity = BugReportDto(title = "No id at all").toEntity()

        assertFalse(
            "a hyphen would make replaceServerReports mistake this for an unsent local report",
            entity.id.contains('-'),
        )
    }

    @Test
    fun `a response missing fields degrades to placeholders rather than blanks`() {
        val entity = BugReportDto(id = "abc123", createdAt = "not-a-date").toEntity()

        assertEquals("(no title)", entity.title)
        assertEquals("Unknown agent", entity.reporterName)
        // No status from the server reads as untriaged, so the badge can't render empty.
        assertEquals(BugStatus.OPEN, entity.status)
        assertEquals("", entity.description)
    }

    /** Drives the badge colour only, but it decides whether an agent thinks their bug is fixed. */
    @Test
    fun `only closed and resolved count as settled`() {
        assertTrue(BugStatus.isSettled(BugStatus.CLOSED))
        assertTrue(BugStatus.isSettled(BugStatus.RESOLVED))
        assertTrue("backend casing shouldn't change the meaning", BugStatus.isSettled("closed"))

        assertFalse(BugStatus.isSettled(BugStatus.OPEN))
        assertFalse(BugStatus.isSettled(BugStatus.IN_PROGRESS))
        assertFalse(BugStatus.isSettled("Wishlist"))
    }
}
