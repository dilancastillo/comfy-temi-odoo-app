// adapta las consultas heredadas de odoo al contrato del catalogo de productos
package com.example.comfyapp.data.repository

import com.example.comfyapp.OdooHelper
import com.example.comfyapp.data.model.ModelProductStock
import com.example.comfyapp.domain.model.CatalogProduct
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.repository.ProductCatalogRepository
import com.example.comfyapp.logging.PersistentLog as Log

class OdooProductCatalogRepository : ProductCatalogRepository {

    override fun load(
        request: ProductListRequest,
        offset: Int,
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
                Log.e(OdooHelper.ODOO_TIMING_TAG, "odoo_catalog_error category=${request.category} $message")
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

        request.searchQuery?.let { query ->
            // Una sola consulta repartida en las dos columnas; se pide el doble para que cada
            // columna reciba una página completa y la paginación del ViewModel siga igual.
            ProductRepository.searchProductByText(
                query = query,
                onSuccess = { results ->
                    val products = results.map { model -> model.toDomain() }
                    onSuccess(
                        products.filterIndexed { index, _ -> index % 2 == 0 },
                        products.filterIndexed { index, _ -> index % 2 == 1 }
                    )
                },
                onError = ::handleError,
                offset = offset * 2,
                limit = request.pageSize * 2,
                maxPrice = request.maxPrice
            )
            return
        }

        request.singleColumn?.let { column ->
            // Se pide el doble de esa sola columna y se reparte en las dos, igual que la búsqueda.
            val split: (List<ModelProductStock>) -> Unit = { results ->
                val products = results.map { model -> model.toDomain() }
                onSuccess(
                    products.filterIndexed { index, _ -> index % 2 == 0 },
                    products.filterIndexed { index, _ -> index % 2 == 1 }
                )
            }
            val handled = loadSingleColumn(request, column, offset * 2, request.pageSize * 2, split, ::handleError)
            if (handled) return
        }

        when (request.category) {
            ProductCategory.FLOOR_AND_WALL -> {
                ProductRepository.getProductsAllWall(
                    locationName = DEFAULT_LOCATION,
                    usedInId = request.usedInIds,
                    onSuccess = firstSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize
                )
                ProductRepository.getProductsAllFloor(
                    locationName = DEFAULT_LOCATION,
                    usedInId = request.usedInIds,
                    onSuccess = secondSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize
                )
            }
            ProductCategory.FLOOR_AND_WALL_BATHROOMS -> {
                ProductRepository.getBathroomsWall(
                    maxPrice = request.maxPrice,
                    onSuccess = firstSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize,
                    order = request.firstColumnOrder
                )
                ProductRepository.getBathroomsFloorAndWall(
                    maxPrice = request.maxPrice,
                    onSuccess = secondSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize,
                    order = request.secondColumnOrder
                )
            }
            ProductCategory.FLOOR_AND_WALL_SOCIAL -> {
                ProductRepository.getSocialWall(
                    maxPrice = request.maxPrice,
                    onSuccess = firstSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize,
                    order = request.firstColumnOrder
                )
                ProductRepository.getSocialFloorAndWall(
                    maxPrice = request.maxPrice,
                    onSuccess = secondSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize,
                    order = request.secondColumnOrder
                )
            }
            ProductCategory.FLOOR_AND_WALL_EXTERIORS -> {
                ProductRepository.getExteriorsWall(
                    maxPrice = request.maxPrice,
                    onSuccess = firstSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize,
                    order = request.firstColumnOrder
                )
                ProductRepository.getExteriorsFloorAndWall(
                    maxPrice = request.maxPrice,
                    onSuccess = secondSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize,
                    order = request.secondColumnOrder
                )
            }
            ProductCategory.TAPS -> {
                ProductRepository.getProductsTapsLavaM(
                    maxPrice = request.maxPrice,
                    locationName = DEFAULT_LOCATION,
                    onSuccess = firstSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize
                )
                ProductRepository.getProductsTapsLavaP(
                    maxPrice = request.maxPrice,
                    locationName = DEFAULT_LOCATION,
                    onSuccess = secondSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize
                )
            }
            ProductCategory.SANITARY -> {
                ProductRepository.getProductsSanitaryCombo(
                    maxPrice = request.maxPrice,
                    locationName = DEFAULT_LOCATION,
                    onSuccess = firstSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize
                )
                ProductRepository.getProductsSanitaryOnly(
                    maxPrice = request.maxPrice,
                    locationName = DEFAULT_LOCATION,
                    onSuccess = secondSuccess,
                    onError = ::handleError,
                    offset = offset,
                    limit = request.pageSize
                )
            }
        }
    }

    // Cada lista tiene dos columnas por tipo (combos/solos, lavamanos/lavaplatos, paredes/pisos,
    // porcelanato/cerámica); FLOOR_AND_WALL general no tiene y sigue con sus dos columnas.
    private fun loadSingleColumn(
        request: ProductListRequest,
        column: Int,
        offset: Int,
        limit: Int,
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        when (request.category to column) {
            ProductCategory.SANITARY to 1 -> ProductRepository.getProductsSanitaryCombo(
                maxPrice = request.maxPrice, locationName = DEFAULT_LOCATION,
                onSuccess = onSuccess, onError = onError, offset = offset, limit = limit
            )
            ProductCategory.SANITARY to 2 -> ProductRepository.getProductsSanitaryOnly(
                maxPrice = request.maxPrice, locationName = DEFAULT_LOCATION,
                onSuccess = onSuccess, onError = onError, offset = offset, limit = limit
            )
            ProductCategory.TAPS to 1 -> ProductRepository.getProductsTapsLavaM(
                maxPrice = request.maxPrice, locationName = DEFAULT_LOCATION,
                onSuccess = onSuccess, onError = onError, offset = offset, limit = limit
            )
            ProductCategory.TAPS to 2 -> ProductRepository.getProductsTapsLavaP(
                maxPrice = request.maxPrice, locationName = DEFAULT_LOCATION,
                onSuccess = onSuccess, onError = onError, offset = offset, limit = limit
            )
            ProductCategory.FLOOR_AND_WALL_BATHROOMS to 1 -> ProductRepository.getBathroomsWall(
                maxPrice = request.maxPrice, onSuccess = onSuccess, onError = onError,
                offset = offset, limit = limit, order = request.firstColumnOrder
            )
            ProductCategory.FLOOR_AND_WALL_BATHROOMS to 2 -> ProductRepository.getBathroomsFloorAndWall(
                maxPrice = request.maxPrice, onSuccess = onSuccess, onError = onError,
                offset = offset, limit = limit, order = request.secondColumnOrder
            )
            ProductCategory.FLOOR_AND_WALL_SOCIAL to 1 -> ProductRepository.getSocialWall(
                maxPrice = request.maxPrice, onSuccess = onSuccess, onError = onError,
                offset = offset, limit = limit, order = request.firstColumnOrder
            )
            ProductCategory.FLOOR_AND_WALL_SOCIAL to 2 -> ProductRepository.getSocialFloorAndWall(
                maxPrice = request.maxPrice, onSuccess = onSuccess, onError = onError,
                offset = offset, limit = limit, order = request.secondColumnOrder
            )
            ProductCategory.FLOOR_AND_WALL_EXTERIORS to 1 -> ProductRepository.getExteriorsWall(
                maxPrice = request.maxPrice, onSuccess = onSuccess, onError = onError,
                offset = offset, limit = limit, order = request.firstColumnOrder
            )
            ProductCategory.FLOOR_AND_WALL_EXTERIORS to 2 -> ProductRepository.getExteriorsFloorAndWall(
                maxPrice = request.maxPrice, onSuccess = onSuccess, onError = onError,
                offset = offset, limit = limit, order = request.secondColumnOrder
            )
            else -> return false
        }
        return true
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
