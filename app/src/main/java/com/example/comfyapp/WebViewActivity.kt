package com.example.comfyapp

import android.os.Bundle
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivityWebviewBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class WebViewActivity : AppCompatActivity() {
    private lateinit var robot: Robot

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var binding: ActivityWebviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        robot.speak(TtsRequest.create("¡Descubre nuestras promociones especiales! Explora los descuentos disponibles y encuentra el producto ideal para ti.", false))

    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }
}
