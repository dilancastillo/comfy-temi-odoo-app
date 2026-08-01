// muestra las categorias principales y ejecuta los efectos indicados por su modelo de vista
package com.example.comfyapp.ui.products.category

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.ui.products.list.ProductListUserActivity
import com.example.comfyapp.ui.products.tiles.TilesListActivity
import com.example.comfyapp.databinding.ActivityProductsUserBinding
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.ui.SimpleViewModelFactory

class ProductsUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductsUserBinding
    private lateinit var viewModel: ProductsUserViewModel

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

        if (savedInstanceState == null) viewModel.announceScreen()
    }

    override fun onStart() {
        super.onStart()
        viewModel.startRobot()
    }

    override fun onStop() {
        viewModel.stopRobot()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onAction(ProductsUserAction.UserInteraction)
    }

    private fun bindActions() = with(binding) {
        btnRevestimientos.setOnClickListener {
            viewModel.onAction(ProductsUserAction.SelectFloorAndWall)
        }
        btnBathrooms.setOnClickListener {
            viewModel.onAction(ProductsUserAction.SelectSanitary)
        }
        btnfaucets.setOnClickListener {
            viewModel.onAction(ProductsUserAction.SelectTaps)
        }
        imgbtnback.setOnClickListener {
            viewModel.onAction(ProductsUserAction.Back)
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
        startActivity(ProductListUserActivity.createIntent(this, request))
    }
}
