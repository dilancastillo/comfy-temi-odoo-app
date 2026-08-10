// interpreta el texto del usuario con gemini y decide que pantalla abrir
package com.example.comfyapp.agent

import android.os.Handler
import android.os.Looper
import com.example.comfyapp.logging.PersistentLog as Log
import com.example.comfyapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AgentAiClient {

    data class Decision(
        val screenId: String = "",
        val mensaje: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    fun resolver(
        textoUsuario: String,
        callback: (Result<Decision>) -> Unit
    ) {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            callback(Result.failure(IllegalStateException("GEMINI_API_KEY no configurada")))
            return
        }

        Thread {
            val model = BuildConfig.GEMINI_MODEL.ifBlank { "gemini-3.5-flash" }
            var tokenUsageLogged = false
            val result = runCatching {
                val systemPrompt = """
                Eres el asistente de voz de un robot en la tienda física de materiales de construcción Comfer, ubicada en Boyacá, Colombia.
                Los clientes hablan de forma informal, con acento boyacense o del altiplano, a veces con errores de dictado por voz, muletillas ("pues", "o sea", "este...", "digamos") y frases cortas o incompletas.
                
                Tu ÚNICA tarea es interpretar la intención real y responder EXCLUSIVAMENTE con un JSON válido de esta forma:
                {"screen_id":"","mensaje":""}
                
                No escribas nada más.
                
                PANTALLAS DISPONIBLES
                - "pisos_y_paredes" → pisos o paredes en general (cuando no mencionan ambiente)
                - "baños" → pisos o enchapes para baño, ducha o zona húmeda
                - "zona_social" → pisos o enchapes para sala, comedor, sala de estar o living
                - "exteriores" → pisos o enchapes para patio, terraza, jardín, parqueadero, quincho o afuera
                - "sanitarios" → inodoros, lavamanos, bidés o combos de baño
                - "griferias" → griferías, llaves, monocomandos, duchas (el producto)
                
                SINÓNIMOS IMPORTANTES (Colombia)
                - piso / pisos / cerámica / porcelanato / baldosa / enchape / revestimiento / azulejo / piso laminado / piso flotante → van a pisos o paredes
                - "enchape" es muy usado para paredes
                
                REGLAS DE DECISIÓN (en este orden)
                
                1. Producto específico gana:
                   - inodoro, sanitario, lavamanos, bidé, combo de baño → "sanitarios"
                   - grifería, llave, monocomando, mezclador, ducha (como producto) → "griferias"
                   Ejemplo: "necesito una ducha nueva" → griferias
                           "el enchape de la ducha" → baños
                
                2. Ambiente + piso/pared/enchape:
                   - baño / ducha / zona húmeda → "baños"
                   - sala / comedor / sala de estar / living → "zona_social"
                   - patio / terraza / jardín / afuera / parqueadero / quincho → "exteriores"
                
                3. Solo dice "piso", "cerámica", "porcelanato", "enchape", "baldosa" sin mencionar ambiente → "pisos_y_paredes"
                   y en el mensaje pregunta de forma amable para qué ambiente es.
                
                4. Menciona varios ambientes → usa el primero que diga. Si no es claro → "pisos_y_paredes" + mensaje aclaratorio.
                
                5. Pide algo que no está en las pantallas (pintura, herramientas, cemento, precios, horarios, etc.) → screen_id="" y responde amable diciendo que no tienes esa sección.
                
                6. Texto confuso, cortado o solo ruido → screen_id="" y pide que repita la consulta.
                
                REGLA DEL MENSAJE
                - Si screen_id tiene valor y no hace falta aclarar → "mensaje":""
                - Solo llena "mensaje" cuando screen_id esté vacío o necesites pedir más detalle.
                
                Ejemplos de respuesta correcta:
                {"screen_id":"baños","mensaje":""}
                {"screen_id":"pisos_y_paredes","mensaje":"¿Para qué ambiente lo necesita? ¿Baño, sala o exterior?"}
                {"screen_id":"","mensaje":"Esa sección no la tengo disponible en el robot. ¿Busca algo de pisos, baños o griferías?"}
                {"screen_id":"","mensaje":"No le entendí bien, ¿me puede repetir por favor?"}
                """  .trimIndent()

                val body = JSONObject().apply {
                    put(
                        "system_instruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", systemPrompt))
                        )
                    )
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().put("text", "El usuario dijo: $textoUsuario")
                                    )
                                )
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject()
                            .put("responseMimeType", "application/json")
                            .put("maxOutputTokens", 512) // más margen
                            .put(
                                "thinkingConfig",
                                JSONObject().put("thinkingBudget", 0) // desactiva el thinking
                            )
                    )
                }

                val connection = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "$model:generateContent"
                ).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10_000
                connection.readTimeout = 35_000
                connection.doOutput = true

                try {
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        tokenUsageLogged = true
                        throw IllegalStateException("Gemini HTTP $code: ${raw.take(800)}")
                    }
                    logTokenUsage(raw, model)
                    tokenUsageLogged = true
                    parseDecision(raw)
                } finally {
                    connection.disconnect()
                }
            }

            result.exceptionOrNull()?.let {
                Log.w(TAG, "Error al resolver con Gemini", it)
                if (!tokenUsageLogged && BuildConfig.DEBUG) {
                    Log.d(TRACE_TAG, "TOKENS: no disponibles (error antes de respuesta)")
                }
            }
            result.getOrNull()?.let { decision ->
                if (BuildConfig.DEBUG) {
                    Log.d(TRACE_TAG, "DECISION: screen_id=${decision.screenId} | mensaje=${decision.mensaje}")
                }
            }

            mainHandler.post { callback(result) }
        }.start()
    }

    private fun parseDecision(raw: String): Decision {
        val root = JSONObject(raw)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            Log.w(TAG, "Gemini no devolvió candidates: $raw")
            return Decision(screenId = "", mensaje = "No le entendí bien, ¿me puede repetir por favor?")
        }

        val candidate = candidates.getJSONObject(0)
        val finishReason = candidate.optString("finishReason")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            Log.w(TAG, "Sin parts en la respuesta (finishReason=$finishReason): $raw")
            return Decision(screenId = "", mensaje = "No le entendí bien, ¿me puede repetir por favor?")
        }

        val text = parts.getJSONObject(0)
            .getString("text")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(text)
        return Decision(
            screenId = json.optString("screen_id", ""),
            mensaje = json.optString("mensaje", "")
        )
    }

    private fun logTokenUsage(raw: String, model: String) {
        if (!BuildConfig.DEBUG) return
        val usage = JSONObject(raw).optJSONObject("usageMetadata") ?: run {
            Log.d(TRACE_TAG, "TOKENS: modelo=$model | no reportados por Gemini")
            return
        }
        Log.d(
            TRACE_TAG,
            "TOKENS: modelo=$model | " +
                "entrada=${usage.optInt("promptTokenCount", 0)} | " +
                "salida=${usage.optInt("candidatesTokenCount", 0)} | " +
                "total=${usage.optInt("totalTokenCount", 0)}"
        )
    }

    companion object {
        private const val TAG = "AgentAiClient"
        private const val TRACE_TAG = "AgentTrace"
    }
}
