package com.crmapplication.utils

/**
 * URL-shape reasoning for note attachments. Deliberately free of Android dependencies so the
 * derivation rules are unit-testable with plain JUnit (this repo has no Robolectric) — the
 * `Context` side of attachments lives in `AttachmentActions.kt`.
 */

/**
 * Extension → MIME, hand-rolled rather than delegating to `MimeTypeMap`. Only the types the
 * uploader accepts (see `allowedUploadMimeTypes` in `LeadDetailScreen`) plus the spreadsheet and
 * archive types a backend or web agent can attach, so the table stays small and testable.
 */
private val MIME_BY_EXTENSION = mapOf(
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "txt" to "text/plain",
    "csv" to "text/csv",
    "zip" to "application/zip",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "bmp" to "image/bmp",
    "heic" to "image/heic",
)

/** Extensions that render in-app via Coil, so Preview can stay inside the app. */
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")

/** Used when a URL carries no usable filename (Cloudinary public ids often don't). */
private const val FALLBACK_BASE_NAME = "attachment"

/** Characters Android/FAT32 won't accept in a filename. */
private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\s]+""")

/**
 * The URL's path only — scheme, host, query and fragment all removed. Every part matters:
 * Cloudinary signs delivery URLs with `?_a=...`, a `#` fragment would land inside the saved
 * filename, and dropping the authority is what stops a path-less URL (`https://x.test`) from
 * reading its host as the filename and the TLD as the extension.
 */
private fun urlPath(url: String): String {
    val bare = url.trim().substringBefore('#').substringBefore('?')
    val schemeEnd = bare.indexOf("://")
    val afterScheme = if (schemeEnd >= 0) bare.substring(schemeEnd + 3) else bare
    val pathStart = afterScheme.indexOf('/')
    return when {
        pathStart >= 0 -> afterScheme.substring(pathStart)
        // No path at all. With a scheme that means host-only (no filename to derive); without one
        // the whole value is a bare relative name, so keep it.
        schemeEnd >= 0 -> ""
        else -> "/$afterScheme"
    }
}

/** Lowercased file extension without the dot, or "" when the URL has none. */
fun attachmentExtension(url: String): String {
    val lastSegment = urlPath(url).trimEnd('/').substringAfterLast('/')
    if (!lastSegment.contains('.')) return ""
    return lastSegment.substringAfterLast('.').lowercase()
        .takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
        .orEmpty()
}

/** MIME type for the URL, or null when the extension is missing or unrecognised. */
fun mimeTypeForUrl(url: String): String? = MIME_BY_EXTENSION[attachmentExtension(url)]

/** True when the attachment can be previewed in-app by Coil rather than handed to another app. */
fun isPreviewableImage(url: String): Boolean = attachmentExtension(url) in IMAGE_EXTENSIONS

/**
 * Filename to save the download as. Falls back to [FALLBACK_BASE_NAME] when the URL's last
 * segment is unusable, so `DownloadManager` never gets an empty or path-bearing name.
 */
fun attachmentFileName(url: String): String {
    val lastSegment = urlPath(url).trimEnd('/').substringAfterLast('/')
    val cleaned = lastSegment.replace(ILLEGAL_FILENAME_CHARS, "_").trim('_', '.')
    if (cleaned.isEmpty()) return FALLBACK_BASE_NAME
    // A bare extension like ".pdf" cleans to "pdf" — a name, not a suffix. Give it a base.
    if (!cleaned.contains('.') && cleaned.lowercase() in MIME_BY_EXTENSION.keys) {
        return "$FALLBACK_BASE_NAME.${cleaned.lowercase()}"
    }
    return cleaned
}

/**
 * Label for an attachment in the UI: the note's own text when it has some (agents typically type
 * what they're sending), otherwise the derived filename.
 */
fun attachmentDisplayLabel(url: String, noteText: String): String =
    noteText.trim().takeIf { it.isNotEmpty() } ?: attachmentFileName(url)
