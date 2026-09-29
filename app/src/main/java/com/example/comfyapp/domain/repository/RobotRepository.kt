// define las acciones del robot que pueden solicitar los casos de uso
package com.example.comfyapp.domain.repository

interface RobotRepository {
    fun start()
    fun stop()
    fun speak(message: String)
    // arrivalMessage reemplaza lo que Temi dice al llegar (null = mensaje habitual del catálogo)
    fun goToLocation(location: String, arrivalMessage: String? = null): Boolean
    fun cancelNavigationByUser()
    fun cancelNavigationForCatalogError()
    fun notifyUserInteraction()
    fun isNavigationInProgress(): Boolean
    fun isAtCenterSala(): Boolean
}
