package com.crmapplication.LeadDetailVM.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

data class ApiLeadDto(
    val id: String? = null,
    @SerializedName("_id") val mongoId: String? = null,
    val leadId: Long? = null,
    val name: String? = null,
    val phone: String? = null,
    val labels: List<String>? = null,
    val status: String? = null,
    val product: String? = null,

    @SerializedName(value = "leadSource", alternate = ["source"]) val leadSource: String? = null,
    val booking: ApiBookingDto? = null,

    val notes: List<ApiNoteDto>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class ApiNoteDto(
    val id: String? = null,
    @SerializedName("_id") val mongoId: String? = null,
    val text: String? = null,
    val timestamp: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val imageUrl: String? = null,
)

data class AddLeadNoteRequest(
    val text: String,
    val imageUrl: String? = null,
)

data class ApiBookingDto(
    val totalDial: Int? = null,
    val dailyDial: Int? = null,
    val connected: Int? = null,
    val talkTime: String? = null,
    val dailyTalkTime: String? = null,
    val firstCall: String? = null,
    val lastCall: String? = null,
)

data class UpdateBookingRequest(
    val totalDial: Int,
    val dailyDial: Int,
    val connected: Int,
    val talkTime: String,
    val dailyTalkTime: String,
    val firstCall: String?,
    val lastCall: String?,
)

data class UpdateDatesRequest(
    val startDate: String?,
    val dueDate: String?,
)

data class UpdateLabelsRequest(
    val labels: List<String>,
)

data class UpdateStatusRequest(

    val status: String,
)

data class CreateLeadRequest(
    val name: String,
    val phone: String,
    val age: Int? = null,
    val origin: String? = null,
    val destination: String? = null,
    val leadSource: String? = null,
    val mailId: String? = null,
    val product: String? = null,
)

interface LeadsApi {
    @GET
    suspend fun getLeads(
        @Url endpoint: String,
        @Header("Authorization") authorization: String?,
    ): List<ApiLeadDto>

    @POST("api/leads")
    suspend fun createLead(
        @Header("Authorization") authorization: String?,
        @Body body: CreateLeadRequest,
    ): ApiLeadDto

    @GET("api/leads/{id}")
    suspend fun getLead(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
    ): ApiLeadDto

    @POST("api/leads/{id}/notes")
    suspend fun addNote(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: AddLeadNoteRequest,
    ): ApiLeadDto

    @DELETE("api/leads/{id}/notes/{noteId}")
    suspend fun deleteNote(
        @Path("id") id: String,
        @Path("noteId") noteId: String,
        @Header("Authorization") authorization: String?,
    ): ApiLeadDto

    @PUT("api/leads/{id}/booking")
    suspend fun updateBooking(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: UpdateBookingRequest,
    ): ApiLeadDto

    @PUT("api/leads/{id}/dates")
    suspend fun updateDates(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: UpdateDatesRequest,
    ): ApiLeadDto

    @PUT("api/leads/{id}/labels")
    suspend fun updateLabels(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: UpdateLabelsRequest,
    ): ApiLeadDto

    @PUT("api/leads/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: UpdateStatusRequest,
    ): ApiLeadDto
}
