// inicializa los servicios compartidos antes de abrir cualquier pantalla
package com.example.comfyapp

import android.app.Application
import com.example.comfyapp.logging.PersistentLog

class ComfyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PersistentLog.initialize(this)
    }
}
