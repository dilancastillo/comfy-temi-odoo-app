package com.example.comfyapp

data class Product (
    val id: Int,
    val name: String,
    val price: Double,
    val imageBase64: String?,
    val stock: Int,
    val description: String?
)