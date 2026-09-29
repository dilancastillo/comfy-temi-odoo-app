// define el contrato para interpretar lo que dijo el cliente sin decidir pantallas
package com.example.comfyapp.domain.repository

import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.IntentAnalysis

interface IntentAnalyzer {
    // lastQuestion es lo último que preguntó Temi, para entender respuestas cortas como "sí"
    fun analyze(
        text: String,
        context: CustomerContext,
        lastQuestion: String?,
        callback: (Result<IntentAnalysis>) -> Unit
    )

    fun reset()
}
