// define el producto que utiliza el dominio sin depender de odoo ni de android
package com.example.comfyapp.domain.model

data class CatalogProduct(
    val id: Int,
    val name: String,
    val price: Double,
    val stock: Double,
    val imageUrl: String?,
    val description: String?,
    val websiteUrl: String?
)
