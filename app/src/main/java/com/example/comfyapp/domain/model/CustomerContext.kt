// lo que la app sabe del cliente durante la sesion comercial y como se enriquece turno a turno
package com.example.comfyapp.domain.model

data class CustomerContext(
    val assistanceType: AssistanceType? = null,
    val category: ProductCategory? = null,
    val productType: ProductType? = null,
    val exactProductQuery: String? = null,
    val project: String? = null,
    val space: String? = null,
    val style: String? = null,
    val color: String? = null,
    val budgetLevel: String? = null,
    val maxPrice: Double? = null,
    val technicalNeeds: Set<String> = emptySet(),
    val selectedProductIds: List<Int> = emptyList(),
    val currentLocation: String? = null,
    val advisorRequested: Boolean = false
) {
    val isEmpty: Boolean get() = this == CustomerContext()

    // Cada respuesta enriquece la sesión: un dato nuevo reemplaza solo su propio campo y lo
    // que el cliente ya había dicho se conserva, así no tiene que repetirlo.
    fun mergeWith(analysis: IntentAnalysis): CustomerContext {
        val newCategory = analysis.category ?: category
        val categoryChanged = category != null && newCategory != category
        return copy(
            // Una frase que solo aclara o agrega un dato ("mejor en blanco") no borra el tipo de
            // ayuda que ya se había identificado.
            assistanceType = if (analysis.assistanceType == AssistanceType.CLARIFICATION && assistanceType != null) {
                assistanceType
            } else {
                analysis.assistanceType
            },
            category = newCategory,
            // "combo" o "lavaplatos" solo tienen sentido dentro de su propia categoría.
            productType = (analysis.productType ?: productType.takeUnless { categoryChanged })
                ?.takeIf { newCategory == null || it.category == newCategory },
            // La referencia buscada pertenece a la categoría anterior; si cambia la categoría deja de aplicar.
            exactProductQuery = analysis.exactProductQuery.clean()
                ?: exactProductQuery.takeUnless { categoryChanged },
            project = analysis.project.clean() ?: project,
            space = analysis.space.clean() ?: space,
            style = analysis.style.clean() ?: style,
            color = analysis.color.clean() ?: color,
            budgetLevel = analysis.budgetLevel.clean() ?: budgetLevel,
            maxPrice = analysis.maxPrice ?: maxPrice,
            technicalNeeds = technicalNeeds + analysis.technicalNeeds.mapNotNull { it.clean() },
            advisorRequested = advisorRequested || analysis.assistanceType == AssistanceType.HUMAN_ADVISOR
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
