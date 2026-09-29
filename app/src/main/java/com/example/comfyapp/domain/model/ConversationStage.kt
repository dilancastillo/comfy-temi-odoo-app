// etapa de la conversacion con el cliente para que voz deteccion y navegacion no se crucen
package com.example.comfyapp.domain.model

enum class ConversationStage {
    IDLE,
    LISTENING,
    ANALYZING,
    CONFIRMING,
    ROUTING,
    ASKING_DETAIL,
    EXECUTING,
    WAITING_ADVISOR
}
