// subtipo que el cliente pide dentro de una categoria ("un combo", "solo para paredes") y su columna en el catalogo
package com.example.comfyapp.domain.model

enum class ProductType(
    val category: ProductCategory,
    // listas del catálogo donde aplica (las zonas de pisos tienen columnas distintas entre sí)
    val listCategories: Set<ProductCategory>,
    // columna de esa lista: 1 = primera, 2 = segunda
    val column: Int,
    val displayName: String,
    val columnTitle: String
) {
    COMBO(ProductCategory.SANITARY, setOf(ProductCategory.SANITARY), 1, "combos de sanitario", "Combos"),
    SANITARIO_SOLO(ProductCategory.SANITARY, setOf(ProductCategory.SANITARY), 2, "sanitarios sin combo", "Solos"),
    LAVAMANOS(ProductCategory.TAPS, setOf(ProductCategory.TAPS), 1, "griferías para lavamanos", "Lavamanos"),
    LAVAPLATOS(ProductCategory.TAPS, setOf(ProductCategory.TAPS), 2, "griferías para lavaplatos", "Lavaplatos"),
    PAREDES(
        ProductCategory.FLOOR_AND_WALL,
        setOf(ProductCategory.FLOOR_AND_WALL_BATHROOMS, ProductCategory.FLOOR_AND_WALL_EXTERIORS),
        1, "revestimientos solo para paredes", "Únicamente para Paredes"
    ),
    PISOS(
        ProductCategory.FLOOR_AND_WALL,
        setOf(ProductCategory.FLOOR_AND_WALL_BATHROOMS, ProductCategory.FLOOR_AND_WALL_EXTERIORS),
        2, "pisos", "Para Pisos y Paredes"
    ),
    PORCELANATO(
        ProductCategory.FLOOR_AND_WALL, setOf(ProductCategory.FLOOR_AND_WALL_SOCIAL),
        1, "porcelanatos", "Porcelanatos"
    ),
    CERAMICA(
        ProductCategory.FLOOR_AND_WALL, setOf(ProductCategory.FLOOR_AND_WALL_SOCIAL),
        2, "cerámicas", "Cerámica"
    );

    companion object {
        fun fromWire(value: String?): ProductType? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}
