package com.crmapplication.LeadDetailVM.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class LogCallRequest(
    val status: String,
    val duration: Long = 0,
    val contactNumber: String? = null,
    val timestamp: String? = null,
)

data class CallLogResponse(
    val message: String? = null,
    val error: String? = null,
)

interface CallsApi {
    @POST("api/calls")
    suspend fun logCall(
        @Header("Authorization") authorization: String?,
        @Body body: LogCallRequest,
    ): Response<CallLogResponse>
}
