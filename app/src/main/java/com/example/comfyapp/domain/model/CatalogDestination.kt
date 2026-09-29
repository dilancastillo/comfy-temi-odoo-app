// traduce la categoria y el espacio del cliente a una zona del catalogo de la tienda
package com.example.comfyapp.domain.model

import java.text.Normalizer

enum class CatalogDestination(val displayName: String) {
    SANITARY("sanitarios"),
    TAPS("griferías"),
    FLOOR_BATHROOMS("pisos y paredes para baños y cocinas"),
    FLOOR_SOCIAL("pisos y paredes para zonas sociales"),
    FLOOR_EXTERIORS("pisos y paredes para exteriores"),
    // pisos y paredes sin espacio conocido: hay tres zonas posibles
    FLOOR_ANY("pisos y paredes");

    companion object {
        fun from(category: ProductCategory?, space: String?): CatalogDestination? = when (category) {
            ProductCategory.SANITARY -> SANITARY
            ProductCategory.TAPS -> TAPS
            ProductCategory.FLOOR_AND_WALL,
            ProductCategory.FLOOR_AND_WALL_BATHROOMS,
            ProductCategory.FLOOR_AND_WALL_SOCIAL,
            ProductCategory.FLOOR_AND_WALL_EXTERIORS -> floorFor(space)
            null -> null
        }

        private fun floorFor(space: String?): CatalogDestination {
            val normalized = space?.normalized() ?: return FLOOR_ANY
            return when {
                EXTERIOR_WORDS.any { it in normalized } -> FLOOR_EXTERIORS
                BATHROOM_WORDS.any { it in normalized } -> FLOOR_BATHROOMS
                SOCIAL_WORDS.any { it in normalized } -> FLOOR_SOCIAL
                else -> FLOOR_ANY
            }
        }

        private fun String.normalized(): String =
            Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        private val EXTERIOR_WORDS = listOf(
            "exterior", "patio", "terraza", "afuera", "jardin", "fachada", "parqueadero",
            "garaje", "balcon", "anden", "piscina", "quincho"
        )
        private val BATHROOM_WORDS = listOf("bano", "ducha", "cocina", "zona humeda", "lavadero")
        private val SOCIAL_WORDS = listOf(
            "sala", "comedor", "social", "habitacion", "alcoba", "cuarto", "dormitorio",
            "estudio", "living", "interior", "oficina"
        )
    }
}
