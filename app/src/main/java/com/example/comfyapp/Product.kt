package com.example.comfyapp

data class Product(
    val id: Int,
    val name: String,
    val price: Double,
    val imageBase64: String? = null,
    val stock: Double = 0.0,
    val description: String? = null,
    var lastEntryQty: Double = 0.0,
    var lastEntryDate: String = ""
)
