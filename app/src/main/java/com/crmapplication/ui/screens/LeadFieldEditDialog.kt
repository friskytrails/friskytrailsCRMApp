package com.crmapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crmapplication.utils.formatApiDate
import com.crmapplication.utils.parseTravelDateMillis
import com.salescrm.R

/** Which of the agent-editable lead fields a dialog is currently editing. */
enum class LeadEditField { NAME, TRAVEL_DATE, PERSONS }

/**
 * Edit dialog for a single lead field.
 *
 * One field per dialog, matching the per-row pencil affordance: each save then sends only the field
 * that changed, so two agents editing different fields of the same lead don't overwrite each other.
 *
 * [onSave] receives the value in the shape the API expects — `yyyy-MM-dd` for the travel date, and
 * for a cleared value the sentinel the repository reads as "reset this" (`""` / `0`).
 */
@Composable
fun LeadFieldEditDialog(
    field: LeadEditField,
    currentName: String,
    currentTravelDate: String?,
    currentPersons: Int?,
    onSaveName: (String) -> Unit,
    onSaveTravelDate: (String) -> Unit,
    onSavePersons: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    when (field) {
        LeadEditField.NAME -> TextEditDialog(
            title = stringResource(R.string.lead_field_edit, stringResource(R.string.lead_field_name)),
            initialValue = currentName,
            keyboardType = KeyboardType.Text,
            // A lead must keep a display name, so blank is rejected rather than treated as a clear.
            validate = { it.isBlank() to R.string.lead_field_name_required },
            onSave = { onSaveName(it.trim()) },
            onDismiss = onDismiss,
        )

        LeadEditField.PERSONS -> TextEditDialog(
            title = stringResource(R.string.lead_field_edit, stringResource(R.string.lead_field_persons)),
            initialValue = currentPersons?.toString().orEmpty(),
            keyboardType = KeyboardType.Number,
            supportingText = R.string.lead_field_persons_hint,
            digitsOnly = true,
            // Empty is allowed and means "clear"; anything else has to be a sane party size.
            validate = { value ->
                val trimmed = value.trim()
                if (trimmed.isEmpty()) false to null
                else (trimmed.toIntOrNull()?.let { it !in 1..999 } ?: true) to
                    R.string.lead_field_persons_invalid
            },
            // 0 is the repository's "clear" sentinel for this field.
            onSave = { onSavePersons(it.trim().toIntOrNull() ?: 0) },
            onDismiss = onDismiss,
        )

        LeadEditField.TRAVEL_DATE -> TravelDatePickerDialog(
            currentTravelDate = currentTravelDate,
            onSave = onSaveTravelDate,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Single-field text dialog. [validate] returns whether the current value is invalid, plus the
 * message to show — checked on save rather than while typing, so the error doesn't flash up before
 * the agent has finished entering anything.
 */
@Composable
private fun TextEditDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType,
    validate: (String) -> Pair<Boolean, Int?>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    supportingText: Int? = null,
    digitsOnly: Boolean = false,
) {
    var value by remember { mutableStateOf(initialValue) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { entered ->
                    value = if (digitsOnly) entered.filter(Char::isDigit).take(3) else entered
                    errorRes = null
                },
                singleLine = true,
                isError = errorRes != null,
                supportingText = (errorRes ?: supportingText)?.let { res ->
                    { Text(stringResource(res)) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val (invalid, messageRes) = validate(value)
                if (invalid) errorRes = messageRes else onSave(value)
            }) { Text(stringResource(R.string.lead_field_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lead_field_cancel)) }
        },
    )
}

/**
 * Travel date picker. Sends `yyyy-MM-dd` via [formatApiDate], the format the API documents.
 *
 * Clear is offered only when a date is already set — it sends `""`, which is what resets the field
 * server-side (an omitted key would mean "leave unchanged").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelDatePickerDialog(
    currentTravelDate: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Opens on the current travel date when it's a parseable yyyy-MM-dd, otherwise on today.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = remember(currentTravelDate) {
            parseTravelDateMillis(currentTravelDate) ?: System.currentTimeMillis()
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onSave(formatApiDate(it)) } },
            ) { Text(stringResource(R.string.lead_field_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!currentTravelDate.isNullOrBlank()) {
                    TextButton(onClick = { onSave("") }) {
                        Text(stringResource(R.string.lead_field_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.lead_field_cancel))
                }
            }
        },
    ) {
        DatePicker(state = state)
    }
}
