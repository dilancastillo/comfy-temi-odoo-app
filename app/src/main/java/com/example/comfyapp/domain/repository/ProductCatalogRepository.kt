// define el contrato para obtener las dos columnas del catalogo de productos
package com.example.comfyapp.domain.repository

import com.example.comfyapp.domain.model.CatalogProduct
import com.example.comfyapp.domain.model.ProductListRequest

interface ProductCatalogRepository {
    fun load(
        request: ProductListRequest,
        onSuccess: (firstColumn: List<CatalogProduct>, secondColumn: List<CatalogProduct>) -> Unit,
        onError: (String) -> Unit
    )
}
