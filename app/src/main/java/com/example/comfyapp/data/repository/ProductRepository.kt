// ejecuta las consultas de productos existencias categorias y ubicaciones en odoo
package com.example.comfyapp.data.repository

import android.util.Log
import com.example.comfyapp.OdooHelper
import com.example.comfyapp.OdooHelper.BASE_URL
import com.example.comfyapp.data.model.ModelProductStock
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.collections.listOf

object ProductRepository {

    private const val TUNJA_E_LOCATION_ID = 8

    /**
     * Trae productos con stock > 0 en una ubicación específica y usados en cierta categoría.
     * Luego agrega la URL de la plantilla (product.template) correspondiente.
     */
    private data class CacheEntry(
        val data: List<ModelProductStock>,
        val timestamp: Long
    )
    private val cache = mutableMapOf<String, CacheEntry>()
    private val CACHE_TTL = 10 * 60 * 1000 // 5 minutos


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

        val fieldsStock = listOf(
            "id",
            "name",
            "list_price",
            "free_qty",
            "description",
            "product_tmpl_id" // Para luego buscar la URL
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domainStock,
            fields = fieldsStock,
            order = "name asc",
            limit = 10,
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


                val fieldsTemplate = listOf(
                    "id",
                    "website_url"
                )

                OdooHelper.executeOdooRpc(
                    model = "product.template",
                    method = "search_read",
                    domain = domainTemplate,
                    fields = fieldsTemplate,
                    order = "id asc",
                    limit = 10,
                    onSuccess = { resultTemplate ->
                        Log.d("TEMPLATE_COUNT", "Templates: ${resultTemplate.size()}")
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
                                val productId = obj["id"].asInt
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
                                    imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
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

        val fields = listOf(
            "id",
            "name",
            "free_qty",
            "website_url",
            "list_price"
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = 15,
            order = "free_qty desc",
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        val productId = obj["id"].asInt
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = obj["free_qty"].asDouble,
                            imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                    .sortedByDescending { it.free_qty }
                onSuccess(products)
            },
            onError = onError
        )
    }
    //pisos y paredes
    fun getProductsAllFloor(
        locationName: String = "Tunja/E",
        usedInId: List<Int>,
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit,
        offset: Int = 0,
        limit: Int = 8
    ) {
        // key única para pisos y paredes
        val cacheKey = "floor_wall_${locationName}_${usedInId.sorted()}_${offset}_$limit"
        val now = System.currentTimeMillis()

        // Revisar cache
        cache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL) {
                Log.d("CACHE", "Usando cache: $cacheKey")
                onSuccess(entry.data)
                return
            }
        }
        val domain = listOf(
            listOf("x_traffic","!=","Pared"),
            listOf("location_id.complete_name", "ilike", locationName),
            listOf("free_qty", ">", 10),
            listOf("active_robot", "=", "true"),
            listOf("website_published", "=", true),
            listOf("used_in_ids", "in", usedInId),
        )


        val fields = listOf(
            "id",
            "name",
            "free_qty",
            "website_url",
            "list_price"
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = limit,
            offset = offset,
            order = "free_qty desc",
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        val productId = obj["id"].asInt
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = BigDecimal(obj["free_qty"].asDouble)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble(),
                            imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                val sortedProducts = products.sortedByDescending { it.free_qty }

                cache[cacheKey] = CacheEntry(
                    data = sortedProducts,
                    timestamp = now
                )

                onSuccess(sortedProducts)
            },
            onError = onError
        )
    }
    //unicamente paredes
    fun getProductsAllWall(
        locationName: String = "Tunja/E",
        usedInId: List<Int>,
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit,
        offset: Int = 0,
        limit: Int = 8
    ) {
        // key única para pisos y paredes
        val cacheKey = "wall_${locationName}_${usedInId.sorted()}_${offset}_$limit"
        val now = System.currentTimeMillis()

        // Revisar cache
        cache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL) {
                Log.d("CACHE", "Usando cache: $cacheKey")
                onSuccess(entry.data)
                return
            }
        }
        val domain = listOf(
            listOf("x_traffic","=","Pared"),
            listOf("location_id.complete_name", "ilike", locationName),
            listOf("free_qty", ">", 10),
            listOf("active_robot", "=", "true"),
            listOf("website_published", "=", true),
            listOf("used_in_ids", "in", usedInId),
        )


        val fields = listOf(
            "id",
            "name",
            "free_qty",
            "website_url",
            "list_price"
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = limit,
            offset = offset,
            order = "free_qty desc",
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        val productId = obj["id"].asInt
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = BigDecimal(obj["free_qty"].asDouble)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble(),
                            imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                val sortedProducts = products.sortedByDescending { it.free_qty }

                cache[cacheKey] = CacheEntry(
                    data = sortedProducts,
                    timestamp = now
                )

                onSuccess(sortedProducts)
            },
            onError = onError
        )
    }
    //lavamanos
    fun getProductsTapsLavaM(
        locationName: String = "Tunja/E",
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit,
        offset: Int = 0,
        limit: Int = 8
    ) {
        // key única para pisos y paredes
        val cacheKey = "lava_${locationName}_${offset}_$limit"
        val now = System.currentTimeMillis()

        // Revisar cache
        cache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL) {
                Log.d("CACHE", "Usando cache: $cacheKey")
                onSuccess(entry.data)
                return
            }
        }
        val domain = listOf(
            listOf("parent_category", "ilike", "GRIFERIAS"),
            listOf("child_category", "ilike", "LAVAMANOS"),
            listOf("website_published", "=", true),
            listOf("free_qty", ">", 1),
        )


        val fields = listOf(
            "id",
            "name",
            "free_qty",
            "website_url",
            "list_price"
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = limit,
            offset = offset,
            order = "id desc",
            context = mapOf("location" to TUNJA_E_LOCATION_ID),
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        val productId = obj["id"].asInt
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = BigDecimal(obj["free_qty"].asDouble)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble(),
                            imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                val sortedProducts = products.sortedByDescending { it.free_qty }

                cache[cacheKey] = CacheEntry(
                    data = sortedProducts,
                    timestamp = now
                )

                onSuccess(sortedProducts)
            },
            onError = onError
        )
    }
    //lavaplatos
    fun getProductsTapsLavaP(
        locationName: String = "Tunja/E",
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit,
        offset: Int = 0,
        limit: Int = 8
    ) {
        // key única para pisos y paredes
        val cacheKey = "lavaP_${locationName}_${offset}_$limit"
        val now = System.currentTimeMillis()

        // Revisar cache
        cache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL) {
                Log.d("CACHE", "Usando cache: $cacheKey")
                onSuccess(entry.data)
                return
            }
        }
        val domain = listOf(
            listOf("parent_category", "ilike", "GRIFERIAS"),
            listOf("child_category", "ilike", "LAVAPLATOS"),
            listOf("website_published", "=", true),
            listOf("free_qty", ">", 1),
        )


        val fields = listOf(
            "id",
            "name",
            "free_qty",
            "website_url",
            "list_price"
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = limit,
            offset = offset,
            order = "id desc",
            context = mapOf("location" to TUNJA_E_LOCATION_ID),
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        val productId = obj["id"].asInt
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = BigDecimal(obj["free_qty"].asDouble)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble(),
                            imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                val sortedProducts = products.sortedByDescending { it.free_qty }

                cache[cacheKey] = CacheEntry(
                    data = sortedProducts,
                    timestamp = now
                )

                onSuccess(sortedProducts)
            },
            onError = onError
        )
    }
    //sanitarios combos
    fun getProductsSanitaryCombo(
        locationName: String = "Tunja/E",
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit,
        offset: Int = 0,
        limit: Int = 15
    ) {
        // key única para pisos y paredes
        val cacheKey = "combo_${locationName}_${offset}_$limit"
        val now = System.currentTimeMillis()

        // Revisar cache
        cache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL) {
                Log.d("CACHE", "Usando cache: $cacheKey")
                onSuccess(entry.data)
                return
            }
        }
        val domain = listOf(
            listOf("parent_category", "ilike", "PORCELANA SANITARIA"),
            listOf("child_category", "ilike", "COMBOS"),
            listOf("website_published", "=", true),
            listOf("free_qty", ">", 1),
        )


        val fields = listOf(
            "id",
            "name",
            "free_qty",
            "website_url",
            "list_price"
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = limit,
            offset = offset,
            order = "id desc",
            context = mapOf("location" to TUNJA_E_LOCATION_ID),
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        val productId = obj["id"].asInt
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = BigDecimal(obj["free_qty"].asDouble)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble(),
                            imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                val sortedProducts = products.sortedByDescending { it.free_qty }

                cache[cacheKey] = CacheEntry(
                    data = sortedProducts,
                    timestamp = now
                )

                onSuccess(sortedProducts)
            },
            onError = onError
        )
    }
    //sanitarios solos
    fun getProductsSanitaryOnly(
        locationName: String = "Tunja/E",
        onSuccess: (List<ModelProductStock>) -> Unit,
        onError: (String) -> Unit,
        offset: Int = 0,
        limit: Int = 15
    ) {
        // key única para pisos y paredes
        val cacheKey = "SaniOnly_${locationName}_${offset}_$limit"
        val now = System.currentTimeMillis()

        // Revisar cache
        cache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL) {
                Log.d("CACHE", "Usando cache: $cacheKey")
                onSuccess(entry.data)
                return
            }
        }
        val domain = listOf(
            listOf("parent_category", "ilike", "PORCELANA SANITARIA"),
            listOf("grandchild_category", "ilike", "ONE PIECE"),
            listOf("website_published", "=", true),
            listOf("free_qty", ">", 1),
        )


        val fields = listOf(
            "id",
            "name",
            "free_qty",
            "website_url",
            "list_price"
        )

        OdooHelper.executeOdooRpc(
            model = "product.product",
            method = "search_read",
            domain = domain,
            fields = fields,
            limit = limit,
            offset = offset,
            order = "id desc",
            context = mapOf("location" to TUNJA_E_LOCATION_ID),
            onSuccess = { result ->
                val products = result.mapNotNull {
                    try {
                        val obj = it.asJsonObject
                        val productId = obj["id"].asInt
                        ModelProductStock(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            free_qty = BigDecimal(obj["free_qty"].asDouble)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble(),
                            imageUrl = "$BASE_URL/web/image/product.product/$productId/image_512",
                            website_url = obj["website_url"]?.asString
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                val sortedProducts = products.sortedByDescending { it.free_qty }

                cache[cacheKey] = CacheEntry(
                    data = sortedProducts,
                    timestamp = now
                )

                onSuccess(sortedProducts)
            },
            onError = onError
        )
    }

}
