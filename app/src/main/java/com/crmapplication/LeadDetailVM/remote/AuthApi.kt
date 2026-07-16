package com.crmapplication.LeadDetailVM.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String = "agent",
    val inviteCode: String? = null,
)

data class AuthLoginRequest(
    val email: String,
    val password: String,
)

data class VerifyEmailRequest(
    val email: String,
    val otp: String,
)

data class ResendOtpRequest(
    val email: String,
)

data class ForgotPasswordRequest(
    val email: String,
)

data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val newPassword: String,
)

data class AuthUser(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val isAdmin: Boolean = false,
    val isVerified: Boolean = false,
    val status: String? = null,
)

data class AuthResponse(
    val token: String? = null,
    val user: AuthUser? = null,
)

data class RegisterResponse(
    val message: String? = null,
    val emailFailed: Boolean = false,
    val error: String? = null,
)

data class MeResponse(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val isAdmin: Boolean = false,
    val isVerified: Boolean = false,
)

data class UpdateProfileRequest(
    val name: String,
    val email: String,
)

data class UpdateProfileResponse(
    val message: String? = null,
    val user: AuthUser? = null,
    val error: String? = null,
)

data class StatusResponse(
    val message: String? = null,
    val error: String? = null,
)

interface AuthApi {

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: AuthLoginRequest): AuthResponse

    @POST("api/auth/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): StatusResponse

    @POST("api/auth/resend-otp")
    suspend fun resendOtp(@Body body: ResendOtpRequest): StatusResponse

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): StatusResponse

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): StatusResponse

    @GET("api/auth/me")
    suspend fun getProfile(@Header("Authorization") authorization: String?): MeResponse

    @PUT("api/auth/me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String?,
        @Body body: UpdateProfileRequest,
    ): UpdateProfileResponse
}
