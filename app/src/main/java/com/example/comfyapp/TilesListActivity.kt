package com.example.comfyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.example.comfyapp.core.TemiController
import com.example.comfyapp.databinding.ActivityTilesListBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest



class TilesListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTilesListBinding
    private lateinit var robot: Robot
    private lateinit var temiController: TemiController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        robot = Robot.getInstance()
        robot.speak(TtsRequest.create("¡Súper!. ¿Para dónde estás buscando estos productos?. Toca mi pantalla, y acompáñame",false))
        binding = ActivityTilesListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        temiController = TemiController(
            this,
            { status -> Log.d("TEMI", status) },
            {
                runOnUiThread {
                    stopTrayectoVideo()
                }
            }
        )

        temiController.start()
        binding.imgbtnback.setOnClickListener {
            finish()
        }
        binding.btnbathrooms.setOnClickListener {
            val localName= "pisos exteriores alfa"
            robot.speak(TtsRequest.create("¡Perfecto!. Acompañame!",false))
            temiController.goToLocation(localName)
            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.FloorAndWall.name)
            intent.putExtra("USED_IN_ID", arrayListOf(30))
            intent.putExtra("tituloMenu", "Pisos y paredes para Baños y zonas húmedas")
            intent.putExtra("columnTitleOne", "Únicamente para Paredes")
            intent.putExtra("columnTitleTwo", "Para Pisos y Paredes")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        binding.btnKitchens.setOnClickListener {
            val localName="pisos exteriores alfa"
            robot.speak(TtsRequest.create("¡Excelente!. Acompañame!", false))
            temiController.goToLocation(localName)
            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.FloorAndWall.name)
            intent.putExtra("USED_IN_ID", arrayListOf(29))
            intent.putExtra("tituloMenu", "Pisos y paredes para Cocinas")
            intent.putExtra("columnTitleOne", "Únicamente para Paredes")
            intent.putExtra("columnTitleTwo", "Para Pisos y Paredes")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        binding.btnZoneSocial.setOnClickListener {
            robot.speak(TtsRequest.create("¡Excelente!. Acompañame!", false))
            temiController.goToLocation("pisos exteriores alfa")
            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.FloorAndWall.name)
            intent.putExtra("USED_IN_ID", arrayListOf(31,28,33))
            intent.putExtra("tituloMenu", "Pisos y Paredes para Zonas Sociales")
            intent.putExtra("columnTitleOne", "Únicamente para Paredes")
            intent.putExtra("columnTitleTwo", "Para Pisos y Paredes")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        binding.btnExterior.setOnClickListener {
            robot.speak(TtsRequest.create("¡Excelente!. Acompañame!", false))
            temiController.goToLocation("pisos exteriores alfa")
            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.FloorAndWall.name)
            intent.putExtra("USED_IN_ID", arrayListOf(32))
            intent.putExtra("tituloMenu", "Pisos y Paredes para Exteriores")
            intent.putExtra("columnTitleOne", "Únicamente para Paredes")
            intent.putExtra("columnTitleTwo", "Para Pisos y Paredes")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        binding.floatingMenu.btnPromos.setOnClickListener {
           // val localName = "promosemana2"
           // temiController.goToLocation(localName)
        }


    }
    private fun stopTrayectoVideo() {
        binding.videoView.stopPlayback()
        binding.videoView.visibility = View.GONE
    }
    override fun onResume(){
        super.onResume()


    }
}