package com.example.comfyapp.data.repository

import com.example.comfyapp.OdooHelper
import com.example.comfyapp.core.ModelProductStock
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode

class ProductRepository {

    /**
     * Trae productos con stock > 0 en una ubicación específica y usados en cierta categoría.
     * Luego agrega la URL de la plantilla (product.template) correspondiente.
     */
    fun getProductsWithStockAndUrl(
        usedInId: Int,
        locationName: String = "Tunja",
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 1️⃣ Primero traer todos los productos con stock en la ubicación
        val domainStock = listOf(
            listOf("location_id.complete_name", "ilike", locationName),
            listOf("free_qty", ">", 0),
            listOf("used_in_ids", "=", usedInId)
        )

        val fieldsStock = mapOf(
            "id" to true,
            "name" to true,
            "list_price" to true,
            "free_qty" to true,
            "description" to true,
            "image_128" to true,
            "product_tmpl_id" to true   // Para luego buscar la URL
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domainStock,
            fields = fieldsStock,
            order = "name asc",
            limit = 5000,
            onSuccess = { resultStock ->
                if (resultStock.isEmpty()) {
                    onSuccess(emptyList())
                    return@executeOdooRpc
                }

                // Extraer IDs de templates únicos
                val templateIds = resultStock.mapNotNull {
                    it.asJsonObject["product_tmpl_id"]?.asJsonArray?.get(0)?.asInt
                }.distinct()

                // 2️⃣ Traer las URLs desde product.template
                val domainTemplate = templateIds.map { listOf("id", "=", it) }

                val fieldsTemplate = mapOf(
                    "id" to true,
                    "website_url" to true
                )

                OdooHelper.executeOdooRpc(
                    model = "product.template",
                    method = "search_read",
                    domain = domainTemplate,
                    fields = fieldsTemplate,
                    order = "id asc",
                    limit = 5000,
                    onSuccess = { resultTemplate ->

                        // Map de template_id a website_url
                        val templateMap = resultTemplate.mapNotNull {
                            val obj = it.asJsonObject
                            val id = obj["id"].asInt
                            val url = obj["website_url"]?.asString
                            id to url
                        }.toMap()

                        // Mapear productos con stock + URL
                        val products = resultStock.mapNotNull {
                            try {
                                val obj = it.asJsonObject
                                val tmplId = obj["product_tmpl_id"]?.asJsonArray?.get(0)?.asInt
                                ModelProductStock(
                                    id = obj["id"].asInt,
                                    name = obj["name"].asString,
                                    price = BigDecimal(obj["list_price"].asDouble)
                                        .setScale(2, RoundingMode.HALF_UP)
                                        .toDouble(),
                                    free_qty = BigDecimal(obj["free_qty"].asDouble)
                                        .setScale(2, RoundingMode.HALF_UP)
                                        .toDouble(),
                                    description = obj["description"]?.asString,
                                    imageBase64 = obj["image_128"]?.asString,
                                    websiteUrl = tmplId?.let { templateMap[it] }
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }

                        onSuccess(products)

                    },
                    onError = { err -> onError("Error al obtener URLs: $err") }
                )

            },
            onError = { err -> onError("Error al obtener stock: $err") }
        )
    }
}
