// accion que decide el enrutador; no ejecuta nada, solo describe lo que debe pasar despues
package com.example.comfyapp.domain.model

sealed interface AssistanceAction {
    data class SearchExactProduct(
        val query: String,
        val destination: CatalogDestination?
    ) : AssistanceAction

    // categoria, proyecto o necesidad tecnica con datos suficientes para mostrar la zona
    data class StartCategoryFlow(val destination: CatalogDestination) : AssistanceAction

    // solo ubicacion: acompañar sin abrir el catalogo
    data class NavigateToCategory(val destination: CatalogDestination) : AssistanceAction

    data object RequestAdvisor : AssistanceAction

    // falta un dato: se pregunta y se vuelve a escuchar sin abrir pantallas ni mover a Temi
    data class AskClarification(val question: String) : AssistanceAction

    data object JustBrowsing : AssistanceAction

    data object Unsupported : AssistanceAction
}
