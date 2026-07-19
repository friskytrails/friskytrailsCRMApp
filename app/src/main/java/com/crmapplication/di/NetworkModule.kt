package com.crmapplication.di

import com.crmapplication.LeadDetailVM.remote.AgentsApi
import com.crmapplication.LeadDetailVM.remote.ApiConfig
import com.crmapplication.LeadDetailVM.remote.AuthApi
import com.crmapplication.LeadDetailVM.remote.CallsApi
import com.crmapplication.LeadDetailVM.remote.ConfigApi
import com.crmapplication.LeadDetailVM.remote.LeadsApi
import com.crmapplication.LeadDetailVM.remote.UploadApi
import com.salescrm.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

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

    private fun String.withTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
