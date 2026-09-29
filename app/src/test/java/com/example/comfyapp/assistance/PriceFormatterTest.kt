// verifica el formato de pesos colombianos del catalogo
package com.example.comfyapp.assistance

import com.example.comfyapp.domain.model.PriceFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatterTest {

    @Test
    fun `prices use thousands dots and no decimals`() {
        assertEquals("$686.900", PriceFormatter.format(686900.0))
        assertEquals("$1.250.000", PriceFormatter.format(1_250_000.0))
        assertEquals("$0", PriceFormatter.format(0.0))
    }
}
