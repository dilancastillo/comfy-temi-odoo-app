// fuente unica de los tipos de ayuda que el agente puede identificar en el cliente
package com.example.comfyapp.domain.model

enum class AssistanceType {
    EXACT_PRODUCT,       // "Busco el sanitario Acuacer"
    CATEGORY_BROWSE,     // "Necesito un sanitario"
    PROJECT_ASSISTANCE,  // "Estoy remodelando mi baño"
    TECHNICAL_NEED,      // "Necesito piso antideslizante para exterior"
    LOCATION_ONLY,       // "¿Dónde están las griferías?"
    JUST_BROWSING,       // "Solo estoy mirando"
    HUMAN_ADVISOR,       // "Quiero hablar con una asesora"
    CLARIFICATION,       // No hay información suficiente
    UNSUPPORTED;         // Solicitud que Comfy todavía no atiende

    companion object {
        fun fromWire(value: String?): AssistanceType? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}
