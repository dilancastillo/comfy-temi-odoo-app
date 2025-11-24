package com.example.comfyapp

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object OdooHelper {

    // ---------------------------------------------------------
    // CLIENTE SSL INSEGURO (solo para STAGING)
    // ---------------------------------------------------------
    fun createUnsafeClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    // ---------------------------------------------------------
    // RETROFIT usando SSL inseguro
    // ---------------------------------------------------------
    private val odooApi: OdooApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://comfer-staging-25883273.dev.odoo.com/")
            .client(createUnsafeClient())
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
        domain: List<List<Any>>,
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
                add("comfer-staging-25883273")     // DB
                add(2)                             // UID
                add("7ce4a291aa6d3e971bb59412ec3a53b630737efc") // API KEY
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
