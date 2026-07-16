package com.crmapplication.LeadDetailVM.remote

import com.salescrm.BuildConfig

object ApiConfig {
    const val BASE_URL = "https://friskytrails-crm-pdte.vercel.app/"
    const val LEADS_ENDPOINT = "api/leads"

    val AUTH_TOKEN: String get() = BuildConfig.LEADS_AUTH_TOKEN

    val isConfigured: Boolean
        get() = BASE_URL.isNotBlank() && LEADS_ENDPOINT.isNotBlank()
}
