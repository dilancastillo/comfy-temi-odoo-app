package com.example.comfyapp

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.ModelProductStock
import com.example.comfyapp.data.repository.ProductRepository
import com.example.comfyapp.databinding.ActivityProductListUserBinding

class ProductListUser : AppCompatActivity() {

    private lateinit var binding: ActivityProductListUserBinding
    private val repository = ProductRepository()
    private var used_In: Int = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListUserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        used_In = intent.getIntExtra("EXTRA_ID", -1)



            repository.getProductsWithStockAndUrl(
                usedInId = used_In,
                locationName = "Tunja",
                onSuccess = { products: List<ModelProductStock> ->
                    Log.d("REPO_RESULT", "Productos recibidos: ${products.size}")
                    Log.d("EXTRA_ID", "usedInId recibido = $used_In")
                    products.forEach {
                        Log.d(
                            "REPO_RESULT",
                            "id=${it.id} | name=${it.name} | websiteUrl=${it.website_url}"
                        )
                    }
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
                            description = model.description,
                            website_url = model.website_url
                        )
                    }

                    runOnUiThread {
                        supportFragmentManager.beginTransaction()
                            .replace(
                                R.id.fragmentContainer3,
                                ProductListFragment.newInstance(productList)
                            )
                            .commit()
                    }
                },
                onError = { err ->
                    Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                }
            )

        binding.imgbtnback.setOnClickListener {
            finish()
        }
    }


}