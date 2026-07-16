package com.crmapplication.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.crmapplication.ui.screens.AddLeadScreen
import com.crmapplication.ui.screens.ApprovalWaitingScreen
import com.crmapplication.ui.screens.DashboardScreen
import com.crmapplication.ui.screens.ForgotPasswordScreen
import com.crmapplication.ui.screens.LeadDetailScreen
import com.crmapplication.ui.screens.LeadsListScreen
import com.crmapplication.ui.screens.LoginScreen
import com.crmapplication.ui.screens.OtpVerifyScreen
import com.crmapplication.ui.screens.ProfileScreen
import com.crmapplication.ui.screens.RegisterScreen
import com.crmapplication.ui.screens.ResetPasswordScreen
import com.crmapplication.viewModel.AuthViewModel

object Routes {
    const val REGISTER      = "register"
    const val VERIFY_OTP    = "verifyOtp"
    const val APPROVAL_WAIT = "approvalWaiting"
    const val LOGIN         = "login"
    const val FORGOT_EMAIL  = "forgotEmail"
    const val FORGOT_OTP    = "forgotOtp"
    const val FORGOT_RESET  = "forgotReset"
    const val DASHBOARD     = "dashboard"
    const val LEADS         = "leads"
    const val ADD_LEAD      = "addLead"
    const val PROFILE       = "profile"
    const val LEAD_DETAIL   = "lead/{leadId}"
    fun leadDetail(id: String) = "lead/$id"
}

@Composable
fun CrmNavGraph() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()

    val startDestination = if (authState.isLoggedIn) Routes.DASHBOARD else Routes.REGISTER

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegistered = { navController.navigate(Routes.VERIFY_OTP) },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
            )
        }

        composable(Routes.VERIFY_OTP) {
            OtpVerifyScreen(
                viewModel = authViewModel,
                forReset = false,
                onVerified = {

                    navController.navigate(Routes.APPROVAL_WAIT) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.APPROVAL_WAIT) {
            ApprovalWaitingScreen(
                viewModel = authViewModel,

                onApproved = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.APPROVAL_WAIT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onForgotPassword = { navController.navigate(Routes.FORGOT_EMAIL) },

                onNeedsVerification = { navController.navigate(Routes.VERIFY_OTP) },
            )
        }

        composable(Routes.FORGOT_EMAIL) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onOtpSent = { navController.navigate(Routes.FORGOT_OTP) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FORGOT_OTP) {
            OtpVerifyScreen(
                viewModel = authViewModel,
                forReset = true,
                onVerified = { navController.navigate(Routes.FORGOT_RESET) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FORGOT_RESET) {
            ResetPasswordScreen(
                viewModel = authViewModel,
                otp = authState.resetOtp,
                onReset = {

                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                agentName = authState.agentName,
                onNavigateToLeads = { navController.navigate(Routes.LEADS) },
                onNavigateToAddLead = { navController.navigate(Routes.ADD_LEAD) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.ADD_LEAD) {
            AddLeadScreen(
                onBack = { navController.popBackStack() },

                onCreated = { navController.popBackStack() },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                agentName = authState.agentName,
                agentEmail = authState.agentEmail,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },

                onProfileUpdated = { authViewModel.refreshAgentInfo() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.LEADS) {
            LeadsListScreen(
                onLeadClick = { lead ->

                    navController.navigate(Routes.leadDetail(lead.id))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.LEAD_DETAIL,
            arguments = listOf(navArgument("leadId") { type = NavType.StringType })
        ) { backStack ->
            val leadId = backStack.arguments?.getString("leadId") ?: return@composable
            LeadDetailScreen(
                leadId = leadId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
