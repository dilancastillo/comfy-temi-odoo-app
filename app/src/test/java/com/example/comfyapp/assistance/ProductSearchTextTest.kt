// verifica que la busqueda tolere el dictado de marcas sin traer productos que no corresponden
package com.example.comfyapp.assistance

import com.example.comfyapp.data.repository.ProductSearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSearchTextTest {

    private fun matches(name: String, query: String) =
        ProductSearchText.matches(name, ProductSearchText.words(query))

    @Test
    fun `query keeps only meaningful words`() {
        assertEquals(listOf("sanitario", "montecarlo"), ProductSearchText.words("el sanitario de Montecarlo"))
    }

    @Test
    fun `doubtful letters become odoo wildcards`() {
        assertEquals("_oral", ProductSearchText.odooPattern("coral"))
        assertEquals("_roo_l_n", ProductSearchText.odooPattern("brooklin"))
    }

    @Test
    fun `sound alike spellings match the real brand`() {
        assertTrue(matches("MONOCONT. LAVAMANOS KORAL MEDIA NIQUEL CORONA", "coral"))
        assertTrue(matches("MONOCONT. LAVAMANOS BROOKLYN ALTA CROMO CORONA", "brooklin"))
        assertTrue(matches("LLAVE SENCILLA LAVAMANOS ALTO VERA CROMO CORONA", "bera"))
    }

    @Test
    fun `display query uses the name found in the catalog`() {
        assertEquals("Koral", ProductSearchText.displayQuery("MONOCONT. LAVAMANOS KORAL MEDIA NIQUEL CORONA", "coral"))
        assertEquals("Montecarlo", ProductSearchText.displayQuery("SANITARIO MONTECARLO BEIGE REDOND CORONA", "montecarlo"))
    }

    @Test
    fun `wildcard noise is filtered out`() {
        assertFalse(matches("(E) CANTERA DUROPISO BLANCO 51X51 PRIM. CORONA", "vera"))
        assertFalse(matches("FACHADA TUNJO NEGRO 34.5X62 PRIM. CORONA", "cusco"))
        assertFalse(matches("BUENOS AIRES BEIGE 51X51 PRIM. CORONA", "cusco"))
    }
}
