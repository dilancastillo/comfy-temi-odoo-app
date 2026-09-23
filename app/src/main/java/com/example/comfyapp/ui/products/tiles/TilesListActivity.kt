// muestra los ambientes de pisos y paredes y ejecuta su navegacion
package com.example.comfyapp.ui.products.tiles

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.ui.products.list.ProductListUserActivity
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
                viewModel.onAction(TilesAction.Select(preselected))
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
        floatingMenu.btnHablar.setOnClickListener { openCategories() }
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

    private fun openCategories() {
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
        viewModel.onAction(TilesAction.Select(category))
    }

    private fun cancelPendingProductLaunch() {
        if (!waitingToOpenProductList) return
        waitingToOpenProductList = false
        speechController.stopSpeaking()
        viewModel.cancelNavigationByUser()
    }

    companion object {
        const val EXTRA_CATEGORY = "extra_tile_category"
        private const val SCREEN_INACTIVITY_TIMEOUT_MS = 60_000L
    }
}
