package com.example.comfyapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.ModelProductStock
import com.example.comfyapp.data.repository.ProductRepository
import com.example.comfyapp.databinding.ActivityProductListUserBinding

class ProductListUser : AppCompatActivity() {

    private lateinit var binding: ActivityProductListUserBinding
    private val repository = ProductRepository()

    enum class ProductQueryType {
        USED_IN,
        CATEGORY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityProductListUserBinding.inflate(layoutInflater)
            setContentView(binding.root)

        val titulo = intent.getStringExtra("tituloMenu")
        binding.tituloMenu.text = titulo

        val queryType = intent.getStringExtra("QUERY_TYPE")
                ?.let { ProductQueryType.valueOf(it) }

            showLoading()

            when (queryType) {

                ProductQueryType.USED_IN -> {
                    val usedInId = intent.getIntExtra("USED_IN_ID", -1)

                    repository.getProductsWithStockAndUrl(
                        usedInId = usedInId,
                        locationName = "Tunja",
                        onSuccess = { products ->
                            hideLoading()
                            showProducts(products)
                        },
                        onError = ::handleError
                    )
                }

                ProductQueryType.CATEGORY -> {
                    val categoryId = intent.getIntExtra("CATEGORY_ID", -1)
                    val locationId = intent.getIntExtra("LOCATION_ID", -1)

                    repository.getProductsByCategoryAndLocation(
                        categoryId = categoryId,
                        locationId = locationId,
                        onSuccess = { products ->
                            hideLoading()
                            showProducts(products)
                        },
                        onError = ::handleError
                    )
                }

                else -> {
                    hideLoading()
                    Toast.makeText(this, "Tipo de búsqueda inválido", Toast.LENGTH_LONG).show()
                }
            }

            binding.imgbtnback.setOnClickListener { finish() }
        }
    private fun showProducts(products: List<ModelProductStock>) {
        if (products.isEmpty()) {
            Toast.makeText(this, "No hay productos disponibles", Toast.LENGTH_LONG).show()
            return
        }

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

        val fragment = ProductListFragment.newInstance()
        fragment.setProducts(productList)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer3, fragment)
            .commit()
    }
private fun handleError(err: String) {
    hideLoading()
    Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
    finish()
}

private fun showLoading() {
        binding.loading.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loading.visibility = View.GONE
    }



}