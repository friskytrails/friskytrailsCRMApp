package com.crmapplication.LeadDetailVM.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

data class AgentMetricsDto(
    val monthlyTarget: Int? = null,
    val targetCompleted: Int? = null,
    val attendance: String? = null,
)

interface AgentsApi {
    @GET("api/agents/{id}/metrics")
    suspend fun getMetrics(
        @Path("id") id: String,
        @Header("Authorization") authorization: String?,
    ): AgentMetricsDto
}
