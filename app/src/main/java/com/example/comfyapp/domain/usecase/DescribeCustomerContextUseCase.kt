// arma la frase con la que temi resume lo que entendio antes de actuar
package com.example.comfyapp.domain.usecase

import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.PriceFormatter
import com.example.comfyapp.domain.model.ProductCategory

class DescribeCustomerContextUseCase {

    operator fun invoke(context: CustomerContext, action: AssistanceAction): String = when (action) {
        is AssistanceAction.NavigateToCategory ->
            "Entendí que quieres que te lleve a ${action.destination.displayName}. ¿Es correcto?"
        is AssistanceAction.SearchExactProduct -> {
            val where = action.destination?.let { " en ${it.displayName}" }.orEmpty()
            "Entendí que buscas ${action.query}$where. ¿Es correcto?"
        }
        else -> "Entendí que buscas ${describeNeed(context)}. ¿Es correcto?"
    }

    private fun describeNeed(context: CustomerContext): String {
        val base = buildString {
            append(context.productType?.displayName ?: categoryName(context.category))
            context.space?.let { append(" para ").append(it) }
        }
        val details = buildList {
            context.style?.let { add("estilo $it") }
            context.color?.let { add("color $it") }
            // Gemini a veces repite el espacio o el tipo como necesidad técnica ("exterior", "paredes").
            val technical = context.technicalNeeds.filterNot { base.lowercase().contains(it.lowercase()) }
            if (technical.isNotEmpty()) add(technical.joinToString(" y "))
            // Una cifra concreta dice más que "de bajo precio".
            val maxPrice = context.maxPrice
            if (maxPrice != null) {
                add("de menos de ${PriceFormatter.format(maxPrice)}")
            } else {
                budgetPhrase(context.budgetLevel)?.let(::add)
            }
        }
        return (listOf(base) + details).joinToString(", ")
    }

    private fun categoryName(category: ProductCategory?) = when (category) {
        ProductCategory.SANITARY -> "sanitarios"
        ProductCategory.TAPS -> "griferías"
        null -> "productos"
        else -> "pisos y paredes"
    }

    private fun budgetPhrase(level: String?): String? = when (level?.lowercase()) {
        null -> null
        "economico" -> "de bajo precio"
        "economico_moderado" -> "a buen precio"
        "medio" -> "de precio medio"
        "alto" -> "de gama alta"
        else -> level
    }
}
