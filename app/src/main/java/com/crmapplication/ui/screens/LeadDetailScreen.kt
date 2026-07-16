package com.crmapplication.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.StatusChange
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallType
import com.crmapplication.calllog.callStats
import com.crmapplication.ui.component.NoteItem
import com.crmapplication.ui.component.StatusDropdown
import com.crmapplication.utils.formatBookingDateTime
import com.crmapplication.utils.formatCallTime
import com.crmapplication.utils.formatDuration
import com.crmapplication.utils.formatTimestamp
import com.crmapplication.utils.formatWhatsAppUrl
import com.crmapplication.viewModel.LeadDetailViewModel
import com.crmapplication.viewModel.LeadsViewModel

import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailScreen(
    leadId: String,
    onBack: () -> Unit,
    leadsVm: LeadsViewModel = hiltViewModel(),
    detailVm: LeadDetailViewModel = hiltViewModel(),
) {
    val leadsState by leadsVm.state.collectAsState()
    val detailState by detailVm.state.collectAsState()
    val context = LocalContext.current

    val lead = leadsState.leads.find { it.id == leadId }

    LaunchedEffect(lead) {
        lead?.let { detailVm.loadLead(it) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) detailVm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var noteText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        detailVm.onCallLogPermissionResult(granted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {  }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { detailVm.uploadDocument(it) }
    }
    val allowedUploadMimeTypes = remember {
        arrayOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(detailState.noteSaveSuccess) {
        if (detailState.noteSaveSuccess) {
            noteText = ""
            snackbarHostState.showSnackbar("Note saved!")
            detailVm.clearNoteSuccess()
        }
    }
    LaunchedEffect(detailState.uploadSuccess) {
        detailState.uploadSuccess?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            detailVm.clearUploadSuccess()
        }
    }
    LaunchedEffect(detailState.error) {
        detailState.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            detailVm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(lead?.name ?: "Lead Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {

                    IconButton(onClick = {
                        lead?.let { context.shareLead(it) }
                    }) {
                        Text("🔗", fontSize = 20.sp)
                    }

                    IconButton(onClick = {
                        lead?.phone?.let { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        }
                    }) {
                        Text("📞", fontSize = 20.sp)
                    }

                    IconButton(onClick = {
                        lead?.phone?.let { phone ->
                            val url = formatWhatsAppUrl(phone)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.setPackage("com.whatsapp")
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {

                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    }) {
                        Text("💬", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (lead == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lead Information", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                        InfoRow("Name", lead.name)

                        lead.product?.let { product ->
                            InfoRow("Product", product)
                        }

                        lead.source?.let { source ->
                            InfoRow("Source", source)
                        }
                        PhoneRow(phone = lead.phone, onClick = { detailVm.openCallHistory(lead.phone) })

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Status",
                                modifier = Modifier.width(100.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            StatusDropdown(
                                status = lead.status,
                                onStatusChange = { leadsVm.updateStatus(lead.id, it) },
                            )
                        }

                        if (detailState.hasCallLogMatch) {

                            InfoRow("Total Dials", lead.totalDial.toString())
                            InfoRow("Connected", lead.connected.toString())
                            InfoRow("Talk Time", lead.talkTime.ifBlank { "—" })
                            InfoRow("First Call", formatBookingDateTime(lead.firstCall) ?: "—")
                            InfoRow("Last Call", formatBookingDateTime(lead.lastCall) ?: "—")
                        } else {
                            Text(
                                "Tap the number to see call details from this phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📅 Reminder / Due Date", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))

                        val dueDateStr = detailState.lead?.dueDate?.let { formatTimestamp(it) }
                        if (dueDateStr != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Set: $dueDateStr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { detailVm.setDueDate(null) }) { Text("Clear") }
                            }
                        } else {
                            Text("No reminder set", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (dueDateStr != null) "Change Date & Time" else "Set Reminder Date & Time")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val history = detailState.statusHistory
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🕓 Status History",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.weight(1f))

                            Text(
                                "${history.size} ${if (history.size == 1) "change" else "changes"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (history.isEmpty()) {
                            Text(
                                "No status changes yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            history.forEachIndexed { index, change ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                StatusHistoryRow(change)
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("💬 Add Note", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Type your note here...") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 4,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {

                            OutlinedButton(
                                onClick = { if (!detailState.isUploading) documentPickerLauncher.launch(allowedUploadMimeTypes) },
                                enabled = !detailState.isUploading,
                            ) {
                                if (detailState.isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("📎", fontSize = 16.sp)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (detailState.isUploading) "Uploading…" else "Upload Doc")
                            }

                            Button(
                                onClick = { detailVm.addNote(noteText) },
                                modifier = Modifier.weight(1f),
                                enabled = noteText.isNotBlank(),
                            ) {
                                Text("Send Note")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "── Previous Notes ──",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (detailState.notes.isEmpty()) {
                item {
                    Text(
                        "No notes yet. Add your first note above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(detailState.notes, key = { it.id }) { note ->
                    NoteItem(
                        note = note,
                        myAgentId = detailState.myAgentId,
                        onOpenAttachment = { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = detailState.lead?.dueDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        pendingDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val existing = remember {
            Calendar.getInstance().apply {
                timeInMillis = detailState.lead?.dueDate ?: System.currentTimeMillis()
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = existing.get(Calendar.HOUR_OF_DAY),
            initialMinute = existing.get(Calendar.MINUTE),
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val day = pendingDateMillis
                    if (day != null) {

                        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day }
                        val due = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utc.get(Calendar.YEAR))
                            set(Calendar.MONTH, utc.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        detailVm.setDueDate(due.timeInMillis)

                        ensureNotificationPermission()
                    }
                    showTimePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            title = { Text("Reminder time") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
        )
    }

    val callHistory = detailState.callHistory
    if (callHistory.isOpen) {
        CallHistoryDialog(
            number = callHistory.number.orEmpty(),
            isLoading = callHistory.isLoading,
            needsPermission = callHistory.needsPermission,
            calls = callHistory.calls,
            onGrantPermission = { callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
            onDismiss = { detailVm.closeCallHistory() },
        )
    }
}

@Composable
private fun PhoneRow(phone: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Phone",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            phone,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(6.dp))
        Text("📞", fontSize = 14.sp)
    }
}

@Composable
private fun CallHistoryDialog(
    number: String,
    isLoading: Boolean,
    needsPermission: Boolean,
    calls: List<CallLogEntry>,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (needsPermission) {
                TextButton(onClick = onGrantPermission) { Text("Grant access") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (needsPermission) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else null,
        title = { Text("Call history") },
        text = {
            Column {
                Text(
                    number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    needsPermission -> {
                        Text(
                            "Allow call-log access to see when this number was called, " +
                                "the call type and how long each call lasted.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    calls.isEmpty() -> {
                        Text(
                            "This number hasn't been dialed or received on this phone yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        val stats = callStats(calls)

                        Text(
                            "${calls.size} call${if (calls.size > 1) "s" else ""} · " +
                                formatDuration(stats.totalDurationSeconds) + " total",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DirectionSummary(
                                icon = "📤",
                                label = "Outgoing",
                                count = stats.dialedCount,
                                duration = stats.outgoingDurationSeconds,
                                color = OutgoingColor,
                            )
                            DirectionSummary(
                                icon = "📥",
                                label = "Incoming",
                                count = stats.incomingCount,
                                duration = stats.incomingDurationSeconds,
                                color = IncomingColor,
                            )
                            if (stats.missedCount > 0) {
                                DirectionSummary(
                                    icon = "📵",
                                    label = "Missed",
                                    count = stats.missedCount,
                                    duration = 0L,
                                    color = MissedColor,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(calls, key = { it.id }) { call ->
                                CallRow(call)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DirectionSummary(
    icon: String,
    label: String,
    count: Int,
    duration: Long,
    color: Color,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                "$count $label",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        if (duration > 0) {
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallRow(call: CallLogEntry) {
    val directionColor = when (call.type) {
        CallType.OUTGOING -> OutgoingColor
        CallType.INCOMING -> IncomingColor
        CallType.MISSED, CallType.REJECTED, CallType.BLOCKED -> MissedColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(call.type.icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                call.type.label,
                fontWeight = FontWeight.Medium,
                color = directionColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                formatCallTime(call.dateMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (call.durationSeconds > 0) formatDuration(call.durationSeconds) else "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (call.durationSeconds > 0) directionColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val OutgoingColor = Color(0xFF2E7D32)
private val IncomingColor = Color(0xFF1565C0)
private val MissedColor = Color(0xFFC62828)

private fun statusChipColors(status: String): Pair<Color, Color> = when (status) {
    "Booked"             -> Color(0xFFDCFCE7) to Color(0xFF16A34A)
    "Prospect Leads"     -> Color(0xFFEDE9FE) to Color(0xFF7C3AED)
    "Pre Prospect Leads" -> Color(0xFFFEF3C7) to Color(0xFFD97706)
    "Interested Leads"   -> Color(0xFFCFFAFE) to Color(0xFF0891B2)
    "Rejected Leads"     -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
    else                  -> Color(0xFFDBEAFE) to Color(0xFF2563EB)
}

@Composable
private fun StatusDisplayCard(
    status: String,
    statusChangedAt: Long?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "🏷️ Status",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            val (bg, fg) = statusChipColors(status)
            Surface(
                color = bg,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            ) {
                Text(
                    status,
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (statusChangedAt != null) "Changed: ${formatTimestamp(statusChangedAt)}"
                else "Not changed yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusHistoryRow(change: StatusChange) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                change.previousStatus?.let { "$it → ${change.newStatus}" } ?: change.newStatus,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "by ${change.changedBy} · ${formatTimestamp(change.changedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.shareLead(lead: Lead) {
    val link = "$LEAD_LINK_BASE${lead.id}"
    val text = buildString {
        append("Frisky Trails CRM — Lead\n\n")
        append("Name: ${lead.name}\n")
        append("Phone: ${lead.phone}\n")
        if (lead.status.isNotBlank()) append("Status: ${lead.status}\n")
        append("\nView lead: $link")
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Frisky Trails Lead: ${lead.name}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(sendIntent, "Share lead via"))
}

private const val LEAD_LINK_BASE = "https://friskytrails.com/leads/"
