// define el contrato para interpretar lo que dijo el cliente sin decidir pantallas
package com.example.comfyapp.domain.repository

import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.model.PendingNeedChange

// Lo que Gemini debe saber del turno: lo aceptado, el borrador y si el cliente está corrigiendo.
data class AnalysisRequest(
    val text: String,
    val accepted: CustomerContext,
    val draft: PendingNeedChange?,
    // el cliente pulsó "Quiero corregir algo": su respuesta corrige el borrador, no lo aceptado
    val correcting: Boolean,
    // lo último que preguntó Temi, para entender respuestas cortas como "sí"
    val lastQuestion: String?
)

interface IntentAnalyzer {
    fun analyze(request: AnalysisRequest, callback: (Result<IntentAnalysis>) -> Unit)

    // Una interpretación rechazada no debe seguir en el historial como si fuera válida.
    fun forgetLastTurn()

    fun reset()
}
