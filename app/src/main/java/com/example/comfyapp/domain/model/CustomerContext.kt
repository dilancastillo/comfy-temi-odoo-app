// la sesion comercial del cliente: proyecto, sus necesidades por separado y cual se esta atendiendo
package com.example.comfyapp.domain.model

data class CustomerContext(
    // proyecto y datos que el cliente declaró para toda la visita ("estoy remodelando")
    val project: String? = null,
    val needs: List<CustomerNeed> = emptyList(),
    // la necesidad que se está escuchando, mostrando o modificando
    val activeNeedId: String? = null,
    // datos operativos de la sesión, no de un producto
    val currentLocation: String? = null,
    val advisorRequested: Boolean = false
) {
    val isEmpty: Boolean get() = this == CustomerContext()

    val activeNeed: CustomerNeed? get() = needs.firstOrNull { it.id == activeNeedId }

    fun need(id: String): CustomerNeed? = needs.firstOrNull { it.id == id }

    // Reemplaza la necesidad con ese id (o la agrega) y la deja como activa.
    fun withActiveNeed(need: CustomerNeed): CustomerContext {
        val updated = if (needs.any { it.id == need.id }) {
            needs.map { if (it.id == need.id) need else it }
        } else {
            needs + need
        }
        return copy(needs = updated, activeNeedId = need.id)
    }
}

// Lo que Temi entendió y todavía no se guardó: se confirma, se corrige o se descarta sin tocar
// las necesidades ya aceptadas.
data class PendingNeedChange(
    val operation: NeedOperation,
    // contexto aceptado + la necesidad propuesta como activa; sobre esto decide el router
    val candidate: CustomerContext,
    // la necesidad propuesta (null si el turno no habló de ningún producto)
    val need: CustomerNeed?,
    // versión aceptada de esa misma necesidad (null si es nueva)
    val accepted: CustomerNeed?,
    // qué cambió en este turno respecto de lo que había antes de oírlo
    val changedFields: Set<String>,
    // otros productos pedidos en la misma frase, guardados aparte para revisarlos después
    val additionalNeeds: List<CustomerNeed> = emptyList(),
    // el cliente se refirió a una necesidad guardada pero hay varias posibles: hay que preguntar cuál
    val ambiguousNeeds: List<CustomerNeed> = emptyList(),
    // pidió retomar algo que no está guardado en esta visita
    val resumeNotFound: Boolean = false
) {
    // Ambigüedad o referencia inexistente: no se guardó nada, primero hay que aclarar.
    val needsDisambiguation: Boolean get() = ambiguousNeeds.isNotEmpty() || resumeNotFound

    // Una necesidad nueva, nunca confirmada o con un cambio importante se confirma antes de actuar.
    val requiresConfirmation: Boolean
        get() {
            // Sin datos nuevos del producto se actúa sobre la activa: basta con que ya esté confirmada.
            val proposed = need ?: return candidate.activeNeed?.isConfirmed == false
            val previous = accepted ?: return true
            return !previous.isConfirmed || proposed.isSignificantChangeFrom(previous)
        }
}
