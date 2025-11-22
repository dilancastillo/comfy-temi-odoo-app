package com.example.comfyapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.SelectNewProductBinding

class SelectNewProductActivity : AppCompatActivity() {

    private lateinit var binding: SelectNewProductBinding

    // ✅ Mapa de zonas a IDs de Odoo
    private val zonaToCategoryIds = mapOf(
        "Pisos tipo madera" to listOf(376),
        "Mates marmolizados Corona" to listOf(377, 372)
    )

    private fun getSelectedDateRange(): Pair<String, String>? {
        val selectedId = binding.toggleButton.checkedButtonId
        if (selectedId == View.NO_ID) return null

        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = java.time.LocalDate.now()
        val startDate: java.time.LocalDate = when (selectedId) {
            R.id.btnYesterday -> today.minusDays(1)
            R.id.btnLast7Days -> today.minusDays(7)
            R.id.btnLast2Weeks -> today.minusWeeks(2)
            R.id.btnLastMonth -> today.minusMonths(1)
            else -> today
        }

        val start = startDate.format(formatter)
        val end = today.format(formatter)
        return start to end
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectNewProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.imgbtnback.setOnClickListener { finish() }

        val zonas = zonaToCategoryIds.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zonas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerZona.adapter = adapter

        binding.btnBuscar.setOnClickListener {
            val zonaSeleccionada = binding.spinnerZona.selectedItem.toString()
            val categoryIds = zonaToCategoryIds[zonaSeleccionada]

            if (categoryIds.isNullOrEmpty()) {
                Toast.makeText(this, "Seleccione una zona válida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateRange = getSelectedDateRange()
            if (dateRange == null) {
                Toast.makeText(this, "Seleccione un rango de fechas", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loadProductsWithLastEntry(categoryIds, dateRange)
        }
    }

    private fun loadProductsWithLastEntry(categoryIds: List<Int>, dateRange: Pair<String, String>) {
        showLoading(true)

        // 1️⃣ Traer productos con stock > 0 en Tunja/E
        val domainStock = listOf(
            listOf("location_id", "=", "Tunja/E"),
            listOf("quantity", ">", 0)
        )
        val fieldsStock = mapOf("product_id" to true, "quantity" to true)

        OdooHelper.executeOdooRpc(
            model = "stock.quant",
            method = "search_read",
            domain = domainStock,
            fields = fieldsStock,
            onSuccess = { resultStock ->
                val productIds = resultStock.map { it.asJsonObject["product_id"].asJsonArray[0].asInt }

                if (productIds.isEmpty()) {
                    runOnUiThread { showLoading(false) }
                    return@executeOdooRpc
                }

                // 2️⃣ Traer info de producto
                val domainProducts = listOf(
                    listOf("id", "in", productIds),
                    listOf("categ_id", "in", categoryIds)
                )
                val fieldsProducts = mapOf(
                    "id" to true,
                    "name" to true,
                    "list_price" to true,
                    "qty_available" to true,
                    "description" to true,
                    "image_128" to true
                )

                OdooHelper.executeOdooRpc(
                    model = "product.product",
                    method = "search_read",
                    domain = domainProducts,
                    fields = fieldsProducts,
                    onSuccess = { resultProducts ->
                        val products = resultProducts.mapNotNull { elem ->
                            try {
                                val obj = elem.asJsonObject
                                Product(
                                    id = obj["id"].asInt,
                                    name = obj["name"].asString,
                                    price = obj["list_price"].asDouble,
                                    imageBase64 = obj["image_128"]?.asString,
                                    stock = obj["qty_available"].asInt,
                                    description = obj["description"]?.asString,
                                    lastEntryQty = 0.0,
                                    lastEntryDate = ""
                                )
                            } catch (e: Exception) {
                                Log.e("ODDO_PARSE", "Error parseando producto: ${e.message}")
                                null
                            }
                        }

                        // 3️⃣ Traer la última entrada done de cada producto
                        val domainMoves = listOf(
                            listOf("state", "=", "done"),
                            listOf("product_id", "in", productIds),
                            listOf("location_dest_id", "=", "Tunja/E"),
                            listOf("date", ">=", dateRange.first),
                            listOf("date", "<=", dateRange.second)
                        )
                        val fieldsMoves = mapOf("product_id" to true, "product_uom_qty" to true, "date" to true)

                        OdooHelper.executeOdooRpc(
                            model = "stock.move",
                            method = "search_read",
                            domain = domainMoves,
                            fields = fieldsMoves,
                            onSuccess = { resultMoves ->
                                val lastMovesMap = resultMoves.groupBy { it.asJsonObject["product_id"].asJsonArray[0].asInt }
                                    .mapValues { entry ->
                                        entry.value.maxByOrNull { it.asJsonObject["date"].asString }?.asJsonObject
                                    }

                                // Asignar la última entrada
                                products.forEach { p ->
                                    lastMovesMap[p.id]?.let { move ->
                                        p.lastEntryQty = move["product_uom_qty"].asDouble
                                        p.lastEntryDate = move["date"].asString
                                    }
                                }

                                // ✅ Filtrar solo productos con última entrada en la fecha seleccionada
                                val filteredProducts = products.filter { it.lastEntryDate.isNotEmpty() }

                                runOnUiThread {
                                    showLoading(false)
                                    if (filteredProducts.isEmpty()) {
                                        Toast.makeText(
                                            this@SelectNewProductActivity,
                                            "No se encontraron productos",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    supportFragmentManager.beginTransaction()
                                        .replace(
                                            R.id.fragmentContainer2,
                                            ProductListFragment.newInstance(filteredProducts)
                                        )
                                        .commit()
                                }
                            },
                            onError = { errMoves ->
                                runOnUiThread {
                                    showLoading(false)
                                    Toast.makeText(
                                        this@SelectNewProductActivity,
                                        "Error cargando entradas: $errMoves",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    },
                    onError = { errProducts ->
                        runOnUiThread {
                            showLoading(false)
                            Toast.makeText(
                                this@SelectNewProductActivity,
                                "Error cargando productos: $errProducts",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            },
            onError = { errStock ->
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@SelectNewProductActivity, "Error cargando stock: $errStock", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun showLoading(show: Boolean) {
        binding.fragmentContainer2.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

}
