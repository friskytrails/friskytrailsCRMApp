package com.crmapplication.LeadDetailVM.remote

import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Global config (`key: GLOBAL_SETTINGS`) — currently just the product catalog shown in the
 * Add Lead dropdown. Admins overwrite it server-side via `PUT /api/config/products`; agents only
 * read, so that endpoint is deliberately not modelled here (it would 403 for every app user).
 *
 * The `_id` / `key` / timestamps in the response are ignored — Gson drops unmapped fields.
 */
data class GlobalConfigDto(
    val products: List<String>? = null,
)

interface ConfigApi {
    @GET("api/config")
    suspend fun getConfig(
        @Header("Authorization") authorization: String?,
    ): GlobalConfigDto
}
