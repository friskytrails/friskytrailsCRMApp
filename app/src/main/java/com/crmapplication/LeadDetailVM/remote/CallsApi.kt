package com.crmapplication.LeadDetailVM.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class LogCallRequest(
    // One of Connected | Missed | Failed | Voicemail — the backend's enum. Anything else is rejected.
    val status: String,
    // Client-generated, stable-per-call id. The backend writes it with $setOnInsert, so a retried
    // POST can't duplicate the row — but it also can't update it. One write per call, ever.
    val clientCallId: String,
    // MongoDB ObjectId of the associated lead. Must be 24-char hex or the backend throws a
    // CastError; send null instead of a non-ObjectId id (see isValidObjectId at the call site).
    val leadId: String? = null,
    val duration: Long = 0,
    val contactNumber: String? = null,
    val timestamp: String? = null,
)

/** The created CallLog object returned by POST /api/calls (201). */
data class CallLogResponse(
    @SerializedName("_id") val id: String? = null,
    val agentId: String? = null,
    val clientCallId: String? = null,
    val leadId: String? = null,
    val duration: Long? = null,
    val status: String? = null,
    val contactNumber: String? = null,
    val timestamp: String? = null,
    // Populated only on error responses ({"error": "..."}).
    val error: String? = null,
)

/** One agent's aggregated performance row from GET /api/calls/historical. */
data class HistoricalReportDto(
    val agentId: String? = null,
    val name: String? = null,
    val tenure: Int? = null,
    val talkTime: Long? = null,
    val totalDials: Int? = null,
    val uniqueCalls: Int? = null,
    val connectedCalls: Int? = null,
    val longCalls: Int? = null,
)

/** One agent's live idle metrics from GET /api/calls/live-status. */
data class LiveStatusDto(
    val agentId: String? = null,
    val name: String? = null,
    val lastCallAt: String? = null,
    val idleMs: Long? = null,
)

/** One agent's daily first/last call bounds and talk time from GET /api/calls/live-activity. */
data class LiveActivityDto(
    val agentId: String? = null,
    val name: String? = null,
    val firstCall: String? = null,
    val lastCall: String? = null,
    // Today's total talk time. The API docs name the field but not its unit or type; nullable Long
    // means a mismatch surfaces as null rather than a Gson crash.
    val talkTime: Long? = null,
)

/**
 * One call row from GET /api/calls/long-calls.
 *
 * The API docs describe this endpoint's filters but not its response shape, so every field is
 * nullable and unverified against a real payload — Gson leaves unknown fields alone and absent ones
 * null, so a mismatch degrades to nulls instead of throwing.
 */
data class LongCallDto(
    @SerializedName("_id") val id: String? = null,
    val agentId: String? = null,
    val name: String? = null,
    val leadId: String? = null,
    val contactNumber: String? = null,
    val status: String? = null,
    val duration: Long? = null,
    val timestamp: String? = null,
)

interface CallsApi {
    @POST("api/calls")
    suspend fun logCall(
        @Header("Authorization") authorization: String?,
        @Body body: LogCallRequest,
    ): Response<CallLogResponse>

    @GET("api/calls/historical")
    suspend fun getHistorical(
        @Header("Authorization") authorization: String?,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("team") team: String? = null,
    ): List<HistoricalReportDto>

    @GET("api/calls/live-status")
    suspend fun getLiveStatus(
        @Header("Authorization") authorization: String?,
    ): List<LiveStatusDto>

    @GET("api/calls/live-activity")
    suspend fun getLiveActivity(
        @Header("Authorization") authorization: String?,
    ): List<LiveActivityDto>

    /**
     * Per-call breakdown behind the aggregates. [metric] is "connected" for every connected call or
     * "longCalls" (the backend default) for calls of 300s or more. [agentId] is admin-only — an
     * agent's token scopes the result to themselves regardless — and accepts "all".
     */
    @GET("api/calls/long-calls")
    suspend fun getLongCalls(
        @Header("Authorization") authorization: String?,
        @Query("agentId") agentId: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("metric") metric: String? = null,
    ): List<LongCallDto>
}
