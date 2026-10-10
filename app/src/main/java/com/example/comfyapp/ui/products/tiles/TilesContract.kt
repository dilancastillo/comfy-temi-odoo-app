// define las opciones acciones y efectos de la seleccion de ambientes
package com.example.comfyapp.ui.products.tiles

import com.example.comfyapp.domain.model.ProductFilters
import com.example.comfyapp.domain.model.ProductListRequest

enum class TileCategory { BATHROOMS, SOCIAL_AREAS, EXTERIORS }

sealed interface TilesAction {
    // filters: precio y color que pidió el cliente al hablar con Temi (vacío si llegó tocando)
    data class Select(
        val category: TileCategory,
        val filters: ProductFilters = ProductFilters()
    ) : TilesAction
    data object Back : TilesAction
    data object UserInteraction : TilesAction
}

sealed interface TilesEffect {
    data class OpenProductList(val request: ProductListRequest) : TilesEffect
    data object Close : TilesEffect
}
