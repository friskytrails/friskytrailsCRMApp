package com.crmapplication.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salescrm.R
import com.crmapplication.LeadDetailVM.repository.BookingForm
import com.crmapplication.LeadDetailVM.repository.BookingFormErrors
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.toIndianMobileDigits
import com.crmapplication.LeadDetailVM.repository.validate
import com.crmapplication.ui.theme.CrmOnSurfaceVar
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.utils.DocumentPartFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Add Booking form, shown when an agent moves a lead to `Booked`.
 *
 * Laid out in the three sections the booking spec defines — traveller, trip, billing — and submits to
 * `POST /api/bookings`. Submitting is the only route to `Booked` (see `LeadsViewModel.updateStatus`)
 * and the status locks afterwards, so every field is required and validation runs before anything is
 * sent.
 *
 * [products] supplies suggestions for the Package Name field from the server-owned catalog. The field
 * takes free text either way — see [PackageNameField] for why it isn't a closed dropdown — so an empty
 * catalog costs the agent a shortcut, not the ability to book.
 *
 * Field state is local to this Composable, matching `AddLeadScreen`; only "which lead / in flight /
 * done" lives in the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailsDialog(
    lead: Lead,
    products: List<String>,
    isSubmitting: Boolean,
    onSubmit: (BookingForm) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // Pre-filled from the lead the agent already has open. The phone is normalised to bare digits on
    // the way in: leads commonly store `+91…`, and the booking API rejects anything that isn't exactly
    // 10 digits, so passing it through untouched would open the form already invalid.
    var form by remember(lead.id) {
        mutableStateOf(
            BookingForm(
                fullName = lead.name,
                contactNumber = lead.phone.toIndianMobileDigits(),
                // Package name is deliberately NOT pre-filled from `lead.product`. That field records
                // what the lead enquired about, which is often not what they end up buying, and the
                // backend overwrites the lead's product from whatever is submitted here. Starting empty
                // makes the agent state the package actually sold instead of accepting a stale guess —
                // and it lets the field's hint show. The catalog is still one tap away as a suggestion.
                adults = lead.numberOfPersons?.takeIf { it > 0 }?.toString().orEmpty(),
                children = "0",
            )
        )
    }

    // Errors stay hidden until the first submit, so a form the agent hasn't filled in yet isn't
    // covered in red. After that they update live as fields are corrected.
    var submitAttempted by remember { mutableStateOf(false) }
    val errors: BookingFormErrors = remember(form, submitAttempted) {
        if (submitAttempted) form.validate() else BookingFormErrors()
    }

    var datePickerTarget by remember { mutableStateOf<BookingDateField?>(null) }

    val screenshotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            form = form.copy(
                screenshotUri = it.toString(),
                screenshotName = context.resolveDisplayName(it),
            )
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(
            // A tall form plus the keyboard needs the full width; the default platform width would
            // clip it.
            usePlatformDefaultWidth = false,
            // A dialog gets its own window, and that window ignores IME insets unless told not to fit
            // system windows. Without this the card keeps its full height behind the keyboard, so the
            // lower fields — transaction id, payment mode, screenshot — sit under it with no way to
            // scroll to them.
            decorFitsSystemWindows = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                // safeDrawing is system bars + cutout + IME, so the card shrinks as the keyboard
                // animates in and the scrolling body below brings the focused field into view.
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(20.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.booking_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.booking_close),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.booking_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = CrmOnSurfaceVar,
                )
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {

                    // ── Traveller Information ─────────────────────────────────────────────────
                    SectionHeader(stringResource(R.string.booking_section_traveller))

                    FieldPair {
                        BookingField(
                            label = stringResource(R.string.booking_field_full_name),
                            value = form.fullName,
                            onValueChange = { form = form.copy(fullName = it) },
                            error = errors.fullName,
                            modifier = Modifier.weight(1f),
                        )
                        BookingField(
                            label = stringResource(R.string.booking_field_email),
                            value = form.emailId,
                            onValueChange = { form = form.copy(emailId = it) },
                            error = errors.emailId,
                            keyboardType = KeyboardType.Email,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FieldPair {
                        BookingField(
                            label = stringResource(R.string.booking_field_phone),
                            value = form.contactNumber,
                            onValueChange = { form = form.copy(contactNumber = it) },
                            error = errors.contactNumber,
                            keyboardType = KeyboardType.Phone,
                            supporting = stringResource(R.string.booking_phone_hint),
                            modifier = Modifier.weight(1f),
                        )
                        BookingField(
                            label = stringResource(R.string.booking_field_emergency_phone),
                            value = form.emergencyContactNumber,
                            onValueChange = { form = form.copy(emergencyContactNumber = it) },
                            error = errors.emergencyContactNumber,
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FieldPair {
                        BookingField(
                            label = stringResource(R.string.booking_field_adults),
                            value = form.adults,
                            onValueChange = { entered ->
                                form = form.copy(adults = entered.filter(Char::isDigit))
                            },
                            error = errors.adults,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                        BookingField(
                            label = stringResource(R.string.booking_field_children),
                            value = form.children,
                            onValueChange = { entered ->
                                form = form.copy(children = entered.filter(Char::isDigit))
                            },
                            error = errors.children,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // ── Trip & Package Details ────────────────────────────────────────────────
                    SectionHeader(stringResource(R.string.booking_section_trip))

                    FieldPair {
                        PackageNameField(
                            value = form.packageName,
                            suggestions = products,
                            onValueChange = { form = form.copy(packageName = it) },
                            error = errors.packageName,
                            placeholder = stringResource(R.string.booking_package_hint),
                            modifier = Modifier.weight(1f),
                        )
                        BookingField(
                            label = stringResource(R.string.booking_field_destination),
                            value = form.location,
                            onValueChange = { form = form.copy(location = it) },
                            error = errors.location,
                            placeholder = stringResource(R.string.booking_location_hint),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FieldPair {
                        DateField(
                            label = stringResource(R.string.booking_field_start_date),
                            millis = form.startDateMillis,
                            error = errors.startDate,
                            onClick = { datePickerTarget = BookingDateField.START },
                            modifier = Modifier.weight(1f),
                        )
                        DateField(
                            label = stringResource(R.string.booking_field_end_date),
                            millis = form.endDateMillis,
                            error = errors.endDate,
                            onClick = { datePickerTarget = BookingDateField.END },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // ── Billing & Transaction Verification ───────────────────────────────────
                    SectionHeader(stringResource(R.string.booking_section_billing))


                    FieldPair {
                        BookingField(
                            label = stringResource(R.string.booking_field_total_amount),
                            value = form.totalAmount,
                            onValueChange = { entered ->
                                form = form.copy(
                                    totalAmount = entered.filter(Char::isDigit)
                                )
                            },
                            error = errors.totalAmount,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )

                        BookingField(
                            label = stringResource(R.string.booking_field_gst_amount),
                            value = form.gstAmount,
                            onValueChange = { entered ->
                                form = form.copy(
                                    gstAmount = entered.filter(Char::isDigit)
                                )
                            },
                            error = errors.gstAmount,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FieldPair {
                        BookingField(
                            label = stringResource(R.string.booking_field_paid_amount),
                            value = form.paidAmount,
                            onValueChange = { entered ->
                                form = form.copy(
                                    paidAmount = entered.filter(Char::isDigit)
                                )
                            },
                            error = errors.paidAmount,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Read-only, not merely auto-filled: the backend derives due from total minus paid
                    // on save, so a number typed here would be silently discarded.
                    ReadOnlyField(
                        label = stringResource(R.string.booking_field_due_amount),
                        value = form.impliedDueAmount?.toString().orEmpty(),
                        supporting = stringResource(R.string.booking_due_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FieldPair {
                        BookingField(
                            label = stringResource(R.string.booking_field_transaction_id),
                            value = form.transactionId,
                            // Not filtered as you type: a rejected character is worth an explanation,
                            // and silently swallowing keystrokes reads as a broken field.
                            onValueChange = { form = form.copy(transactionId = it.trim()) },
                            error = errors.transactionId,
                            imeAction = ImeAction.Done,
                            modifier = Modifier.weight(1f),
                        )
                        // Fixed, not merely pre-selected: every booking is paid into the HDFC account,
                        // so there is nothing here for the agent to choose. Shows the exact string sent
                        // to the API rather than a friendlier label, so the field can't disagree with
                        // what an admin sees against the payment in the backend.
                        ReadOnlyField(
                            label = stringResource(R.string.booking_field_payment_mode),
                            value = form.paymentMode.apiValue,
                            supporting = stringResource(R.string.booking_payment_mode_hint),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    ScreenshotField(
                        fileName = form.screenshotName,
                        error = errors.screenshot,
                        enabled = !isSubmitting,
                        onPick = { screenshotPicker.launch(SCREENSHOT_MIME_TYPES) },
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text(stringResource(R.string.booking_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            submitAttempted = true
                            // Validated here rather than by disabling the button: a dead button doesn't
                            // say which field is wrong, and the error text does.
                            if (form.validate().isValid) onSubmit(form)
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = CrmPrimary),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.booking_submitting))
                        } else {
                            Text(
                                stringResource(R.string.booking_submit),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }

    datePickerTarget?.let { target ->
        val initial = when (target) {
            BookingDateField.START -> form.startDateMillis
            // Default the end date to the start, so picking a trip's last day is a short scroll
            // rather than starting from today.
            BookingDateField.END -> form.endDateMillis ?: form.startDateMillis
        }
        BookingDatePicker(
            initialMillis = initial,
            onDismiss = { datePickerTarget = null },
            onPicked = { picked ->
                form = when (target) {
                    BookingDateField.START -> {
                        // Keep the range coherent: a start after the existing end clears the end
                        // rather than leaving an invalid pair for validation to reject.
                        val end = form.endDateMillis?.takeIf { it >= picked }
                        form.copy(startDateMillis = picked, endDateMillis = end)
                    }
                    BookingDateField.END -> form.copy(endDateMillis = picked)
                }
                datePickerTarget = null
            },
        )
    }
}

/** What the backend accepts for a transaction screenshot. Narrower than a note attachment. */
private val SCREENSHOT_MIME_TYPES = arrayOf("image/png", "image/jpeg", "application/pdf")

/** Which of the two date fields the picker is currently open for. */
private enum class BookingDateField { START, END }

/** One of the spec's three groupings. */
@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(bottom = 10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CrmPrimary,
        )
    }
}

/** The mockup's two-up layout. Kept as one place so every row shares the same gap. */
@Composable
private fun FieldPair(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun BookingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    supporting: String? = null,
    placeholder: String? = null,
) {
    Column(modifier) {
        BookingFieldLabel(label)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            placeholder = placeholder?.let {
                { Text(it, fontSize = 13.sp, color = CrmOnSurfaceVar) }
            },
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            supportingText = (error ?: supporting)?.let { message ->
                { Text(message, fontSize = 11.sp) }
            },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrmPrimary),
        )
    }
}

/** A value the agent can read but not set — it's derived, or the server owns it. */
@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
    supporting: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BookingFieldLabel(label, required = false)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            supportingText = supporting?.let { { Text(it, fontSize = 11.sp) } },
            colors = OutlinedTextFieldDefaults.colors(
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledSupportingTextColor = CrmOnSurfaceVar,
            ),
        )
    }
}

/**
 * Package Name — free text with suggestions, deliberately not a closed dropdown.
 *
 * The catalog from `GET /api/config` is a starting point rather than the whole world: agents close
 * deals on packages nobody added to it, and a fixed dropdown left them unable to record the booking
 * they had actually made. So whatever is typed here is accepted and saved. [suggestions] are a
 * shortcut for the common case — tapping one fills the field, and the agent can then edit it.
 *
 * Matching is by substring, so "lada" finds "Ladakh Package". A suggestion identical to what's
 * already typed is dropped: there's nothing left to pick once the field says it.
 *
 * An empty [suggestions] (catalog never synced) degrades to a plain text field — the menu simply
 * never has anything to show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageNameField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    error: String?,
    placeholder: String?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // Tracked separately from focus so picking a suggestion (or dismissing the list) closes it while
    // the field keeps focus and the keyboard stays up. Typing clears it, which reopens the list.
    var dismissed by remember { mutableStateOf(false) }

    val matches = remember(value, suggestions) {
        val typed = value.trim()
        if (typed.isEmpty()) suggestions
        else suggestions.filter {
            it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true)
        }
    }
    val expanded = focused && !dismissed && matches.isNotEmpty()

    Column(modifier) {
        BookingFieldLabel(stringResource(R.string.booking_field_package))
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            // Focus and typing drive the list, so there's nothing to toggle here. A no-op rather than
            // flipping `expanded`: tapping the field to place the cursor shouldn't close a list of
            // suggestions the agent is still reading.
            onExpandedChange = {},
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    dismissed = false
                    onValueChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .onFocusChanged { focusState ->
                        focused = focusState.isFocused
                        // Re-arm on the way out, so coming back to the field offers the list again
                        // instead of staying silently collapsed.
                        if (!focusState.isFocused) dismissed = false
                    },
                singleLine = true,
                isError = error != null,
                placeholder = placeholder?.let {
                    { Text(it, fontSize = 13.sp, color = CrmOnSurfaceVar) }
                },
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrmPrimary),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { dismissed = true }) {
                matches.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            dismissed = true
                        },
                    )
                }
            }
        }
    }
}

/**
 * The transaction proof. Shows the picked filename rather than a preview: the agent needs to confirm
 * they attached the right file, and a PDF has no thumbnail to show anyway.
 */
@Composable
private fun ScreenshotField(
    fileName: String?,
    error: String?,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        BookingFieldLabel(stringResource(R.string.booking_field_screenshot))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onPick,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        if (fileName == null) R.string.booking_screenshot_add
                        else R.string.booking_screenshot_replace
                    ),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                fileName ?: stringResource(
                    R.string.booking_screenshot_hint,
                    DocumentPartFactory.MAX_SCREENSHOT_SIZE_MB,
                ),
                fontSize = 11.sp,
                color = if (fileName != null) MaterialTheme.colorScheme.onSurface else CrmOnSurfaceVar,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Read-only field that opens the date picker on tap — the mockup's `dd-mm-yyyy` input. */
@Composable
private fun DateField(
    label: String,
    millis: Long?,
    error: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BookingFieldLabel(label)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = millis?.let(::formatDisplayDate).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            placeholder = { Text(stringResource(R.string.booking_date_hint), fontSize = 13.sp) },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, tint = CrmPrimary) },
            // `enabled = false` is what stops the keyboard appearing for a field the agent can only
            // fill from the picker. A disabled field doesn't consume touches, so the clickable on the
            // wrapper still receives them — that's what keeps it tappable.
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            singleLine = true,
            isError = error != null,
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            supportingText = error?.let { { Text(it, fontSize = 11.sp) } },
            colors = OutlinedTextFieldDefaults.colors(
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledPlaceholderColor = CrmOnSurfaceVar,
                disabledTrailingIconColor = CrmPrimary,
                errorBorderColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

@Composable
private fun BookingFieldLabel(label: String, required: Boolean = true) {
    Row {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (required) {
            Text(
                " *",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingDatePicker(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPicked(it.utcDateToLocalMidnight()) } },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.booking_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** The picked file's name for display. Falls back to the last path segment when no provider answers. */
private fun android.content.Context.resolveDisplayName(uri: Uri): String {
    val fromCursor = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    }.getOrNull()
    return fromCursor?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "screenshot"
}

/**
 * Re-reads the picker's UTC-midnight value as local midnight of the same calendar day.
 *
 * `DatePickerState.selectedDateMillis` is midnight **UTC**. Formatting that with a local formatter
 * shifts the date back a day for any negative-offset zone (23:00 the previous day in UTC-1), so the
 * agent would pick the 15th and send the 14th. Mirrors the same conversion in `LeadDetailScreen`.
 */
private fun Long.utcDateToLocalMidnight(): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = this@utcDateToLocalMidnight }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** `dd-MM-yyyy` for display only — the wire format is `yyyy-MM-dd` via `formatApiDate`. */
private fun formatDisplayDate(millis: Long): String =
    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(millis))
