package com.crmapplication

import com.crmapplication.utils.formatWhatsAppUrl
import com.crmapplication.utils.toWhatsAppNumber
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the phone normalisation behind the lead-detail WhatsApp action.
 *
 * wa.me only resolves a number in full international form (country code + national number, no
 * `+`, no leading zero). Every shape below reached WhatsApp as "not on WhatsApp" when the app
 * merely stripped non-digits, even though the same contact was reachable from the dialler — the
 * number was fine, the link was not.
 */
class WhatsAppNumberTest {

    private val expected = "919876543210"

    @Test
    fun `bare national number gets the country code`() {
        assertEquals(expected, toWhatsAppNumber("9876543210"))
        assertEquals(expected, toWhatsAppNumber("98765 43210"))
        assertEquals(expected, toWhatsAppNumber("98765-43210"))
    }

    @Test
    fun `trunk prefix is dropped`() {
        assertEquals(expected, toWhatsAppNumber("09876543210"))
        assertEquals(expected, toWhatsAppNumber("0 98765 43210"))
    }

    @Test
    fun `already international shapes are preserved`() {
        assertEquals(expected, toWhatsAppNumber("+919876543210"))
        assertEquals(expected, toWhatsAppNumber("+91 98765 43210"))
        assertEquals(expected, toWhatsAppNumber("+91-98765-43210"))
        assertEquals(expected, toWhatsAppNumber("919876543210"))
        assertEquals(expected, toWhatsAppNumber("(+91) 98765 43210"))
    }

    @Test
    fun `00 dial-out prefix is dropped`() {
        assertEquals(expected, toWhatsAppNumber("00919876543210"))
        assertEquals(expected, toWhatsAppNumber("00 91 98765 43210"))
    }

    @Test
    fun `country code typed twice is collapsed`() {
        assertEquals(expected, toWhatsAppNumber("91919876543210"))
        assertEquals(expected, toWhatsAppNumber("+91 91 98765 43210"))
    }

    /** Leads from other countries must not have 91 forced onto them. */
    @Test
    fun `foreign numbers keep their own country code`() {
        assertEquals("14155552671", toWhatsAppNumber("+1 415 555 2671"))
        assertEquals("14155552671", toWhatsAppNumber("14155552671"))
        assertEquals("442071838750", toWhatsAppNumber("+44 20 7183 8750"))
        assertEquals("442071838750", toWhatsAppNumber("0044 20 7183 8750"))
        assertEquals("6591234567", toWhatsAppNumber("+65 9123 4567"))
    }

    @Test
    fun `junk digits beyond E164 fall back to the subscriber number`() {
        assertEquals(expected, toWhatsAppNumber("91 91 91 98765 43210"))
    }

    @Test
    fun `blank and zero-only input yield no number`() {
        assertEquals("", toWhatsAppNumber(""))
        assertEquals("", toWhatsAppNumber("   "))
        assertEquals("", toWhatsAppNumber("n/a"))
        assertEquals("", toWhatsAppNumber("000"))
    }

    @Test
    fun `url wraps the normalised number`() {
        assertEquals("https://wa.me/$expected", formatWhatsAppUrl("98765 43210"))
    }
}
