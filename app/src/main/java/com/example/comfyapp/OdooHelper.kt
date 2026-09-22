// construye y procesa las solicitudes json rpc utilizadas para consultar odoo
package com.example.comfyapp

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import android.os.SystemClock
import com.example.comfyapp.logging.PersistentLog as Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object OdooHelper {
    const val BASE_URL = BuildConfig.ODOO_BASE_URL

    // ---------------------------------------------------------
    // RETROFIT seguro usando HTTPS
    // ---------------------------------------------------------
    private val odooApi: OdooApi by lazy {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.ODOO_BASE_URL)
            .client(httpClient)
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
        fields: List<String>? = null,
        onSuccess: (JsonArray) -> Unit,
        onError: (String) -> Unit = {},
        order: String,
        limit: Int,
        offset: Int = 0,
        context: Map<String, Any>? = null
    ) {
        val requestId = System.currentTimeMillis()
        val requestStartedAt = SystemClock.elapsedRealtime()
        Log.i(
            ODOO_TIMING_TAG,
            "odoo_request_started id=$requestId model=$model method=$method " +
                "offset=$offset limit=$limit"
        )

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
                    addProperty("offset", offset)
                    addProperty("order", order)
                    if (context != null) add("context", Gson().toJsonTree(context))
                }
                add(kwargs)

            }

            add("args", args)
        }

        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "call")
            add("params", params)
            addProperty("id", requestId)
        }

        odooApi.call(request).enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                val result = response.body()?.get("result")
                val resultCount = result?.takeIf { it.isJsonArray }?.asJsonArray?.size()
                Log.i(
                    ODOO_TIMING_TAG,
                    "odoo_request_finished id=$requestId model=$model " +
                        "durationMs=${SystemClock.elapsedRealtime() - requestStartedAt} " +
                        "httpCode=${response.code()} hasResult=${result != null} " +
                        "resultCount=${resultCount ?: "n/a"}"
                )

                if (response.isSuccessful) {
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
                Log.i(
                    ODOO_TIMING_TAG,
                    "odoo_request_failed id=$requestId model=$model " +
                        "durationMs=${SystemClock.elapsedRealtime() - requestStartedAt} " +
                        "errorType=${t.javaClass.simpleName}"
                )
                onError("Error de red: ${t.message}")
            }
        })
    }

    const val ODOO_TIMING_TAG = "OdooTiming"
}
