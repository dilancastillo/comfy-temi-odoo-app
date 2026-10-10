package com.example.comfyapp.assistance

import com.example.comfyapp.domain.model.ProductFilters
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.ui.products.tiles.TileCategory
import com.example.comfyapp.ui.products.tiles.TilesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProductFiltersTest {

    private val exteriors = TilesViewModel.requestFor(TileCategory.EXTERIORS)

    @Test
    fun `color and type are applied to a tile request`() {
        val request = ProductFilters(color = "gris", productType = ProductType.PISOS).applyTo(exteriors)

        assertEquals("gris", request.color)
        assertEquals(ProductType.PISOS.column, request.singleColumn)
        assertEquals("Pisos y Paredes para Exteriores de color gris", request.title)
    }

    @Test
    fun `without limits keeps the type but drops color and price`() {
        val filters = ProductFilters(maxPrice = 100_000.0, color = "beige", productType = ProductType.PISOS)
        val plain = filters.withoutLimits()

        assertFalse(plain.hasLimits)
        val request = plain.applyTo(exteriors)
        assertNull(request.color)
        assertNull(request.maxPrice)
        assertEquals(ProductType.PISOS.column, request.singleColumn)
    }
}
