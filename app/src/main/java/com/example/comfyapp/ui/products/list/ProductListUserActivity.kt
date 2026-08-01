// presenta las dos columnas del catalogo y controla el video de acompanamiento
package com.example.comfyapp.ui.products.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.R
import com.example.comfyapp.core.LocationEventManager
import com.example.comfyapp.data.repository.OdooProductCatalogRepository
import com.example.comfyapp.databinding.ActivityProductListUserBinding
import com.example.comfyapp.domain.model.CatalogProduct
import com.example.comfyapp.domain.model.Product
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.model.ProductVideo
import com.example.comfyapp.ui.SimpleViewModelFactory

class ProductListUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListUserBinding
    private lateinit var viewModel: ProductListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val request = readRequest() ?: run {
            Toast.makeText(this, "Configuración de productos inválida", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(
            this,
            SimpleViewModelFactory { ProductListViewModel(OdooProductCatalogRepository()) }
        )[ProductListViewModel::class.java]

        renderConfiguration(request)
        bindActions()
        observeViewModel()
        observeRobotArrival()
        viewModel.load(request)
    }

    override fun onDestroy() {
        if (isFinishing) LocationEventManager.clear()
        super.onDestroy()
    }

    private fun renderConfiguration(request: ProductListRequest) = with(binding) {
        tituloMenu.text = request.title
        ColumntitleOne.text = request.firstColumnTitle
        ColumntitleTwo.text = request.secondColumnTitle
        showVideoOverlay(request.video.toRawResource())
    }

    private fun bindActions() {
        binding.imgbtnback.setOnClickListener { finish() }
        binding.btnCloseVideo.setOnClickListener { closeVideoOverlay() }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            binding.loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.Secondloading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            if (state.isLoaded) {
                showProducts(state.firstColumn, R.id.fragmentContainerOne)
                showProducts(state.secondColumn, R.id.fragmentContainerTwo)
            }
        }
        viewModel.effect.observe(this) { effect ->
            when (effect) {
                is ProductListEffect.ShowError -> {
                    Toast.makeText(this, "Error: ${effect.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
                null -> return@observe
            }
            viewModel.effectHandled()
        }
    }

    private fun observeRobotArrival() {
        LocationEventManager.locationArrived.observe(this) { location ->
            if (location != null) closeVideoOverlay()
        }
    }

    private fun showProducts(products: List<CatalogProduct>, containerId: Int) {
        val items = products.map {
            Product(
                id = it.id,
                name = it.name,
                price = it.price,
                stock = it.stock,
                imageUrl = it.imageUrl,
                description = it.description,
                website_url = it.websiteUrl
            )
        }
        supportFragmentManager.beginTransaction()
            .replace(containerId, ProductListFragment.newInstance().apply { setProducts(items) })
            .commit()
    }

    private fun showVideoOverlay(videoResId: Int) = with(binding) {
        videoOverlayContainer.visibility = View.VISIBLE
        videoView.setVideoURI(Uri.parse("android.resource://$packageName/$videoResId"))
        videoView.setOnCompletionListener { videoView.start() }
        videoView.start()
    }

    fun closeVideoOverlay() = with(binding) {
        videoView.stopPlayback()
        videoOverlayContainer.visibility = View.GONE
    }

    @Suppress("DEPRECATION")
    private fun readRequest(): ProductListRequest? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getSerializableExtra(EXTRA_REQUEST, ProductListRequest::class.java)
    } else {
        intent.getSerializableExtra(EXTRA_REQUEST) as? ProductListRequest
    }

    private fun ProductVideo.toRawResource(): Int = when (this) {
        ProductVideo.SANITARY -> R.raw.sanitario
        ProductVideo.TAPS -> R.raw.griferias
        ProductVideo.FLOOR_AND_WALL -> R.raw.tendenciasreve
    }

    companion object {
        private const val EXTRA_REQUEST = "product_list_request"

        fun createIntent(context: Context, request: ProductListRequest) =
            Intent(context, ProductListUserActivity::class.java).apply {
                putExtra(EXTRA_REQUEST, request)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
