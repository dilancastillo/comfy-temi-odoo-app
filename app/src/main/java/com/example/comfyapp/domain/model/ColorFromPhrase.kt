// toma el color de la frase del cliente cuando gemini no lo devolvio ("pisos de color gris" sin color)
package com.example.comfyapp.domain.model

import java.text.Normalizer

object ColorFromPhrase {

    // Palabra dicha (sin tildes, minúscula) -> color guardado. "Madera" queda fuera: suele ser el
    // material o el aspecto del piso, no un color que el cliente quiera filtrar.
    private val COLORS = mapOf(
        "negro" to "negro", "negra" to "negro", "negros" to "negro", "negras" to "negro", "black" to "negro",
        "blanco" to "blanco", "blanca" to "blanco", "blancos" to "blanco", "blancas" to "blanco", "white" to "blanco",
        "gris" to "gris", "grises" to "gris", "gray" to "gris", "grey" to "gris",
        "beige" to "beige", "beis" to "beige",
        "cafe" to "café", "cafes" to "café", "marron" to "café",
        "azul" to "azul", "azules" to "azul",
        "verde" to "verde", "verdes" to "verde",
        "rojo" to "rojo", "roja" to "rojo",
        "dorado" to "dorado", "dorada" to "dorado",
        "plateado" to "plateado", "plateada" to "plateado",
        "cromado" to "cromado", "cromada" to "cromado"
    )
    private val NEGATIONS = setOf("no", "sin", "ni")

    // Un solo color, no negado: si la frase nombra dos ("ni gris ni beige", "blanco y negro") no se
    // adivina cuál quiso decir.
    fun find(phrase: String?): String? {
        val words = normalize(phrase ?: return null).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        val found = words.mapIndexedNotNull { index, word ->
            val color = COLORS[word] ?: return@mapIndexedNotNull null
            // Cualquier negación antes del color en la frase lo descarta ("ya no quiero que sea negro"):
            // ante la duda es mejor no filtrar que filtrar por el color equivocado.
            val negated = words.subList(0, index).any { it in NEGATIONS }
            if (negated) null else color
        }.distinct()
        val mentioned = words.mapNotNull { COLORS[it] }.distinct()
        return found.singleOrNull()?.takeIf { mentioned.size == 1 }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
