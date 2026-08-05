package com.crmapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crmapplication.LeadDetailVM.repository.BookingForm
import com.crmapplication.LeadDetailVM.repository.BookingFormErrors
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.validate
import com.crmapplication.ui.theme.CrmOnSurfaceVar
import com.crmapplication.ui.theme.CrmPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Booking Details form, shown when an agent moves a lead to `Booked`.
 *
 * Submitting is the only route to `Booked` — see `LeadsViewModel.updateStatus` — and the status locks
 * afterwards, so every field is required and validation runs before anything is sent.
 *
 * [products] feeds the Package Name dropdown from the server-owned catalog. If it's empty (catalog
 * never synced), the field falls back to free text rather than trapping the agent behind an empty menu.
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
    // Pre-filled from the lead the agent already has open. This isn't only convenience: the backend
    // overwrites the lead's root `name`/`product` from `fullName`/`packageName`, so starting from the
    // current values means a submit can't silently rename the lead.
    var form by remember(lead.id) {
        mutableStateOf(
            BookingForm(
                fullName = lead.name,
                contactNumber = lead.phone,
                packageName = lead.product.orEmpty(),
            )
        )
    }

    // Errors stay hidden until the first submit, so a form the agent hasn't filled in yet isn't
    // covered in red. After that they update live as fields are corrected.
    var submitAttempted by remember { mutableStateOf(false) }
    val errors: BookingFormErrors = remember(form, submitAttempted) {
        if (submitAttempted) form.validate() else BookingFormErrors()
    }

    // Due = total - paid, until the agent types their own value (a discount breaks the arithmetic, and
    // their number should win from then on).
    var dueEdited by remember(lead.id) { mutableStateOf(false) }
    LaunchedEffect(form.totalAmount, form.paidAmount, dueEdited) {
        if (dueEdited) return@LaunchedEffect
        val implied = form.impliedDueAmount?.toString() ?: return@LaunchedEffect
        if (implied != form.dueAmount) form = form.copy(dueAmount = implied)
    }

    var datePickerTarget by remember { mutableStateOf<BookingDateField?>(null) }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        // A tall form plus the keyboard needs the full width; the default platform width would clip it.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(20.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Booking Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "All fields are required. Once booked, this lead's status can't be changed.",
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

                    // ── Customer ──────────────────────────────────────────────────────────────
                    FieldPair {
                        BookingField(
                            label = "Full Name",
                            value = form.fullName,
                            onValueChange = { form = form.copy(fullName = it) },
                            error = errors.fullName,
                            modifier = Modifier.weight(1f),
                        )
                        BookingField(
                            label = "Email ID",
                            value = form.emailId,
                            onValueChange = { form = form.copy(emailId = it) },
                            error = errors.emailId,
                            keyboardType = KeyboardType.Email,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FieldPair {
                        BookingField(
                            label = "Contact Number",
                            value = form.contactNumber,
                            onValueChange = { form = form.copy(contactNumber = it) },
                            error = errors.contactNumber,
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.weight(1f),
                        )
                        BookingField(
                            label = "Emergency Contact Number",
                            value = form.emergencyContactNumber,
                            onValueChange = { form = form.copy(emergencyContactNumber = it) },
                            error = errors.emergencyContactNumber,
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // ── Trip ──────────────────────────────────────────────────────────────────
                    FieldPair {
                        if (products.isEmpty()) {
                            BookingField(
                                label = "Package Name",
                                value = form.packageName,
                                onValueChange = { form = form.copy(packageName = it) },
                                error = errors.packageName,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            PackageDropdown(
                                value = form.packageName,
                                options = products,
                                onValueChange = { form = form.copy(packageName = it) },
                                error = errors.packageName,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        BookingField(
                            label = "No. of Pax",
                            value = form.noOfPax,
                            onValueChange = { entered ->
                                form = form.copy(noOfPax = entered.filter(Char::isDigit))
                            },
                            error = errors.noOfPax,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FieldPair {
                        DateField(
                            label = "Start Date",
                            millis = form.startDateMillis,
                            error = errors.startDate,
                            onClick = { datePickerTarget = BookingDateField.START },
                            modifier = Modifier.weight(1f),
                        )
                        DateField(
                            label = "End Date",
                            millis = form.endDateMillis,
                            error = errors.endDate,
                            onClick = { datePickerTarget = BookingDateField.END },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // ── Payment ───────────────────────────────────────────────────────────────
                    FieldPair {
                        BookingField(
                            label = "Total Amount (₹)",
                            value = form.totalAmount,
                            onValueChange = { entered ->
                                form = form.copy(totalAmount = entered.filter(Char::isDigit))
                            },
                            error = errors.totalAmount,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                        BookingField(
                            label = "Paid Amount (₹)",
                            value = form.paidAmount,
                            onValueChange = { entered ->
                                form = form.copy(paidAmount = entered.filter(Char::isDigit))
                            },
                            error = errors.paidAmount,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    BookingField(
                        label = "Due Amount (₹)",
                        value = form.dueAmount,
                        onValueChange = { entered ->
                            dueEdited = true
                            form = form.copy(dueAmount = entered.filter(Char::isDigit))
                        },
                        error = errors.dueAmount,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        supporting = if (!dueEdited) "Auto-filled from total minus paid" else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
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
                            Text("Booking…")
                        } else {
                            Text("Confirm Booking", fontWeight = FontWeight.SemiBold)
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

/** Which of the two date fields the picker is currently open for. */
private enum class BookingDateField { START, END }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageDropdown(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        BookingFieldLabel("Package Name")
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Select", fontSize = 13.sp, color = CrmOnSurfaceVar) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                supportingText = error?.let { { Text(it, fontSize = 11.sp) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrmPrimary),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
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
            placeholder = { Text("dd-mm-yyyy", fontSize = 13.sp) },
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
private fun BookingFieldLabel(label: String) {
    Row {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(" *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
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
