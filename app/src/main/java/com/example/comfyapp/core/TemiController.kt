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
//import kotlinx.coroutines.Runnable


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
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private var inactivityRunnable: Runnable? = null
    private var isAtHomeBase = false
    private val hourlyHandler = Handler(Looper.getMainLooper())
    private var hourlyRunnable: Runnable? = null
    private var abortExpectedFromUser = false
    private var navigationStartedByUser = false
    private var arrivedHomeByInactivity = false



    companion object {
        private const val TAG = "TemiController"
        private const val INACTIVITY_TIMEOUT = 1 * 60 * 1000L
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
            robot.goTo(target,true,false, SpeedLevel.MEDIUM,false, false)

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

            LocationEventManager.notifyLocationArrived(location)
            last_location = location
            onArrived?.invoke()
            robot.speak(TtsRequest.create("Aquí puedes ver las ultimas tendencias para la zona que seleccioanste", false))

            if (location.equals("home base", ignoreCase = true)) {

                Log.d(TAG, "Llegó a Home Base")

                isAtHomeBase = true
                cancelInactivityTimer()

                //CLAVE
                if (arrivedHomeByInactivity) {
                    Log.d(TAG, "Home Base por inactividad → NO reactivar contador")
                    arrivedHomeByInactivity = false
                    return
                }

                return
            }
            isAtHomeBase = false
            resetInactivityTimer()

            if (!executeSequences) return // si es false, no hace nada más

            val sequenceName = when {
                last_location?.contains("promococina", ignoreCase = true) == true -> "promococina"
                last_location?.contains("fachadas porcelanatos", ignoreCase = true) == true -> "video_bano"
                last_location?.contains("promolavamanos", ignoreCase = true) == true -> "promolavamanos"
                last_location?.contains("pisobaño", ignoreCase = true) == true -> "video_bano"
                last_location?.contains("pisococina", ignoreCase = true) == true -> "video_bano"
                last_location?.contains("promosemana1", ignoreCase = true) == true -> run{
                    robot.tiltAngle(10, 1f)
                    "promocionintro"
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
        if (status == "abort") {

            if (abortExpectedFromUser) {
                Log.d(TAG, "Abort por interacción humana")

                robot.speak(
                    TtsRequest.create(
                        "¿En qué te puedo ayudar? Toca alguna opción en mi pantalla y te guiaré",
                        true
                    )
                )

                resetInactivityTimer()
            } else {
                Log.d(TAG, "Abort técnico (obstáculo / sistema / scheduler)")
                //goToLocation("home base")
            }

            abortExpectedFromUser = false
            navigationStartedByUser = false
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
    fun notifyUserInteraction() {
        abortExpectedFromUser = true
        navigationStartedByUser = true
        hourlyRunnable?.let {
            hourlyHandler.removeCallbacks(it)
            hourlyRunnable = null
        }

        if (isAtHomeBase) {
            isAtHomeBase = false
        }

        resetInactivityTimer()
    }
    private fun goToAutoLocation(location: String) {
        navigationStartedByUser = false
        abortExpectedFromUser = false
        goToLocation(location)
    }




    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    private fun resetInactivityTimer() {
        cancelInactivityTimer()

        inactivityRunnable = Runnable {

            if (isAtHomeBase) return@Runnable

            Log.d(TAG, "Inactividad detectada → volver a Home Base")

            arrivedHomeByInactivity = true

            robot.cancelAllTtsRequests()
            robot.speak(
                TtsRequest.create(
                    "Gracias por interactuar conmigo, regresaré a Centro Sala",
                    false
                )
            )

            goToLocation("home base")
        }

        inactivityHandler.postDelayed(inactivityRunnable!!, INACTIVITY_TIMEOUT)
    }

    private fun cancelInactivityTimer() {
        inactivityRunnable?.let {
            inactivityHandler.removeCallbacks(it)
        }
        inactivityRunnable = null
    }
    //hacer algo en determinado timepo
//    fun startHourlyScheduleIfNeeded() {
//        val now = java.util.Calendar.getInstance()
//        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
//
//        // Solo entre 12 y 17
//        if (hour !in 12..17) {
//            Log.d(TAG, "Fuera del horario automático")
//            return
//        }
//
//
//        val millisToNextHour =
//            (60 - now.get(java.util.Calendar.MINUTE)) * 60 * 1000L -
//                    now.get(java.util.Calendar.SECOND) * 1000L
//
//        Log.d(TAG, "Programando recorrido en $millisToNextHour ms")
//
//        hourlyRunnable?.let { hourlyHandler.removeCallbacks(it) }
//
//        hourlyRunnable = Runnable {
//            Log.d(TAG, "Hora exacta → iniciar recorrido automático")
//
//            // Reprogramar para la siguiente hora
//            startHourlyScheduleIfNeeded()
//        }
//
//        hourlyHandler.postDelayed(hourlyRunnable!!, millisToNextHour)
//    }
}