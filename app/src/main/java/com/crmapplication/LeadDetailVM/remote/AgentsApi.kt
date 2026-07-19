package com.crmapplication.LeadDetailVM.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class AgentMetricsDto(
    val monthlyTarget: Int? = null,
    val targetCompleted: Int? = null,
    val attendance: String? = null,
)

data class UpdateMetricsRequest(
    val attendance: String? = null,
    val attendanceDate: String? = null,
    val monthlyTarget: Int? = null,
    val targetCompleted: Int? = null,
)

/** One admin-set attendance log for a single day. status = "P" (Present) / "A" (Absent). */
data class AttendanceLogDto(
    val date: String? = null,
    val status: String? = null,
)

/** Server-side monthly attendance summary for an agent (authoritative P/A counts). */
data class MonthlyAttendanceDto(
    val present: Int? = null,
    val absent: Int? = null,
)

interface AgentsApi {
    @GET("api/agents/{id}/metrics")
    suspend fun getMetrics(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
    ): AgentMetricsDto

    @PUT("api/agents/{id}/metrics")
    suspend fun updateMetrics(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: UpdateMetricsRequest,
    ): AgentMetricsDto

    @GET("api/agents/{id}/attendance")
    suspend fun getAttendance(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
    ): List<AttendanceLogDto>

    @GET("api/agents/{id}/attendance/monthly")
    suspend fun getMonthlyAttendance(
        @Path("id") id: String,
        @Query("month") month: Int,
        @Query("year") year: Int,
        @Header("Authorization") authorization: String?,
    ): MonthlyAttendanceDto
}
