// una necesidad del cliente dentro de la sesion (un producto que busca) con atributos propios que no se mezclan con otras
package com.example.comfyapp.domain.model

import java.text.Normalizer

data class CustomerNeed(
    val id: String,
    val assistanceType: AssistanceType? = null,
    val category: ProductCategory? = null,
    val productType: ProductType? = null,
    val exactProductQuery: String? = null,
    val space: String? = null,
    val style: String? = null,
    val color: String? = null,
    val budgetLevel: String? = null,
    val maxPrice: Double? = null,
    val technicalNeeds: Set<String> = emptySet(),
    // aumenta cada vez que la necesidad cambia; la confirmación vale solo para la revisión aceptada
    val revision: Int = 0,
    val confirmedRevision: Int = NOT_CONFIRMED
) {
    val isConfirmed: Boolean get() = confirmedRevision == revision

    // Sin ningún dato del producto: no vale la pena guardarla como necesidad.
    val isBlank: Boolean get() = sameContentAs(CustomerNeed(id))

    // Aplica lo que dijo el cliente solo a esta necesidad: un valor nuevo reemplaza el campo,
    // un campo pedido en clearFields se borra y lo no mencionado se conserva.
    fun applyChange(analysis: IntentAnalysis): CustomerNeed {
        val productChanged = category != null && analysis.category != null && analysis.category != category
        // "No es sanitario, es grifería": lo exclusivo del producto anterior no pasa al nuevo.
        val base = if (productChanged) {
            copy(productType = null, exactProductQuery = null, style = null, color = null, technicalNeeds = emptySet())
        } else {
            this
        }
        val newCategory = analysis.category ?: base.category
        val clear = analysis.clearFields
        val keptTechnical = if (NeedField.TECHNICAL_NEEDS in clear) emptySet() else base.technicalNeeds
        val removed = analysis.technicalNeedsRemove.map(::normalized).toSet()
        val newSpace = pick(base.space, analysis.space.clean(), NeedField.SPACE, clear)
        val technical = (keptTechnical.filterNot { normalized(it) in removed } + analysis.technicalNeeds)
            .mapNotNull { it.clean() }
            .filter { isProductCondition(it, newSpace) }
            .distinctBy(::normalized)
            .toSet()

        return base.copy(
            assistanceType = if (analysis.assistanceType in NEED_TYPES) analysis.assistanceType else base.assistanceType,
            category = newCategory,
            productType = pick(base.productType, analysis.productType, NeedField.PRODUCT_TYPE, clear)
                ?.takeIf { newCategory == null || it.category == newCategory },
            exactProductQuery = pick(base.exactProductQuery, analysis.exactProductQuery.clean(), NeedField.EXACT_PRODUCT_QUERY, clear),
            space = newSpace,
            style = pick(base.style, analysis.style.clean(), NeedField.STYLE, clear),
            color = pick(base.color, analysis.color.clean(), NeedField.COLOR, clear),
            budgetLevel = pick(base.budgetLevel, analysis.budgetLevel.clean(), NeedField.BUDGET_LEVEL, clear),
            maxPrice = pick(base.maxPrice, analysis.maxPrice, NeedField.MAX_PRICE, clear),
            technicalNeeds = technical
        )
    }

    // Cambia algo que Temi ya había entendido y confirmado (no solo un detalle): se vuelve a confirmar.
    fun isSignificantChangeFrom(previous: CustomerNeed): Boolean =
        category != previous.category ||
            space != previous.space ||
            exactProductQuery != previous.exactProductQuery ||
            productType != previous.productType ||
            routeFamily(assistanceType) != routeFamily(previous.assistanceType)

    fun sameContentAs(other: CustomerNeed): Boolean =
        copy(id = other.id, revision = other.revision, confirmedRevision = other.confirmedRevision) == other

    fun changedFieldsFrom(other: CustomerNeed?): Set<String> = buildSet {
        val before = other ?: CustomerNeed(id)
        if (assistanceType != before.assistanceType) add("assistanceType")
        if (category != before.category) add("category")
        if (productType != before.productType) add("productType")
        if (exactProductQuery != before.exactProductQuery) add("exactProductQuery")
        if (space != before.space) add("space")
        if (style != before.style) add("style")
        if (color != before.color) add("color")
        if (budgetLevel != before.budgetLevel) add("budgetLevel")
        if (maxPrice != before.maxPrice) add("maxPrice")
        if (technicalNeeds != before.technicalNeeds) add("technicalNeeds")
    }

    companion object {
        const val NOT_CONFIRMED = -1

        // Tipos que describen el producto buscado; aclaraciones, asesor o "solo miro" no lo cambian.
        private val NEED_TYPES = setOf(
            AssistanceType.EXACT_PRODUCT,
            AssistanceType.CATEGORY_BROWSE,
            AssistanceType.PROJECT_ASSISTANCE,
            AssistanceType.TECHNICAL_NEED,
            AssistanceType.LOCATION_ONLY
        )

        // Palabras que nombran productos o espacios: Gemini a veces las devuelve como requisito
        // técnico y así terminaban frases como "grifería sanitario".
        private val NOT_CONDITIONS = setOf(
            "sanitario", "sanitarios", "inodoro", "griferia", "griferias", "grifo", "llave", "monocontrol",
            "piso", "pisos", "pared", "paredes", "enchape", "revestimiento", "ceramica", "porcelanato",
            "combo", "combos", "lavamanos", "lavaplatos", "bano", "cocina", "sala", "comedor", "patio", "terraza"
        )

        private fun routeFamily(type: AssistanceType?): String? = when (type) {
            AssistanceType.EXACT_PRODUCT -> "exact"
            AssistanceType.LOCATION_ONLY -> "location"
            AssistanceType.CATEGORY_BROWSE,
            AssistanceType.PROJECT_ASSISTANCE,
            AssistanceType.TECHNICAL_NEED -> "browse"
            else -> null
        }

        private fun isProductCondition(value: String, space: String?): Boolean {
            val normalizedValue = normalized(value)
            return normalizedValue !in NOT_CONDITIONS && normalizedValue != space?.let(::normalized)
        }

        private fun <T> pick(current: T?, newValue: T?, field: NeedField, clear: Set<NeedField>): T? = when {
            newValue != null -> newValue
            field in clear -> null
            else -> current
        }

        private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

        fun normalized(value: String): String =
            Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
}

// Campos de una necesidad que el cliente puede pedir quitar ("ya no quiero límite de precio").
// Para cada campo: si llega un valor se asigna (SET), si está en clearFields se borra (CLEAR) y si
// no se mencionó se conserva (KEEP); un null de Gemini nunca borra por accidente.
enum class NeedField(val wire: String) {
    PRODUCT_TYPE("product_type"),
    EXACT_PRODUCT_QUERY("exact_product_query"),
    SPACE("space"),
    STYLE("style"),
    COLOR("color"),
    BUDGET_LEVEL("budget_level"),
    MAX_PRICE("max_price"),
    TECHNICAL_NEEDS("technical_needs");

    companion object {
        fun fromWire(value: String?): NeedField? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

// Qué hacer con la memoria, aparte del tipo de ayuda: agregar otro producto, cambiar el activo o
// corregir lo que Temi acaba de entender. Gemini la propone y el código la valida.
enum class NeedOperation {
    ADD_NEED,
    UPDATE_ACTIVE_NEED,
    CORRECT_PENDING_NEED,
    RESUME_NEED;

    companion object {
        fun fromWire(value: String?): NeedOperation? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}
