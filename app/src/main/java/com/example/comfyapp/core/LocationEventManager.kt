// comunica la llegada del robot a una ubicacion con las pantallas interesadas
package com.example.comfyapp.core

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object LocationEventManager {

    private val _locationArrived = MutableLiveData<String?>()
    val locationArrived: LiveData<String?> = _locationArrived

    fun notifyLocationArrived(location: String) {
        _locationArrived.postValue(location)
    }

    fun clear() {
        _locationArrived.postValue(null)
    }
}
