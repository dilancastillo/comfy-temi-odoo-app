// prepara la busqueda por nombre tolerando como el dictado por voz escribe las marcas (coral/koral, brooklin/brooklyn)
package com.example.comfyapp.data.repository

object ProductSearchText {

    private val STOP_WORDS = setOf(
        "de", "del", "la", "el", "los", "las", "un", "una", "para", "con", "por", "que", "y"
    )

    // Letras que el dictado confunde porque suenan igual; cada grupo se trata como una sola letra.
    private val SOUND_ALIKE = listOf("ckq", "sz", "bv", "yi")

    fun words(query: String): List<String> = query.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .distinct()

    // Patrón para Odoo: cada letra dudosa pasa a "_" (comodín de un carácter en ilike).
    // Es amplio a propósito; matches() descarta después lo que no corresponde.
    fun odooPattern(word: String): String =
        word.map { char -> if (SOUND_ALIKE.any { char in it }) '_' else char }.joinToString("")

    // Cada palabra buscada debe aparecer como palabra completa del nombre, aceptando solo las
    // letras equivalentes (evita que "vera" traiga "CANTERA" o que "cusco" traiga "TUNJO").
    fun matches(productName: String, words: List<String>): Boolean {
        val nameWords = productName.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        return words.all { word ->
            val regex = soundAlikeRegex(word)
            nameWords.any { regex.matches(it) }
        }
    }

    // La consulta escrita como aparece en el catálogo ("coral" -> "Koral"): así el cliente ve que
    // Temi encontró lo que pidió aunque el dictado lo haya escrito distinto.
    fun displayQuery(productName: String, query: String): String {
        val nameWords = productName.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        return words(query).joinToString(" ") { word ->
            val regex = soundAlikeRegex(word)
            (nameWords.firstOrNull { regex.matches(it) } ?: word).replaceFirstChar { it.uppercase() }
        }
    }

    private fun soundAlikeRegex(word: String): Regex = Regex(
        word.map { char ->
            SOUND_ALIKE.firstOrNull { char in it }?.let { "[$it]" } ?: Regex.escape(char.toString())
        }.joinToString("")
    )
}
