package com.crmapplication.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.viewModel.AuthViewModel

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onOtpSent: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    ClearTransientAuthStateEffect(viewModel)

    var email by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.forgotOtpSent) {
        if (state.forgotOtpSent) {
            viewModel.clearForgotOtpSent()
            onOtpSent()
        }
    }

    fun submit() {
        focusManager.clearFocus()
        if (!email.trim().isValidEmail()) {
            localError = "Please enter a valid email"
            return
        }
        localError = null
        viewModel.clearError()
        viewModel.requestPasswordReset(email)
    }

    AuthBackground {
        AuthHeader(subtitle = "Reset your password")
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(24.dp)) {

                Text(
                    "Enter your account email and we'll send you a code to reset your password.",
                    color = com.crmapplication.ui.theme.CrmOnSurfaceVar,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))

                AuthTextField(
                    value = email,
                    onValueChange = { email = it; localError = null },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )

                AuthError(localError ?: state.error)

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrmPrimary),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Send Code", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Back to Sign In", color = CrmPrimary, fontSize = 13.sp)
        }
    }
}
