package com.example.comfyapp.core

import android.app.Application
import com.example.comfyapp.core.TemiController

class TemiApp : Application() {
    companion object {
        var temiController: TemiController? = null
    }
}