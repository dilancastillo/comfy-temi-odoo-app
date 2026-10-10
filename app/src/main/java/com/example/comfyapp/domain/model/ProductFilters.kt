// precio maximo, color y tipo de producto que pidio el cliente; acotan la lista de productos en odoo
package com.example.comfyapp.domain.model

data class ProductFilters(
    val maxPrice: Double? = null,
    val color: String? = null,
    // "pisos", "combos": se muestra solo esa columna; no es un límite, nunca se quita al no haber resultados
    val productType: ProductType? = null
) {

    // Precio o color: pueden dejar la lista vacía, por eso se consultan antes de abrirla.
    val hasLimits: Boolean get() = maxPrice != null || !color.isNullOrBlank()

    val isEmpty: Boolean get() = !hasLimits && productType == null

    fun withoutLimits(): ProductFilters = ProductFilters(productType = productType)

    // " de menos de $100.000 de color negro": se agrega a títulos y a lo que dice Temi.
    fun suffix(): String = buildString {
        maxPrice?.let { append(" de menos de ").append(PriceFormatter.format(it)) }
        color?.takeIf { it.isNotBlank() }?.let { append(" de color ").append(it) }
    }

    fun applyTo(request: ProductListRequest): ProductListRequest {
        val type = productType
        val typed = if (type == null || request.category !in type.listCategories) {
            request
        } else {
            request.copy(singleColumn = type.column, firstColumnTitle = type.columnTitle, secondColumnTitle = "")
        }
        return if (!hasLimits) typed
        else typed.copy(maxPrice = maxPrice, color = color, title = "${typed.title}${suffix()}")
    }
}
