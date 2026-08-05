package com.crmapplication.di

import com.crmapplication.LeadDetailVM.remote.AgentsApi
import com.crmapplication.LeadDetailVM.remote.ApiConfig
import com.crmapplication.LeadDetailVM.remote.AuthApi
import com.crmapplication.LeadDetailVM.remote.BugReportApi
import com.crmapplication.LeadDetailVM.remote.CallsApi
import com.crmapplication.LeadDetailVM.remote.ConfigApi
import com.crmapplication.LeadDetailVM.remote.LeadsApi
import com.crmapplication.LeadDetailVM.remote.UploadApi
import com.salescrm.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val READ_TIMEOUT_SECONDS = 30L
private const val WRITE_TIMEOUT_SECONDS = 30L

/** Extra attempts (beyond the first) for replay-safe requests. */
private const val MAX_GET_RETRIES = 2
private const val RETRY_BACKOFF_MS = 400L

/**
 * Retries only requests that are safe to replay. A dropped connection on a marginal mobile network
 * is usually transient, and OkHttp's own [OkHttpClient.Builder.retryOnConnectionFailure] doesn't
 * cover a request that failed after the connection was established.
 *
 * GET only, deliberately: replaying a POST could create a duplicate note or lead.
 */
private class RetryIdempotentInterceptor(private val maxRetries: Int) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.method.equals("GET", ignoreCase = true)) return chain.proceed(request)

        var lastError: IOException? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) Thread.sleep(RETRY_BACKOFF_MS * attempt)
            try {
                val response = chain.proceed(request)
                // 502/503/504 from a serverless host usually means the function hadn't woken yet.
                if (attempt == maxRetries || !response.isTransientServerError()) return response
                response.close()
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Request failed after ${maxRetries + 1} attempts")
    }
}

private fun Response.isTransientServerError(): Boolean = code == 502 || code == 503 || code == 504

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            // The backend is serverless (Vercel): a cold start can blow past OkHttp's default 10s
            // read timeout even on a healthy connection. Agents on mobile data were seeing this as
            // a hard "failed to connect" on the first request after an idle period.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RetryIdempotentInterceptor(MAX_GET_RETRIES))
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("leads")
    fun provideLeadsRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL.withTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideLeadsApi(@Named("leads") retrofit: Retrofit): LeadsApi =
        retrofit.create(LeadsApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(@Named("leads") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUploadApi(@Named("leads") retrofit: Retrofit): UploadApi =
        retrofit.create(UploadApi::class.java)

    @Provides
    @Singleton
    fun provideAgentsApi(@Named("leads") retrofit: Retrofit): AgentsApi =
        retrofit.create(AgentsApi::class.java)

    @Provides
    @Singleton
    fun provideCallsApi(@Named("leads") retrofit: Retrofit): CallsApi =
        retrofit.create(CallsApi::class.java)

    @Provides
    @Singleton
    fun provideConfigApi(@Named("leads") retrofit: Retrofit): ConfigApi =
        retrofit.create(ConfigApi::class.java)

    @Provides
    @Singleton
    fun provideBugReportApi(@Named("leads") retrofit: Retrofit): BugReportApi =
        retrofit.create(BugReportApi::class.java)

    private fun String.withTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
