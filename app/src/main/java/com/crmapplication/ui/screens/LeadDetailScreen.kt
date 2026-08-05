package com.crmapplication.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.StatusChange
import com.crmapplication.LeadDetailVM.repository.isBooked
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallType
import com.crmapplication.calllog.callStats
import com.crmapplication.ui.component.AttachmentActionSheet
import com.crmapplication.ui.component.ImagePreviewDialog
import com.crmapplication.ui.component.NoteItem
import com.crmapplication.ui.component.StatusDropdown
import com.crmapplication.utils.downloadAttachment
import com.crmapplication.utils.downloadNeedsStoragePermission
import com.crmapplication.utils.formatBookingDateTime
import com.crmapplication.utils.formatCallTime
import com.crmapplication.utils.formatDuration
import com.crmapplication.utils.formatTimestamp
import com.crmapplication.utils.formatTravelDate
import com.crmapplication.utils.formatWhatsAppUrl
import com.crmapplication.utils.isPreviewableImage
import com.crmapplication.utils.openAttachmentExternally
import com.crmapplication.viewModel.LeadDetailViewModel
import com.crmapplication.viewModel.LeadsViewModel
import com.salescrm.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.*

/** How often to re-pull this lead while the screen is in front of the agent. */
private const val DETAIL_POLL_INTERVAL_MS = 25_000L

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
    var isResumed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isResumed = true
                    detailVm.refresh()
                    // Statuses are server-owned, and this screen's status dropdown is one of the
                    // things they drive — so refresh config here too, not just the lead.
                    leadsVm.refreshConfig()
                }
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // There's no push channel, so a note or status change made on the web dashboard would otherwise
    // stay invisible for as long as the agent sits on this screen. Poll while resumed; ON_RESUME
    // already covers the return-to-screen case, so wait out the first interval before asking.
    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (true) {
            delay(DETAIL_POLL_INTERVAL_MS)
            detailVm.refresh(showLoading = false)
            // Same reason as ON_RESUME: a status added on the backend should reach this screen's
            // dropdown while the agent is sitting on it. Throttled in the repository.
            leadsVm.refreshConfig()
        }
    }

    var noteText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    var showWhatsAppSheet by remember { mutableStateOf(false) }

    /** Which lead field's edit dialog is open, or null when none is. */
    var editingField by remember { mutableStateOf<LeadEditField?>(null) }

    // Attachment flow: tapping an attachment opens the action sheet; Preview on an image opens the
    // in-app viewer, Preview on a document hands off to another app, Download always saves.
    var attachmentSheetUrl by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var pendingDownloadUrl by remember { mutableStateOf<String?>(null) }

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
    // Booking lives on LeadsViewModel (it owns updateStatus and the product catalog), so its signals
    // need their own collectors here — detailState's error channel never sees them.
    LaunchedEffect(leadsState.error) {
        leadsState.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            leadsVm.clearError()
        }
    }
    LaunchedEffect(leadsState.bookingSuccess) {
        if (leadsState.bookingSuccess) {
            snackbarHostState.showSnackbar("Lead booked. Status is now locked.")
            leadsVm.clearBookingSuccess()
        }
    }
    val leadInfoSavedMessage = stringResource(R.string.lead_field_saved)
    LaunchedEffect(leadsState.leadInfoSaved) {
        if (leadsState.leadInfoSaved) {
            snackbarHostState.showSnackbar(leadInfoSavedMessage)
            leadsVm.clearLeadInfoSaved()
        }
    }

    // Resolved up front: these are read inside callbacks, where stringResource isn't available.
    val scope = rememberCoroutineScope()
    val downloadStartedTemplate = stringResource(R.string.attachment_download_started)
    val downloadFailedMessage = stringResource(R.string.attachment_download_failed)
    val noAppMessage = stringResource(R.string.attachment_no_app)
    val storagePermissionMessage = stringResource(R.string.attachment_storage_permission_needed)

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun performDownload(url: String) {
        val fileName = context.downloadAttachment(url)
        notify(
            if (fileName != null) downloadStartedTemplate.format(fileName)
            else downloadFailedMessage
        )
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val url = pendingDownloadUrl
        pendingDownloadUrl = null
        when {
            !granted -> notify(storagePermissionMessage)
            url != null -> performDownload(url)
        }
    }

    /**
     * Writing to public Downloads needs WRITE_EXTERNAL_STORAGE up to API 28; from 29 scoped storage
     * exempts DownloadManager, so nothing is requested on modern devices.
     */
    fun requestDownload(url: String) {
        val needsPermission = downloadNeedsStoragePermission() &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownloadUrl = url
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performDownload(url)
        }
    }

    fun openExternally(url: String) {
        if (!context.openAttachmentExternally(url)) notify(noAppMessage)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
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
                                val hasWhatsApp = context.isPackageInstalled(WHATSAPP_PKG)
                                val hasWhatsAppBusiness = context.isPackageInstalled(WHATSAPP_BUSINESS_PKG)
                                when {
                                    hasWhatsApp && hasWhatsAppBusiness -> showWhatsAppSheet = true
                                    hasWhatsApp -> context.launchWhatsApp(phone, WHATSAPP_PKG)
                                    hasWhatsAppBusiness -> context.launchWhatsApp(phone, WHATSAPP_BUSINESS_PKG)
                                    else -> context.launchWhatsApp(phone, null)
                                }
                            }
                        }) {
                            Text("💬", fontSize = 20.sp)
                        }
                    }
                )
                if (detailState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
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
                        EditableInfoRow(
                            label = stringResource(R.string.lead_field_name),
                            value = lead.name,
                            onEdit = { editingField = LeadEditField.NAME },
                        )

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
                                statuses = leadsState.statusOptions,
                                onStatusChange = { leadsVm.updateStatus(lead.id, it) },
                                enabled = !lead.isBooked(),
                            )
                        }

                        if (lead.isBooked()) {
                            Text(
                                "This lead is booked — the status is final and can't be changed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        EditableInfoRow(
                            label = stringResource(R.string.lead_field_travel_date),
                            value = formatTravelDate(lead.travelDate)
                                ?: stringResource(R.string.lead_field_not_set),
                            onEdit = { editingField = LeadEditField.TRAVEL_DATE },
                        )

                        EditableInfoRow(
                            label = stringResource(R.string.lead_field_persons),
                            value = lead.numberOfPersons?.toString()
                                ?: stringResource(R.string.lead_field_not_set),
                            onEdit = { editingField = LeadEditField.PERSONS },
                        )

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
                                enabled = noteText.isNotBlank() && !detailState.isSavingNote,
                            ) {
                                if (detailState.isSavingNote) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sending…")
                                } else {
                                    Text("Send Note")
                                }
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
                        onAttachmentClick = { url -> attachmentSheetUrl = url },
                    )
                }
            }
        }
    }

    leadsState.bookingFor?.let { bookingLead ->
        BookingDetailsDialog(
            lead = bookingLead,
            products = leadsState.products,
            isSubmitting = leadsState.isBooking,
            onSubmit = { leadsVm.submitBooking(it) },
            onDismiss = { leadsVm.cancelBooking() },
        )
    }

    // Each save sends only the field that changed, so a concurrent edit to another field (from the
    // web dashboard, say) survives. The dialog closes immediately: the local write has already
    // landed by the time the push resolves, and a failure surfaces on the error snackbar.
    editingField?.let { field ->
        lead?.let { editedLead ->
            LeadFieldEditDialog(
                field = field,
                currentName = editedLead.name,
                currentTravelDate = editedLead.travelDate,
                currentPersons = editedLead.numberOfPersons,
                onSaveName = { newName ->
                    editingField = null
                    leadsVm.updateLeadInfo(editedLead.id, name = newName)
                },
                onSaveTravelDate = { apiDate ->
                    editingField = null
                    leadsVm.updateLeadInfo(editedLead.id, travelDate = apiDate)
                },
                onSavePersons = { persons ->
                    editingField = null
                    leadsVm.updateLeadInfo(editedLead.id, numberOfPersons = persons)
                },
                onDismiss = { editingField = null },
            )
        }
    }

    attachmentSheetUrl?.let { url ->
        AttachmentActionSheet(
            url = url,
            onPreview = {
                attachmentSheetUrl = null
                // Images stay in-app; anything else has to go to an app that can render it.
                if (isPreviewableImage(url)) previewImageUrl = url else openExternally(url)
            },
            onDownload = {
                attachmentSheetUrl = null
                requestDownload(url)
            },
            onDismiss = { attachmentSheetUrl = null },
        )
    }

    previewImageUrl?.let { url ->
        ImagePreviewDialog(
            url = url,
            onOpenExternally = {
                previewImageUrl = null
                openExternally(url)
            },
            onDismiss = { previewImageUrl = null },
        )
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

    if (showWhatsAppSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showWhatsAppSheet = false },
            sheetState = sheetState,
        ) {
            val whatsAppIcon = remember { context.loadAppIcon(WHATSAPP_PKG) }
            val whatsAppBusinessIcon = remember { context.loadAppIcon(WHATSAPP_BUSINESS_PKG) }
            Text(
                "Send message via",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("WhatsApp") },
                leadingContent = { AppIcon(icon = whatsAppIcon, fallback = "💬") },
                modifier = Modifier.clickable {
                    showWhatsAppSheet = false
                    lead?.phone?.let { context.launchWhatsApp(it, WHATSAPP_PKG) }
                },
            )
            ListItem(
                headlineContent = { Text("WhatsApp Business") },
                leadingContent = { AppIcon(icon = whatsAppBusinessIcon, fallback = "💼") },
                modifier = Modifier.clickable {
                    showWhatsAppSheet = false
                    lead?.phone?.let { context.launchWhatsApp(it, WHATSAPP_BUSINESS_PKG) }
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, fallback: String) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    } else {
        Text(fallback, fontSize = 24.sp)
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

/**
 * An [InfoRow] with a trailing pencil that opens the field's edit dialog.
 *
 * The icon carries the whole affordance — the value text itself isn't tappable, so a long lead name
 * can still be selected and read without a stray tap opening a dialog.
 */
@Composable
private fun EditableInfoRow(label: String, value: String, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        // Default IconButton size (48dp) rather than a tighter one: the glyph is small to keep the
        // row dense, but the touch target has to stay at Android's 48dp accessibility minimum.
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.lead_field_edit, label),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
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

private const val LEAD_LINK_BASE = "https://friskytrails-crm.vercel.app/leads/"

private const val WHATSAPP_PKG = "com.whatsapp"
private const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"

private fun Context.isPackageInstalled(packageName: String): Boolean =
    try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

private fun Context.launchWhatsApp(phone: String, packageName: String?) {
    val url = formatWhatsAppUrl(phone)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (packageName != null) intent.setPackage(packageName)
    try {
        startActivity(intent)
    } catch (e: Exception) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun Context.loadAppIcon(packageName: String): ImageBitmap? = try {
    val drawable = packageManager.getApplicationIcon(packageName)
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bitmap.asImageBitmap()
} catch (e: Exception) {
    null
}
