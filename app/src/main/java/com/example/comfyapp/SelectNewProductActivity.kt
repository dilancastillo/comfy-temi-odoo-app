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

        // Si no se ha seleccionado nada, retornar null
        if (selectedId == View.NO_ID) return null

        // Formato Odoo
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

        // Funcionalidad de la flecha
        binding.imgbtnback.setOnClickListener { finish() }

        // Configurar Spinner con las zonas disponibles
        val zonas = zonaToCategoryIds.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zonas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerZona.adapter = adapter


        // BOTÓN BUSCAR
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

            loadProductsFromOdoo(categoryIds, dateRange)
        }

    }





    private fun loadProductsFromOdoo(categoryIds: List<Int>, dateRange: Pair<String, String>) {
        showLoading(true)

        // Construimos el domain usando "in" para varios IDs
        val domain = listOf(
            listOf("categ_id", "in", categoryIds),
            listOf("create_date", ">=", dateRange.first),
            listOf("create_date", "<=", dateRange.second)
        )

        // Campos que queremos obtener
        val fields = mapOf(
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
            domain = domain,
            fields = fields,
            onSuccess = { result ->
                val products = result.mapNotNull { elem ->
                    try {
                        val obj = elem.asJsonObject
                        Product(
                            id = obj["id"].asInt,
                            name = obj["name"].asString,
                            price = obj["list_price"].asDouble,
                            imageBase64 = obj["image_128"]?.asString,
                            stock = obj["qty_available"].asInt,
                            description = obj["description"]?.asString
                        )
                    } catch (e: Exception) {
                        Log.e("ODDO_PARSE", "Error parseando producto: ${e.message}")
                        null
                    }
                }

                runOnUiThread {
                    showLoading(false)
                    if (products.isEmpty()) {
                        Toast.makeText(this@SelectNewProductActivity, "No se encontraron productos", Toast.LENGTH_SHORT).show()
                    }

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer2, ProductListFragment.newInstance(products))
                        .commit()
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@SelectNewProductActivity, "Error cargando productos: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        )
    }



    private fun showLoading(show: Boolean) {
        binding.fragmentContainer2.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

}
