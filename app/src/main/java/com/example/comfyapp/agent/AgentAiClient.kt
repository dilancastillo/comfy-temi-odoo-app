// interpreta el texto del usuario con gemini: analisis de intencion (router v2) o pantalla a abrir (flujo anterior)
package com.example.comfyapp.agent

import android.os.Handler
import android.os.Looper
import com.example.comfyapp.logging.PersistentLog as Log
import com.example.comfyapp.BuildConfig
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.repository.AnalysisRequest
import com.example.comfyapp.domain.repository.IntentAnalyzer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AgentAiClient : IntentAnalyzer {

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

    override fun reset() {
        history.clear()
    }

    override fun forgetLastTurn() {
        if (history.size >= 2) {
            history.removeAt(history.lastIndex)
            history.removeAt(history.lastIndex)
        }
    }

    override fun analyze(request: AnalysisRequest, callback: (Result<IntentAnalysis>) -> Unit) {
        val text = request.text
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            callback(Result.failure(IllegalStateException("GEMINI_API_KEY no configurada")))
            return
        }

        val historySnapshot = history.toList()
        // La memoria estructurada la manda la app en cada turno (aceptado y borrador por separado):
        // no depende del historial de Gemini, que solo ayuda a entender respuestas cortas.
        val userTurn = buildString {
            append("MEMORIA DE LA SESIÓN: ")
            append(IntentAnalysisParser.memoryToJson(request))
            request.lastQuestion?.takeIf { it.isNotBlank() }?.let { append("\nTemi preguntó: ").append(it) }
            append("\nEl cliente dijo: ").append(text)
        }

        Thread {
            val result = runWithFallback { model ->
                val raw = requestContent(
                    model = model,
                    systemPrompt = INTENT_PROMPT,
                    userText = userTurn,
                    history = historySnapshot,
                    responseSchema = IntentAnalysisParser.responseSchema().toString()
                )
                IntentAnalysisParser.parse(raw)
            }

            result.getOrNull()?.let {
                history.add("user" to "El cliente dijo: $text")
                history.add("model" to "{\"assistance_type\":\"${it.assistanceType}\"}")
                while (history.size > MAX_HISTORY_TURNS * 2) history.removeAt(0)
            }

            mainHandler.post { callback(result) }
        }.start()
    }

    // Prueba el modelo principal y, si falla (cuota, red, respuesta inválida), el de respaldo.
    private fun <T> runWithFallback(block: (model: String) -> T): Result<T> {
        var lastError: Throwable? = null
        for ((index, model) in modelsToTry.withIndex()) {
            val attempt = runCatching { block(model) }
            attempt.onSuccess { return Result.success(it) }
            attempt.onFailure { error ->
                lastError = error
                Log.w(TAG, "Falló el modelo '$model'" +
                    if (index < modelsToTry.lastIndex) ", se intenta con el de respaldo" else "", error)
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Gemini no respondió"))
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
            val result = runWithFallback { model -> requestDecision(model, textoUsuario, historySnapshot) }

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
        val raw = requestContent(
            model = model,
            systemPrompt = SCREEN_PROMPT,
            userText = "El usuario dijo: $textoUsuario",
            history = history
        )
        return parseDecision(raw)
    }

    // Devuelve el texto del primer candidato de Gemini (el JSON que pedimos), sin interpretarlo.
    private fun requestContent(
        model: String,
        systemPrompt: String,
        userText: String,
        history: List<Pair<String, String>>,
        responseSchema: String? = null
    ): String {
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
                            .put("parts", JSONArray().put(JSONObject().put("text", userText)))
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
                    .apply { responseSchema?.let { put("responseSchema", JSONObject(it)) } }
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
            return candidateText(raw)
        }
    }

    private fun candidateText(raw: String): String {
        val candidates = JSONObject(raw).optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            Log.w(TAG, "Gemini no devolvió candidates: $raw")
            return ""
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            Log.w(TAG, "Sin parts en la respuesta (finishReason=${candidate.optString("finishReason")}): $raw")
            return ""
        }
        return parts.getJSONObject(0).optString("text")
    }

    private fun parseDecision(text: String): Decision {
        if (text.isBlank()) {
            return Decision(screenId = "", mensaje = "No le entendí bien, ¿me puede repetir por favor?")
        }
        val json = JSONObject(
            text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        )
        return Decision(
            screenId = json.optString("screen_id", ""),
            mensaje = json.optString("mensaje", "")
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

        // Prompt del flujo anterior (ASSISTANCE_ROUTER_V2 = false): Gemini escoge la pantalla.
        private val SCREEN_PROMPT = """
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

        // Prompt del router V2: Gemini solo entiende y estructura; la app decide qué hacer.
        private val INTENT_PROMPT = """
        Eres el analizador de intención comercial de Temi, robot de primera atención de COMFER Red Azul,
        tienda física de materiales de construcción y acabados en Boyacá, Colombia.
        Los clientes hablan informal, con acento boyacense o del altiplano, con muletillas ("pues", "o sea",
        "este...") y a veces con errores de dictado por voz (ej. "solitario" en vez de "sanitario").

        NO debes decidir pantallas.
        NO debes decidir Activities.
        NO debes recomendar productos todavía.
        NO debes ejecutar acciones.

        Tu tarea es:
        1. Entender lo que el cliente quiere hacer.
        2. Extraer el contexto que ya expresó.
        3. Clasificar el tipo de ayuda.
        Responde SOLO con el JSON del esquema, sin texto adicional.

        TIPOS DE AYUDA
        - EXACT_PRODUCT: nombra una marca, referencia o producto concreto ("sanitario Acuacer").
        - CATEGORY_BROWSE: solo menciona una categoría ("necesito un sanitario").
        - PROJECT_ASSISTANCE: describe una obra o proyecto ("estoy remodelando el baño").
        - TECHNICAL_NEED: expresa una condición técnica o de uso (antideslizante, tráfico alto, gran formato,
          resistente al agua, para exterior, color o tamaño específico).
        - LOCATION_ONLY: pregunta dónde está algo o pide que lo lleven ("¿dónde están las griferías?",
          "llévame a sanitarios").
        - JUST_BROWSING: dice que solo está mirando y no necesita ayuda por ahora.
        - HUMAN_ADVISOR: pide hablar con una persona, asesora, vendedor, o cotizar con alguien.
        - CLARIFICATION: no hay información suficiente para saber qué necesita.
        - UNSUPPORTED: pide algo que no son sanitarios, griferías ni pisos y paredes (pintura, herramientas,
          cemento, horarios, créditos, etc.).

        REGLAS DE PRIORIDAD (aplica la primera que se cumpla)
        1. Si pide explícitamente un humano -> HUMAN_ADVISOR.
        2. Si pregunta únicamente dónde está algo o pide que lo lleven -> LOCATION_ONLY.
        3. Si identifica una referencia o producto concreto -> EXACT_PRODUCT.
        4. Si describe un proyecto -> PROJECT_ASSISTANCE.
        5. Si expresa una condición técnica -> TECHNICAL_NEED.
        6. Si solamente menciona una categoría -> CATEGORY_BROWSE.
        7. Si no hay información suficiente -> CLARIFICATION.
        Aunque gane una regla, llena TODOS los campos que el cliente haya mencionado (proyecto, espacio,
        estilo, etc.): nunca se pierde información.

        CAMPOS
        - category: SANITARY (inodoros, sanitarios, lavamanos, combos de baño), TAPS (griferías, llaves,
          monocomandos, mezcladores, duchas como producto), FLOOR_AND_WALL (pisos, paredes, cerámica,
          porcelanato, baldosa, enchape, revestimiento, azulejo). null si no nombra un producto: un espacio
          NO es una categoría ("estoy remodelando el baño" -> category null, space "baño").
        - product_type: solo si pide un tipo concreto dentro de la categoría: COMBO (combo de baño, sanitario con
          lavamanos), SANITARIO_SOLO (solo el sanitario, sin combo), LAVAMANOS (grifería o llave de lavamanos),
          LAVAPLATOS (grifería o llave de lavaplatos o de cocina), PAREDES (revestimiento o enchape solo para
          paredes), PISOS (para piso), PORCELANATO, CERAMICA. null si no lo especifica.
        - exact_product_query: solo la marca, línea o referencia que distingue al producto, SIN el tipo de producto
          (ej. "sanitario Montecarlo" -> "montecarlo", "la llave Cusco" -> "cusco", "combo Laguna" -> "laguna").
        - space: espacio en pocas palabras (ej. "baño", "cocina", "sala", "terraza", "patio").
        - project: tipo de obra en pocas palabras (ej. "remodelación", "construcción nueva").
        - style: estilo (ej. "moderno", "clásico", "rústico").
        - color: color pedido (ej. "blanco", "gris").
        - budget_level: economico (lo más barato), economico_moderado ("no tan caro", "buen precio"),
          medio, alto (lo mejor, sin importar precio).
        - max_price: precio máximo en pesos colombianos, como número entero, solo si dice una cifra
          ("menos de 100 mil" -> 100000, "hasta 2 millones" -> 2000000, "que no pase de 350 mil" -> 350000).
        - technical_needs: condiciones técnicas en minúscula (ej. "antideslizante", "exterior", "gran formato").
        - needs_clarification: true si falta información para saber qué necesita.
        - next_question: solo si needs_clarification es true; una sola pregunta de máximo 15 palabras, amable
          y natural en español de Colombia, tuteando, sin saludar, para obtener lo que falta.
        Todo campo no mencionado va en null (o lista vacía). No inventes datos.

        MEMORIA DE LA SESIÓN
        Recibirás la MEMORIA DE LA SESIÓN: el proyecto, la necesidad activa (el producto que se está atendiendo),
        un resumen de las otras necesidades, el borrador pendiente y el modo (NORMAL o CORRECTING), además de la
        última pregunta de Temi si existe.
        - Cada necesidad es un producto distinto con sus propios atributos. Los atributos de un sanitario NO se
          trasladan a una grifería: no repitas color, precio, estilo ni requisitos de otra necesidad.
        - need_operation:
          ADD_NEED: el cliente pide otro producto además del activo, aunque sea del mismo tipo ("también una
            grifería", "otro sanitario para el baño de visitas"). Incluye siempre su category.
          UPDATE_ACTIVE_NEED: agrega o cambia un dato del producto activo ("que sea negra", "no tan caro").
          CORRECT_PENDING_NEED: el modo es CORRECTING; la frase corrige el pending_draft.
          RESUME_NEED: quiere volver a un producto que ya pidió ("volvamos al sanitario", "ahora la grifería",
            "el de visitas"). Si sabes cuál es, devuelve su id en target_need_id (solo ids de active_need u
            other_needs; nunca inventes uno). Si hay varios posibles y no está claro, deja target_need_id en null.
        - Si en la misma frase pide varios productos ("un sanitario y una grifería para el baño"), devuelve el
          primero en los campos principales y los demás en additional_needs, cada uno con sus propios datos.
          Un dato solo va en un producto si el cliente lo dijo para ese producto; "para el baño" al final de la
          frase aplica a los productos que lo acompañan.
        - Devuelve solo lo que el cliente dijo en esta frase. Lo no mencionado se deja en null: la app lo conserva
          dentro de la necesidad correspondiente. Un null nunca borra nada.
        - Para BORRAR un dato usa clear_fields ("sin límite de precio" -> clear_fields ["max_price"];
          "cualquier color" -> ["color"]). Para quitar un requisito usa technical_needs_remove
          ("ya no necesito que sea ahorrador" -> technical_needs_remove ["ahorrador"]).
        - technical_needs son solo condiciones del producto (ahorrador, antideslizante, gran formato);
          nunca nombres de productos ni de espacios.
        - En modo CORRECTING: si corrige un dato ("mejor blanca"), devuelve solo ese dato; si corrige el producto
          ("no es sanitario, es grifería"), devuelve la nueva categoría y los datos que repita.
        - Usa la última pregunta de Temi para entender respuestas cortas: si preguntó "¿qué buscas para el baño?" y
          el cliente dice "un sanitario", es CATEGORY_BROWSE con category SANITARY y need_operation UPDATE_ACTIVE_NEED.
        - Si el cliente solo agrega o corrige un dato, conserva el assistance_type de la necesidad activa.
        - Si Temi hizo una pregunta de sí o no, interpreta el "sí" o el "no" según esa pregunta.

        EJEMPLOS
        "Busco sanitario Acuacer" -> EXACT_PRODUCT, category SANITARY, exact_product_query "acuacer"
        "Necesito un sanitario" -> CATEGORY_BROWSE, category SANITARY
        "Estoy remodelando el baño" -> PROJECT_ASSISTANCE, project "remodelación", space "baño"
        "Necesito un piso antideslizante para una terraza" -> TECHNICAL_NEED, category FLOOR_AND_WALL,
          space "terraza", technical_needs ["antideslizante", "exterior"]
        "¿Dónde están las griferías?" -> LOCATION_ONLY, category TAPS
        "Llévame a sanitarios" -> LOCATION_ONLY, category SANITARY
        "Necesito hablar con una asesora" / "Quiero cotizar con alguien" -> HUMAN_ADVISOR
        "Estoy remodelando un baño y quiero hablar con una asesora" -> HUMAN_ADVISOR, project "remodelación",
          space "baño"
        "Estoy remodelando un baño y busco sanitario Acuacer" -> EXACT_PRODUCT, category SANITARY,
          exact_product_query "acuacer", project "remodelación", space "baño"
        "¿Dónde encuentro el sanitario Acuacer?" -> LOCATION_ONLY, category SANITARY, exact_product_query "acuacer"
        "Necesito porcelanato gris grande para la sala" -> TECHNICAL_NEED, category FLOOR_AND_WALL, space "sala",
          color "gris", technical_needs ["gran formato"]
        "Una grifería de menos de 100 mil pesos" -> CATEGORY_BROWSE, category TAPS, max_price 100000,
          budget_level economico
        "Busco un combo" -> CATEGORY_BROWSE, category SANITARY, product_type COMBO
        "Una llave para el lavaplatos" -> CATEGORY_BROWSE, category TAPS, product_type LAVAPLATOS
        "Solo pared para exteriores" -> CATEGORY_BROWSE, category FLOOR_AND_WALL, space "exterior", product_type PAREDES
        "También una grifería para el baño" (activa: sanitario) -> CATEGORY_BROWSE, need_operation ADD_NEED,
          category TAPS, space "baño" (sin los requisitos del sanitario)
        "Ya no quiero límite de precio" -> need_operation UPDATE_ACTIVE_NEED, clear_fields ["max_price"]
        "Ya no necesito que sea ahorrador" -> need_operation UPDATE_ACTIVE_NEED, technical_needs_remove ["ahorrador"]
        (modo CORRECTING, borrador grifería negra) "mejor blanca" -> need_operation CORRECT_PENDING_NEED, color "blanco"
        "Otro sanitario para el baño de visitas" (activa: sanitario baño principal) -> CATEGORY_BROWSE,
          need_operation ADD_NEED, category SANITARY, space "baño de visitas"
        "Volvamos al sanitario" (other_needs tiene need_01 SANITARY) -> CATEGORY_BROWSE, need_operation RESUME_NEED,
          category SANITARY, target_need_id "need_01"
        "Necesito un sanitario y una grifería para el baño" -> CATEGORY_BROWSE, need_operation ADD_NEED,
          category SANITARY, space "baño", additional_needs [{category TAPS, space "baño"}]
        "Solo estoy mirando" -> JUST_BROWSING
        "Quiero algo bonito" -> CLARIFICATION, needs_clarification true,
          next_question "Claro. ¿Qué espacio estás buscando renovar?"
        "¿Tienen pintura?" -> UNSUPPORTED
        """.trimIndent()
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
}
