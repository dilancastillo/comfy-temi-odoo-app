package com.example.comfyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.TemiApp
import com.example.comfyapp.core.TemiController
import com.example.comfyapp.databinding.ActivityTilesListBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.navigation.model.SpeedLevel

class TilesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTilesListBinding
    private lateinit var robot: Robot
    private lateinit var temiController: TemiController

    val puntos = listOf("sanitarios corona")
    var indicePunto = 0
    var cicloActivo = true

    private var yaTocado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTilesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        robot = Robot.getInstance()

        //Mensaje inicial
        robot.speak(
            TtsRequest.create(
                "¡Súper! ¿Para dónde estás buscando estos productos? Toca la pantalla y acompáñame.",
                false
            )
        )

        // VIDEO AL ENTRAR
//        val uri = Uri.parse("android.resource://$packageName/${R.raw.tendenrevestimientos}")
//        binding.videoView.setVideoURI(uri)
//        binding.videoView.setOnPreparedListener { mp ->
//            mp.isLooping = true
//            binding.videoView.start()
//
//            val duracionVideo = mp.duration.toLong()
//            val tiempoEntrePuntos = duracionVideo / puntos.size
//
//            fun moverTemi(indice: Int) {
//                if (!cicloActivo || indice >= puntos.size) {
//                    binding.videoView.stopPlayback()
//                    binding.videoView.visibility = View.GONE
//                    return
//                }
//                robot.goTo(puntos[indice],true,false, SpeedLevel.MEDIUM,false, false)
//
//                // Programa siguiente movimiento solo si el ciclo sigue activo
//                binding.videoView.postDelayed({
//                    if (cicloActivo) moverTemi(indice + 1)
//                }, tiempoEntrePuntos)
//            }
//
//            moverTemi(indicePunto)
//        }

        // TOQUE GLOBAL (solo una vez)
//        binding.touchOverlay.bringToFront()
//        binding.touchOverlay.setOnClickListener {
//            if (yaTocado) return@setOnClickListener
//            yaTocado = true
//
//            // Pausar y ocultar video
//            if (binding.videoView.isPlaying) binding.videoView.pause()
//            binding.videoView.visibility = View.GONE
//
//            // Hablar y moverse
//            robot.speak(TtsRequest.create("Perfecto, acompáñame y te mostraré las últimas tendencias de revestimientos.", false))
//            robot.goTo("pisos tipo madera", true)
//
//            // Ocultar overlay
//            binding.touchOverlay.visibility = View.GONE
//        }


        // Temi Controller
        temiController = TemiController(
            this,
            { status -> Log.d("TEMI", status) },
            {
                runOnUiThread {
                    //stopTrayectoVideo()
                } }
        )
        temiController.start()

        // Back
        binding.imgbtnback.setOnClickListener {
            finish()
        }


        binding.btnbathrooms.setOnClickListener {
            robot.speak(TtsRequest.create("¡Perfecto!. Acompañame!", false))
            temiController.goToLocation("pisos exteriores alfa")

            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.FloorAndWall.name)
            intent.putExtra("USED_IN_ID", arrayListOf(15))
            intent.putExtra("tituloMenu", "Pisos y paredes para Baños y zonas húmedas")
            intent.putExtra("columnTitleOne", "Únicamente para Paredes")
            intent.putExtra("columnTitleTwo", "Para Pisos y Paredes")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        binding.btnKitchens.setOnClickListener {
            robot.speak(TtsRequest.create("¡Excelente!. Acompañame!", false))
            temiController.goToLocation("pisos exteriores alfa")

            val intent = Intent(this, ProductListUser::class.java)
            intent.putExtra("QUERY_TYPE", ProductListUser.ProductQueryType.FloorAndWall.name)
            intent.putExtra("USED_IN_ID", arrayListOf(14))
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
            intent.putExtra("USED_IN_ID", arrayListOf(24, 13, 18))
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
            intent.putExtra("USED_IN_ID", arrayListOf(17))
            intent.putExtra("tituloMenu", "Pisos y Paredes para Exteriores")
            intent.putExtra("columnTitleOne", "Únicamente para Paredes")
            intent.putExtra("columnTitleTwo", "Para Pisos y Paredes")
            intent.putExtra("VIDEO_RES", R.raw.tendenciasreve)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        yaTocado = false
       // binding.touchOverlay.visibility = View.VISIBLE
        //binding.videoView.start()
    }

//    private fun stopTrayectoVideo() {
//        binding.videoView.stopPlayback()
//        binding.videoView.visibility = View.GONE
//    }
    override fun onUserInteraction() {
        super.onUserInteraction()
        TemiApp.temiController?.notifyUserInteraction()
    }

}
