// controla la navegacion la voz y los eventos principales del robot temi
package com.example.comfyapp.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.comfyapp.logging.PersistentLog as Log
import android.widget.Toast
import com.example.comfyapp.agent.AgentSpeechController
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
//import kotlinx.coroutines.Runnable


class TemiController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onArrived: (() -> Unit)? = null,
    private val onReturnedByInactivity: (() -> Unit)? = null,
    private val onNavigationFailed: (() -> Unit)? = null,
    private val onNavigationStateChanged: ((Boolean) -> Unit)? = null
) : OnRobotReadyListener, OnGoToLocationStatusChangedListener {

    private val robot: Robot = Robot.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var robotReady = false
    private var started = false
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
    private var isNavigating = false
    private var returningToCenter = false
    private var activeTarget: String? = null
    private var cancelledTarget: String? = null



    companion object {
        private const val TAG = "TemiController"
        private const val INACTIVITY_TIMEOUT = 40 * 1000L
    }

    fun start() {
        if (started) return
        started = true
        robot.addOnRobotReadyListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        onStatus("Inicializando Temi…")
    }

    fun stop() {
        if (!started) return
        started = false
        robot.removeOnRobotReadyListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
        cancelInactivityTimer()
    }

    override fun onRobotReady(isReady: Boolean) {
        robotReady = isReady
        onStatus(if (isReady) "Robot listo" else "Robot no disponible")
    }

    fun goToLocation(location: String, automaticReturn: Boolean = false): Boolean {
        if (!robotReady) {
            toast("Robot no listo")
            handleNavigationStartFailure(automaticReturn)
            return false
        }

        val target = location.lowercase()
        if (!robot.locations.map { it.lowercase() }.contains(target)) {
            toast("Ubicación no existe: $target")
            speak("Hubo un error de ubicación, pero puedes ver el catálogo")
            handleNavigationStartFailure(automaticReturn)
            return false
        }
        cancelInactivityTimer()

        hourlyRunnable?.let {
            hourlyHandler.removeCallbacks(it)
            hourlyRunnable = null
        }

        isAtHomeBase = false
        isNavigating = true
        onNavigationStateChanged?.invoke(true)
        returningToCenter = automaticReturn
        activeTarget = target
        navigationStartedByUser = true
        abortExpectedFromUser = false

        try {
            if(target.contains("promosemana1")||target.contains("promosemana")) {
                robot.goTo(target, false, false, null, true, true)
            } else {
                robot.goTo(target, true, false, null, false, false)
            }
        } catch (error: Exception) {
            Log.e(TAG, "No se pudo iniciar la navegación", error)
            isNavigating = false
            onNavigationStateChanged?.invoke(false)
            activeTarget = null
            handleNavigationStartFailure(automaticReturn)
            return false
        }

        //robot.goTo(target)
        return true
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        Log.d(TAG, "GoTo $location → $status")

        Log.d(
            TAG,
            "GoTo details location=$location status=$status " +
                "descriptionId=$descriptionId description=$description"
        )
        val normalizedLocation = location.lowercase()
        if (cancelledTarget == normalizedLocation) {
            if (status == "abort" || status == "complete") cancelledTarget = null
            return
        }

        if (status == "complete") {
            isNavigating = false
            onNavigationStateChanged?.invoke(false)
            abortExpectedFromUser = false
            navigationStartedByUser = false
            returningToCenter = false
            activeTarget = null
            LocationEventManager.notifyLocationArrived(location)
            last_location = location
            onArrived?.invoke()
            val isHomeBase = location.equals("home base", ignoreCase = true)
            val isCentroSala = location.equals("centro sala", ignoreCase = true)

            // Solo habla si NO es Home Base ni Centro Sala
            if (!isHomeBase && !isCentroSala) {
                speakAfterCurrent("Aquí puedes ver las últimas tendencias para la zona que seleccionaste. Además, puedes ver sus especificaciones haciendo clic sobre cada producto.")
            }

            if (location.equals("centro sala", ignoreCase = true)) {

                Log.d(TAG, "Llegó a centro salaz")

                isAtHomeBase = true
                cancelInactivityTimer()

                //CLAVE
                if (arrivedHomeByInactivity) {
                    Log.d(TAG, "cemntro sala por inactividad → NO reactivar contador")
                    arrivedHomeByInactivity = false
                    onReturnedByInactivity?.invoke()
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
            val failedWhileReturning = returningToCenter
            isNavigating = false
            onNavigationStateChanged?.invoke(false)
            returningToCenter = false
            activeTarget = null

            if (abortExpectedFromUser) {
                Log.d(TAG, "Abort por interacción humana")

                speak("¿En qué te puedo ayudar? Toca alguna opción en mi pantalla y te guiaré")

                resetInactivityTimer()
            } else {
                Log.d(TAG, "Abort técnico (obstáculo / sistema / scheduler)")
                val message = if (failedWhileReturning) {
                    "No pude regresar a centro sala"
                } else {
                    "No pude llegar a mi destino"
                }
                speak(message)
                onNavigationFailed?.invoke()
                if (failedWhileReturning) onReturnedByInactivity?.invoke()
                resetInactivityTimer()
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
            stopSpeaking()
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

        if (isNavigating) {
            if (returningToCenter) {
                cancelNavigationByUser()
                return
            }
            cancelInactivityTimer()
            return
        }

        arrivedHomeByInactivity = false
        if (!isAtHomeBase) resetInactivityTimer()
    }

    private fun speak(message: String) {
        AgentSpeechController.shared.speak(message)
    }

    private fun speakAfterCurrent(message: String) {
        AgentSpeechController.shared.speakAfterCurrent(message)
    }

    private fun stopSpeaking() {
        AgentSpeechController.shared.stopSpeaking()
    }

    fun isAtCenterSala(): Boolean = isAtHomeBase

    fun isNavigationInProgress(): Boolean = isNavigating

    fun cancelNavigationByUser() {
        if (!isNavigating) {
            notifyUserInteraction()
            return
        }

        cancelledTarget = activeTarget
        arrivedHomeByInactivity = false
        activeTarget = null
        isNavigating = false
        onNavigationStateChanged?.invoke(false)
        returningToCenter = false
        abortExpectedFromUser = false
        robot.stopMovement()
        stopSpeaking()
        speak("Dime, ¿en qué te puedo ayudar?")
        onNavigationFailed?.invoke()
        resetInactivityTimer()
    }

    fun cancelNavigationForCatalogError() {
        if (isNavigating) {
            cancelledTarget = activeTarget
            activeTarget = null
            isNavigating = false
            onNavigationStateChanged?.invoke(false)
            returningToCenter = false
            arrivedHomeByInactivity = false
            robot.stopMovement()
        }
        stopSpeaking()
        speak("No pude cargar el catálogo. Puedes intentarlo nuevamente.")
        onNavigationFailed?.invoke()
        if (!isAtHomeBase) resetInactivityTimer()
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

            if (isAtHomeBase || isNavigating) return@Runnable

            Log.d(TAG, "Inactividad detectada → volver a centro sala")

            arrivedHomeByInactivity = true

            stopSpeaking()
            speak("Gracias por interactuar conmigo, regresaré a Centro Sala")

            goToLocation("centro sala", automaticReturn = true)
        }

        inactivityHandler.postDelayed(inactivityRunnable!!, INACTIVITY_TIMEOUT)
    }

    private fun handleNavigationStartFailure(automaticReturn: Boolean) {
        isNavigating = false
        onNavigationStateChanged?.invoke(false)
        returningToCenter = false
        activeTarget = null
        onNavigationFailed?.invoke()
        if (automaticReturn) onReturnedByInactivity?.invoke()
        if (!isAtHomeBase) resetInactivityTimer()
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
