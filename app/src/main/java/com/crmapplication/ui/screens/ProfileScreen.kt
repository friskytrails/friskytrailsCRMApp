package com.crmapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.ui.theme.CrmSecondary
import com.crmapplication.viewModel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    agentName: String,
    agentEmail: String,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onProfileUpdated: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val displayName = state.name.ifBlank { agentName }.ifBlank { "Agent" }
    val displayEmail = state.email.ifBlank { agentEmail }

    var name by remember { mutableStateOf(displayName) }
    var email by remember { mutableStateOf(displayEmail) }
    LaunchedEffect(state.name, state.email) {
        if (state.name.isNotBlank()) name = state.name
        if (state.email.isNotBlank()) email = state.email
    }

    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.profileUpdated) {
        if (state.profileUpdated) {
            viewModel.clearProfileUpdated()
            onProfileUpdated()
            snackbarHostState.showSnackbar("Profile updated")
        }
    }

    fun saveProfile() {
        focusManager.clearFocus()
        localError = when {
            name.isBlank()               -> "Please enter your name"
            !email.trim().isValidEmail() -> "Please enter a valid email"
            else                         -> null
        }
        if (localError == null) {
            viewModel.clearError()
            viewModel.updateProfile(name, email)
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
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        "Profile",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .size(110.dp)
                    .shadow(elevation = 10.dp, shape = CircleShape),
            ) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(CrmPrimary, CrmSecondary))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initialsOf(displayName),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 40.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                displayName,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (displayEmail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    displayEmail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            SectionCard(title = "Account details") {
                AuthTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = "Full Name",
                    keyboardType = KeyboardType.Text,
                )
                Spacer(Modifier.height(12.dp))
                AuthTextField(
                    value = email,
                    onValueChange = { email = it; localError = null },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { saveProfile() }),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { saveProfile() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrmPrimary),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save changes", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            AuthError(localError ?: state.error)

            Spacer(Modifier.height(16.dp))

            val darkEnabled = state.darkMode ?: isSystemInDarkTheme()
            SectionCard(title = "Appearance") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Dark mode",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            if (darkEnabled) "On" else "Off",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = darkEnabled,
                        onCheckedChange = { viewModel.setDarkMode(it) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626)),
            ) {
                Text("🚪", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
