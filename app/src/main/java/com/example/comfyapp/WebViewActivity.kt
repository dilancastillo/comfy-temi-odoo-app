// muestra la pagina web de un producto y permite cerrarla o navegar hacia atras
package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivityWebviewBinding
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.ui.RobotInactivityNavigator
import com.example.comfyapp.ui.products.category.ProductsUserActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class WebViewActivity : AppCompatActivity() {
    private lateinit var robot: Robot
    private var promoId: Int = -1
    private val robotRepository by lazy { TemiRobotRepository(applicationContext) }
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
    private val centerScreenHandler = Handler(Looper.getMainLooper())
    private var useCenterScreenTimeout = false
    private val centerScreenTimeout = Runnable {
        robotRepository.speak("Dime, ¿en qué te puedo ayudar?")
        startActivity(
            Intent(this, ProductsUserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_ID = "extra_id"
        private const val CENTER_SCREEN_TIMEOUT_MS = 60_000L
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

    override fun onStart() {
        super.onStart()
        inactivityNavigator.start()
        robotRepository.start()
        useCenterScreenTimeout = robotRepository.isAtCenterSala()
        if (useCenterScreenTimeout) resetCenterScreenTimeout()
    }

    override fun onStop() {
        cancelCenterScreenTimeout()
        inactivityNavigator.stop()
        robotRepository.stop()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (useCenterScreenTimeout) resetCenterScreenTimeout()
        robotRepository.notifyUserInteraction()
    }

    override fun onDestroy() {
        cancelCenterScreenTimeout()
        super.onDestroy()
    }

    private fun resetCenterScreenTimeout() {
        cancelCenterScreenTimeout()
        centerScreenHandler.postDelayed(centerScreenTimeout, CENTER_SCREEN_TIMEOUT_MS)
    }

    private fun cancelCenterScreenTimeout() {
        centerScreenHandler.removeCallbacks(centerScreenTimeout)
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }
}
