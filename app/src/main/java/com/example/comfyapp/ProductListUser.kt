package com.example.comfyapp

import android.content.ContentValues.TAG
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.LocationEventManager
import com.example.comfyapp.core.ModelProductStock
import com.example.comfyapp.data.repository.ProductRepository
import com.example.comfyapp.databinding.ActivityProductListUserBinding

class ProductListUser : AppCompatActivity() {

    private lateinit var binding: ActivityProductListUserBinding
    private val repository = ProductRepository
    private var videoResId: Int = 0

    enum class ProductQueryType {
        USED_IN,
        CATEGORY,
        FloorAndWall,
        Taps,
        Sanitary
    }

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityProductListUserBinding.inflate(layoutInflater)
            setContentView(binding.root)

        val titulo = intent.getStringExtra("tituloMenu")
        binding.tituloMenu.text = titulo
        val columnTitleOne = intent.getStringExtra("columnTitleOne")
        binding.ColumntitleOne.text = columnTitleOne
        val columnTitleTwo = intent.getStringExtra("columnTitleTwo")
        binding.ColumntitleTwo.text = columnTitleTwo

        videoResId = intent.getIntExtra("VIDEO_RES", 0)

        // Si hay un video, mostrarlo
        if (videoResId != 0) {
            showVideoOverlay(videoResId)
        }

        // Configurar el botón de cerrar video manualmente
        binding.btnCloseVideo.setOnClickListener {
            closeVideoOverlay()
        }
        LocationEventManager.locationArrived.observe(this) { location ->
            if (location != null) {
                Log.d(TAG, "Robot llegó a: $location")
                closeVideoOverlay()
            }
        }


        val queryType = intent.getStringExtra("QUERY_TYPE")
                ?.let { ProductQueryType.valueOf(it) }

            showLoading()

            when (queryType) {
                /*
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
                }*/
                /*
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
                }*/
                ProductQueryType.FloorAndWall -> {
                    val usedInId =  intent.getIntegerArrayListExtra("USED_IN_ID")?: arrayListOf()

                    showLoading()



                    //unicamente paredes
                    repository.getProductsAllWall(
                        locationName = "Tunja/E",
                        usedInId = usedInId,
                        onSuccess = { products ->
                            showProductsInContainer(
                                products = products,
                                containerId = R.id.fragmentContainerOne
                            )
                            hideLoading()
                        },
                        onError = ::handleError
                    )
                    // pisos y apredes
                    repository.getProductsAllFloor(
                        locationName = "Tunja/E",
                        usedInId = usedInId,
                        onSuccess = { products ->
                            showProductsInContainer(
                                products = products,
                                containerId = R.id.fragmentContainerTwo
                            )
                        },
                        onError = ::handleError
                    )
                }
                ProductQueryType.Taps -> {

                    showLoading()

                    // lavamanos
                    repository.getProductsTapsLavaM(
                        locationName = "Tunja/E",
                        onSuccess = { products ->
                            showProductsInContainer(
                                products = products,
                                containerId = R.id.fragmentContainerOne
                            )
                        },
                        onError = ::handleError
                    )

                    //lavaplatos
                    repository.getProductsTapsLavaP(
                        locationName = "Tunja/E",
                        onSuccess = { products ->
                            showProductsInContainer(
                                products = products,
                                containerId = R.id.fragmentContainerTwo
                            )
                            hideLoading()
                        },
                        onError = ::handleError
                    )
                }
                ProductQueryType.Sanitary -> {

                    showLoading()

                    // sanitarios - combos
                    repository.getProductsSanitaryCombo(
                        locationName = "Tunja/E",
                        onSuccess = { products ->
                            showProductsInContainer(
                                products = products,
                                containerId = R.id.fragmentContainerOne
                            )
                        },
                        onError = ::handleError
                    )

                    //sanitarios - solos
                    repository.getProductsSanitaryOnly(
                        locationName = "Tunja/E",
                        onSuccess = { products ->
                            showProductsInContainer(
                                products = products,
                                containerId = R.id.fragmentContainerTwo
                            )
                            hideLoading()
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
    private fun showProductsInContainer(
        products: List<ModelProductStock>,
        containerId: Int
    ) {
        if (products.isEmpty()) return

        val productList = products.map { model ->
            Product(
                id = model.id,
                name = model.name,
                price = model.price,
                stock = model.free_qty,
                imageUrl = model.imageUrl,
                description = model.description,
                website_url = model.website_url
            )
        }

        val fragment = ProductListFragment.newInstance()
        fragment.setProducts(productList)

        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commit()
    }

    private fun handleError(err: String) {
    hideLoading()
    Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
    finish()
}

private fun showLoading() {
        binding.loading.visibility = View.VISIBLE
        binding.Secondloading.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loading.visibility = View.GONE
        binding.Secondloading.visibility = View.GONE
    }

    private fun showVideoOverlay(videoResId: Int) {
        binding.videoOverlayContainer.visibility = View.VISIBLE

        val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
        binding.videoView.setVideoURI(videoUri)

        // Configurar listener para cuando termine el video
        binding.videoView.setOnCompletionListener {
            // Opcional: reiniciar el video o cerrarlo
            binding.videoView.start() // Para loop
        }

        // Iniciar reproducción
        binding.videoView.start()
    }

    fun closeVideoOverlay() {
        binding.videoView.stopPlayback()
        binding.videoOverlayContainer.visibility = View.GONE
    }
    override fun onDestroy() {
        super.onDestroy()

        LocationEventManager.clear()
    }

}