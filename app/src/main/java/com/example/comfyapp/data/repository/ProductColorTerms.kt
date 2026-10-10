// traduce el color que pidio el cliente a los terminos con que aparece en el nombre de los productos de odoo
package com.example.comfyapp.data.repository

import java.text.Normalizer

object ProductColorTerms {

    // En Odoo el color solo está en el nombre ("PARED MANTIS GRIS OSCURO", "GRIFERÍA ... NEGRA").
    // Se busca por raíz para cubrir masculino y femenino (negro/negra) y por los tonos con que
    // la tienda nombra ese color.
    private val SYNONYMS = mapOf(
        "negro" to listOf("negr", "black"),
        "blanco" to listOf("blanc", "white", "nieve"),
        "gris" to listOf("gris", "grafito", "gray", "grey", "cemento"),
        "beige" to listOf("beige", "crema", "marfil", "arena"),
        "cafe" to listOf("cafe", "marron", "chocolate", "nogal"),
        "madera" to listOf("madera", "roble", "nogal", "wood"),
        "dorado" to listOf("dorad", "oro", "gold"),
        "plateado" to listOf("platead", "plata", "silver"),
        "cromado" to listOf("cromad", "cromo", "chrome")
    )

    fun terms(color: String?): List<String> {
        val normalized = color?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val base = normalized.split(' ').first()
        // "negra", "blanca": se buscan con la forma masculina de la tabla.
        val masculine = if (base.endsWith("a")) base.dropLast(1) + "o" else base
        (SYNONYMS[base] ?: SYNONYMS[masculine])?.let { return it }
        // Color sin sinónimos conocidos: se busca su raíz ("verde", "azul", "rojo" -> "roj").
        return listOf(if (base.length > 4 && (base.endsWith("o") || base.endsWith("a"))) base.dropLast(1) else base)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
}
