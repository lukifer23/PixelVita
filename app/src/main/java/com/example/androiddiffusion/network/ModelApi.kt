package com.example.androiddiffusion.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ModelApi {
    @Streaming
    @GET
    suspend fun downloadModel(@Url url: String): Response<ResponseBody>
} 