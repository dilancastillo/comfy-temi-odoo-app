// envia en segundo plano cada linea persistida a un Google Sheet (Apps Script Web App)
package com.example.comfyapp.logging

import com.example.comfyapp.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object SheetLogUploader {

    private const val TAG = "SheetLogUploader"
    private val executor = Executors.newSingleThreadExecutor()

    private val enabled: Boolean
        get() = BuildConfig.SHEETS_LOG_URL.isNotBlank()

    fun send(timestamp: String, level: String, tag: String, message: String) {
        if (!enabled) return
        executor.execute {
            runCatching {
                val body = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("level", level)
                    put("tag", tag)
                    put("message", message)
                }
                val connection = URL(BuildConfig.SHEETS_LOG_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.doOutput = true
                // Apps Script ejecuta el doPost y responde 302 hacia una URL de eco de un solo
                // uso para entregar el texto de salida; para cuando llega ese 302 la fila ya
                // quedó guardada. No seguimos el redirect: la segunda petición a esa URL de eco
                // suele fallar (405/411) y no aporta nada, solo generaría ruido en el log.
                connection.instanceFollowRedirects = false
                try {
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                    val code = connection.responseCode
                    check(code in 200..299 || code == HttpURLConnection.HTTP_MOVED_TEMP) {
                        "Sheets HTTP $code"
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                // No usar PersistentLog aquí para no reintentar el envío en bucle si falla.
                android.util.Log.w(TAG, "No se pudo enviar el log al Sheet", error)
            }
        }
    }
}
