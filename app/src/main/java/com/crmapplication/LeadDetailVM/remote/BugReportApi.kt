package com.crmapplication.LeadDetailVM.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Bug reports, shared across every agent, manager, and admin.
 *
 * Base path is **`api/bugs`** (not `api/bug-reports`, which this interface guessed at before the
 * endpoint existed). All three calls need `Authorization: Bearer <token>`; the reporter's identity is
 * resolved server-side from that token, which is why [CreateBugReportRequest] carries only the text.
 */
interface BugReportApi {

    /** Every agent's reports, newest first. */
    @GET("api/bugs")
    suspend fun getBugReports(
        @Header("Authorization") authorization: String?,
    ): List<BugReportDto>

    /** Files a report. Responds `201` with the created report. */
    @POST("api/bugs")
    suspend fun createBugReport(
        @Header("Authorization") authorization: String?,
        @Body body: CreateBugReportRequest,
    ): BugReportDto

    /**
     * Moves a report between [BugStatus] values. Not called from the app yet — there is no
     * agent-facing control for it, since any agent could otherwise close another agent's report.
     */
    @PUT("api/bugs/{id}/status")
    suspend fun updateBugStatus(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
        @Body body: UpdateBugStatusRequest,
    ): BugReportDto
}

data class BugReportDto(
    val id: String? = null,
    /** Kept as a fallback: the documented responses use `id`, other endpoints here use `_id`. */
    @SerializedName("_id") val mongoId: String? = null,
    val title: String? = null,
    val description: String? = null,
    /** Id of the filing agent. Named `reportedBy` on the wire, unlike the `authorId` notes use. */
    val reportedBy: String? = null,
    val reporterName: String? = null,
    val reporterEmail: String? = null,
    /** One of [BugStatus]; unknown values are shown as-is rather than dropped. */
    val status: String? = null,
    /** ISO-8601, e.g. "2026-08-02T10:51:51.151Z". */
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class CreateBugReportRequest(
    val title: String,
    val description: String,
)

data class UpdateBugStatusRequest(
    val status: String,
)

/**
 * The status values the backend accepts. Treated as an open set everywhere it's read — the app
 * displays whatever the server sends, so a new status added server-side shows up instead of
 * vanishing or crashing.
 */
object BugStatus {
    const val OPEN = "Open"
    const val IN_PROGRESS = "In Progress"
    const val CLOSED = "Closed"
    const val RESOLVED = "Resolved"

    /** True once a report needs no more attention — drives the badge colour, nothing else. */
    fun isSettled(status: String): Boolean =
        status.equals(CLOSED, ignoreCase = true) || status.equals(RESOLVED, ignoreCase = true)
}
