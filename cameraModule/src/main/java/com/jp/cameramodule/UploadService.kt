package com.jp.cameramodule

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadService {

    @Multipart
    @POST("/upload")
    suspend fun uploadImage(
        @Part image : MultipartBody.Part,
        @Part ("hand") hand : RequestBody
    ) : Response<ResponseBody>
}