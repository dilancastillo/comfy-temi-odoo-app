// representa los datos de producto y existencias recibidos desde odoo
package com.example.comfyapp.data.model

data class ModelProductStock(
    val id: Int,
    val name: String,
    val price: Double,
    val imageUrl: String?,
    val free_qty: Double = 0.0,
    val description: String? = null,
    val website_url: String?
)
