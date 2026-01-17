package com.example.comfyapp

import android.animation.Animator
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivitySpinnerBinding
import android.animation.AnimatorListenerAdapter


class SpinnerActivity : AppCompatActivity() {
    // IMPORTANTE: Ajusta este array según tu imagen
    // Empieza desde ARRIBA (donde apunta la flecha) y ve en sentido HORARIO
    private val sectors = arrayOf(
        "25",  // Posición 0 - ARRIBA
        "5",   // Posición 1 - siguiente en sentido horario
        "20",  // Posición 2
        "10",  // Posición 3
        "30",  // Posición 4
        "10"   // Posición 5
    )

    private val sectorAngle = 360f / sectors.size // 60 grados cada sector
    private var prizeTextShown = false

    private lateinit var binding: ActivitySpinnerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpinnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSpin.setOnClickListener {
            spinWheel()
        }
    }

    private fun spinWheel() {
        binding.btnSpin.isEnabled = false

        val randomIndex = sectors.indices.random()

        // 5 vueltas completas + el sector objetivo
        val fullRotations = 360f * 5

        // Calculamos cuánto girar para que el sector randomIndex quede arriba
        // La flecha está arriba (0°), queremos que el CENTRO del sector quede ahí
        val targetAngle = fullRotations - (randomIndex * sectorAngle)

        // Rotación absoluta desde 0
        binding.imgWheel.animate()
            .rotation(targetAngle)
            .setDuration(5000)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                showResult(sectors[randomIndex])
                //binding.btnSpin.isEnabled = true
            }
            .start()
    }

    private fun showResult(premio: String) {

        // Texto dinámico
        binding.tvPrize.text = "¡GANASTE $premio% DE DESCUENTO!"

        // Mostrar confetti
        binding.lottieConfetti.visibility = View.VISIBLE
        binding.lottieConfetti.playAnimation()

        // Reset
        prizeTextShown = false
        binding.tvPrize.visibility = View.GONE

        // Escuchar frames de Lottie
        binding.lottieConfetti.addAnimatorUpdateListener { animator ->
            val progress = animator.animatedFraction

            // Mostrar texto aprox en frame 62
            if (!prizeTextShown && progress >= 0.38f) {
                prizeTextShown = true
                binding.tvPrize.visibility = View.VISIBLE
                binding.tvPrize.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }

            // Ocultar texto aprox en frame 130
            if (progress >= 0.79f) {
                binding.tvPrize.visibility = View.GONE
            }
        }

        // Al terminar animación
        binding.lottieConfetti.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.lottieConfetti.visibility = View.GONE
            }
        })
    }

}