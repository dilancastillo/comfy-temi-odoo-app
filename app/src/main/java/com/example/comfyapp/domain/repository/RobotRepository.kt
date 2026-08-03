// define las acciones del robot que pueden solicitar los casos de uso
package com.example.comfyapp.domain.repository

interface RobotRepository {
    fun start()
    fun stop()
    fun speak(message: String)
    fun goToLocation(location: String): Boolean
    fun cancelNavigationByUser()
    fun cancelNavigationForCatalogError()
    fun notifyUserInteraction()
}
