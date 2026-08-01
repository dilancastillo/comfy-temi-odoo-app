// construye y procesa las solicitudes json rpc utilizadas para consultar odoo
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
    const val BASE_URL = BuildConfig.ODOO_BASE_URL

    // ---------------------------------------------------------
    // RETROFIT seguro usando HTTPS
    // ---------------------------------------------------------
    private val odooApi: OdooApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.ODOO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OdooApi::class.java)
    }

    // ---------------------------------------------------------
    // EJECUTAR JSON-RPC
    // ---------------------------------------------------------
    fun executeOdooRpc(
        model: String,
        method: String,
        //antes= domain: List<List<Any>>,
        domain: List<Any>,
        fields: Map<String, Boolean>? = null,
        onSuccess: (JsonArray) -> Unit,
        onError: (String) -> Unit = {},
        order: String,
        limit: Int
    ) {

        val params = JsonObject().apply {
            addProperty("service", "object")
            addProperty("method", "execute_kw")

            val args = JsonArray().apply {
                add(BuildConfig.ODOO_DB)      // DB desde local.properties
                add(BuildConfig.ODOO_UID.toInt()) // UID desde local.properties
                add(BuildConfig.ODOO_API_KEY) // API KEY desde local.properties
                add(model)
                add(method)

                val innerArgs = JsonArray().apply {
                    add(Gson().toJsonTree(domain))
                }
                add(innerArgs)
                val kwargs = JsonObject().apply {
                    if (fields != null) add("fields", Gson().toJsonTree(fields))
                    addProperty("limit", limit)
                    addProperty("order", order)
                }
                add(kwargs)

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

                // 👇 LOG COMPLETO
                android.util.Log.e("ODDO-DEBUG", "Raw response: ${response.body()}")

                if (response.isSuccessful) {
                    val result = response.body()?.get("result")
                    if (result != null) {
                        if (result.isJsonArray) {
                            onSuccess(result.asJsonArray)
                        } else {
                            onError("El result NO es un array: $result")
                        }
                    } else {
                        onError("Respuesta sin 'result': ${response.body()}")
                    }
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
