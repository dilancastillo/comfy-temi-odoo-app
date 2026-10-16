// frases de temi al salir hacia una zona y al llegar, conectadas con lo que el cliente ya pidió
package com.example.comfyapp.domain.usecase

import com.example.comfyapp.domain.model.CustomerNeed
import com.example.comfyapp.domain.model.ProductCategory

object NavigationMessages {

    // Al salir basta una frase corta: la pantalla dice a dónde van, sin más información.
    const val DEPARTURE_SPEECH = "Perfecto. Vamos."

    private data class Zone(
        val screenName: String,
        // "nuestros sanitarios" / "nuestras griferías": el artículo cambia con la zona
        val ourProducts: String,
        val category: ProductCategory
    )

    // Ubicaciones del mapa de Temi (en minúscula, como se guardan en el robot).
    private val ZONES = mapOf(
        "sanitarios" to Zone("Sanitarios", "nuestros sanitarios", ProductCategory.SANITARY),
        "griferias" to Zone("Griferías", "nuestras griferías", ProductCategory.TAPS),
        "baños revestimientos alfa" to Zone(
            "Baños y cocinas", "nuestros pisos y paredes para baños y cocinas", ProductCategory.FLOOR_AND_WALL
        ),
        "zona social revestimiento" to Zone(
            "Zona social", "nuestros pisos y paredes para zonas sociales", ProductCategory.FLOOR_AND_WALL
        ),
        "pisos exteriores alfa" to Zone(
            "Exteriores", "nuestros pisos y paredes para exteriores", ProductCategory.FLOOR_AND_WALL
        )
    )

    private val describe = DescribeCustomerContextUseCase()

    fun departureScreen(location: String): String {
        val zone = ZONES[location.lowercase()]?.screenName ?: return "Vamos.\nSígueme 👋"
        return "Vamos a $zone.\nSígueme 👋"
    }

    // showingProducts: la lista ya está en pantalla ("Ir a verlos"); si no, Temi solo acompañó.
    fun arrival(location: String, need: CustomerNeed?, showingProducts: Boolean): String {
        val zone = ZONES[location.lowercase()]
        val greeting = zone?.let { "Llegamos. Aquí están ${it.ourProducts}." } ?: "Llegamos."
        val context = need?.takeIf { zone != null && it.matches(zone.category) && it.hasDetails() }
            ?.let { describe.describeNeed(it) }
        val next = when {
            context != null && showingProducts ->
                "Como buscas $context, en mi pantalla tienes las opciones que mejor se ajustan; aquí las puedes ver en exhibición."
            context != null ->
                "Como buscas $context, toca Hablar con Temi y te muestro las opciones que mejor se ajustan."
            showingProducts ->
                "En mi pantalla tienes los productos que te mostré; aquí los puedes ver en exhibición, junto con otros diseños."
            else -> "Si quieres, también puedo ayudarte a comparar algunas opciones."
        }
        return "$greeting $next"
    }

    // El contexto solo se menciona si es de esta zona: lo pedido para un sanitario no aplica a una grifería.
    private fun CustomerNeed.matches(zoneCategory: ProductCategory): Boolean = when (zoneCategory) {
        ProductCategory.SANITARY, ProductCategory.TAPS -> category == zoneCategory
        else -> category != null && category != ProductCategory.SANITARY && category != ProductCategory.TAPS
    }

    // "Como buscas sanitarios" justo después de "aquí están nuestros sanitarios" no aporta nada.
    private fun CustomerNeed.hasDetails(): Boolean =
        productType != null || space != null || style != null || color != null ||
            technicalNeeds.isNotEmpty() || maxPrice != null || budgetLevel != null
}
