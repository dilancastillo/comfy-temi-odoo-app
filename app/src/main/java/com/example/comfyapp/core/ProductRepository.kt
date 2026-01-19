package com.example.comfyapp.data.repository

import android.util.Log
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
    val productsId= mutableListOf(18753, 20979)
    fun getProductsWithStockAndUrl(
        usedInId: Int,
        locationName: String = "Tunja",
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit
    ) {
        // Primero traer todos los productos con stock en la ubicación
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

                //  Traer las URLs desde product.template
                val domainTemplate = listOf(
                    listOf("id", "in", templateIds)
                )


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
                        Log.d("RAW_TEMPLATE_JSON", resultTemplate.toString())
                        // map de template_id a website_url
                        val templateMap = resultTemplate.mapNotNull {
                            val obj = it.asJsonObject
                            val id = obj["id"].asInt
                            val url = obj["website_url"]?.asString
                            id to url
                        }.toMap()

                        // mapear productos con stock + url
                        val products = resultStock.mapNotNull {
                            try {
                                val obj = it.asJsonObject
                                val tmplId = obj["product_tmpl_id"]?.asJsonArray?.get(0)?.asInt
                                val websiteUrl = tmplId?.let { templateMap[it] }

                                Log.d(
                                    "FINAL_PRODUCT_URL",
                                    "productId=${obj["id"].asInt} | tmplId=$tmplId | websiteUrl=$websiteUrl"
                                )
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
                                    website_url = tmplId?.let { templateMap[it] }
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
    fun getProductsByCategoryAndLocation(
        categoryId: Int,
        locationId: Int,
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit
    ) {
        val domain = listOf(
            listOf("categ_id", "child_of", categoryId),
            listOf("free_qty", ">", 0)
        )

        val fields = mapOf(
            "id" to true,
            "name" to true,
            "free_qty" to true,
            "website_url" to true,
            "list_price" to true,
            "image_128" to true
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = 10000,
            order = "free_qty desc",
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = obj["free_qty"].asDouble,
                            imageBase64 = obj["image_128"]?.asString,
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onSuccess(products)
            },
            onError = onError
        )
    }

}
