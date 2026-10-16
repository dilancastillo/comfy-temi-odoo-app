// muestra las categorias principales y ejecuta los efectos indicados por su modelo de vista
package com.example.comfyapp.ui.products.category

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.CoverActivity
import com.example.comfyapp.agent.AgentSpeechController
import com.example.comfyapp.databinding.ActivityProductsUserBinding
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.robot.TemiSessionManager
import com.example.comfyapp.ui.RobotInactivityNavigator
import com.example.comfyapp.ui.SimpleViewModelFactory
import com.example.comfyapp.ui.TalkHint
import com.example.comfyapp.ui.products.list.ProductListUserActivity
import com.example.comfyapp.ui.products.tiles.TilesListActivity

class ProductsUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductsUserBinding
    private lateinit var viewModel: ProductsUserViewModel
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
    private val speechController = AgentSpeechController.shared
    private var waitingToOpenProductList = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductsUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            SimpleViewModelFactory {
                TemiRobotRepository(applicationContext).let(::ProductsUserViewModel)
            }
        )[ProductsUserViewModel::class.java]

        bindActions()
        observeEffects()
        TalkHint.attach(binding.floatingMenu.tvTalkHint)
    }

    override fun onStop() {
        inactivityNavigator.stop()
        viewModel.stopRobot()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        inactivityNavigator.start()
        viewModel.startRobot()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (waitingToOpenProductList) {
            return
        }
        viewModel.onAction(ProductsUserAction.UserInteraction)
    }

    private fun bindActions() = with(binding) {
        btnRevestimientos.setOnClickListener {
            handleCategorySelection(ProductsUserAction.SelectFloorAndWall)
        }
        btnBathrooms.setOnClickListener {
            handleCategorySelection(ProductsUserAction.SelectSanitary)
        }
        btnfaucets.setOnClickListener {
            handleCategorySelection(ProductsUserAction.SelectTaps)
        }
        imgbtnback.setOnClickListener {
            viewModel.onAction(ProductsUserAction.Back)
        }
        // La conversación vive en la pantalla principal: se vuelve allá y Temi empieza a escuchar.
        floatingMenu.btnHablar.setOnClickListener {
            if (!viewModel.isRobotNavigating()) startActivity(CoverActivity.talkIntent(this@ProductsUserActivity))
        }
    }

    private fun observeEffects() {
        viewModel.effect.observe(this) { effect ->
            when (effect) {
                ProductsUserEffect.OpenTiles ->
                    startActivity(Intent(this, TilesListActivity::class.java))
                is ProductsUserEffect.OpenProductList -> openProductList(effect.request)
                ProductsUserEffect.Close -> finish()
                null -> return@observe
            }
            viewModel.effectHandled()
        }
    }

    private fun openProductList(request: ProductListRequest) {
        val openScreen = {
            if (!isFinishing) {
                startActivity(ProductListUserActivity.createIntent(this, request))
            }
        }
        if (request.showTravelVideo) {
            waitingToOpenProductList = true
            speechController.speak(com.example.comfyapp.domain.usecase.NavigationMessages.DEPARTURE_SPEECH) {
                if (waitingToOpenProductList) {
                    waitingToOpenProductList = false
                    openScreen()
                }
            }
        } else {
            openScreen()
        }
    }

    private fun handleCategorySelection(action: ProductsUserAction) {
        if (waitingToOpenProductList) {
            cancelPendingProductLaunch()
            return
        }
        viewModel.onAction(action)
    }

    private fun cancelPendingProductLaunch() {
        if (!waitingToOpenProductList) return
        waitingToOpenProductList = false
        speechController.stopSpeaking()
        TemiSessionManager.cancelNavigationByUser()
    }
}
