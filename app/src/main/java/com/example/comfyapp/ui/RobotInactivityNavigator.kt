// abre la pantalla principal cuando temi regresa por inactividad
package com.example.comfyapp.ui

import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.robot.TemiSessionManager
import com.example.comfyapp.CoverActivity

class RobotInactivityNavigator(
    private val activity: AppCompatActivity
) {
    private val listener: () -> Unit = {
        activity.startActivity(CoverActivity.homeIntent(activity))
    }

    fun start() = TemiSessionManager.addReturnListener(listener)

    fun stop() = TemiSessionManager.removeReturnListener(listener)
}
