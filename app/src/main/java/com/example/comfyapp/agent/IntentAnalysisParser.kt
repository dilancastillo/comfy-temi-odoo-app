// valida la respuesta json de gemini y la convierte en un analisis de intencion confiable
package com.example.comfyapp.agent

import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object IntentAnalysisParser {

    val ASSISTANCE_TYPES = AssistanceType.entries.map { it.name }
    val CATEGORIES = listOf("SANITARY", "TAPS", "FLOOR_AND_WALL")
    val PRODUCT_TYPES = ProductType.entries.map { it.name }
    val BUDGET_LEVELS = listOf("economico", "economico_moderado", "medio", "alto")

    // Lanza IllegalArgumentException si la estructura no es la esperada: una respuesta que no
    // valida se trata igual que una falla de Gemini, nunca se adivina qué quiso decir.
    fun parse(text: String): IntentAnalysis {
        val clean = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = runCatching { JsonParser.parseString(clean).asJsonObject }
            .getOrElse { throw IllegalArgumentException("Respuesta no es un objeto JSON", it) }

        val type = AssistanceType.fromWire(json.string("assistance_type"))
            ?: throw IllegalArgumentException("assistance_type inválido: ${json.get("assistance_type")}")

        return IntentAnalysis(
            assistanceType = type,
            category = when (json.string("category")?.uppercase()) {
                "SANITARY" -> ProductCategory.SANITARY
                "TAPS" -> ProductCategory.TAPS
                "FLOOR_AND_WALL" -> ProductCategory.FLOOR_AND_WALL
                else -> null
            },
            productType = ProductType.fromWire(json.string("product_type")),
            exactProductQuery = json.string("exact_product_query"),
            space = json.string("space"),
            project = json.string("project"),
            style = json.string("style"),
            color = json.string("color"),
            budgetLevel = json.string("budget_level")?.lowercase()?.takeIf { it in BUDGET_LEVELS },
            maxPrice = json.get("max_price")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asDouble
                ?.takeIf { it > 0 },
            technicalNeeds = json.stringList("technical_needs"),
            needsClarification = json.get("needs_clarification")
                ?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            nextQuestion = json.string("next_question")
        )
    }

    fun contextToJson(context: CustomerContext): String = JsonObject().apply {
        addProperty("assistance_type", context.assistanceType?.name)
        addProperty("category", context.category?.name)
        addProperty("product_type", context.productType?.name)
        addProperty("exact_product_query", context.exactProductQuery)
        addProperty("space", context.space)
        addProperty("project", context.project)
        addProperty("style", context.style)
        addProperty("color", context.color)
        addProperty("budget_level", context.budgetLevel)
        addProperty("max_price", context.maxPrice?.toLong())
        add("technical_needs", JsonArray().apply { context.technicalNeeds.forEach(::add) })
    }.toString()

    // Esquema de salida estructurada de Gemini (subconjunto OpenAPI que acepta generateContent).
    fun responseSchema(): JsonObject {
        fun stringField(nullable: Boolean = true, values: List<String>? = null) = JsonObject().apply {
            addProperty("type", "STRING")
            if (nullable) addProperty("nullable", true)
            values?.let { add("enum", JsonArray().apply { it.forEach(::add) }) }
        }
        val properties = JsonObject().apply {
            add("assistance_type", stringField(nullable = false, values = ASSISTANCE_TYPES))
            add("category", stringField(values = CATEGORIES))
            add("product_type", stringField(values = PRODUCT_TYPES))
            add("exact_product_query", stringField())
            add("space", stringField())
            add("project", stringField())
            add("style", stringField())
            add("color", stringField())
            add("budget_level", stringField(values = BUDGET_LEVELS))
            add("max_price", JsonObject().apply {
                addProperty("type", "INTEGER")
                addProperty("nullable", true)
            })
            add("technical_needs", JsonObject().apply {
                addProperty("type", "ARRAY")
                add("items", JsonObject().apply { addProperty("type", "STRING") })
            })
            add("needs_clarification", JsonObject().apply { addProperty("type", "BOOLEAN") })
            add("next_question", stringField())
        }
        return JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", properties)
            add("required", JsonArray().apply {
                add("assistance_type")
                add("needs_clarification")
            })
        }
    }

    private fun JsonObject.string(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return element.asString.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun JsonObject.stringList(key: String): List<String> {
        val element = get(key) ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            item.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
