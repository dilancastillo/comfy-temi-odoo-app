// agrupa la configuracion necesaria para abrir y cargar una lista de productos
package com.example.comfyapp.domain.model

import java.io.Serializable

data class ProductListRequest(
    val category: ProductCategory,
    val title: String,
    val firstColumnTitle: String,
    val secondColumnTitle: String,
    val robotLocation: String,
    val video: ProductVideo,
    val usedInIds: List<Int> = emptyList(),
    val pageSize: Int = 8,
    val showTravelVideo: Boolean = true,
    // orden para cada columna: "free_qty desc" | "free_qty asc"
    val firstColumnOrder: String = "free_qty desc",
    val secondColumnOrder: String = "free_qty desc",
    // límite total de productos por columna (0 = sin límite)
    val maxProductsPerColumn: Int = 0
) : Serializable

enum class ProductVideo : Serializable {
    SANITARY,
    TAPS,
    FLOOR_AND_WALL
}
