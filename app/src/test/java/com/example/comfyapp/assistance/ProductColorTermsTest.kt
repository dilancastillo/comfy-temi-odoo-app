package com.example.comfyapp.assistance

import com.example.comfyapp.data.repository.ProductColorTerms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductColorTermsTest {

    @Test
    fun `known colors cover gender and store tones`() {
        assertEquals(listOf("negr", "black"), ProductColorTerms.terms("Negra"))
        assertTrue("grafito" in ProductColorTerms.terms("gris"))
        assertTrue("cafe" in ProductColorTerms.terms("café"))
        assertEquals(listOf("negr", "black"), ProductColorTerms.terms("negro mate"))
    }

    @Test
    fun `unknown colors are searched by their root`() {
        assertEquals(listOf("verde"), ProductColorTerms.terms("verde"))
        assertEquals(listOf("amarill"), ProductColorTerms.terms("amarilla"))
        assertEquals(listOf("azul"), ProductColorTerms.terms("azul"))
    }

    @Test
    fun `no color means no filter`() {
        assertTrue(ProductColorTerms.terms(null).isEmpty())
        assertTrue(ProductColorTerms.terms("  ").isEmpty())
    }
}
