// interpreta el texto del usuario con gemini y decide que pantalla abrir
package com.example.comfyapp.agent

import android.os.Handler
import android.os.Looper
import com.example.comfyapp.logging.PersistentLog as Log
import com.example.comfyapp.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AgentAiClient {

    data class Decision(
        val screenId: String = "",
        val mensaje: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    // Modelo principal y de respaldo — si el principal falla (cuota, error de red,
    // saturación, etc.) se reintenta la misma petición con el de respaldo antes de rendirse.
    private val modelsToTry: List<String> by lazy {
        listOf(
            BuildConfig.GEMINI_MODEL.ifBlank { "gemini-3.5-flash" },
            BuildConfig.GEMINI_MODEL_FALLBACK.ifBlank { "gemini-3.6-flash" }
        ).distinct()
    }

    // Historial corto de la conversación en curso (turno "user"/"model"), para que una
    // respuesta incompleta del cliente (ej. "para el baño") se entienda en el contexto de
    // la aclaración que el propio agente acaba de pedir. Se reinicia con reset() al empezar
    // un nuevo ciclo de detección para no arrastrar contexto de un cliente anterior.
    private val history = mutableListOf<Pair<String, String>>()

    fun reset() {
        history.clear()
    }

    fun resolver(
        textoUsuario: String,
        callback: (Result<Decision>) -> Unit
    ) {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            callback(Result.failure(IllegalStateException("GEMINI_API_KEY no configurada")))
            return
        }

        val historySnapshot = history.toList()

        Thread {
            var lastError: Exception? = null
            var decision: Decision? = null

            for ((index, model) in modelsToTry.withIndex()) {
                val attemptResult = runCatching { requestDecision(model, textoUsuario, historySnapshot) }
                attemptResult.onSuccess {
                    decision = it
                    lastError = null
                }
                attemptResult.onFailure { error ->
                    lastError = error as? Exception ?: Exception(error)
                    Log.w(TAG, "Falló el modelo '$model'" +
                        if (index < modelsToTry.lastIndex) ", se intenta con el de respaldo" else "", error)
                }
                if (decision != null) break
            }

            val result = decision?.let { Result.success(it) }
                ?: Result.failure(lastError ?: IllegalStateException("Gemini no respondió"))

            result.getOrNull()?.let {
                if (BuildConfig.DEBUG) {
                    Log.d(TRACE_TAG, "DECISION: screen_id=${it.screenId} | mensaje=${it.mensaje}")
                }
                history.add("user" to textoUsuario)
                history.add("model" to "{\"screen_id\":\"${it.screenId}\",\"mensaje\":\"${it.mensaje}\"}")
                while (history.size > MAX_HISTORY_TURNS * 2) history.removeAt(0)
            }

            mainHandler.post { callback(result) }
        }.start()
    }

    private fun requestDecision(model: String, textoUsuario: String, history: List<Pair<String, String>>): Decision {
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

        CONTEXTO DE CONVERSACIÓN
        Si ves turnos anteriores, úsalos para entender respuestas incompletas. Ejemplo: si en el
        turno anterior preguntaste "¿para qué ambiente lo necesita?" y el cliente ahora solo dice
        "para el baño", interpreta que es la respuesta a esa pregunta y responde con screen_id="baños".

        REGLA DEL MENSAJE
        - Si screen_id tiene valor y no hace falta aclarar → "mensaje":""
        - Solo llena "mensaje" cuando screen_id esté vacío o necesites pedir más detalle.

        Ejemplos de respuesta correcta:
        {"screen_id":"baños","mensaje":""}
        {"screen_id":"pisos_y_paredes","mensaje":"¿Para qué ambiente lo necesita? ¿Baño, sala o exterior?"}
        {"screen_id":"","mensaje":"Esa sección no la tengo disponible en el robot. ¿Busca algo de pisos, baños o griferías?"}
        {"screen_id":"","mensaje":"No le entendí bien, ¿me puede repetir por favor?"}
        """.trimIndent()

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
                JSONArray().apply {
                    history.forEach { (role, text) ->
                        put(
                            JSONObject()
                                .put("role", role)
                                .put("parts", JSONArray().put(JSONObject().put("text", text)))
                        )
                    }
                    put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", "El usuario dijo: $textoUsuario")
                                )
                            )
                    )
                }
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

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini HTTP ${response.code}: ${raw.take(800)}")
            }
            logTokenUsage(raw, model)
            return parseDecision(raw)
        }
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
        private const val MAX_HISTORY_TURNS = 3 // pares user/model

        // Cliente compartido: OkHttp reutiliza conexiones TCP/TLS entre peticiones al mismo
        // host en vez de abrir una nueva por cada pregunta (como hacía HttpURLConnection con
        // disconnect() forzado). Una sola instancia para toda la app.
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(13, TimeUnit.SECONDS)
                .build()
        }
    }
}
