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
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.ui.SimpleViewModelFactory
import com.example.comfyapp.ui.RobotInactivityNavigator

class TilesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTilesListBinding
    private lateinit var viewModel: TilesViewModel
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
    private val screenInactivityHandler = Handler(Looper.getMainLooper())
    private val screenInactivityTimeout = Runnable { openCategories() }

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

        if (savedInstanceState == null) viewModel.announceScreen()
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
            viewModel.onAction(TilesAction.Select(TileCategory.BATHROOMS))
        }
        btnZoneSocial.setOnClickListener {
            viewModel.onAction(TilesAction.Select(TileCategory.SOCIAL_AREAS))
        }
        btnExterior.setOnClickListener {
            viewModel.onAction(TilesAction.Select(TileCategory.EXTERIORS))
        }
    }

    private fun observeEffects() {
        viewModel.effect.observe(this) { effect ->
            when (effect) {
                is TilesEffect.OpenProductList ->
                    startActivity(ProductListUserActivity.createIntent(this, effect.request))
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

    companion object {
        private const val SCREEN_INACTIVITY_TIMEOUT_MS = 60_000L
    }
}
