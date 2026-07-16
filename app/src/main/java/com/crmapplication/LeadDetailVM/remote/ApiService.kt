package com.crmapplication.LeadDetailVM.remote

import retrofit2.http.*

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String, val agentName: String)

data class DashboardDto(
    val totalTalktime: String,
    val totalDials: Int,
    val newCalls: Int,
    val repeatedCalls: Int,
)

data class LeadDto(
    val id: String,
    val name: String,
    val phone: String,
    val createdAt: Long,
    val dueDate: Long?,
)

data class NoteDto(
    val id: String,
    val leadId: String,
    val text: String,
    val timestamp: Long,
)

data class AddNoteRequest(val leadId: String, val text: String)
data class SetDueDateRequest(val leadId: String, val dueDate: Long?)

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("dashboard")
    suspend fun getDashboard(@Header("Authorization") token: String): DashboardDto

    @GET("leads")
    suspend fun getLeads(@Header("Authorization") token: String): List<LeadDto>

    @GET("leads/{id}")
    suspend fun getLead(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): LeadDto

    @POST("leads/{id}/notes")
    suspend fun addNote(
        @Header("Authorization") token: String,
        @Path("id") leadId: String,
        @Body request: AddNoteRequest
    ): NoteDto

    @PATCH("leads/{id}/due-date")
    suspend fun setDueDate(
        @Header("Authorization") token: String,
        @Path("id") leadId: String,
        @Body request: SetDueDateRequest
    ): LeadDto
}
