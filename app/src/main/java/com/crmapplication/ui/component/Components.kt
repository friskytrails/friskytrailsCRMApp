package com.crmapplication.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.salescrm.R
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.Note
import com.crmapplication.LeadDetailVM.repository.isBooked
import com.crmapplication.utils.attachmentDisplayLabel
import com.crmapplication.utils.formatDate
import com.crmapplication.utils.formatTimestamp
import com.crmapplication.utils.getDueDateStatus
import com.crmapplication.utils.timeAgo

@Composable
fun StatCard(
    icon: String,
    label: String,
    value: String,
    accentHex: Long,
    modifier: Modifier = Modifier,
) {
    val accent = Color(accentHex)
    Card(
        modifier = modifier.border(width = 0.dp, color = Color.Transparent, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) { Spacer(Modifier.size(8.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

    }
}

private fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "Booked"             -> Color(0xFFDCFCE7) to Color(0xFF16A34A)
    "Prospect Leads"     -> Color(0xFFEDE9FE) to Color(0xFF7C3AED)
    "Pre Prospect Leads" -> Color(0xFFFEF3C7) to Color(0xFFD97706)
    "Interested Leads"   -> Color(0xFFCFFAFE) to Color(0xFF0891B2)
    "Rejected Leads"     -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
    else                  -> Color(0xFFDBEAFE) to Color(0xFF2563EB)
}

@Composable
fun LeadCard(
    lead: Lead,
    statuses: List<String>,
    onClick: () -> Unit,
    onStatusChange: (String) -> Unit = {},
) {
    val dueDateStatus = lead.dueDate?.let { getDueDateStatus(it) }

    val (badgeBg, badgeText, badgeLabel) = when (dueDateStatus) {
        "overdue"  -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "⚠️ Overdue")
        "today"    -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "🔔 Today")
        "upcoming" -> Triple(Color(0xFFDBEAFE), Color(0xFF2563EB), "🔔 ${formatDate(lead.dueDate!!)}")
        else       -> Triple(Color.Transparent, Color.Transparent, "")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        lead.name.first().toString(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    lead.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "📞 ${lead.totalDial} dials · ${lead.connected} connected" +
                        (lead.talkTime.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${lead.phone} · ${timeAgo(lead.createdAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                // Derived here rather than passed in, so every caller of LeadCard gets the lock
                // without having to remember to ask for it.
                StatusDropdown(
                    status = lead.status,
                    statuses = statuses,
                    onStatusChange = onStatusChange,
                    enabled = !lead.isBooked(),
                )

                if (dueDateStatus != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            badgeLabel,
                            color = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Status chip that opens the status list on tap.
 *
 * [enabled] false renders the chip as plain, un-tappable text — used for booked leads, whose status is
 * final in this app. The chip still shows (the agent needs to see the status); it just can't be
 * changed, and the ▾ affordance is dropped so it doesn't look tappable.
 */
@Composable
fun StatusDropdown(
    status: String,
    statuses: List<String>,
    onStatusChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val (chipBg, chipText) = statusColors(status)

    Box {
        Surface(
            color = chipBg,
            shape = RoundedCornerShape(8.dp),
            modifier = if (enabled) Modifier.clickable { expanded = true } else Modifier,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    status,
                    color = chipText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (enabled) {
                    Text(" ▾", color = chipText, fontSize = 12.sp)
                } else {
                    Text(" 🔒", fontSize = 10.sp)
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            statuses.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        if (option != status) onStatusChange(option)
                    },
                )
            }
        }
    }
}

@Composable
fun NoteItem(
    note: Note,
    myAgentId: String? = null,
    /** Opens the Preview/Download sheet for the note's attachment. */
    onAttachmentClick: (String) -> Unit = {},
) {

    val isMine = note.authorId != null && note.authorId == myAgentId ||
        (note.authorId == null && note.authorName != null)
    val container = if (isMine) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                Column(Modifier.weight(1f)) {
                    note.authorName?.let { author ->
                        Text(
                            author,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        note.timeLabel ?: formatTimestamp(note.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (note.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
            }

            note.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Spacer(Modifier.height(8.dp))
                NoteAttachment(
                    url = url,
                    label = attachmentDisplayLabel(url, note.text),
                    isDocument = note.isDocument,
                    onClick = { onAttachmentClick(url) },
                )
            }
        }
    }
}

/**
 * A note's attachment: an image thumbnail, or a document card when the URL isn't an image.
 *
 * Images route through [SubcomposeAsyncImage] specifically for its error slot. [Note.isDocument]
 * can only detect a document by extension or `/raw/upload/`, so a Cloudinary URL whose public id
 * carries no extension reaches here marked as an image and used to render as an empty 160dp box.
 * Falling back to the document card on decode failure means the agent still gets something
 * tappable instead of blank space.
 */
@Composable
private fun NoteAttachment(
    url: String,
    label: String,
    isDocument: Boolean,
    onClick: () -> Unit,
) {
    if (isDocument) {
        DocumentCard(label = label, onClick = onClick)
        return
    }

    // The load state drives the layout rather than a subcomposed error slot, so a failed image
    // collapses to the document card's own height instead of leaving dead space inside a 160dp box.
    val painter = rememberAsyncImagePainter(model = url)
    val state = painter.state

    if (state is AsyncImagePainter.State.Error) {
        DocumentCard(label = label, onClick = onClick)
        return
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = stringResource(R.string.attachment_image_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (state is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun DocumentCard(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📄", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
