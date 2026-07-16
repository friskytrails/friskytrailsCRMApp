package com.crmapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crmapplication.ui.theme.CrmOnBackground
import com.crmapplication.ui.theme.CrmOnSurfaceVar
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.viewModel.AuthViewModel
import kotlinx.coroutines.delay

private const val APPROVAL_POLL_INTERVAL_MS = 15_000L

@Composable
fun ApprovalWaitingScreen(
    viewModel: AuthViewModel,
    onApproved: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.approvalGranted) {
        if (!state.approvalGranted) {
            while (true) {
                viewModel.checkApproval()
                delay(APPROVAL_POLL_INTERVAL_MS)
            }
        }
    }

    LaunchedEffect(state.approvalGranted) {
        if (state.approvalGranted) {
            delay(1800)
            viewModel.clearAwaitingApproval()
            onApproved()
        }
    }

    AuthBackground {
        if (state.approvalGranted) {

            AuthHeader(subtitle = "You're all set")
            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your account is approved!",
                        color = CrmOnBackground,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Welcome aboard — signing you in…",
                        color = CrmOnSurfaceVar,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    CircularProgressIndicator(Modifier.size(28.dp), color = CrmPrimary, strokeWidth = 3.dp)
                }
            }
        } else {

            AuthHeader(subtitle = "Almost there")
            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⏳", fontSize = 44.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Waiting for admin approval",
                        color = CrmOnBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your email is verified. An admin needs to approve your account before you can sign in. This screen will let you in automatically the moment that happens.",
                        color = CrmOnSurfaceVar,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (state.pendingEmail.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.pendingEmail,
                            color = CrmPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(Modifier.size(30.dp), color = CrmPrimary, strokeWidth = 3.dp)

                    AuthError(state.approvalCheckError)
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                viewModel.clearApprovalCheckError()
                onBackToLogin()
            }) {
                Text("Back to login", color = CrmPrimary, fontSize = 13.sp)
            }
        }
    }
}
