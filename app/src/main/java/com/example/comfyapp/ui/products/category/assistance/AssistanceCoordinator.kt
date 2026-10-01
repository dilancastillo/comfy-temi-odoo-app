// ejecuta la accion decidida por el enrutador: consultar odoo, abrir la zona, mover a temi o pedir asesor
package com.example.comfyapp.ui.products.category.assistance

import com.example.comfyapp.agent.AgentSpeechController
import com.example.comfyapp.data.repository.OdooProductCatalogRepository
import com.example.comfyapp.data.repository.ProductRepository
import com.example.comfyapp.data.repository.ProductSearchText
import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CatalogDestination
import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.PriceFormatter
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.domain.model.ProductVideo
import com.example.comfyapp.domain.repository.ProductCatalogRepository
import com.example.comfyapp.domain.repository.RobotRepository
import com.example.comfyapp.logging.AssistanceEventLog
import com.example.comfyapp.session.CustomerSessionManager
import com.example.comfyapp.ui.products.category.ProductsUserViewModel
import com.example.comfyapp.ui.products.tiles.TileCategory
import com.example.comfyapp.ui.products.tiles.TilesViewModel

class AssistanceCoordinator(
    private val robotRepository: RobotRepository,
    private val speech: AgentSpeechController,
    private val host: Host,
    private val catalog: ProductCatalogRepository = OdooProductCatalogRepository()
) {

    // Lo que depende de la Activity: abrir pantallas sin cortar lo que Temi está diciendo.
    interface Host {
        fun openProductList(request: ProductListRequest, announcement: String? = null)
        fun openTiles(category: TileCategory?)
    }

    // onFinished(null) = la acción terminó; onFinished(pregunta) = hay que volver a escuchar.
    fun execute(action: AssistanceAction, context: CustomerContext, onFinished: (String?) -> Unit) {
        when (action) {
            is AssistanceAction.SearchExactProduct -> searchExactProduct(action, context, onFinished)
            // Los filtros salen solo de la necesidad activa: el precio de un sanitario no se aplica a una grifería.
            is AssistanceAction.StartCategoryFlow -> {
                val need = context.activeNeed
                openDestination(action.destination, need?.maxPrice, need?.productType) { onFinished(null) }
            }
            is AssistanceAction.NavigateToCategory -> navigateOnly(action.destination, context, onFinished)
            AssistanceAction.RequestAdvisor -> {
                // Evento preparado para el módulo de transferencia: cuando exista, recibirá todas las
                // necesidades aceptadas, cada una con sus propios atributos.
                CustomerSessionManager.markAdvisorRequested()
                AssistanceEventLog.event(
                    "advisor_requested",
                    "project" to context.project,
                    "needs" to context.needs.size,
                    "active_need" to context.activeNeedId
                )
                context.needs.forEach { need ->
                    AssistanceEventLog.event("advisor_need", *AssistanceEventLog.needFields(need))
                }
                speech.speak(ADVISOR_TEXT) { onFinished(null) }
            }
            AssistanceAction.JustBrowsing -> speech.speak(JUST_BROWSING_TEXT) { onFinished(null) }
            AssistanceAction.Unsupported -> onFinished(UNSUPPORTED_QUESTION)
            is AssistanceAction.AskClarification -> onFinished(action.question)
        }
    }

    private fun searchExactProduct(
        action: AssistanceAction.SearchExactProduct,
        context: CustomerContext,
        onFinished: (String?) -> Unit
    ) {
        AssistanceEventLog.event("product_search_started", "query" to action.query)
        val maxPrice = context.activeNeed?.maxPrice
        ProductRepository.searchProductByText(
            query = action.query,
            limit = SEARCH_PAGE_SIZE * 2,
            maxPrice = maxPrice,
            onSuccess = { results ->
                AssistanceEventLog.event("product_search_finished", "results" to results.size)
                if (results.isNotEmpty()) {
                    val foundName = ProductSearchText.displayQuery(results.first().name, action.query)
                    host.openProductList(
                        searchRequest(action, foundName).withMaxPrice(maxPrice),
                        "Encontré estas opciones para $foundName${priceSuffix(maxPrice)}."
                    )
                    onFinished(null)
                    return@searchProductByText
                }
                // Sin coincidencias la referencia deja de servir: se sigue por la categoría si la hay.
                val destination = action.destination
                CustomerSessionManager.updateActiveNeed { need ->
                    need.copy(
                        exactProductQuery = null,
                        assistanceType = if (destination != null) AssistanceType.CATEGORY_BROWSE
                        else AssistanceType.CLARIFICATION
                    )
                }
                if (destination == null) {
                    onFinished("No encontré ${action.query}${priceSuffix(maxPrice)}. ¿Me dices qué tipo de producto es: sanitario, grifería o piso?")
                } else {
                    speech.speak("No encontré ${action.query}${priceSuffix(maxPrice)} en exhibición, pero te muestro lo que tenemos en ${destination.displayName}.") {
                        openDestination(destination, maxPrice) { onFinished(null) }
                    }
                }
            },
            onError = { message ->
                AssistanceEventLog.event("product_search_error", "message" to message)
                onFinished(CATALOG_ERROR_QUESTION)
            }
        )
    }

    // Solo ubicación: Temi acompaña y al llegar no abre el catálogo.
    private fun navigateOnly(
        destination: CatalogDestination,
        context: CustomerContext,
        onFinished: (String?) -> Unit
    ) {
        val location = requestFor(destination)?.robotLocation
        if (location == null) {
            onFinished(null)
            return
        }
        speech.speak("Claro, acompáñame.") {
            AssistanceEventLog.event("navigation_started", "location" to location, "mode" to "location_only")
            if (robotRepository.goToLocation(location, arrivalMessage = LOCATION_ARRIVAL_TEXT)) {
                CustomerSessionManager.setCurrentLocation(location)
            }
            onFinished(null)
        }
    }

    // Con tope de precio se consulta primero: si no hay nada bajo ese precio Temi lo dice y muestra
    // la zona completa, en vez de abrir una lista vacía. La lista luego reutiliza lo consultado.
    private fun openDestination(
        destination: CatalogDestination,
        maxPrice: Double?,
        productType: ProductType? = null,
        onOpened: () -> Unit
    ) {
        val base = requestFor(destination)
        val request = base?.withProductType(productType)
        // Sin tope ni tipo se abre como siempre (pisos pasan por la pantalla de ambientes).
        if (base == null || request == null || (maxPrice == null && request == base)) {
            openDestination(destination)
            onOpened()
            return
        }
        if (maxPrice == null) {
            openRequest(request)
            onOpened()
            return
        }
        val limited = request.withMaxPrice(maxPrice)
        catalog.load(limited, 0, onSuccess = { first, second ->
            AssistanceEventLog.event(
                "price_filter_checked",
                "location" to request.robotLocation,
                "max_price" to maxPrice.toLong(),
                "results" to first.size + second.size
            )
            if (first.isEmpty() && second.isEmpty()) {
                speech.speak(
                    "No tengo ${destination.displayName}${priceSuffix(maxPrice)} en exhibición, " +
                        "pero te muestro todas las opciones."
                ) {
                    openDestination(destination)
                    onOpened()
                }
            } else {
                openRequest(limited)
                onOpened()
            }
        }, onError = {
            // Si la consulta previa falla, se abre la zona sin tope y la lista maneja su propio error.
            openDestination(destination)
            onOpened()
        })
    }

    // Primero se muestran los productos donde está Temi; el cliente decide con "Ir a verlos" si
    // quiere que lo acompañe a la zona de exhibición.
    private fun openRequest(request: ProductListRequest) {
        AssistanceEventLog.event("catalog_shown", "location" to request.robotLocation)
        host.openProductList(request.copy(showTravelVideo = false), CATALOG_SHOWN_TEXT)
    }

    private fun openDestination(destination: CatalogDestination) {
        when (destination) {
            CatalogDestination.FLOOR_ANY -> host.openTiles(null)
            // con la zona de pisos ya clara se muestran sus productos directamente, igual que el resto
            CatalogDestination.FLOOR_BATHROOMS,
            CatalogDestination.FLOOR_SOCIAL,
            CatalogDestination.FLOOR_EXTERIORS,
            CatalogDestination.SANITARY,
            CatalogDestination.TAPS -> requestFor(destination)?.let(::openRequest)
        }
    }

    private fun requestFor(destination: CatalogDestination): ProductListRequest? = when (destination) {
        CatalogDestination.SANITARY -> ProductsUserViewModel.SANITARY_REQUEST
        CatalogDestination.TAPS -> ProductsUserViewModel.TAPS_REQUEST
        CatalogDestination.FLOOR_BATHROOMS -> TilesViewModel.requestFor(TileCategory.BATHROOMS)
        CatalogDestination.FLOOR_SOCIAL -> TilesViewModel.requestFor(TileCategory.SOCIAL_AREAS)
        CatalogDestination.FLOOR_EXTERIORS -> TilesViewModel.requestFor(TileCategory.EXTERIORS)
        CatalogDestination.FLOOR_ANY -> null
    }

    // Los resultados se muestran donde está Temi; si la categoría es clara, "Ir a verlos" lleva a su zona.
    // searchQuery conserva lo que se buscó (misma consulta y caché); el título muestra el nombre encontrado.
    private fun searchRequest(action: AssistanceAction.SearchExactProduct, foundName: String) = ProductListRequest(
        category = when (action.destination) {
            CatalogDestination.SANITARY -> ProductCategory.SANITARY
            CatalogDestination.TAPS -> ProductCategory.TAPS
            else -> ProductCategory.FLOOR_AND_WALL
        },
        title = "Resultados para \"$foundName\"",
        firstColumnTitle = "Opciones",
        secondColumnTitle = "Más opciones",
        robotLocation = action.destination?.let(::requestFor)?.robotLocation.orEmpty(),
        video = ProductVideo.FLOOR_AND_WALL,
        pageSize = SEARCH_PAGE_SIZE,
        showTravelVideo = false,
        searchQuery = action.query
    )

    // "Un combo" o "llave de lavaplatos": se muestra solo esa columna, repartida en las dos.
    private fun ProductListRequest.withProductType(type: ProductType?): ProductListRequest =
        if (type == null || category !in type.listCategories) {
            this
        } else {
            copy(singleColumn = type.column, firstColumnTitle = type.columnTitle, secondColumnTitle = "")
        }

    private fun ProductListRequest.withMaxPrice(maxPrice: Double?): ProductListRequest =
        if (maxPrice == null) this else copy(maxPrice = maxPrice, title = "$title${priceSuffix(maxPrice)}")

    private fun priceSuffix(maxPrice: Double?): String =
        maxPrice?.let { " de menos de ${PriceFormatter.format(it)}" }.orEmpty()

    companion object {
        private const val SEARCH_PAGE_SIZE = 10
        const val CATALOG_SHOWN_TEXT =
            "Aquí tienes algunas opciones. Si quieres verlas en exhibición, toca Ir a verlos y te acompaño."
        const val LOCATION_ARRIVAL_TEXT =
            "Ya llegamos. Si quieres, también puedo ayudarte a comparar algunas opciones."
        const val ADVISOR_TEXT =
            "Claro. Liliana te atenderá apenas esté disponible. Mientras tanto, puedes mirar los productos en mi pantalla."
        const val JUST_BROWSING_TEXT =
            "Claro, mira con calma. Si necesitas algo, toca Hablar con Temi en mi pantalla."
        const val UNSUPPORTED_QUESTION =
            "Eso todavía no lo tengo en el robot. Te puedo ayudar con sanitarios, griferías o pisos y paredes. ¿Qué te gustaría ver?"
        const val CATALOG_ERROR_QUESTION = "No pude cargar los productos, ¿quieres que te ayude una asesora?"
    }
}
