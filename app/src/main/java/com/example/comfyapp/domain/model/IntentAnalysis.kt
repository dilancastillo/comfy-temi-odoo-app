// resultado estructurado de lo que gemini entendio de una frase del cliente
package com.example.comfyapp.domain.model

data class IntentAnalysis(
    val assistanceType: AssistanceType,
    // operación de memoria que propone Gemini; el código la valida antes de aplicarla
    val needOperation: NeedOperation? = null,
    // necesidad guardada a la que se refiere ("volvamos al sanitario"); debe existir en la sesión
    val targetNeedId: String? = null,
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
    // requisitos que se agregan
    val technicalNeeds: List<String> = emptyList(),
    // requisitos que el cliente pidió quitar ("ya no necesito que sea ahorrador")
    val technicalNeedsRemove: List<String> = emptyList(),
    // campos que el cliente pidió borrar ("sin límite de precio"); TECHNICAL_NEEDS vacía la lista
    val clearFields: Set<NeedField> = emptySet(),
    // otros productos pedidos en la misma frase ("un sanitario y una grifería"): cada uno es otra necesidad
    val additionalNeeds: List<RequestedNeed> = emptyList(),
    val needsClarification: Boolean = false,
    val nextQuestion: String? = null
)

// Un producto adicional de la misma frase, con sus propios atributos.
data class RequestedNeed(
    val category: ProductCategory,
    val productType: ProductType? = null,
    val space: String? = null,
    val color: String? = null,
    val maxPrice: Double? = null,
    val technicalNeeds: List<String> = emptyList()
) {
    fun asAnalysis(): IntentAnalysis = IntentAnalysis(
        assistanceType = AssistanceType.CATEGORY_BROWSE,
        category = category,
        productType = productType,
        space = space,
        color = color,
        maxPrice = maxPrice,
        technicalNeeds = technicalNeeds
    )
}
