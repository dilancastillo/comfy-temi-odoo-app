// arma la frase con la que temi resume la necesidad que esta confirmando, sin mezclarla con otras
package com.example.comfyapp.domain.usecase

import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.CustomerNeed
import com.example.comfyapp.domain.model.PriceFormatter
import com.example.comfyapp.domain.model.ProductCategory

class DescribeCustomerContextUseCase {

    // alsoNoted: otros productos que el cliente pidió en la misma frase; se mencionan para que sepa
    // que quedaron anotados, pero la confirmación es solo de la necesidad activa.
    operator fun invoke(
        need: CustomerNeed?,
        action: AssistanceAction,
        alsoNoted: List<CustomerNeed> = emptyList()
    ): String {
        val main = when (action) {
            is AssistanceAction.NavigateToCategory ->
                "Entendí que quieres que te lleve a ${action.destination.displayName}."
            is AssistanceAction.SearchExactProduct -> {
                val where = action.destination?.let { " en ${it.displayName}" }.orEmpty()
                "Entendí que buscas ${action.query}$where."
            }
            else -> "Entendí que buscas ${describeNeed(need)}."
        }
        val noted = alsoNoted.takeIf { it.isNotEmpty() }
            ?.joinToString(" y ") { label(it) }
            ?.let { " También anoté $it para revisarlo después." }
            .orEmpty()
        return "$main$noted ¿Es correcto?"
    }

    // Hay varias necesidades guardadas que encajan: se pregunta por la que distingue a cada una.
    fun askWhich(options: List<CustomerNeed>): String {
        val labels = options.map(::label)
        val distinct = if (labels.distinct().size == labels.size) {
            labels
        } else {
            labels.mapIndexed { index, text -> "$text (${ORDINALS.getOrElse(index) { "otro" }})" }
        }
        val list = if (distinct.size == 2) {
            "${distinct[0]} o a ${distinct[1]}"
        } else {
            distinct.dropLast(1).joinToString(", ") + " o a " + distinct.last()
        }
        return "Tengo anotados varios. ¿Te refieres a $list?"
    }

    // Nombre corto de una necesidad: tipo o categoría, más espacio y color si los tiene.
    private fun label(need: CustomerNeed): String = buildString {
        append(need.productType?.displayName ?: categoryName(need.category))
        need.space?.let { append(" para ").append(it) }
        need.color?.let { append(" ").append(it) }
    }

    private fun describeNeed(need: CustomerNeed?): String {
        val context = need ?: return "productos"
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

    private companion object {
        val ORDINALS = listOf("el primero", "el segundo", "el tercero", "el cuarto")
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
