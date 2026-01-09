package com.example.comfyapp

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.ModelProductStock
import com.example.comfyapp.data.repository.ProductRepository
import com.example.comfyapp.databinding.ActivityProductListUserBinding

class ProductListUser : AppCompatActivity() {

    private lateinit var binding: ActivityProductListUserBinding
    private val repository = ProductRepository()

    private val zoneToUsedInId = mapOf(
        "Baños" to 1,
        "Cocina" to 2
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()

        binding.btnSearch.setOnClickListener {
            val selectedZone = binding.spinnerZone.selectedItem.toString()
            val usedInId = zoneToUsedInId[selectedZone] ?: return@setOnClickListener

            repository.getProductsWithStockAndUrl(
                usedInId = usedInId,
                locationName = "Tunja",
                onSuccess = { products: List<ModelProductStock> ->
                    if (products.isEmpty()) {
                        Toast.makeText(this, "No hay productos disponibles", Toast.LENGTH_LONG).show()
                        return@getProductsWithStockAndUrl
                    }

                    // Convertir ModelProductStock a Product para el fragment
                    val productList = products.map { model ->
                        Product(
                            id = model.id,
                            name = model.name,
                            price = model.price,
                            stock = model.free_qty,
                            imageBase64 = model.imageBase64,
                            description = model.description
                        )
                    }

                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.fragmentContainer2,
                            ProductListFragment.newInstance(productList)
                        )
                        .commit()
                },
                onError = { err ->
                    Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun setupSpinner() {
        val zonas = zoneToUsedInId.keys.toList()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            zonas
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerZone.adapter = adapter
    }
}