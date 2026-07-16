package com.crmapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crmapplication.ui.theme.CrmOnSurfaceVar
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.viewModel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onNeedsVerification: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    ClearTransientAuthStateEffect(viewModel)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    LaunchedEffect(state.needsEmailVerification) {
        if (state.needsEmailVerification) {
            viewModel.clearNeedsEmailVerification()
            onNeedsVerification()
        }
    }

    fun submit() {
        focusManager.clearFocus()
        localError = when {
            !email.trim().isValidEmail() -> "Please enter a valid email"
            password.isBlank()           -> "Please enter your password"
            else                         -> null
        }
        if (localError == null) {
            viewModel.clearError()
            viewModel.login(email, password)
        }
    }

    AuthBackground {
        AuthHeader(subtitle = "Welcome back 👋")
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(24.dp)) {

                AuthTextField(
                    value = email,
                    onValueChange = { email = it; localError = null },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                )
                Spacer(Modifier.height(12.dp))

                AuthTextField(
                    value = password,
                    onValueChange = { password = it; localError = null },
                    label = "Password",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isPassword = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword },
                )

                AuthError(localError ?: state.error)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(
                        onClick = {
                            viewModel.clearError()
                            onForgotPassword()
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("Forgot password?", color = CrmPrimary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

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
                        Text("Sign In", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account?", color = CrmOnSurfaceVar, fontSize = 13.sp)
            TextButton(onClick = onNavigateToRegister) {
                Text("Sign Up", color = CrmPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
