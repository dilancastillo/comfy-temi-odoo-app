// comunica la llegada del robot a una ubicacion con las pantallas interesadas
package com.example.comfyapp.core

import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object LocationEventManager {

    private val _locationArrived = MutableLiveData<String?>()
    val locationArrived: LiveData<String?> = _locationArrived

    fun notifyLocationArrived(location: String) {
        _locationArrived.postValue(location)
    }

    fun clear() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _locationArrived.value = null
        } else {
            _locationArrived.postValue(null)
        }
    }
}
