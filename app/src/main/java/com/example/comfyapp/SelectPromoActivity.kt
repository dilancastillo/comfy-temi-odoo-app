package com.example.comfyapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.SelectPromoBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import android.content.Intent
import android.net.Uri


class SelectPromoActivity : AppCompatActivity() {
    private lateinit var binding: SelectPromoBinding
    private lateinit var robot: Robot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        robot = Robot.getInstance()
        robot.speak(TtsRequest.create("¡Hola!, Tenemos varias promociones especiales para ti. Selecciona una opción y descúbrelas.",false))

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
        binding.imgWebsiteWashBasin.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            intent.putExtra(
                WebViewActivity.EXTRA_URL,
                "https://www.comfer.co/shop/category/promociones-descuentos-sanitarios-lavamanos-accesorios-202"
            )
            intent.putExtra(WebViewActivity.EXTRA_ID, 202)
            startActivity(intent)
        }


        binding.imgWebsiteFloortile.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            intent.putExtra(
                WebViewActivity.EXTRA_URL,
                "https://www.comfer.co/shop/category/promociones-ofertas-descuentos-pisos-paredes-201"
            )
            intent.putExtra(WebViewActivity.EXTRA_ID, 201)
            startActivity(intent)
        }
        binding.imgWebsiteKitchen.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            intent.putExtra(
                WebViewActivity.EXTRA_URL,
                "https://www.comfer.co/shop/category/muebles-para-cocina-integral-cocinas-integrales-prefabricadas-133"
            )
            intent.putExtra(WebViewActivity.EXTRA_ID, 133)
            startActivity(intent)
        }

        binding.imgbtnback.setOnClickListener { finish() }
    }
}


