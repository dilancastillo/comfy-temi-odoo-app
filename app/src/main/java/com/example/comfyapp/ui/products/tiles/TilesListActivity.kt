// muestra los ambientes de pisos y paredes y ejecuta su navegacion
package com.example.comfyapp.ui.products.tiles

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.ui.products.list.ProductListUserActivity
import com.example.comfyapp.databinding.ActivityTilesListBinding
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.ui.SimpleViewModelFactory

class TilesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTilesListBinding
    private lateinit var viewModel: TilesViewModel

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
        viewModel.startRobot()
    }

    override fun onStop() {
        viewModel.stopRobot()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onAction(TilesAction.UserInteraction)
    }

    private fun bindActions() = with(binding) {
        imgbtnback.setOnClickListener { viewModel.onAction(TilesAction.Back) }
        btnbathrooms.setOnClickListener {
            viewModel.onAction(TilesAction.Select(TileCategory.BATHROOMS))
        }
        btnKitchens.setOnClickListener {
            viewModel.onAction(TilesAction.Select(TileCategory.KITCHENS))
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
}
