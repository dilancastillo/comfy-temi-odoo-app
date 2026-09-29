// resultado estructurado de lo que gemini entendio de una frase del cliente
package com.example.comfyapp.domain.model

data class IntentAnalysis(
    val assistanceType: AssistanceType,
    // solo SANITARY, TAPS o FLOOR_AND_WALL: las subcategorias de pisos las resuelve el router con space
    val category: ProductCategory? = null,
    val productType: ProductType? = null,
    val exactProductQuery: String? = null,
    val space: String? = null,
    val project: String? = null,
    val style: String? = null,
    val color: String? = null,
    val budgetLevel: String? = null,
    // tope en pesos cuando el cliente lo dice ("menos de 100 mil" -> 100000)
    val maxPrice: Double? = null,
    val technicalNeeds: List<String> = emptyList(),
    val needsClarification: Boolean = false,
    val nextQuestion: String? = null
)
