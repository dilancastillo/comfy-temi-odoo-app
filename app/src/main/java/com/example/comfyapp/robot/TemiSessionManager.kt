// mantiene una sola sesion de temi y comparte el temporizador entre pantallas
package com.example.comfyapp.robot

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.comfyapp.core.TemiController
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import java.util.concurrent.CopyOnWriteArraySet

object TemiSessionManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val returnListeners = CopyOnWriteArraySet<() -> Unit>()
    private var controller: TemiController? = null

    @Synchronized
    fun start(context: Context, onStatus: (String) -> Unit = {}) {
        if (controller == null) {
            controller = TemiController(
                context = context.applicationContext,
                onStatus = onStatus,
                onReturnedByInactivity = ::notifyReturnedByInactivity
            )
        }
        controller?.start()
    }

    fun goToLocation(location: String) {
        controller?.goToLocation(location)
    }

    fun notifyUserInteraction() {
        controller?.notifyUserInteraction()
    }

    fun speak(message: String) {
        Robot.getInstance().speak(TtsRequest.create(message, false))
    }

    fun addReturnListener(listener: () -> Unit) {
        returnListeners += listener
    }

    fun removeReturnListener(listener: () -> Unit) {
        returnListeners -= listener
    }

    private fun notifyReturnedByInactivity() {
        mainHandler.post {
            returnListeners.forEach { it.invoke() }
        }
    }
}
