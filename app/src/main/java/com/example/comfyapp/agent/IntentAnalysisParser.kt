// valida la respuesta json de gemini y la convierte en un analisis de intencion confiable
package com.example.comfyapp.agent

import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CustomerNeed
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.model.NeedField
import com.example.comfyapp.domain.model.NeedOperation
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.domain.model.RequestedNeed
import com.example.comfyapp.domain.repository.AnalysisRequest
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object IntentAnalysisParser {

    val ASSISTANCE_TYPES = AssistanceType.entries.map { it.name }
    val CATEGORIES = listOf("SANITARY", "TAPS", "FLOOR_AND_WALL")
    val PRODUCT_TYPES = ProductType.entries.map { it.name }
    val NEED_OPERATIONS = NeedOperation.entries.map { it.name }
    val CLEARABLE_FIELDS = NeedField.entries.map { it.wire }
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
            needOperation = NeedOperation.fromWire(json.string("need_operation")),
            targetNeedId = json.string("target_need_id"),
            category = category(json.string("category")),
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
            technicalNeedsRemove = json.stringList("technical_needs_remove"),
            clearFields = json.stringList("clear_fields").mapNotNull(NeedField::fromWire).toSet(),
            additionalNeeds = additionalNeeds(json),
            needsClarification = json.get("needs_clarification")
                ?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            nextQuestion = json.string("next_question")
        )
    }

    // Memoria que se le envía a Gemini en cada turno: lo aceptado separado del borrador, para que
    // una interpretación aún sin confirmar nunca se trate como dato del cliente.
    fun memoryToJson(request: AnalysisRequest): String = JsonObject().apply {
        addProperty("mode", if (request.correcting) "CORRECTING" else "NORMAL")
        addProperty("project", request.accepted.project)
        add("active_need", request.accepted.activeNeed?.let(::needToJson))
        add("other_needs", JsonArray().apply {
            request.accepted.needs
                .filter { it.id != request.accepted.activeNeedId }
                .forEach { need ->
                    add(JsonObject().apply {
                        addProperty("id", need.id)
                        addProperty("category", need.category?.name)
                        addProperty("product_type", need.productType?.name)
                        addProperty("space", need.space)
                        addProperty("color", need.color)
                    })
                }
        })
        add("pending_draft", request.draft?.need?.let(::needToJson))
    }.toString()

    private fun needToJson(need: CustomerNeed): JsonObject = JsonObject().apply {
        addProperty("id", need.id)
        addProperty("assistance_type", need.assistanceType?.name)
        addProperty("category", need.category?.name)
        addProperty("product_type", need.productType?.name)
        addProperty("exact_product_query", need.exactProductQuery)
        addProperty("space", need.space)
        addProperty("style", need.style)
        addProperty("color", need.color)
        addProperty("budget_level", need.budgetLevel)
        addProperty("max_price", need.maxPrice?.toLong())
        add("technical_needs", JsonArray().apply { need.technicalNeeds.forEach(::add) })
    }

    // Esquema de salida estructurada de Gemini (subconjunto OpenAPI que acepta generateContent).
    fun responseSchema(): JsonObject {
        fun stringField(nullable: Boolean = true, values: List<String>? = null) = JsonObject().apply {
            addProperty("type", "STRING")
            if (nullable) addProperty("nullable", true)
            values?.let { add("enum", JsonArray().apply { it.forEach(::add) }) }
        }
        val properties = JsonObject().apply {
            add("assistance_type", stringField(nullable = false, values = ASSISTANCE_TYPES))
            add("need_operation", stringField(values = NEED_OPERATIONS))
            add("target_need_id", stringField())
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
            add("technical_needs_remove", JsonObject().apply {
                addProperty("type", "ARRAY")
                add("items", JsonObject().apply { addProperty("type", "STRING") })
            })
            add("clear_fields", JsonObject().apply {
                addProperty("type", "ARRAY")
                add("items", stringField(nullable = false, values = CLEARABLE_FIELDS))
            })
            add("additional_needs", JsonObject().apply {
                addProperty("type", "ARRAY")
                add("items", JsonObject().apply {
                    addProperty("type", "OBJECT")
                    add("properties", JsonObject().apply {
                        add("category", stringField(nullable = false, values = CATEGORIES))
                        add("product_type", stringField(values = PRODUCT_TYPES))
                        add("space", stringField())
                        add("color", stringField())
                        add("max_price", JsonObject().apply {
                            addProperty("type", "INTEGER")
                            addProperty("nullable", true)
                        })
                        add("technical_needs", JsonObject().apply {
                            addProperty("type", "ARRAY")
                            add("items", JsonObject().apply { addProperty("type", "STRING") })
                        })
                    })
                    add("required", JsonArray().apply { add("category") })
                })
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

    private fun category(value: String?): ProductCategory? = when (value?.uppercase()) {
        "SANITARY" -> ProductCategory.SANITARY
        "TAPS" -> ProductCategory.TAPS
        "FLOOR_AND_WALL" -> ProductCategory.FLOOR_AND_WALL
        else -> null
    }

    private fun JsonObject.price(key: String): Double? = get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asDouble
        ?.takeIf { it > 0 }

    // Un producto adicional sin categoría válida no se puede guardar: se descarta.
    private fun additionalNeeds(json: JsonObject): List<RequestedNeed> {
        val element = json.get("additional_needs") ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val itemCategory = category(obj.string("category")) ?: return@mapNotNull null
            RequestedNeed(
                category = itemCategory,
                productType = ProductType.fromWire(obj.string("product_type")),
                space = obj.string("space"),
                color = obj.string("color"),
                maxPrice = obj.price("max_price"),
                technicalNeeds = obj.stringList("technical_needs")
            )
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
