// abre la pantalla de categorias cuando temi regresa por inactividad
package com.example.comfyapp.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.robot.TemiSessionManager
import com.example.comfyapp.ui.products.category.ProductsUserActivity

class RobotInactivityNavigator(
    private val activity: AppCompatActivity
) {
    private val listener: () -> Unit = {
        activity.startActivity(
            Intent(activity, ProductsUserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    fun start() = TemiSessionManager.addReturnListener(listener)

    fun stop() = TemiSessionManager.removeReturnListener(listener)
}
