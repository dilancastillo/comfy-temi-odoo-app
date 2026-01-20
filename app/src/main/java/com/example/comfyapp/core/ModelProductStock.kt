package com.example.comfyapp.core

data class ModelProductStock(
    val id: Int,
    val name: String,
    val price: Double,
    val imageUrl: String?,
    val free_qty: Double = 0.0,
    val description: String? = null,
    val website_url: String?
)