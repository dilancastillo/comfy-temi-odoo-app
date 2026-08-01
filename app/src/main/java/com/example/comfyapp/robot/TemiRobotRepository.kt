// adapta el controlador y el sdk de temi al contrato de robot del dominio
package com.example.comfyapp.robot

import android.content.Context
import com.example.comfyapp.core.TemiController
import com.example.comfyapp.domain.repository.RobotRepository
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class TemiRobotRepository(
    context: Context,
    onStatus: (String) -> Unit = {},
    onArrived: (() -> Unit)? = null
) : RobotRepository {

    private val robot = Robot.getInstance()
    private val controller = TemiController(context.applicationContext, onStatus, onArrived)

    override fun start() = controller.start()

    override fun stop() = controller.stop()

    override fun speak(message: String) {
        robot.speak(TtsRequest.create(message, false))
    }

    override fun goToLocation(location: String) = controller.goToLocation(location)

    override fun notifyUserInteraction() = controller.notifyUserInteraction()
}
