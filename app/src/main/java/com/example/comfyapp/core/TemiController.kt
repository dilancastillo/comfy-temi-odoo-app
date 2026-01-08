package com.example.comfyapp.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.navigation.model.SpeedLevel


class TemiController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onArrived: (() -> Unit)? = null
) : OnRobotReadyListener, OnGoToLocationStatusChangedListener {

    private val robot: Robot = Robot.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var robotReady = false
    var last_location: String? = null
        private set
    var executeSequences = true


    companion object {
        private const val TAG = "TemiController"
    }

    fun start() {
        robot.addOnRobotReadyListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        onStatus("Inicializando Temi…")
    }

    fun stop() {
        robot.removeOnRobotReadyListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
    }

    override fun onRobotReady(isReady: Boolean) {
        robotReady = isReady
        onStatus(if (isReady) "Robot listo" else "Robot no disponible")
    }

    fun goToLocation(location: String) {
        if (!robotReady) {
            toast("Robot no listo")
            return
        }

        val target = location.lowercase()
        if (!robot.locations.map { it.lowercase() }.contains(target)) {
            toast("Ubicación no existe: $target")
            return
        }

        if(target.contains("promosemana1")||target.contains("promosemana")){

            robot.goTo(target,false,false, SpeedLevel.MEDIUM,true, true)
        }
        else{
            robot.goTo(target,true,false, SpeedLevel.MEDIUM,false, true)

        }

        //robot.goTo(target)
       //


    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        Log.d(TAG, "GoTo $location → $status")

        if (status == "complete") {
            robot.cancelAllTtsRequests()
            onArrived?.invoke()
            last_location = location
            if (!executeSequences) return // si es false, no hace nada más

            val sequenceName = when {
                last_location?.contains("promococina", ignoreCase = true) == true -> "promococina"
                last_location?.contains("promorevestimientos", ignoreCase = true) == true -> "promorevestimientos"
                last_location?.contains("promolavamanos", ignoreCase = true) == true -> "promolavamanos"
                last_location?.contains("pisobaño", ignoreCase = true) == true -> "video_bano"
                last_location?.contains("pisococina", ignoreCase = true) == true -> "video_bano"
                last_location?.contains("promosemana1", ignoreCase = true) == true -> run{
                    robot.tiltAngle(10, 1f)
                    "ejemplo promocion"
                }
                last_location?.contains("promosemana", ignoreCase = true) == true -> "promosemana"
                last_location?.contains("promosemana2", ignoreCase = true) == true -> "ejemplo promocion2"
                else -> null
            }
            sequenceName?.let { seq ->
                handler.postDelayed({
                        ejecutarSequence(seq)
                }, 1)
            }




        }
    }

    private fun ejecutarSequence(sequenceName: String) {
        try {


            val sequence = robot.getAllSequences()
                ?.firstOrNull { it.name.equals(sequenceName, true) }

            if (sequence == null) {
                toast("Sequence '${sequenceName}' no encontrada")
                return
            }
            Log.i(TAG, "Ejecutando sequence ${sequence.name}")
            robot.cancelAllTtsRequests()
            robot.playSequence(
                sequence.id,
                false,
                1,
                1
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando sequence", e)
            toast("Error al ejecutar sequence")
        }
    }
    fun playSequence(sequenceName: String) {
        ejecutarSequence(sequenceName)
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}