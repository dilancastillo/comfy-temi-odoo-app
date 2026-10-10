package com.example.comfyapp.assistance

import com.example.comfyapp.domain.model.ColorFromPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorFromPhraseTest {

    @Test
    fun `finds the single color the customer said`() {
        assertEquals("gris", ColorFromPhrase.find("Pisos de color gris."))
        assertEquals("beige", ColorFromPhrase.find("Ahora que sea de color beige."))
        assertEquals("beige", ColorFromPhrase.find("Su país con el beis"))
        assertEquals("negro", ColorFromPhrase.find("una chape Black para el piso"))
        assertEquals("blanco", ColorFromPhrase.find("griferías blancas"))
    }

    @Test
    fun `two colors or a negated color are not guessed`() {
        assertNull(ColorFromPhrase.find("ni gris ni beige"))
        assertNull(ColorFromPhrase.find("blanco y negro"))
        assertNull(ColorFromPhrase.find("ya no quiero que sea negro"))
        assertNull(ColorFromPhrase.find("pisos para la sala"))
        assertNull(ColorFromPhrase.find(null))
    }
}
