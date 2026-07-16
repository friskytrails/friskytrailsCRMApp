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
fun OtpVerifyScreen(
    viewModel: AuthViewModel,
    forReset: Boolean = false,
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    ClearTransientAuthStateEffect(viewModel)

    var otp by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val verified = if (forReset) state.forgotVerified else state.verifySuccess
    LaunchedEffect(verified) {
        if (verified) {
            if (forReset) viewModel.clearForgotVerified() else viewModel.clearVerifySuccess()
            onVerified()
        }
    }

    fun submit() {
        focusManager.clearFocus()
        if (otp.trim().length < 4) {
            localError = "Enter the code from your email"
            return
        }
        localError = null
        viewModel.clearError()
        if (forReset) viewModel.verifyResetOtp(otp) else viewModel.verifyEmail(otp)
    }

    AuthBackground {
        AuthHeader(subtitle = "Enter the code we sent to\n${state.pendingEmail}")
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(24.dp)) {

                AuthTextField(
                    value = otp,
                    onValueChange = { new -> otp = new.filter { it.isDigit() }.take(6); localError = null },
                    label = "Verification Code",
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )

                AuthError(localError ?: state.error)

                if (state.otpResent) {
                    Spacer(Modifier.height(8.dp))
                    Text("A new code has been sent.", color = CrmPrimary, fontSize = 12.sp)
                }

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
                        Text("Verify", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        viewModel.clearOtpResent()
                        if (forReset) viewModel.resendResetOtp() else viewModel.resendOtp()
                    },
                    enabled = !state.isLoading,
                ) {
                    Text("Didn't get a code? Resend", color = CrmPrimary, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Back", color = CrmPrimary, fontSize = 13.sp)
        }
    }
}
