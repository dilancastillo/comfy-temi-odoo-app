// decide de forma deterministica que flujo sigue segun la necesidad activa del cliente, validando lo minimo necesario
package com.example.comfyapp.domain.usecase

import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CatalogDestination
import com.example.comfyapp.domain.model.CustomerNeed

class RouteAssistanceUseCase {

    // need: la necesidad activa (o el borrador que se está confirmando).
    // turnType: lo que pidió el cliente en esta frase; asesor, "solo miro" o algo que no tenemos
    // se atienden aunque haya una necesidad guardada.
    // nextQuestion es la pregunta sugerida por Gemini; solo se usa cuando el propio router no
    // tiene una pregunta más precisa para el dato que falta.
    operator fun invoke(
        need: CustomerNeed?,
        turnType: AssistanceType,
        nextQuestion: String? = null
    ): AssistanceAction {
        val suggested = nextQuestion?.trim()?.takeIf { it.isNotEmpty() }
        when (turnType) {
            AssistanceType.HUMAN_ADVISOR -> return AssistanceAction.RequestAdvisor
            AssistanceType.JUST_BROWSING -> return AssistanceAction.JustBrowsing
            AssistanceType.UNSUPPORTED -> return AssistanceAction.Unsupported
            else -> Unit
        }
        val context = need ?: return AssistanceAction.AskClarification(suggested ?: DEFAULT_CLARIFICATION)
        val destination = CatalogDestination.from(context.category, context.space)
        val query = context.exactProductQuery?.trim()?.takeIf { it.isNotEmpty() }
        // Si el cliente nombró un producto concreto, se busca ese nombre aunque Gemini lo haya
        // clasificado como categoría, proyecto o necesidad técnica ("monocontrol Koral barato").
        if (query != null && context.assistanceType in BROWSE_TYPES) {
            return AssistanceAction.SearchExactProduct(query, destination)
        }
        return when (context.assistanceType) {
            AssistanceType.HUMAN_ADVISOR -> AssistanceAction.RequestAdvisor

            AssistanceType.EXACT_PRODUCT -> {
                val query = context.exactProductQuery?.trim()
                if (query.isNullOrEmpty()) {
                    AssistanceAction.AskClarification(suggested ?: ASK_PRODUCT_NAME)
                } else {
                    AssistanceAction.SearchExactProduct(query, destination)
                }
            }

            AssistanceType.LOCATION_ONLY -> when (destination) {
                null -> AssistanceAction.AskClarification(suggested ?: ASK_WHICH_ZONE)
                CatalogDestination.FLOOR_ANY -> AssistanceAction.AskClarification(ASK_WHICH_FLOOR_ZONE)
                else -> AssistanceAction.NavigateToCategory(destination)
            }

            // Pisos sin espacio abre el menú de ambientes: ahí el cliente elige tocando.
            // Si ya dijo el espacio ("para la cocina"), la pregunta lo retoma para que no sienta
            // que Temi lo olvidó.
            AssistanceType.CATEGORY_BROWSE -> destination
                ?.let(AssistanceAction::StartCategoryFlow)
                ?: AssistanceAction.AskClarification(
                    if (context.space != null) askCategoryFor(context.space) else suggested ?: ASK_CATEGORY
                )

            // Proyecto y necesidad técnica siguen conversando hasta tener lo que cambia la
            // siguiente decisión; lo que el cliente ya dijo no se le vuelve a preguntar.
            AssistanceType.PROJECT_ASSISTANCE,
            AssistanceType.TECHNICAL_NEED -> when (destination) {
                null -> AssistanceAction.AskClarification(askCategoryFor(context.space))
                CatalogDestination.FLOOR_ANY -> AssistanceAction.AskClarification(ASK_FLOOR_SPACE)
                else -> AssistanceAction.StartCategoryFlow(destination)
            }

            AssistanceType.JUST_BROWSING -> AssistanceAction.JustBrowsing

            AssistanceType.CLARIFICATION, null ->
                AssistanceAction.AskClarification(suggested ?: DEFAULT_CLARIFICATION)

            AssistanceType.UNSUPPORTED -> AssistanceAction.Unsupported
        }
    }

    private fun askCategoryFor(space: String?): String {
        val target = space?.trim()?.takeIf { it.isNotEmpty() }?.let { "para $it" } ?: "para tu proyecto"
        return "Perfecto. ¿Qué estás buscando $target: pisos y paredes, sanitario o grifería?"
    }

    companion object {
        private val BROWSE_TYPES = setOf(
            AssistanceType.CATEGORY_BROWSE,
            AssistanceType.PROJECT_ASSISTANCE,
            AssistanceType.TECHNICAL_NEED
        )
        const val ASK_PRODUCT_NAME = "¿Cuál es el nombre o la referencia del producto que buscas?"
        const val ASK_WHICH_ZONE = "Claro. ¿A qué zona te llevo: sanitarios, griferías o pisos y paredes?"
        const val ASK_WHICH_FLOOR_ZONE =
            "Tenemos pisos en varias zonas. ¿Te llevo a los de baños y cocinas, zona social o exteriores?"
        const val ASK_CATEGORY = "¿Qué estás buscando: sanitarios, griferías o pisos y paredes?"
        const val ASK_FLOOR_SPACE = "¿Para qué espacio es el piso: baño o cocina, zona social o exterior?"
        const val DEFAULT_CLARIFICATION = "Claro. ¿Qué espacio estás buscando renovar?"
    }
}
