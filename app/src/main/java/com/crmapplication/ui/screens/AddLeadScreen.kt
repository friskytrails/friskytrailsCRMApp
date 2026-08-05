package com.crmapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.crmapplication.LeadDetailVM.remote.CreateLeadRequest
import com.crmapplication.ui.theme.CrmOnSurfaceVar
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.ui.theme.CrmSecondary
import com.crmapplication.viewModel.LeadsViewModel

private val LEAD_SOURCES = listOf("Instagram", "FaceBook", "AdCampaign", "Referral", "Website", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLeadScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit = {},
    viewModel: LeadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var mail by remember { mutableStateOf("") }
    var leadSource by remember { mutableStateOf("") }
    var originCity by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }

    val canSubmit = fullName.isNotBlank() && phone.isNotBlank() && !state.isCreating

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.createSuccess) {
        if (state.createSuccess) {
            viewModel.clearCreateSuccess()
            onCreated()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(CrmPrimary, CrmSecondary)))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Text("←", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            "Add New Lead",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                        )
                        Text(
                            "Fill the details to add a client",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            SectionCard(title = "Contact Details", icon = Icons.Filled.Person) {
                LabeledField(
                    label = "Full Name",
                    value = fullName,
                    onValueChange = { fullName = it },
                    placeholder = "John Doe",
                    leadingIcon = Icons.Filled.Person,
                    required = true,
                )
                LabeledField(
                    label = "Phone Number",
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit) },
                    placeholder = "10 digit number",
                    leadingIcon = Icons.Filled.Call,
                    keyboardType = KeyboardType.Phone,
                    required = true,
                )
                LabeledField(
                    label = "Mail ID",
                    value = mail,
                    onValueChange = { mail = it },
                    placeholder = "abc@example.com",
                    leadingIcon = Icons.Filled.Email,
                    keyboardType = KeyboardType.Email,
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Trip Details", icon = Icons.Filled.Flight) {
                LabeledDropdown(
                    label = "Lead Source",
                    value = leadSource,
                    onValueChange = { leadSource = it },
                    options = LEAD_SOURCES,
                    placeholder = "Select a source...",
                    leadingIcon = Icons.Filled.Campaign,
                )
                LabeledField(
                    label = "Origin City",
                    value = originCity,
                    onValueChange = { originCity = it },
                    placeholder = "New Delhi",
                    leadingIcon = Icons.Filled.Place,
                )
                LabeledDropdown(
                    label = "Product",
                    value = product,
                    onValueChange = { product = it },
                    options = state.products,
                    placeholder = "Select a product...",
                    leadingIcon = Icons.Filled.Sell,
                    // Catalog is server-owned: re-fetch as the menu opens so a product added on the
                    // backend shows up without reopening the screen. Throttled in the repository.
                    onExpand = { viewModel.refreshConfig() },
                )
                LabeledField(
                    label = "Destination of Interest",
                    value = destination,
                    onValueChange = { destination = it },
                    placeholder = "Paris, France",
                    leadingIcon = Icons.Filled.Public,
                    imeAction = ImeAction.Done,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {

                    fun String.orNull() = trim().takeIf { it.isNotBlank() }
                    viewModel.createLead(
                        CreateLeadRequest(
                            name = fullName.trim(),
                            phone = phone.trim(),
                            origin = originCity.orNull(),
                            destination = destination.orNull(),
                            leadSource = leadSource.orNull(),
                            mailId = mail.orNull(),
                            product = product.orNull(),
                        )
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrmPrimary),
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Client Lead", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = CrmPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = CrmPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    required: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label, required)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = CrmOnSurfaceVar, fontSize = 14.sp) },
            leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = CrmPrimary, modifier = Modifier.size(20.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CrmPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    placeholder: String,
    leadingIcon: ImageVector,
    /** Fired when the menu opens, so a server-backed list can refresh just before it's read. */
    onExpand: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label, required = false)
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = it
                if (it) onExpand()
            },
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(placeholder, color = CrmOnSurfaceVar, fontSize = 14.sp) },
                leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = CrmPrimary, modifier = Modifier.size(20.dp)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrmPrimary,
                    unfocusedBorderColor = Color(0xFFD9D6F0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFBFAFF),
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
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

@Composable
private fun FieldLabel(label: String, required: Boolean) {
    Row {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        if (required) {
            Text(" *", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
