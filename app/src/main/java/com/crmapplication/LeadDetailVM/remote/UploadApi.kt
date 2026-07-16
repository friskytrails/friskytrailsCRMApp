package com.crmapplication.LeadDetailVM.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class UploadResponse(
    val message: String? = null,
    val fileUrl: String? = null,
    val fileData: UploadFileData? = null,

    val error: String? = null,
)

data class UploadFileData(
    val originalname: String? = null,
    val mimetype: String? = null,
    val path: String? = null,
    val size: Long? = null,
)

interface UploadApi {
    @Multipart
    @POST("api/upload")
    suspend fun upload(
        @Header("Authorization") authorization: String?,
        @Part file: MultipartBody.Part,
    ): Response<UploadResponse>
}
