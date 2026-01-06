package com.example.comfyapp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
            val localName= "pisobaño"
            robot.speak(TtsRequest.create("¡Perfecto!. ¡Buena elección, sígueme!",false))
            showTrayectoVideo()
           // robot.goTo(localName, true, false,null, false, false)
            temiController.goToLocation(localName)
            //temiController.playSequence("secuencia de trayecto")
        }
        binding.btnKitchens.setOnClickListener {
            val localName="pisococina"
            robot.speak(TtsRequest.create("¡Excelente!. ¡Buena elección, sígueme!", false))
            showTrayectoVideo()
            temiController.goToLocation(localName)
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