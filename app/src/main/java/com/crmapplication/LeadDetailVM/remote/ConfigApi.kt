package com.crmapplication.LeadDetailVM.remote

import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Global config (`key: GLOBAL_SETTINGS`) — the server-owned lead statuses and product catalog.
 * Admins overwrite it server-side; agents only read, so the write endpoints are deliberately not
 * modelled here (they would 403 for every app user).
 *
 * Both lists drive UI that used to be hardcoded, so a status or product added on the backend shows
 * up across the app without an app release. Nullable because a partial config response must not
 * fail the whole parse — a missing key means "keep what's cached".
 *
 * The `_id` / `key` / timestamps in the response are ignored — Gson drops unmapped fields.
 */
data class GlobalConfigDto(
    val statuses: List<String>? = null,
    val products: List<String>? = null,
)

interface ConfigApi {
    @GET("api/config")
    suspend fun getConfig(
        @Header("Authorization") authorization: String?,
    ): GlobalConfigDto
}
