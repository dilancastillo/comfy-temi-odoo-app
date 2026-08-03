// define el estado y los efectos utilizados por la pantalla del catalogo
package com.example.comfyapp.ui.products.list

import com.example.comfyapp.domain.model.CatalogProduct

data class ProductListUiState(
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val firstColumn: List<CatalogProduct> = emptyList(),
    val secondColumn: List<CatalogProduct> = emptyList(),
    val hasMore: Boolean = true
)

sealed interface ProductListEffect {
    data class ShowError(val message: String) : ProductListEffect
}
