package com.crmapplication.LeadDetailVM.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    /**
     * When the party travels. A free-form string in the backend schema (`""` by default) — usually
     * `yyyy-MM-dd`, but the dashboard can write a formatted date, so it is stored and echoed as
     * given rather than normalised. See `formatTravelDate` for display.
     */
    val travelDate: String? = null,

    /**
     * Party size. `noOfPax` is the backend's legacy alias for the same value, so both spellings
     * parse — a lead written by an older dashboard build would otherwise read as null.
     */
    @SerializedName(value = "numberOfPersons", alternate = ["noOfPax"])
    val numberOfPersons: Int? = null,

    val booking: ApiBookingDto? = null,
    val dates: ApiDatesDto? = null,

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

/**
 * The lead's `dates` block. [reminderDate] is a full ISO-8601 UTC instant (date **and** time) set via
 * [LeadsApi.updateReminder]; [startDate]/[dueDate] are date-only and are written by the call-log
 * sync through [LeadsApi.updateDates]. That split is why the reminder has its own endpoint — the
 * two can't share a field.
 */
data class ApiDatesDto(
    val startDate: String? = null,
    val dueDate: String? = null,
    val reminderDate: String? = null,
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

/**
 * Body for [LeadsApi.bookLead] — the booking details the agent fills in when moving a lead to
 * `Booked`. Wrapped in a `bookingDetails` object because that's the shape the endpoint expects.
 */
data class BookLeadRequest(
    val bookingDetails: BookingDetailsDto,
)

/**
 * The booking payload. Every field is sent (no nulls) — the app makes all of them mandatory, so
 * there's nothing for Gson to omit and nothing for the backend to fill in from its defaults.
 *
 * `tripId` and `tripIndex` are **deliberately absent**: omitting both is what makes the backend
 * append a new trip with a generated `TRIP-XXXXXX` id. Adding `tripIndex` here would turn every
 * booking into an overwrite of an existing trip.
 */
data class BookingDetailsDto(
    val fullName: String,
    val emailId: String,
    val contactNumber: String,
    val emergencyContactNumber: String,
    val packageName: String,
    val noOfPax: Int,
    /** `yyyy-MM-dd` — see `formatApiDate`. */
    val startDate: String,
    /** `yyyy-MM-dd` — see `formatApiDate`. */
    val endDate: String,
    val totalAmount: Long,
    val paidAmount: Long,
    val dueAmount: Long,
)

/**
 * Body for [LeadsApi.updateReminder]: `{"reminderDate": "<iso>"}`, or an explicit
 * `{"reminderDate": null}` to clear.
 *
 * Returns a pre-serialized [RequestBody] rather than a data class because **the clear case depends
 * on the null key surviving**, and on the shared Retrofit it wouldn't:
 * - `GsonConverterFactory.create()` uses a default Gson with `serializeNulls = false`, so a
 *   `data class UpdateReminderRequest(null)` — and even a `JsonObject` holding `JsonNull` —
 *   serializes to `{}` (verified in `UpdateReminderBodyTest`). The backend reads a missing key as
 *   "leave unchanged", so Clear would silently do nothing.
 * - Enabling `serializeNulls` on that Retrofit isn't an option: `CreateLeadRequest` and
 *   `AddLeadNoteRequest` both rely on nulls being omitted.
 *
 * So this serializes with its own null-preserving Gson and hands Retrofit a `RequestBody`, which
 * passes through untouched.
 */
fun updateReminderBody(isoInstant: String?): RequestBody {
    val json = NULL_PRESERVING_GSON.toJson(
        JsonObject().apply {
            add("reminderDate", isoInstant?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        }
    )
    return json.toRequestBody(JSON_MEDIA_TYPE)
}

private val NULL_PRESERVING_GSON: Gson = GsonBuilder().serializeNulls().create()
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

data class UpdateLabelsRequest(
    val labels: List<String>,
)

data class UpdateStatusRequest(

    val status: String,
)

/**
 * Body for [LeadsApi.updateLeadInfo] — a partial update of the agent-editable lead fields.
 *
 * Every field is nullable and the shared Retrofit's Gson has `serializeNulls = false`, so a null
 * field is **omitted from the JSON** and the backend leaves it unchanged. That's what lets one
 * endpoint serve three independent edits: changing the party size sends only `numberOfPersons`, so
 * it can't clobber a name someone edited on the dashboard in the meantime.
 *
 * Clearing a travel date therefore sends `""` rather than null — an omitted key would mean "leave
 * it alone", and `""` is the schema's own default for the field.
 */
data class UpdateLeadInfoRequest(
    val name: String? = null,
    val travelDate: String? = null,
    val numberOfPersons: Int? = null,
) {
    /** True when there is nothing to send, so the caller can skip the request entirely. */
    val isEmpty: Boolean
        get() = name == null && travelDate == null && numberOfPersons == null
}

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

    /**
     * Records the agent's booking form and moves the lead to `Booked` server-side.
     *
     * **Not the same endpoint as [updateBooking].** That one is `api/leads/{id}/booking` and pushes
     * call-log metrics (dials, talk time); this is `api/leads/{id}/book` and pushes the customer's
     * trip details. The names are one letter apart — check which one you mean.
     *
     * Side effects worth knowing: the backend overwrites the lead's root `name` and `product` from
     * `fullName`/`packageName`, and a lead that wasn't already booked increments the agent's monthly
     * booking count.
     */
    @PUT("api/leads/{id}/book")
    suspend fun bookLead(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: BookLeadRequest,
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

    /**
     * Sets, updates, or (with a null `reminderDate`) clears the lead's reminder date **and time**.
     * Build [body] with [updateReminderBody] — see there for why it isn't a data class.
     */
    @PUT("api/leads/{id}/reminder")
    suspend fun updateReminder(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: RequestBody,
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

    /**
     * Partial update of the lead's own fields — name, travel date, party size.
     *
     * Note the path: the API doc writes this as `PUT /leads/:id`, but that is relative to the
     * deployment's API root, which is `api/` here (the doc's own JS sends `${API_URL}/leads/:id`).
     * Every other endpoint in this interface is prefixed the same way.
     */
    @PUT("api/leads/{id}")
    suspend fun updateLeadInfo(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: UpdateLeadInfoRequest,
    ): ApiLeadDto
}
