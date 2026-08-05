package com.crmapplication

import com.crmapplication.utils.attachmentDisplayLabel
import com.crmapplication.utils.attachmentExtension
import com.crmapplication.utils.attachmentFileName
import com.crmapplication.utils.isPreviewableImage
import com.crmapplication.utils.mimeTypeForUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the URL derivation behind the note-attachment Preview/Download sheet.
 *
 * The shapes here are the ones Cloudinary actually serves: signed query params, versioned public
 * ids, and public ids with no extension at all. A wrong answer is user-visible — the wrong MIME
 * sends Preview to the browser instead of a viewer, and a bad filename makes `DownloadManager`
 * reject the enqueue outright.
 */
class AttachmentsTest {

    private val cloudinaryPdf =
        "https://res.cloudinary.com/demo/raw/upload/v1699999999/notes/invoice_2026.pdf"
    private val cloudinaryImage =
        "https://res.cloudinary.com/demo/image/upload/v1699999999/notes/passport.jpg"

    @Test
    fun `extension comes from the last path segment`() {
        assertEquals("pdf", attachmentExtension(cloudinaryPdf))
        assertEquals("jpg", attachmentExtension(cloudinaryImage))
        assertEquals("docx", attachmentExtension("https://x.test/a/b/contract.docx"))
    }

    /** Cloudinary appends `?_a=...` to signed delivery URLs; it must not become the extension. */
    @Test
    fun `query and fragment are ignored`() {
        assertEquals("pdf", attachmentExtension("$cloudinaryPdf?_a=BAVFB+DW0"))
        assertEquals("pdf", attachmentExtension("$cloudinaryPdf#page=2"))
        assertEquals("application/pdf", mimeTypeForUrl("$cloudinaryPdf?_a=BAVFB"))
        assertEquals("invoice_2026.pdf", attachmentFileName("$cloudinaryPdf?_a=BAVFB"))
    }

    @Test
    fun `extension-less urls yield no extension and no mime`() {
        val noExt = "https://res.cloudinary.com/demo/image/upload/v1699999999/notes/abc123"
        assertEquals("", attachmentExtension(noExt))
        assertNull(mimeTypeForUrl(noExt))
        assertFalse(isPreviewableImage(noExt))
        assertEquals("abc123", attachmentFileName(noExt))
    }

    /** A version segment is not an extension — "v1699999999" must not read as a 10-char suffix. */
    @Test
    fun `dotted path segments do not leak into the extension`() {
        assertEquals("", attachmentExtension("https://x.test/v1699999999/notes/report"))
        assertEquals("", attachmentExtension("https://sub.domain.test/file"))
    }

    @Test
    fun `mime types cover the uploadable set`() {
        assertEquals("application/pdf", mimeTypeForUrl(cloudinaryPdf))
        assertEquals("image/jpeg", mimeTypeForUrl(cloudinaryImage))
        assertEquals("application/msword", mimeTypeForUrl("https://x.test/a.doc"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            mimeTypeForUrl("https://x.test/a.docx"),
        )
        assertNull(mimeTypeForUrl("https://x.test/a.xyz"))
    }

    @Test
    fun `only coil-renderable types preview in-app`() {
        assertTrue(isPreviewableImage(cloudinaryImage))
        assertTrue(isPreviewableImage("https://x.test/a.PNG"))
        assertTrue(isPreviewableImage("https://x.test/a.webp"))
        assertFalse(isPreviewableImage(cloudinaryPdf))
        assertFalse(isPreviewableImage("https://x.test/a.docx"))
    }

    @Test
    fun `filenames are sanitised for the filesystem`() {
        assertEquals("my_file.pdf", attachmentFileName("https://x.test/my file.pdf"))
        assertEquals("a_b.pdf", attachmentFileName("https://x.test/a:b.pdf"))
    }

    @Test
    fun `unusable last segments fall back to a safe name`() {
        assertEquals("attachment", attachmentFileName("https://x.test/"))
        assertEquals("attachment", attachmentFileName("https://x.test"))
        assertEquals("attachment.pdf", attachmentFileName("https://x.test/.pdf"))
        assertEquals("attachment", attachmentFileName(""))
    }

    @Test
    fun `trailing slash falls back to the previous segment`() {
        assertEquals("invoice.pdf", attachmentFileName("https://x.test/notes/invoice.pdf/"))
    }

    @Test
    fun `label prefers the note text over the filename`() {
        assertEquals("Passport scan", attachmentDisplayLabel(cloudinaryImage, "Passport scan"))
        assertEquals("invoice_2026.pdf", attachmentDisplayLabel(cloudinaryPdf, "   "))
        assertEquals("invoice_2026.pdf", attachmentDisplayLabel(cloudinaryPdf, ""))
    }
}
