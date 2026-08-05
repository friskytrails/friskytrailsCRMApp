package com.crmapplication

import com.crmapplication.LeadDetailVM.repository.DEFAULT_PRODUCTS
import com.crmapplication.LeadDetailVM.repository.sanitizeConfigList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the product catalog served by `GET /api/config`, which drives the Add Lead dropdown.
 */
class ProductCatalogTest {

    @Test
    fun `server ordering is preserved, not sorted`() {
        // The admin controls the order in the config document, so alphabetising it would override
        // a deliberate choice (e.g. best-sellers first).
        val fromServer = listOf("Spiti Package", "Adventure Activities", "Kerala Trip")
        assertEquals(fromServer, sanitizeConfigList(fromServer))
    }

    @Test
    fun `blank and whitespace-only entries are dropped and names trimmed`() {
        assertEquals(
            listOf("Ladakh Package", "Others"),
            sanitizeConfigList(listOf("  Ladakh Package ", "", "   ", "Others")),
        )
    }

    @Test
    fun `duplicates are removed case-insensitively, keeping the first spelling`() {
        // The backend rejects duplicates on write, but an older document may still hold them.
        assertEquals(
            listOf("Kerala Trip", "Others"),
            sanitizeConfigList(listOf("Kerala Trip", "KERALA TRIP", "kerala trip", "Others")),
        )
    }

    @Test
    fun `a missing or empty products field yields an empty list`() {
        // The repository treats empty as "nothing to apply" and keeps the previous cache, so the
        // dropdown never blanks out.
        assertTrue(sanitizeConfigList(null).isEmpty())
        assertTrue(sanitizeConfigList(emptyList()).isEmpty())
    }

    @Test
    fun `the offline fallback matches the documented server default`() {
        assertEquals(
            listOf(
                "Meghalaya Package",
                "Hampta Pass Trek",
                "Rishikesh Activities",
                "Spiti Package",
                "Ladakh Package",
                "Kerala Trip",
                "Adventure Activities",
                "Others",
            ),
            DEFAULT_PRODUCTS,
        )
    }

    @Test
    fun `the fallback itself survives sanitizing unchanged`() {
        assertEquals(DEFAULT_PRODUCTS, sanitizeConfigList(DEFAULT_PRODUCTS))
    }
}
