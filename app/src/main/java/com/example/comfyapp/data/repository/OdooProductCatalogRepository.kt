// adapta las consultas heredadas de odoo al contrato del catalogo de productos
package com.example.comfyapp.data.repository

import com.example.comfyapp.data.model.ModelProductStock
import com.example.comfyapp.domain.model.CatalogProduct
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.repository.ProductCatalogRepository

class OdooProductCatalogRepository : ProductCatalogRepository {

    override fun load(
        request: ProductListRequest,
        onSuccess: (List<CatalogProduct>, List<CatalogProduct>) -> Unit,
        onError: (String) -> Unit
    ) {
        var first: List<CatalogProduct>? = null
        var second: List<CatalogProduct>? = null
        var failed = false

        fun completeIfReady() {
            val firstResult = first
            val secondResult = second
            if (!failed && firstResult != null && secondResult != null) {
                onSuccess(firstResult, secondResult)
            }
        }

        fun handleError(message: String) {
            if (!failed) {
                failed = true
                onError(message)
            }
        }

        val firstSuccess: (List<ModelProductStock>) -> Unit = {
            first = it.map { model -> model.toDomain() }
            completeIfReady()
        }
        val secondSuccess: (List<ModelProductStock>) -> Unit = {
            second = it.map { model -> model.toDomain() }
            completeIfReady()
        }

        when (request.category) {
            ProductCategory.FLOOR_AND_WALL -> {
                ProductRepository.getProductsAllWall(
                    locationName = DEFAULT_LOCATION,
                    usedInId = request.usedInIds,
                    onSuccess = firstSuccess,
                    onError = ::handleError
                )
                ProductRepository.getProductsAllFloor(
                    locationName = DEFAULT_LOCATION,
                    usedInId = request.usedInIds,
                    onSuccess = secondSuccess,
                    onError = ::handleError
                )
            }
            ProductCategory.TAPS -> {
                ProductRepository.getProductsTapsLavaM(
                    locationName = DEFAULT_LOCATION,
                    onSuccess = firstSuccess,
                    onError = ::handleError
                )
                ProductRepository.getProductsTapsLavaP(
                    locationName = DEFAULT_LOCATION,
                    onSuccess = secondSuccess,
                    onError = ::handleError
                )
            }
            ProductCategory.SANITARY -> {
                ProductRepository.getProductsSanitaryCombo(
                    locationName = DEFAULT_LOCATION,
                    onSuccess = firstSuccess,
                    onError = ::handleError
                )
                ProductRepository.getProductsSanitaryOnly(
                    locationName = DEFAULT_LOCATION,
                    onSuccess = secondSuccess,
                    onError = ::handleError
                )
            }
        }
    }

    private fun ModelProductStock.toDomain() = CatalogProduct(
        id = id,
        name = name,
        price = price,
        stock = free_qty,
        imageUrl = imageUrl,
        description = description,
        websiteUrl = website_url
    )

    private companion object {
        const val DEFAULT_LOCATION = "Tunja/E"
    }
}
