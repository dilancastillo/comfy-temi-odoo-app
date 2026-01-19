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
            //lo de abajo lo comenté para pruebas
            //showTrayectoVideo()
            temiController.goToLocation(localName)

            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.USED_IN.name)
            intent.putExtra("USED_IN_ID", 1)
            intent.putExtra("tituloMenu", "Pisos y paredes en Baños")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        binding.btnKitchens.setOnClickListener {
            val localName="pisos exteriores alfa"
            robot.speak(TtsRequest.create("¡Excelente!. Acompañame!", false))
            //lo de abajo lo comenté para pruebas
            //showTrayectoVideo()
            temiController.goToLocation(localName)
            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.USED_IN.name)
            intent.putExtra("USED_IN_ID", 2)
            intent.putExtra("tituloMenu", "Pisos y paredes en Cocinas")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        binding.floatingMenu.btnPromos.setOnClickListener {
           // val localName = "promosemana2"
           // temiController.goToLocation(localName)
        }


    }
    private fun showTrayectoVideo() {
        binding.videoView.visibility = View.VISIBLE
        binding.videoView.setVideoURI(
            Uri.parse("android.resource://${packageName}/${R.raw.loteri}")
        )
        binding.videoView.start()
    }
    private fun stopTrayectoVideo() {
        binding.videoView.stopPlayback()
        binding.videoView.visibility = View.GONE
    }
    override fun onResume(){
        super.onResume()


    }
}