package com.example.comfyapp.assistance

import com.example.comfyapp.domain.model.CustomerNeed
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.usecase.NavigationMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMessagesTest {

    @Test
    fun `departure screen names the zone`() {
        assertEquals("Vamos a Sanitarios.\nSígueme 👋", NavigationMessages.departureScreen("sanitarios"))
        assertEquals("Vamos a Exteriores.\nSígueme 👋", NavigationMessages.departureScreen("Pisos Exteriores Alfa"))
    }

    @Test
    fun `arrival connects with what the customer asked`() {
        val need = CustomerNeed("need_01", category = ProductCategory.SANITARY, style = "moderno", space = "baño pequeño")
        val text = NavigationMessages.arrival("sanitarios", need, showingProducts = true)

        assertTrue(text, text.startsWith("Llegamos. Aquí están nuestros sanitarios. Como buscas sanitarios para baño pequeño, estilo moderno"))
    }

    @Test
    fun `context from another zone or without details is not mentioned`() {
        val taps = CustomerNeed("need_01", category = ProductCategory.TAPS, color = "negro")
        val plain = CustomerNeed("need_02", category = ProductCategory.SANITARY)

        assertEquals(
            "Llegamos. Aquí están nuestros sanitarios. Si quieres, también puedo ayudarte a comparar algunas opciones.",
            NavigationMessages.arrival("sanitarios", taps, showingProducts = false)
        )
        assertTrue(!NavigationMessages.arrival("sanitarios", plain, showingProducts = true).contains("Como buscas"))
    }
}
