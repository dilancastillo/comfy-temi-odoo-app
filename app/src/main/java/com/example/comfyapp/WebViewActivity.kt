package com.example.comfyapp

import android.os.Bundle
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivityWebviewBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class WebViewActivity : AppCompatActivity() {
    private lateinit var robot: Robot
    private var promoId: Int = -1

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_ID = "extra_id"
    }

    private lateinit var binding: ActivityWebviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        promoId = intent.getIntExtra(EXTRA_ID, -1)

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)

        }

        binding.btnCloseWeb.setOnClickListener {
            finish()
        }
    }
    override fun onResume(){
        super.onResume()
        robot = Robot.getInstance()

        val message = when (promoId) {
            202 -> "¡Descubre nuestras promociones en Lavamanos! Explora los descuentos disponibles y encuentra el producto ideal para ti."
            201 -> "¡Descubre nuestras promociones en Revestimientos! Explora los descuentos disponibles y encuentra el producto ideal para ti."
            133 -> "¡Descubre nuestras promociones en Cocinas Integrales! Explora los descuentos disponibles y encuentra el producto ideal para ti"
            else -> "¡Excelente!, Aquí puedes conocer todos los detalles del producto y explorar sus características!"
        }

        robot.speak(TtsRequest.create(message, false))
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }
}
