package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.TemiApp
import com.example.comfyapp.core.TemiController
import com.example.comfyapp.databinding.ActivityProductsUserBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class ProductsUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductsUserBinding
    private lateinit var robot: Robot
    private lateinit var temiController: TemiController

    override fun onCreate(savedInstanceState: Bundle?) {
        robot = Robot.getInstance()
        robot.speak(TtsRequest.create("¡Bienvenido!, ¿Qué tipo de producto estás buscondo? pisos y paredes, sanitarios o griferías. Toca en mi pantalla y te  mostraré las últimas tendencias de esta categoría",false))

        super.onCreate(savedInstanceState)
        binding = ActivityProductsUserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        temiController = TemiController(
            this,
            { status -> Log.d("TEMI", status) },
            {

            }
        )
        TemiApp.temiController = temiController

        temiController.start()
        binding.btnRevestimientos.setOnClickListener {
            startActivity(Intent(this, TilesListActivity::class.java))
        }
        binding.imgbtnback.setOnClickListener {
            finish()
        }
        binding.floatingMenu.btnPromos.setOnClickListener {
           // val localName = "promosemana2"
           // temiController.goToLocation(localName)
        }
        binding.btnBathrooms.setOnClickListener {
            robot.speak(TtsRequest.create("¡Perfecto!, Acompáñame.",false))
            val intent = Intent(this, ProductListUser::class.java)
            val localName="sanitarios"
            temiController.goToLocation(localName)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.Sanitary.name)
            intent.putExtra("tituloMenu", "Sanitarios y Accesorios")
            intent.putExtra("columnTitleOne", "Combos")
            intent.putExtra("columnTitleTwo", "Solos")
            intent.putExtra("VIDEO_RES", R.raw.sanitario)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        binding.btnfaucets.setOnClickListener {
            robot.speak(TtsRequest.create("¡Perfecto!, Acompáñame.",false)) //val localName="griferías lavamanos"
            val localName="griferias"
            temiController.goToLocation(localName)
            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.Taps.name)
            intent.putExtra("tituloMenu", "Griferias")
            intent.putExtra("columnTitleOne", "Lavamanos")
            intent.putExtra("columnTitleTwo", "Lavaplatos")
            intent.putExtra("VIDEO_RES", R.raw.griferias)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

    }
    override fun onResume(){
        super.onResume()
        }
    override fun onUserInteraction() {
        super.onUserInteraction()
        TemiApp.temiController?.notifyUserInteraction()
    }

}
