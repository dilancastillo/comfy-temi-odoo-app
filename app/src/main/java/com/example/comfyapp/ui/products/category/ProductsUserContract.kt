// define el estado las acciones y los efectos de la pantalla de categorias
package com.example.comfyapp.ui.products.category

import com.example.comfyapp.domain.model.ProductListRequest

data class ProductsUserUiState(
    val robotStatus: String = "Inicializando Temi"
)

sealed interface ProductsUserAction {
    data object SelectFloorAndWall : ProductsUserAction
    data object SelectSanitary : ProductsUserAction
    data object SelectTaps : ProductsUserAction
    data object Back : ProductsUserAction
    data object UserInteraction : ProductsUserAction
}

sealed interface ProductsUserEffect {
    data object OpenTiles : ProductsUserEffect
    data class OpenProductList(val request: ProductListRequest) : ProductsUserEffect
    data object Close : ProductsUserEffect
}
