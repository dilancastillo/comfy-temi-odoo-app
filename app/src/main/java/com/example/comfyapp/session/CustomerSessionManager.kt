// conserva en memoria lo que se sabe del cliente actual, separado del estado de navegacion del robot
package com.example.comfyapp.session

import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.IntentAnalysis

object CustomerSessionManager {

    data class MergeResult(
        val previous: CustomerContext,
        val current: CustomerContext,
        val changedFields: Set<String>
    ) {
        // Cambia algo que Temi ya había entendido (no solo un dato que se agrega): hay que
        // volver a confirmar antes de actuar.
        val isSignificantChange: Boolean
            get() = (previous.category != null && "category" in changedFields) ||
                (previous.space != null && "space" in changedFields) ||
                (previous.exactProductQuery != null && "exactProductQuery" in changedFields) ||
                routeFamily(previous.assistanceType) != routeFamily(current.assistanceType) &&
                routeFamily(previous.assistanceType) != null
    }

    // Pasar de categoría a proyecto o necesidad técnica termina en la misma ruta y no amerita
    // reconfirmar; pasar de "ver productos" a "solo llévame" o a una referencia exacta, sí.
    private fun routeFamily(type: AssistanceType?): String? = when (type) {
        AssistanceType.EXACT_PRODUCT -> "exact"
        AssistanceType.LOCATION_ONLY -> "location"
        AssistanceType.CATEGORY_BROWSE,
        AssistanceType.PROJECT_ASSISTANCE,
        AssistanceType.TECHNICAL_NEED -> "browse"
        else -> null
    }

    private var context = CustomerContext()
    private var confirmed = false
    private var lastActivityAtMs = 0L

    @Synchronized
    fun current(): CustomerContext = context

    @Synchronized
    fun hasActiveCustomer(): Boolean = !context.isEmpty

    @Synchronized
    fun isConfirmed(): Boolean = confirmed

    @Synchronized
    fun isIdleFor(timeoutMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        hasActiveCustomer() && nowMs - lastActivityAtMs >= timeoutMs

    @Synchronized
    fun update(newContext: CustomerContext, nowMs: Long = System.currentTimeMillis()) {
        context = newContext
        lastActivityAtMs = nowMs
    }

    @Synchronized
    fun merge(analysis: IntentAnalysis, nowMs: Long = System.currentTimeMillis()): MergeResult {
        val previous = context
        val merged = previous.mergeWith(analysis)
        val changed = changedFields(previous, merged)
        val result = MergeResult(previous, merged, changed)
        if (result.isSignificantChange) confirmed = false
        context = merged
        lastActivityAtMs = nowMs
        return result
    }

    @Synchronized
    fun markConfirmed(nowMs: Long = System.currentTimeMillis()) {
        confirmed = true
        lastActivityAtMs = nowMs
    }

    @Synchronized
    fun touch(nowMs: Long = System.currentTimeMillis()) {
        lastActivityAtMs = nowMs
    }

    @Synchronized
    fun reset() {
        context = CustomerContext()
        confirmed = false
        lastActivityAtMs = 0L
    }

    private fun changedFields(old: CustomerContext, new: CustomerContext): Set<String> = buildSet {
        if (old.assistanceType != new.assistanceType) add("assistanceType")
        if (old.category != new.category) add("category")
        if (old.productType != new.productType) add("productType")
        if (old.exactProductQuery != new.exactProductQuery) add("exactProductQuery")
        if (old.project != new.project) add("project")
        if (old.space != new.space) add("space")
        if (old.style != new.style) add("style")
        if (old.color != new.color) add("color")
        if (old.budgetLevel != new.budgetLevel) add("budgetLevel")
        if (old.maxPrice != new.maxPrice) add("maxPrice")
        if (old.technicalNeeds != new.technicalNeeds) add("technicalNeeds")
    }
}
