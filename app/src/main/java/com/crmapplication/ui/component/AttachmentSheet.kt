package com.crmapplication.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.crmapplication.utils.attachmentFileName
import com.crmapplication.utils.isPreviewableImage
import com.salescrm.R

/**
 * Preview/Download choices for a note attachment.
 *
 * Preview means "in-app" for images and "hand to another app" for documents, so the subtitle spells
 * out which one the agent is about to get — the two behave differently enough that a bare "Preview"
 * would mislead. Download always saves to the public Downloads folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentActionSheet(
    url: String,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isImage = remember(url) { isPreviewableImage(url) }
    val fileName = remember(url) { attachmentFileName(url) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            fileName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text(stringResource(R.string.attachment_preview)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (isImage) R.string.attachment_preview_image_subtitle
                        else R.string.attachment_preview_document_subtitle
                    )
                )
            },
            leadingContent = { Text("⤢", fontSize = 20.sp) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPreview),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.attachment_download)) },
            supportingContent = { Text(stringResource(R.string.attachment_download_subtitle)) },
            leadingContent = { Text("⬇", fontSize = 20.sp) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDownload),
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Full-screen image preview with pinch-to-zoom and pan.
 *
 * Zoom exists because attachments are usually documents photographed by the customer — an ID, a
 * cheque, a handwritten passport number — and a 160dp thumbnail is unreadable. Double-tap style
 * reset is handled by clamping: pinching back below 1x snaps the pan back to centre.
 */
@Composable
fun ImagePreviewDialog(
    url: String,
    onOpenExternally: () -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
        ) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = stringResource(R.string.attachment_image_description),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.attachment_preview_failed),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onOpenExternally) {
                            Text(stringResource(R.string.attachment_open_externally))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            if (scale <= 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )

            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenExternally) {
                    Text(stringResource(R.string.attachment_open_externally), color = Color.White)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.attachment_close), color = Color.White)
                }
            }
        }
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
