// adapta el controlador y el sdk de temi al contrato de robot del dominio
package com.example.comfyapp.robot

import android.content.Context
import com.example.comfyapp.domain.repository.RobotRepository

class TemiRobotRepository(
    context: Context,
    private val onStatus: (String) -> Unit = {}
) : RobotRepository {

    private val appContext = context.applicationContext

    override fun start() = TemiSessionManager.start(appContext, onStatus)

    override fun stop() = Unit

    override fun speak(message: String) {
        TemiSessionManager.speak(message)
    }

    override fun goToLocation(location: String) = TemiSessionManager.goToLocation(location)

    override fun cancelNavigationByUser() = TemiSessionManager.cancelNavigationByUser()

    override fun notifyUserInteraction() = TemiSessionManager.notifyUserInteraction()
}
