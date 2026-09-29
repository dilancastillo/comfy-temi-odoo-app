// indica si el robot tiene internet utilizable; voz, gemini y odoo dependen de eso
package com.example.comfyapp.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkStatus {

    @Volatile
    private var connectivity: ConnectivityManager? = null

    fun initialize(context: Context) {
        connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }

    // VALIDATED descarta redes conectadas pero sin salida real (wifi sin internet, portal cautivo).
    // Si no se pudo consultar, se asume conectado para no bloquear la app por un falso negativo.
    fun isOnline(): Boolean {
        val manager = connectivity ?: return true
        return runCatching {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(true)
    }
}
