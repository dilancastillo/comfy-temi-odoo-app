// avisa a una asesora con una llamada de temi que solo funciona como timbre: si contesta se cuelga
// de inmediato (ya quedó avisada); si no contesta a tiempo se llama a la siguiente de la lista
package com.example.comfyapp.core

import com.example.comfyapp.logging.AssistanceEventLog
import com.robotemi.sdk.telepresence.CallState

class AdvisorCallNotifier(
    private val gateway: Gateway,
    private val scheduler: Scheduler,
    private val contactNames: List<String>,
    private val ringMs: Long,
    private val isOnline: () -> Boolean = { true },
    private val logEvent: (String, List<Pair<String, Any?>>) -> Unit =
        { name, fields -> AssistanceEventLog.event(name, *fields.toTypedArray()) }
) {

    private fun event(name: String, vararg fields: Pair<String, Any?>) = logEvent(name, fields.toList())

    // Lo que depende del robot, separado para poder probar el flujo sin Temi.
    interface Gateway {
        fun hasPermission(): Boolean
        fun contacts(): List<Contact>
        // Devuelve el id de la llamada, o vacío si Temi no la pudo iniciar.
        fun call(contact: Contact): String
        fun hangUp()
        fun setCallListener(listener: ((CallState.State) -> Unit)?)
        fun tiltHead(degrees: Int)
    }

    interface Scheduler {
        fun schedule(delayMs: Long, block: () -> Unit): Any
        fun cancel(token: Any)
    }

    data class Contact(val id: String, val name: String)

    sealed class Result {
        data class Answered(val name: String) : Result()
        data class NoAnswer(val tried: List<String>) : Result()
        data class Unavailable(val reason: String) : Result()
    }

    private enum class Stage { IDLE, RINGING, BETWEEN_CALLS }

    private var stage = Stage.IDLE
    private var queue: List<Contact> = emptyList()
    private var index = 0
    private var pendingTimer: Any? = null
    private var headRestTimer: Any? = null
    private var onResult: ((Result) -> Unit)? = null
    private var onAttempt: ((Contact) -> Unit)? = null

    val isBusy: Boolean get() = stage != Stage.IDLE

    // false = ya hay un aviso en curso; el resultado llega solo una vez por aviso.
    fun notify(onAttempt: (Contact) -> Unit = {}, onResult: (Result) -> Unit): Boolean {
        if (isBusy) return false
        val unavailable = when {
            contactNames.isEmpty() -> REASON_NOT_CONFIGURED
            !isOnline() -> REASON_OFFLINE
            !gateway.hasPermission() -> REASON_NO_PERMISSION
            else -> null
        }
        if (unavailable != null) {
            event("advisor_call_unavailable", "reason" to unavailable)
            onResult(Result.Unavailable(unavailable))
            return true
        }
        val available = gateway.contacts()
        queue = resolveContacts(contactNames, available)
        val missing = contactNames.filter { name -> queue.none { matches(it.name, name) } }
        if (missing.isNotEmpty()) {
            event("advisor_contact_missing", "names" to missing.joinToString(","))
        }
        if (queue.isEmpty()) {
            event("advisor_call_unavailable", "reason" to REASON_NO_CONTACTS)
            onResult(Result.Unavailable(REASON_NO_CONTACTS))
            return true
        }
        this.onResult = onResult
        this.onAttempt = onAttempt
        index = 0
        stage = Stage.BETWEEN_CALLS
        gateway.setCallListener(::onCallState)
        // La llamada siempre muestra la cámara (ni el SDK ni el modo privacidad la apagan): la
        // cabeza mira al techo para no enfocar la cara del cliente.
        headRestTimer?.let(scheduler::cancel)
        headRestTimer = null
        gateway.tiltHead(HEAD_UP_DEGREES)
        callCurrent()
        return true
    }

    private fun callCurrent() {
        if (index >= queue.size) {
            finish(Result.NoAnswer(queue.map { it.name }))
            return
        }
        val contact = queue[index]
        onAttempt?.invoke(contact)
        val sessionId = gateway.call(contact)
        event(
            "advisor_call_started",
            "contact" to contact.name,
            "attempt" to index + 1,
            "session" to sessionId.ifBlank { "-" }
        )
        if (sessionId.isBlank()) {
            moveOn(contact, "call_failed")
            return
        }
        stage = Stage.RINGING
        pendingTimer = scheduler.schedule(ringMs) {
            pendingTimer = null
            if (stage == Stage.RINGING) moveOn(contact, "timeout")
        }
    }

    private fun onCallState(state: CallState.State) {
        val contact = queue.getOrNull(index)
        event("advisor_call_state", "state" to state, "contact" to contact?.name, "stage" to stage)
        // Fuera de RINGING llegan los ecos de la llamada anterior (ENDED tras colgar): se ignoran.
        if (stage != Stage.RINGING || contact == null) return
        when (state) {
            CallState.State.INITIALIZED -> Unit
            CallState.State.STARTED -> {
                cancelTimer()
                gateway.hangUp()
                event("advisor_call_answered", "contact" to contact.name)
                finish(Result.Answered(contact.name))
            }
            else -> moveOn(contact, state.name.lowercase())
        }
    }

    private fun moveOn(contact: Contact, reason: String) {
        cancelTimer()
        if (reason != "call_failed") gateway.hangUp()
        event("advisor_call_missed", "contact" to contact.name, "reason" to reason)
        index++
        stage = Stage.BETWEEN_CALLS
        // Pausa para que Temi cierre la llamada anterior antes de marcar la siguiente.
        pendingTimer = scheduler.schedule(BETWEEN_CALLS_MS) {
            pendingTimer = null
            if (stage == Stage.BETWEEN_CALLS) callCurrent()
        }
    }

    private fun finish(result: Result) {
        cancelTimer()
        stage = Stage.IDLE
        gateway.setCallListener(null)
        // Temi da la llamada por cerrada, pero la reunión sigue transmitiendo unos 30 s del lado
        // de la asesora: la cabeza sigue mirando al techo hasta que eso termine.
        headRestTimer = scheduler.schedule(HEAD_HOLD_AFTER_CALL_MS) {
            headRestTimer = null
            gateway.tiltHead(HEAD_REST_DEGREES)
        }
        if (result is Result.NoAnswer) {
            event("advisor_call_no_answer", "tried" to result.tried.size)
        }
        val callback = onResult
        onResult = null
        onAttempt = null
        callback?.invoke(result)
    }

    private fun cancelTimer() {
        pendingTimer?.let(scheduler::cancel)
        pendingTimer = null
    }

    companion object {
        const val REASON_NOT_CONFIGURED = "not_configured"
        const val REASON_OFFLINE = "offline"
        const val REASON_NO_PERMISSION = "no_permission"
        const val REASON_NO_CONTACTS = "no_contacts"
        const val BETWEEN_CALLS_MS = 1_500L
        const val HEAD_UP_DEGREES = 55
        const val HEAD_REST_DEGREES = 0
        const val HEAD_HOLD_AFTER_CALL_MS = 40_000L

        // Mantiene el orden configurado; cada nombre se busca primero exacto y luego por coincidencia parcial.
        fun resolveContacts(names: List<String>, contacts: List<Contact>): List<Contact> =
            names.mapNotNull { name ->
                contacts.firstOrNull { normalize(it.name) == normalize(name) }
                    ?: contacts.firstOrNull { matches(it.name, name) }
            }.distinctBy { it.id }

        private fun matches(contactName: String, configured: String): Boolean {
            val contact = normalize(contactName)
            val wanted = normalize(configured)
            return wanted.isNotEmpty() && (contact == wanted || contact.split(' ').containsAll(wanted.split(' ')))
        }

        private fun normalize(text: String): String =
            java.text.Normalizer.normalize(text.trim().lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("\\s+"), " ")

        fun parseNames(raw: String): List<String> =
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
