// registra la atencion como eventos y estados, sin transcribir lo que dice el cliente
package com.example.comfyapp.logging

import com.example.comfyapp.domain.model.CustomerNeed

object AssistanceEventLog {
    const val TAG = "AssistanceEvent"

    // Ej: intent_classified type=PROJECT_ASSISTANCE category=SANITARY space=baño
    fun event(name: String, vararg fields: Pair<String, Any?>) {
        val details = fields
            .filter { it.second != null }
            .joinToString(" ") { (key, value) -> "$key=$value" }
        PersistentLog.i(TAG, if (details.isEmpty()) name else "$name $details")
    }

    // Solo los datos estructurados de una necesidad: tipo, categoría y atributos, nunca frases libres.
    fun needFields(need: CustomerNeed?): Array<Pair<String, Any?>> = if (need == null) emptyArray() else arrayOf(
        "need" to need.id,
        "rev" to need.revision,
        "type" to need.assistanceType,
        "category" to need.category,
        "product_type" to need.productType,
        "query" to need.exactProductQuery,
        "space" to need.space,
        "style" to need.style,
        "color" to need.color,
        "budget" to need.budgetLevel,
        "max_price" to need.maxPrice?.toLong(),
        "technical" to need.technicalNeeds.takeIf { it.isNotEmpty() }?.joinToString(",")
    )
}
