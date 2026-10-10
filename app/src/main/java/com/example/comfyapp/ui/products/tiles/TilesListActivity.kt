// muestra los ambientes de pisos y paredes y ejecuta su navegacion
package com.example.comfyapp.ui.products.tiles

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.data.repository.OdooProductCatalogRepository
import com.example.comfyapp.domain.model.ProductFilters
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.logging.AssistanceEventLog
import com.example.comfyapp.ui.products.list.ProductListUserActivity
import com.example.comfyapp.CoverActivity
import com.example.comfyapp.ui.TalkHint
import com.example.comfyapp.ui.products.category.ProductsUserActivity
import com.example.comfyapp.databinding.ActivityTilesListBinding
import com.example.comfyapp.agent.AgentSpeechController
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.ui.SimpleViewModelFactory
import com.example.comfyapp.ui.RobotInactivityNavigator

class TilesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTilesListBinding
    private lateinit var viewModel: TilesViewModel
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
    private val screenInactivityHandler = Handler(Looper.getMainLooper())
    private val screenInactivityTimeout = Runnable { openCategories() }
    private val speechController = AgentSpeechController.shared
    private var waitingToOpenProductList = false
    private val catalog by lazy { OdooProductCatalogRepository() }
    // precio y color que pidió el cliente al hablar con Temi; vacío si llegó tocando la pantalla
    private val filters by lazy {
        ProductFilters(
            maxPrice = intent.getDoubleExtra(EXTRA_MAX_PRICE, -1.0).takeIf { it > 0 },
            color = intent.getStringExtra(EXTRA_COLOR),
            productType = intent.getStringExtra(EXTRA_PRODUCT_TYPE)
                ?.let { name -> runCatching { ProductType.valueOf(name) }.getOrNull() }
        )
    }
    private var checkingFilters = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTilesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            SimpleViewModelFactory {
                TemiRobotRepository(applicationContext).let(::TilesViewModel)
            }
        )[TilesViewModel::class.java]

        bindActions()
        observeEffects()

        if (savedInstanceState == null) {
            // Si llegamos desde el agente con una categoría preseleccionada, la disparamos directo
            val categoryName = intent.getStringExtra(EXTRA_CATEGORY)
            val preselected = categoryName?.let {
                runCatching { TileCategory.valueOf(it) }.getOrNull()
            }
            if (preselected != null) {
                viewModel.onAction(TilesAction.Select(preselected, filters))
            } else {
                viewModel.announceScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        inactivityNavigator.start()
        viewModel.startRobot()
        resetScreenInactivityTimer()
    }

    override fun onStop() {
        cancelScreenInactivityTimer()
        inactivityNavigator.stop()
        viewModel.stopRobot()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (waitingToOpenProductList) {
            return
        }
        resetScreenInactivityTimer()
        viewModel.onAction(TilesAction.UserInteraction)
    }

    override fun onDestroy() {
        cancelScreenInactivityTimer()
        super.onDestroy()
    }

    private fun bindActions() = with(binding) {
        imgbtnback.setOnClickListener { viewModel.onAction(TilesAction.Back) }
        btnbathrooms.setOnClickListener {
            handleTileSelection(TileCategory.BATHROOMS)
        }
        btnZoneSocial.setOnClickListener {
            handleTileSelection(TileCategory.SOCIAL_AREAS)
        }
        btnExterior.setOnClickListener {
            handleTileSelection(TileCategory.EXTERIORS)
        }
        TalkHint.attach(floatingMenu.tvTalkHint)
        floatingMenu.btnHablar.setOnClickListener { openCategories(startAgent = true) }
    }

    private fun observeEffects() {
        viewModel.effect.observe(this) { effect ->
            when (effect) {
                is TilesEffect.OpenProductList -> openProductList(effect.request)
                TilesEffect.Close -> finish()
                null -> return@observe
            }
            viewModel.effectHandled()
        }
    }

    private fun resetScreenInactivityTimer() {
        cancelScreenInactivityTimer()
        screenInactivityHandler.postDelayed(screenInactivityTimeout, SCREEN_INACTIVITY_TIMEOUT_MS)
    }

    private fun cancelScreenInactivityTimer() {
        screenInactivityHandler.removeCallbacks(screenInactivityTimeout)
    }

    private fun openCategories(startAgent: Boolean = false) {
        // "Hablar con Temi" vuelve a la pantalla principal, donde Temi conversa.
        if (startAgent) {
            startActivity(CoverActivity.talkIntent(this))
            return
        }
        startActivity(
            Intent(this, ProductsUserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    private fun openProductList(request: com.example.comfyapp.domain.model.ProductListRequest) {
        val openScreen = {
            if (!isFinishing) {
                startActivity(ProductListUserActivity.createIntent(this, request))
            }
        }
        if (request.showTravelVideo) {
            waitingToOpenProductList = true
            speechController.speak("¡Excelente! Acompáñame.") {
                if (waitingToOpenProductList) {
                    waitingToOpenProductList = false
                    openScreen()
                }
            }
        } else {
            openScreen()
        }
    }

    private fun handleTileSelection(category: TileCategory) {
        if (waitingToOpenProductList) {
            cancelPendingProductLaunch()
            return
        }
        // Sin precio ni color no hay nada que consultar: el tipo pedido ("pisos") se aplica directo.
        if (!filters.hasLimits) {
            viewModel.onAction(TilesAction.Select(category, filters))
            return
        }
        if (checkingFilters) return
        // Se consulta antes de abrir: si en ese ambiente no hay nada del color o precio pedido,
        // Temi lo dice y muestra todo, en vez de abrir una lista vacía.
        checkingFilters = true
        val base = TilesViewModel.requestFor(category)
        catalog.load(filters.applyTo(base), 0, onSuccess = { first, second ->
            checkingFilters = false
            AssistanceEventLog.event(
                "filter_checked",
                "location" to base.robotLocation,
                "max_price" to filters.maxPrice?.toLong(),
                "color" to filters.color,
                "results" to first.size + second.size
            )
            if (first.isEmpty() && second.isEmpty()) {
                speechController.speak(
                    "No tengo ${base.title.lowercase()}${filters.suffix()} en exhibición, " +
                        "pero te muestro todas las opciones."
                ) { if (!isFinishing) viewModel.onAction(TilesAction.Select(category, filters.withoutLimits())) }
            } else {
                viewModel.onAction(TilesAction.Select(category, filters))
            }
        }, onError = {
            // Si la consulta falla se abre sin filtros y la lista maneja su propio error.
            checkingFilters = false
            viewModel.onAction(TilesAction.Select(category, filters.withoutLimits()))
        })
    }

    private fun cancelPendingProductLaunch() {
        if (!waitingToOpenProductList) return
        waitingToOpenProductList = false
        speechController.stopSpeaking()
        viewModel.cancelNavigationByUser()
    }

    companion object {
        const val EXTRA_CATEGORY = "extra_tile_category"
        private const val EXTRA_MAX_PRICE = "extra_max_price"
        private const val EXTRA_COLOR = "extra_color"
        private const val EXTRA_PRODUCT_TYPE = "extra_product_type"

        fun createIntent(context: Context, category: TileCategory?, filters: ProductFilters): Intent =
            Intent(context, TilesListActivity::class.java).apply {
                category?.let { putExtra(EXTRA_CATEGORY, it.name) }
                filters.maxPrice?.let { putExtra(EXTRA_MAX_PRICE, it) }
                filters.color?.let { putExtra(EXTRA_COLOR, it) }
                filters.productType?.let { putExtra(EXTRA_PRODUCT_TYPE, it.name) }
            }
        private const val SCREEN_INACTIVITY_TIMEOUT_MS = 60_000L
    }
}
