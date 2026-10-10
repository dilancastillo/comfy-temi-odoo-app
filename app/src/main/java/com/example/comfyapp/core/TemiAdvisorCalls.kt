// conecta el aviso a asesoras con las llamadas de temi (startMeeting) y su configuración
package com.example.comfyapp.core

import android.os.Handler
import android.os.Looper
import com.example.comfyapp.BuildConfig
import com.example.comfyapp.logging.PersistentLog as Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.constants.Platform
import com.robotemi.sdk.listeners.OnTelepresenceStatusChangedListener
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.telepresence.CallState
import com.robotemi.sdk.telepresence.Participant

object TemiAdvisorCalls {

    private const val TAG = "AdvisorCalls"
    private const val HANG_UP_CHECK_MS = 1_500L
    private const val MAX_HANG_UP_ATTEMPTS = 4
    private val CLOSED_STATES = setOf(
        CallState.State.ENDED, CallState.State.DECLINED, CallState.State.NOT_ANSWERED,
        CallState.State.BUSY, CallState.State.CANT_JOIN, CallState.State.POOR_CONNECTION
    )
    // role 10 = contacto solo para reconocimiento facial: no se puede llamar.
    private const val FACE_ONLY_ROLE = 10

    val notifier: AdvisorCallNotifier by lazy {
        AdvisorCallNotifier(
            gateway = RobotGateway(),
            scheduler = HandlerScheduler(),
            contactNames = AdvisorCallNotifier.parseNames(BuildConfig.ADVISOR_CONTACTS),
            ringMs = BuildConfig.ADVISOR_RING_SECONDS * 1_000L,
            isOnline = NetworkStatus::isOnline
        )
    }

    // Sin contactos configurados se mantiene el aviso hablado de antes.
    val isEnabled: Boolean get() = BuildConfig.ADVISOR_CONTACTS.isNotBlank()

    // Tiempo después del aviso en que la detección no vuelve a saludar: el cliente sigue ahí
    // esperando a la asesora.
    private const val QUIET_AFTER_FLOW_MS = 30_000L

    // Desde "ya le aviso a una asesora" hasta que Temi le dice el resultado al cliente.
    @Volatile
    var isFlowActive = false
        private set
    @Volatile
    private var quietUntilMs = 0L

    fun beginFlow() {
        isFlowActive = true
    }

    fun endFlow(nowMs: Long = System.currentTimeMillis()) {
        isFlowActive = false
        quietUntilMs = nowMs + QUIET_AFTER_FLOW_MS
    }

    fun blocksDetection(nowMs: Long = System.currentTimeMillis()): Boolean =
        isFlowActive || nowMs < quietUntilMs

    private class RobotGateway : AdvisorCallNotifier.Gateway {
        private val robot = Robot.getInstance()
        private var listener: OnTelepresenceStatusChangedListener? = null

        override fun hasPermission(): Boolean =
            robot.checkSelfPermission(Permission.MEETINGS) == Permission.GRANTED

        override fun contacts(): List<AdvisorCallNotifier.Contact> =
            robot.allContact
                .filter { it.role != FACE_ONLY_ROLE && it.userId.isNotBlank() }
                .map { AdvisorCallNotifier.Contact(it.userId, it.name) }

        override fun call(contact: AdvisorCallNotifier.Contact): String = runCatching {
            watchCallEnd()
            // Una llamada nueva deja sin efecto los reintentos de colgar la anterior.
            cancelHangUpRetry()
            callClosed = false
            robot.startMeeting(
                listOf(Participant(contact.id, Platform.MOBILE)),
                firstParticipantJoinedAsHost = false,
                // El cliente no puede tocar botones de la llamada: el aviso no es para conversar.
                blockRobotInteraction = true
            )
        }.getOrElse { error ->
            Log.e(TAG, "No se pudo iniciar la llamada", error)
            ""
        }

        // stopTelepresence responde 200 aunque la reunión siga abierta (pasó al colgar justo después
        // de contestar): se repite hasta que Temi confirme que la llamada terminó.
        private val handler = Handler(Looper.getMainLooper())
        private var callClosed = true
        private var hangUpAttempts = 0
        private var hangUpRetry: Runnable? = null
        private var watcherRegistered = false
        private val endWatcher = object : OnTelepresenceStatusChangedListener("") {
            override fun onTelepresenceStatusChanged(callState: CallState) {
                if (callState.state !in CLOSED_STATES || callClosed) return
                callClosed = true
                cancelHangUpRetry()
                Log.i(TAG, "call_closed state=${callState.state} hang_up_attempts=$hangUpAttempts")
            }
        }

        private fun watchCallEnd() {
            if (watcherRegistered) return
            robot.addOnTelepresenceStatusChangedListener(endWatcher)
            watcherRegistered = true
        }

        override fun hangUp() {
            hangUpAttempts = 0
            cancelHangUpRetry()
            stopAndVerify()
        }

        private fun stopAndVerify() {
            hangUpAttempts++
            val code = runCatching { robot.stopTelepresence() }.getOrDefault(500)
            Log.i(TAG, "stop_telepresence code=$code attempt=$hangUpAttempts")
            // 404 = Temi ya no tiene llamada activa.
            if (code == 404) callClosed = true
            if (callClosed) return
            hangUpRetry = Runnable {
                hangUpRetry = null
                when {
                    callClosed -> Unit
                    hangUpAttempts < MAX_HANG_UP_ATTEMPTS -> stopAndVerify()
                    else -> Log.w(TAG, "call_not_closed hang_up_attempts=$hangUpAttempts")
                }
            }.also { handler.postDelayed(it, HANG_UP_CHECK_MS) }
        }

        private fun cancelHangUpRetry() {
            hangUpRetry?.let(handler::removeCallbacks)
            hangUpRetry = null
        }

        override fun setCallListener(listener: ((CallState.State) -> Unit)?) {
            this.listener?.let(robot::removeOnTelepresenceStatusChangedListener)
            this.listener = listener?.let { onState ->
                object : OnTelepresenceStatusChangedListener("") {
                    override fun onTelepresenceStatusChanged(callState: CallState) {
                        Log.i(TAG, "call_state session=${callState.sessionId} state=${callState.state}")
                        callState.state?.let(onState)
                    }
                }.also(robot::addOnTelepresenceStatusChangedListener)
            }
        }

        override fun tiltHead(degrees: Int) {
            runCatching { robot.tiltAngle(degrees, 1f) }
        }
    }

    private class HandlerScheduler : AdvisorCallNotifier.Scheduler {
        private val handler = Handler(Looper.getMainLooper())

        override fun schedule(delayMs: Long, block: () -> Unit): Any =
            Runnable(block).also { handler.postDelayed(it, delayMs) }

        override fun cancel(token: Any) {
            handler.removeCallbacks(token as Runnable)
        }
    }
}
