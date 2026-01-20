package com.example.comfyapp

import com.robotemi.sdk.serial.Serial
import java.io.Serializable

data class Product(
    val id: Int,
    val name: String,
    val price: Double,
    val imageUrl: String?,
    val stock: Double = 0.0,
    val description: String? = null,
    var lastEntryQty: Double = 0.0,
    var lastEntryDate: String = "",
    val website_url: String?= null
) : Serializable
