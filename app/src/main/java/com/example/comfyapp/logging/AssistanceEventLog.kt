// registra la atencion como eventos y estados, sin transcribir lo que dice el cliente
package com.example.comfyapp.logging

import com.example.comfyapp.domain.model.CustomerContext

object AssistanceEventLog {
    const val TAG = "AssistanceEvent"

    // Ej: intent_classified type=PROJECT_ASSISTANCE category=SANITARY space=baño
    fun event(name: String, vararg fields: Pair<String, Any?>) {
        val details = fields
            .filter { it.second != null }
            .joinToString(" ") { (key, value) -> "$key=$value" }
        PersistentLog.i(TAG, if (details.isEmpty()) name else "$name $details")
    }

    // Solo los datos estructurados del contexto: tipo, categoría y atributos, nunca frases libres.
    fun contextFields(context: CustomerContext): Array<Pair<String, Any?>> = arrayOf(
        "type" to context.assistanceType,
        "category" to context.category,
        "product_type" to context.productType,
        "query" to context.exactProductQuery,
        "space" to context.space,
        "project" to context.project,
        "style" to context.style,
        "color" to context.color,
        "budget" to context.budgetLevel,
        "max_price" to context.maxPrice?.toLong(),
        "technical" to context.technicalNeeds.takeIf { it.isNotEmpty() }?.joinToString(",")
    )
}
