package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.TemiController
import com.example.comfyapp.databinding.ActivityProductsUserBinding
import com.example.comfyapp.databinding.ActivityTilesListBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class ProductsUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductsUserBinding
    private lateinit var robot: Robot
    private lateinit var temiController: TemiController

    override fun onCreate(savedInstanceState: Bundle?) {
        robot = Robot.getInstance()
        robot.speak(TtsRequest.create("¡Perfecto!, ¿Qué tipo de producto estás buscondo? pisos y paredes, sanitarios o griferías. Toca en mi pantalla y te  mostraré las últimas tendencias de esta categoría",false))

        super.onCreate(savedInstanceState)
        binding = ActivityProductsUserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        temiController = TemiController(
            this,
            { status -> Log.d("TEMI", status) },
            {

            }
        )

        temiController.start()
        binding.btnRevestimientos.setOnClickListener {
            startActivity(Intent(this, TilesListActivity::class.java))
        }
        binding.imgbtnback.setOnClickListener {
            finish()
        }
        binding.floatingMenu.btnPromos.setOnClickListener {
            val localName = "promosemana2"
            temiController.goToLocation(localName)
        }
    }
    override fun onResume(){
        super.onResume()
        }
}
