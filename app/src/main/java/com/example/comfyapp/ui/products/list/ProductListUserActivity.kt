// presenta las dos columnas del catalogo y controla el video de acompanamiento
package com.example.comfyapp.ui.products.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.comfyapp.logging.PersistentLog as Log
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
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.robot.TemiSessionManager
import com.example.comfyapp.ui.RobotInactivityNavigator
import com.example.comfyapp.ui.products.category.ProductsUserActivity

class ProductListUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListUserBinding
    private lateinit var viewModel: ProductListViewModel
    private val firstColumnFragment = ProductListFragment.newInstance()
    private val secondColumnFragment = ProductListFragment.newInstance()
    private val robotRepository by lazy { TemiRobotRepository(applicationContext) }
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
    // Si la navegación se interrumpe (falla o el cliente la detiene) se vuelve a ofrecer "Ir a verlos".
    private val navigationFailureListener: () -> Unit = {
        closeVideoOverlay()
        updateGoToButton(travelling = false)
    }
    private var travelling = false
    // Temi dejó de caminar sin llegar (la detuvieron tocando la pantalla, por ejemplo): tras un
    // momento, por si el aviso de llegada aún no se procesó, se cierra el video y se ofrece el botón.
    private val navigationStateListener: (Boolean) -> Unit = { isNavigating ->
        if (!isNavigating && travelling) {
            binding.root.postDelayed({
                if (travelling && !arrivedAtProducts && !robotRepository.isNavigationInProgress()) {
                    travelling = false
                    closeVideoOverlay()
                    updateGoToButton(travelling = false)
                }
            }, NAVIGATION_SETTLE_MS)
        }
    }
    private var screenEnteredAtMs = 0L
    private var requestedRobotLocation: String? = null
    private val catalogErrorHandler = Handler(Looper.getMainLooper())
    private val stationaryScreenHandler = Handler(Looper.getMainLooper())
    private var useStationaryScreenTimeout = false
    private val catalogErrorTimeout = Runnable {
        binding.catalogErrorContainer.visibility = View.GONE
        robotRepository.speak("Dime, ¿en qué te puedo ayudar?")
        openCategories("catalog_error_timeout")
    }
    private val stationaryScreenTimeout = Runnable {
        robotRepository.speak("Dime, ¿en qué te puedo ayudar?")
        openCategories("navigation_not_started_timeout")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenEnteredAtMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "screen_enter elapsedRealtimeMs=$screenEnteredAtMs")
        binding = ActivityProductListUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val request = readRequest() ?: run {
            Toast.makeText(this, "Configuración de productos inválida", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        requestedRobotLocation = request.robotLocation
        LocationEventManager.clear()
        useStationaryScreenTimeout =
            !request.showTravelVideo && robotRepository.isAtCenterSala()

        viewModel = ViewModelProvider(
            this,
            SimpleViewModelFactory { ProductListViewModel(OdooProductCatalogRepository()) }
        )[ProductListViewModel::class.java]

        renderConfiguration(request)
        travelling = request.showTravelVideo
        updateGoToButton(travelling)
        setupProductColumns()
        bindActions()
        observeViewModel()
        observeRobotArrival()
        viewModel.load(request)
    }

    override fun onDestroy() {
        cancelCatalogErrorTimeout()
        cancelStationaryScreenTimeout()
        if (isFinishing) LocationEventManager.clear()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        inactivityNavigator.start()
        TemiSessionManager.addNavigationFailureListener(navigationFailureListener)
        TemiSessionManager.addNavigationStateListener(navigationStateListener)
        robotRepository.start()
        if (useStationaryScreenTimeout) scheduleStationaryScreenTimeout()
    }

    override fun onStop() {
        Log.i(
            TAG,
            "screen_stop durationMs=${SystemClock.elapsedRealtime() - screenEnteredAtMs} " +
                "isFinishing=$isFinishing"
        )
        cancelCatalogErrorTimeout()
        cancelStationaryScreenTimeout()
        inactivityNavigator.stop()
        TemiSessionManager.removeNavigationFailureListener(navigationFailureListener)
        TemiSessionManager.removeNavigationStateListener(navigationStateListener)
        robotRepository.stop()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (binding.catalogErrorContainer.visibility == View.VISIBLE) {
            Log.i(TAG, "generic_interaction_ignored_while_error_panel_is_visible")
            return
        }
        if (useStationaryScreenTimeout) scheduleStationaryScreenTimeout()
        robotRepository.notifyUserInteraction()
    }

    private fun renderConfiguration(request: ProductListRequest) = with(binding) {
        tituloMenu.text = request.title
        ColumntitleOne.text = request.firstColumnTitle
        ColumntitleTwo.text = request.secondColumnTitle
        if (request.showTravelVideo) showVideoOverlay(request.video.toRawResource())
    }

    private fun bindActions() {
        binding.imgbtnback.setOnClickListener { finish() }
        binding.btnCloseVideo.setOnClickListener {
            closeVideoOverlay()
            robotRepository.cancelNavigationByUser()
        }
        binding.btnRetryCatalog.setOnClickListener {
            cancelCatalogErrorTimeout()
            binding.catalogErrorContainer.visibility = View.GONE
            viewModel.retry()
        }
        binding.btnBackToCategories.setOnClickListener {
            cancelCatalogErrorTimeout()
            openCategories("back_to_categories_button")
        }
        binding.btnGoToProducts.setOnClickListener { goToProducts() }
        binding.floatingMenu.btnHablar.setOnClickListener {
            openCategories("btn_hablar", startAgent = true)
        }
    }

    private fun setupProductColumns() {
        firstColumnFragment.setOnLoadMore(viewModel::loadNextPage)
        secondColumnFragment.setOnLoadMore(viewModel::loadNextPage)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerOne, firstColumnFragment)
            .replace(R.id.fragmentContainerTwo, secondColumnFragment)
            .commit()
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            val showInitialLoading = state.isLoading &&
                state.firstColumn.isEmpty() && state.secondColumn.isEmpty()
            binding.loading.visibility = if (showInitialLoading) View.VISIBLE else View.GONE
            binding.Secondloading.visibility = if (showInitialLoading) View.VISIBLE else View.GONE
            if (state.isLoaded) {
                cancelCatalogErrorTimeout()
                binding.catalogErrorContainer.visibility = View.GONE
                firstColumnFragment.setProducts(state.firstColumn.toProducts())
                secondColumnFragment.setProducts(state.secondColumn.toProducts())
            }
        }
        viewModel.effect.observe(this) { effect ->
            when (effect) {
                ProductListEffect.ShowInitialError -> {
                    Log.i(
                        TAG,
                        "catalog_error_panel_shown " +
                            "durationMs=${SystemClock.elapsedRealtime() - screenEnteredAtMs}"
                    )
                    closeVideoOverlay()
                    cancelStationaryScreenTimeout()
                    robotRepository.cancelNavigationForCatalogError()
                    binding.catalogErrorContainer.visibility = View.VISIBLE
                    scheduleCatalogErrorTimeout("initial_error")
                }
                ProductListEffect.ShowPaginationError ->
                    Toast.makeText(
                        this,
                        "No pudimos cargar más productos. Inténtalo nuevamente.",
                        Toast.LENGTH_LONG
                    ).show()
                null -> return@observe
            }
            viewModel.effectHandled()
        }
    }

    private fun observeRobotArrival() {
        LocationEventManager.locationArrived.observe(this) { location ->
            if (location.equals(requestedRobotLocation, ignoreCase = true)) {
                closeVideoOverlay()
                travelling = false
                arrivedAtProducts = true
                updateGoToButton(travelling = false)
            }
        }
    }

    private var arrivedAtProducts = false

    // El botón solo tiene sentido si la lista tiene una zona y Temi no está yendo ni ya llegó allí.
    private fun updateGoToButton(travelling: Boolean) {
        val canGo = !requestedRobotLocation.isNullOrBlank() && !travelling && !arrivedAtProducts
        binding.btnGoToProducts.visibility = if (canGo) View.VISIBLE else View.GONE
    }

    private fun goToProducts() {
        val location = requestedRobotLocation?.takeIf { it.isNotBlank() } ?: return
        val request = readRequest() ?: return
        Log.i(TAG, "go_to_products location=$location")
        if (!robotRepository.goToLocation(location, arrivalMessage = PRODUCTS_ARRIVAL_TEXT)) return
        travelling = true
        // Ya camina hacia la zona: la pantalla no debe volver sola a categorías por estar quieta.
        useStationaryScreenTimeout = false
        cancelStationaryScreenTimeout()
        updateGoToButton(travelling = true)
        showVideoOverlay(request.video.toRawResource())
    }

    private fun List<CatalogProduct>.toProducts() = map {
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

    private fun scheduleCatalogErrorTimeout(reason: String) {
        cancelCatalogErrorTimeout()
        Log.i(
            TAG,
            "catalog_error_timer_scheduled reason=$reason timeoutMs=$CATALOG_ERROR_TIMEOUT_MS " +
                "durationMs=${SystemClock.elapsedRealtime() - screenEnteredAtMs}"
        )
        catalogErrorHandler.postDelayed(catalogErrorTimeout, CATALOG_ERROR_TIMEOUT_MS)
    }

    private fun cancelCatalogErrorTimeout() {
        catalogErrorHandler.removeCallbacks(catalogErrorTimeout)
    }

    private fun scheduleStationaryScreenTimeout() {
        cancelStationaryScreenTimeout()
        Log.i(
            TAG,
            "stationary_screen_timer_scheduled timeoutMs=$STATIONARY_SCREEN_TIMEOUT_MS " +
                "durationMs=${SystemClock.elapsedRealtime() - screenEnteredAtMs}"
        )
        stationaryScreenHandler.postDelayed(
            stationaryScreenTimeout,
            STATIONARY_SCREEN_TIMEOUT_MS
        )
    }

    private fun cancelStationaryScreenTimeout() {
        stationaryScreenHandler.removeCallbacks(stationaryScreenTimeout)
    }

    private fun openCategories(reason: String, startAgent: Boolean = false) {
        Log.i(
            TAG,
            "return_to_categories reason=$reason " +
                "durationMs=${SystemClock.elapsedRealtime() - screenEnteredAtMs}"
        )
        startActivity(
            Intent(this, ProductsUserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(ProductsUserActivity.EXTRA_START_AGENT, startAgent)
            }
        )
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
        private const val TAG = "ProductListTiming"
        private const val EXTRA_REQUEST = "product_list_request"
        private const val CATALOG_ERROR_TIMEOUT_MS = 60_000L
        private const val STATIONARY_SCREEN_TIMEOUT_MS = 60_000L
        private const val NAVIGATION_SETTLE_MS = 1_000L
        private const val PRODUCTS_ARRIVAL_TEXT =
            "Llegamos. En esta zona puedes ver los productos que te estoy mostrando en pantalla, junto con otros diseños."

        fun createIntent(context: Context, request: ProductListRequest) =
            Intent(context, ProductListUserActivity::class.java).apply {
                putExtra(EXTRA_REQUEST, request)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
