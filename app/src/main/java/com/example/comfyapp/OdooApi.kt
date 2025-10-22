package com.example.comfyapp

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface OdooApi {
    @POST("jsonrpc")
    fun call(@Body request: JsonObject): Call<JsonObject>
}