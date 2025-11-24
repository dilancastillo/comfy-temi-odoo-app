package com.example.comfyapp

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.SelectNewProductBinding
import java.math.BigDecimal
import java.math.RoundingMode
import com.robotemi.sdk.Robot


class SelectNewProductActivity : AppCompatActivity() {

    private lateinit var binding: SelectNewProductBinding

    // ✅ Mapa de zonas a IDs de Odoo
    private val zonaToCategoryIds = mapOf(
        "Pisos tipo madera" to listOf(376),
        "Mates marmolizados Corona" to listOf(377, 374),
        "Fachadas porcelanato" to listOf(2438)
    )
    private val zonaToTemiLocation = mapOf(
        "Pisos tipo madera" to "pisos tipo madera",       // nombre del punto de Temi
        "Mates marmolizados Corona" to "mates marmolizados corona",
        "Fachadas porcelanato" to "fachadas porcelanatos"
    )
    private var lastTemiLocation: String? = null

    fun AppCompatActivity.showCustomToast(message: String, duration: Int = Toast.LENGTH_LONG) {
        val toast = Toast(this)
        toast.duration = duration

        // Crear un TextView con estilo personalizado
        val textView = TextView(this)
        textView.text = message
        textView.textSize = 25f  // tamaño en sp
        textView.setPadding(20, 10, 20, 10)
        textView.setTextColor(Color.WHITE)
        textView.gravity = Gravity.CENTER

        // Fondo con color y bordes redondeados
        val background = GradientDrawable()
        background.shape = GradientDrawable.RECTANGLE
        background.cornerRadius = 0f
        background.setColor(0xFFE9630B.toInt())
        textView.background = background

        toast.view = textView
        toast.show()
    }



    private fun getSelectedDateRange(): Pair<String, String>? {
        val selectedId = binding.toggleButton.checkedButtonId
        if (selectedId == View.NO_ID) return null

        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val today = java.time.LocalDate.now()
        val startDate: java.time.LocalDate = when (selectedId) {
            R.id.btnYesterday -> today.minusDays(1)
            R.id.btnLast7Days -> today.minusDays(7)
            R.id.btnLast2Weeks -> today.minusWeeks(2)
            R.id.btnLastMonth -> today.minusMonths(1)
            else -> today
        }

        val start = startDate.atStartOfDay().format(formatter)
        val end = today.atTime(23, 59, 59).format(formatter)
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
                showCustomToast("Seleccione una zona válida")
                return@setOnClickListener
            }

            val dateRange = getSelectedDateRange()
            if (dateRange == null) {

                showCustomToast("Seleccione un rango de fechas")

                return@setOnClickListener
            }
            // ✅ Mover Temi a la zona seleccionada
            loadProductsWithLastEntry(categoryIds, dateRange, onMovementsFetched = { hasMovements ->
                if (hasMovements) {
                    // Solo mover Temi si hay movimientos y si no está ya allí
                    val locationName = zonaToTemiLocation[zonaSeleccionada]
                    Log.d("TEMI_MOVE", "Intentando mover a: $locationName")
                    if (locationName != null && lastTemiLocation != locationName) {
                        try {
                            val robot = Robot.getInstance()
                            robot.goTo(locationName)
                            Toast.makeText(this, "Moviéndose a $locationName", Toast.LENGTH_SHORT).show()
                            lastTemiLocation = locationName
                        } catch (e: Exception) {
                            Log.e("TEMI_MOVE", "Error moviendo Temi: ${e.message}")
                        }
                    }
                }
            })


        }
    }

    private fun loadProductsWithLastEntry(categoryIds: List<Int>, dateRange: Pair<String, String>, onMovementsFetched: (hasMovements: Boolean) -> Unit) {
        showLoading(true)

        // 1️⃣ Traer movimientos de entrada hechos (incoming done) en el rango de fecha
        val domainMoves = buildList<Any> {
            add(listOf("picking_type_id.code", "=", "incoming"))
            add(listOf("state", "=", "done"))

            // Agregar categorías con OR
            if (categoryIds.size == 1) {
                add(listOf("product_id.categ_id", "=", categoryIds[0]))
            } else {
                // Primero agregar el operador OR
                for (i in 1 until categoryIds.size) {
                    add("|")
                }
                // Luego agregar las condiciones
                categoryIds.forEach { catId ->
                    add(listOf("product_id.categ_id", "=", catId))
                }
            }

            add(listOf("date", ">=", dateRange.first))
            add(listOf("date", "<=", dateRange.second))
            add(listOf("location_dest_id.complete_name", "=", "Tunja/Input"))
        } as List<List<Any>>

        val fieldsMoves = mapOf(
            "product_id" to true,
            "product_uom_qty" to true,
            "reference" to true,
            "date" to true,
            "picking_id" to true,
            "location_dest_id" to true
        )

        OdooHelper.executeOdooRpc(
            model = "stock.move",
            method = "search_read",
            domain = domainMoves,
            fields = fieldsMoves,
            order = "date desc",
            limit = 9000,
            onSuccess = { resultMoves ->
                if (resultMoves.isEmpty()) {
                    runOnUiThread {
                        showLoading(false)
                        showCustomToast("No se encontraron movimientos de entrada para esa fecha")
                        // Limpiar el fragment container
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer2, BlankFragment())
                            .commit()
                    }
                    onMovementsFetched(false)
                    return@executeOdooRpc
                }

                // ✅ Notificar que SÍ hay movimientos (para que Temi se mueva)
                onMovementsFetched(true)

                // Extraer IDs únicos de productos
                val productIds = resultMoves.map {
                    it.asJsonObject["product_id"].asJsonArray[0].asInt
                }.distinct()

                // 2️⃣ Verificar cuáles de esos productos tienen stock > 0
                val domainStock = listOf(
                    listOf("id", "in", productIds),
                    listOf("qty_available", ">", 0)
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
                    domain = domainStock,
                    fields = fieldsProducts,
                    order = "name asc",
                    limit = 5000,
                    onSuccess = { resultProducts ->
                        val productsWithStock = resultProducts.mapNotNull { elem ->
                            try {
                                val obj = elem.asJsonObject
                                Product(
                                    id = obj["id"].asInt,
                                    name = obj["name"].asString,
                                    price = obj["list_price"].asDouble,
                                    imageBase64 = obj["image_128"]?.asString,
                                    stock = BigDecimal(obj["qty_available"].asDouble)
                                        .setScale(2, RoundingMode.HALF_UP)
                                        .toDouble(),
                                    description = obj["description"]?.asString,
                                    lastEntryQty = 0.0,
                                    lastEntryDate = ""
                                )
                            } catch (e: Exception) {
                                Log.e("ODOO_PARSE", "Error parseando producto: ${e.message}")
                                null
                            }
                        }

                        // Agrupar movimientos por producto y obtener el más reciente
                        val lastMovesMap = resultMoves
                            .groupBy { it.asJsonObject["product_id"].asJsonArray[0].asInt }
                            .mapValues { entry ->
                                entry.value.maxByOrNull {
                                    it.asJsonObject["date"].asString
                                }?.asJsonObject
                            }

                        // Asignar la última entrada solo a productos con stock
                        productsWithStock.forEach { p ->
                            lastMovesMap[p.id]?.let { move ->
                                p.lastEntryQty = move["product_uom_qty"].asDouble
                                p.lastEntryDate = move["date"].asString
                            }
                        }

                        runOnUiThread {
                            showLoading(false)
                            if (productsWithStock.isEmpty()) {
                                Toast.makeText(
                                    this@SelectNewProductActivity,
                                    "No se encontraron productos con stock",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            supportFragmentManager.beginTransaction()
                                .replace(
                                    R.id.fragmentContainer2,
                                    ProductListFragment.newInstance(productsWithStock)
                                )
                                .commit()
                        }
                    },
                    onError = { errProducts ->
                        runOnUiThread {
                            showLoading(false)
                            Toast.makeText(
                                this@SelectNewProductActivity,
                                "Error verificando stock: $errProducts",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
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
    }
    class BlankFragment : androidx.fragment.app.Fragment(R.layout.fragment_blank)


    private fun showLoading(show: Boolean) {
        binding.fragmentContainer2.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

}