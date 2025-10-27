package com.example.comfyapp

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object OdooHelper {
    private val odooApi: OdooApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://comfer-staging-24811489.dev.odoo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(OdooApi::class.java)
    }

    fun executeOdooRpc(
        model: String,
        method: String,
        domain: List<List<Any>>,
        fields: Map<String, Boolean>? = null,
        onSuccess: (JsonArray) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val params = JsonObject().apply {
            addProperty("service", "object")
            addProperty("method", "execute_kw")
            val args = JsonArray().apply {
                add("comfer-staging-24811489")
                add(2)
                add("9588b9edaf833e41078736aafa96a10320a3b2db")
                add(model)
                add(method)
                val innerArgs = JsonArray().apply {
                    add(Gson().toJsonTree(domain))
                    if (fields != null) add(Gson().toJsonTree(fields))
                }
                add(innerArgs)
            }
            add("args", args)
        }

        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "call")
            add("params", params)
            addProperty("id", System.currentTimeMillis().toInt())
        }

        odooApi.call(request).enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val result = response.body()?.get("result")?.asJsonArray
                    result?.let(onSuccess)
                } else {
                    onError("Error HTTP: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                onError("Error de red: ${t.message}")
            }
        })
    }
}