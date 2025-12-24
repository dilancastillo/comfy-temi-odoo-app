package com.example.comfyapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.SelectPromoBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class SelectPromoActivity : AppCompatActivity() {
    private lateinit var binding: SelectPromoBinding
    private lateinit var robot: Robot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        robot = Robot.getInstance()


        binding = SelectPromoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnfloortilepromo.setOnClickListener {
            val sequenceName = "promorevestimientos"
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de revestimientos!", true))
            robot.goTo(sequenceName)

        }
        binding.btnkitchenpromo.setOnClickListener {
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de cocinas!", true))
            val sequenceName = "promococina"
            robot.goTo(sequenceName)
        }
        binding.tbniconWashbasinpromo.setOnClickListener {
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de lavamanos!", true))
            val sequenceName = "promolavamanos"
            robot.goTo(sequenceName)
        }
        binding.imgbtnback.setOnClickListener { finish() }
    }
}


