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
    private val navigationFailureListeners = CopyOnWriteArraySet<() -> Unit>()
    private var controller: TemiController? = null

    @Synchronized
    fun start(context: Context, onStatus: (String) -> Unit = {}) {
        if (controller == null) {
            controller = TemiController(
                context = context.applicationContext,
                onStatus = onStatus,
                onReturnedByInactivity = ::notifyReturnedByInactivity,
                onNavigationFailed = ::notifyNavigationFailed
            )
        }
        controller?.start()
    }

    fun goToLocation(location: String): Boolean = controller?.goToLocation(location) == true

    fun cancelNavigationByUser() {
        controller?.cancelNavigationByUser()
    }

    fun cancelNavigationForCatalogError() {
        controller?.cancelNavigationForCatalogError()
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

    fun addNavigationFailureListener(listener: () -> Unit) {
        navigationFailureListeners += listener
    }

    fun removeNavigationFailureListener(listener: () -> Unit) {
        navigationFailureListeners -= listener
    }

    private fun notifyReturnedByInactivity() {
        mainHandler.post {
            returnListeners.forEach { it.invoke() }
        }
    }

    private fun notifyNavigationFailed() {
        mainHandler.post {
            navigationFailureListeners.forEach { it.invoke() }
        }
    }
}
