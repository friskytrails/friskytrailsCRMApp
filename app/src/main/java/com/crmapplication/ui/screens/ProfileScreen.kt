package com.crmapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.ui.theme.CrmSecondary
import com.crmapplication.viewModel.ProfileViewModel

@Composable
fun ProfileScreen(
    agentName: String,
    agentEmail: String,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onOpenBugReports: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val displayName = state.name.ifBlank { agentName }.ifBlank { "Agent" }
    val displayEmail = state.email.ifBlank { agentEmail }

    // Null means "follow the system", so resolve it for the icon and the toggle target.
    val darkEnabled = state.darkMode ?: isSystemInDarkTheme()

    Scaffold(
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
                            "Profile",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.setDarkMode(!darkEnabled) }) {
                            Icon(
                                if (darkEnabled) Icons.Default.LightMode else Icons.Default.DarkMode,
                                // Describes what the tap does, not the current state — that's what a
                                // screen reader user needs from a control.
                                contentDescription = if (darkEnabled) {
                                    "Switch to light mode"
                                } else {
                                    "Switch to dark mode"
                                },
                                tint = Color.White,
                            )
                        }
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

            // Read-only: an agent's name and email are set at registration and owned by the backend,
            // so there's nothing to edit here.
            SectionCard(title = "Account details") {
                ReadOnlyRow(label = "Full Name", value = displayName)
                Spacer(Modifier.height(12.dp))
                ReadOnlyRow(label = "Email", value = displayEmail.ifBlank { "—" })
            }

            AuthError(state.error)

            Spacer(Modifier.height(16.dp))

            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBugReports() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🐞", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Bug Reports",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            "Report a problem or see what others reported",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun ReadOnlyRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
