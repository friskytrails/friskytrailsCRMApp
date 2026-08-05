package com.crmapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crmapplication.LeadDetailVM.remote.BugStatus
import com.crmapplication.LeadDetailVM.repository.BugReport
import com.crmapplication.ui.theme.CrmOnBackground
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.ui.theme.CrmSecondary
import com.crmapplication.utils.formatTimestamp
import com.crmapplication.viewModel.BugReportsViewModel

@Composable
fun BugReportsScreen(
    onBack: () -> Unit,
    viewModel: BugReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // Hoisted out of any scoped content lambda on purpose — a LaunchedEffect inside one restarts
    // whenever that lambda recomposes, which would fire the snackbar more than once per submit.
    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) {
            viewModel.clearSubmitSuccess()
            title = ""
            description = ""
            focusManager.clearFocus()
            snackbarHostState.showSnackbar("Bug report filed")
        }
    }

    val canSubmit = title.isNotBlank() && description.isNotBlank() && !state.isSubmitting

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(CrmPrimary, CrmSecondary)))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                        Text(
                            "Bug Reports (${state.reports.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = CrmPrimary.copy(alpha = 0.3f),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Report a bug",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    AuthTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Title",
                        keyboardType = KeyboardType.Text,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("What went wrong?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrmPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.submit(title, description) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = canSubmit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrmPrimary),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Post Report", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                    AuthError(state.error)
                }
            }

            if (state.reports.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🐞", fontSize = 40.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No bug reports yet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = CrmOnBackground,
                        )
                        Text(
                            "Found something broken? Post it above.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(state.reports, key = { it.id }) { report ->
                        BugReportItem(report)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BugReportItem(report: BugReport) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    report.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                // Only meaningful for reports the server knows about — a local one has no triage
                // state yet, and showing "Open" for it would be inventing information.
                if (report.isSynced) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(report.status)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    report.reporterName,
                    color = CrmPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Text(
                    "  •  ${formatTimestamp(report.createdAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            if (report.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    report.description,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            }
            if (!report.isSynced) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⏳ Not yet visible to other agents",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Triage state as a pill, following the lead-status chips in `Components.kt`.
 *
 * Unrecognised values still render, in neutral colours: the backend owns this vocabulary and can add
 * to it, so an unfamiliar status should show as-is rather than be hidden or relabelled as something
 * it isn't.
 */
@Composable
private fun StatusBadge(status: String) {
    val (background, foreground) = when {
        BugStatus.isSettled(status) -> Color(0xFFDCFCE7) to Color(0xFF16A34A)
        status.equals(BugStatus.OPEN, ignoreCase = true) -> Color(0xFFFEF3C7) to Color(0xFFD97706)
        status.equals(BugStatus.IN_PROGRESS, ignoreCase = true) ->
            Color(0xFFDBEAFE) to Color(0xFF2563EB)
        else -> Color(0xFFF1F5F9) to Color(0xFF64748B)
    }
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(
            status,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
