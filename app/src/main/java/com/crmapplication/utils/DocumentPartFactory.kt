package com.crmapplication.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentPartFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class DocumentMeta(val fileName: String, val mimeType: String)

    fun build(uri: Uri): Pair<MultipartBody.Part, DocumentMeta>? {
        val resolver = context.contentResolver
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null

        val fileName = resolver.displayName(uri)
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"

        require(bytes.isNotEmpty()) { "The selected file is empty." }
        require(bytes.size <= MAX_FILE_SIZE_BYTES) {
            val mb = String.format(Locale.US, "%.1f", bytes.size / (1024.0 * 1024.0))
            "File is too large ($mb MB). The maximum is 5 MB."
        }
        require(isAllowedType(mimeType, fileName)) {
            "Unsupported file type. Allowed: JPG, PNG, GIF, WEBP, PDF, DOC, DOCX."
        }

        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        return part to DocumentMeta(fileName, mimeType)
    }

    private fun isAllowedType(mimeType: String, fileName: String): Boolean {
        if (mimeType.lowercase(Locale.US) in ALLOWED_MIME_TYPES) return true
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in ALLOWED_EXTENSIONS
    }

    private fun ContentResolver.displayName(uri: Uri): String {
        val fromCursor = runCatching {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        }.getOrNull()
        return fromCursor?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "document"
    }

    companion object {

        const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024

        private val ALLOWED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )

        private val ALLOWED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx",
        )
    }
}
