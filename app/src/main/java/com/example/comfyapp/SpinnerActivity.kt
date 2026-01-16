package com.example.comfyapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivitySpinnerBinding

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
            .setDuration(4000)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                showResult(sectors[randomIndex])
                binding.btnSpin.isEnabled = true
            }
            .start()
    }

    private fun showResult(premio: String) {
        Toast.makeText(
            this,
            "¡Ganaste $premio!",
            Toast.LENGTH_LONG
        ).show()
    }
}