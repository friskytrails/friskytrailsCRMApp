package com.crmapplication.utils

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.getSystemService

/**
 * The `Context` half of note attachments — downloading to public Downloads and handing a document
 * to whatever app can display it. The URL-shape rules these build on live in `Attachments.kt`.
 */

/**
 * Queues [url] for download into the device's public Downloads folder, notifying on completion.
 *
 * Returns the derived filename on success, or null if the download could not be queued — callers
 * surface that difference to the agent, because a silently dropped download is indistinguishable
 * from a slow one.
 */
fun Context.downloadAttachment(url: String): String? {
    val manager = getSystemService<DownloadManager>() ?: return null
    val fileName = attachmentFileName(url)

    return runCatching {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription(DOWNLOAD_DESCRIPTION)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // Public Downloads rather than app-private storage: the point of Download (as opposed
            // to Preview) is that the file outlives the app and is reachable from a file manager.
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            mimeTypeForUrl(url)?.let { setMimeType(it) }
        }
        manager.enqueue(request)
        fileName
    }.getOrNull()
}

private const val DOWNLOAD_DESCRIPTION = "FriskyTrails CRM attachment"

/**
 * True when [downloadAttachment] needs no runtime permission.
 *
 * `setDestinationInExternalPublicDir` writes outside the app sandbox, which required
 * `WRITE_EXTERNAL_STORAGE` up to API 28. From API 29 scoped storage exempts `DownloadManager`,
 * so nothing is asked for on modern devices. minSdk here is 26, so the legacy branch is live.
 */
fun downloadNeedsStoragePermission(): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

/**
 * Hands [url] to an app that can display it, MIME type included so a PDF reader can claim the
 * intent instead of every https link defaulting to a browser.
 *
 * Returns false when nothing on the device accepted it, so the caller can tell the agent rather
 * than letting the tap look ignored. (The previous code called `startActivity` unguarded, which
 * risked an `ActivityNotFoundException` crash on this exact path.)
 */
fun Context.openAttachmentExternally(url: String): Boolean {
    val uri = Uri.parse(url)
    val mime = mimeTypeForUrl(url)

    val typed = mime?.let {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, it)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val plain = Intent(Intent.ACTION_VIEW, uri)

    // Typed first so a viewer wins over the browser; plain second because a typed https intent
    // resolves to nothing on some devices.
    for (intent in listOfNotNull(typed, plain)) {
        val launched = runCatching { startActivity(intent); true }
            .recoverCatching { if (it is ActivityNotFoundException) false else throw it }
            .getOrDefault(false)
        if (launched) return true
    }
    return false
}
