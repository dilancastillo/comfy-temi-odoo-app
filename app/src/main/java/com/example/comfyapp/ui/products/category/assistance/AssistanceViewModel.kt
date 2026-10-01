// mantiene la conversacion con el cliente: escucha, analiza, confirma y decide la accion sin tocar la vista
package com.example.comfyapp.ui.products.category.assistance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.ConversationStage
import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.repository.AnalysisRequest
import com.example.comfyapp.domain.repository.IntentAnalyzer
import com.example.comfyapp.domain.usecase.DescribeCustomerContextUseCase
import com.example.comfyapp.domain.usecase.RouteAssistanceUseCase
import com.example.comfyapp.logging.AssistanceEventLog
import com.example.comfyapp.session.CustomerSessionManager

data class AssistanceUiState(
    val stage: ConversationStage = ConversationStage.IDLE,
    val context: CustomerContext = CustomerContext(),
    val question: String? = null,
    val error: String? = null
)

sealed interface AssistanceEffect {
    // decir la pregunta y abrir el micrófono al terminar
    data class AskAndListen(val question: String) : AssistanceEffect
    // decir el resumen y mostrar "Sí, correcto" / "Quiero corregir algo"
    data class Confirm(val summary: String) : AssistanceEffect
    // context es la memoria ya aceptada; la acción usa su necesidad activa
    data class Execute(val action: AssistanceAction, val context: CustomerContext) : AssistanceEffect
    // se agotaron los intentos: decir el mensaje y cerrar la conversación
    data class GiveUp(val message: String) : AssistanceEffect
}

class AssistanceViewModel(
    private val analyzer: IntentAnalyzer,
    private val session: CustomerSessionManager = CustomerSessionManager,
    private val route: RouteAssistanceUseCase = RouteAssistanceUseCase(),
    private val describe: DescribeCustomerContextUseCase = DescribeCustomerContextUseCase(),
    private val logEvent: (String, List<Pair<String, Any?>>) -> Unit =
        { name, fields -> AssistanceEventLog.event(name, *fields.toTypedArray()) },
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _state = MutableLiveData(AssistanceUiState())
    val state: LiveData<AssistanceUiState> = _state

    private val _effect = MutableLiveData<AssistanceEffect?>()
    val effect: LiveData<AssistanceEffect?> = _effect

    private var failedAttempts = 0
    private var listens = 0
    private var lastQuestion: String? = null
    private var pendingAction: AssistanceAction? = null
    // la próxima respuesta corrige el borrador pendiente (se pulsó "Quiero corregir algo")
    private var correcting = false
    // identifica la petición vigente a Gemini: una respuesta de una petición anterior se ignora
    private var requestId = 0L
    private var stageChangedAtMs = 0L

    val stage: ConversationStage get() = _state.value?.stage ?: ConversationStage.IDLE

    // Devuelve false si ya hay una conversación en curso (evita que la detección la reinicie).
    // fromDetection: si nadie habló en un buen rato, quien se para ahora frente a Temi es otro
    // cliente; con el botón "Hablar con Temi" se asume que sigue el mismo.
    fun start(fromDetection: Boolean): Boolean {
        if (stage != ConversationStage.IDLE) {
            // Una escucha o respuesta que nunca volvió (la cortó otra voz o el sistema) no puede
            // dejar al agente bloqueado para siempre.
            if (clock() - stageChangedAtMs < STALE_CONVERSATION_MS) return false
            logEvent("conversation_stale_reset", listOf("stage" to stage))
            abandonTurn()
        }
        if (fromDetection && session.isIdleFor(SESSION_IDLE_TIMEOUT_MS, clock())) {
            session.reset()
            logEvent("session_reset", listOf("reason" to "idle"))
        }
        val returningCustomer = session.hasActiveCustomer()
        if (!returningCustomer) {
            analyzer.reset()
            logEvent("session_started", emptyList())
        } else {
            session.touch(clock())
        }
        failedAttempts = 0
        listens = 0
        ask(if (returningCustomer) FOLLOW_UP_QUESTION else GREETING)
        return true
    }

    fun onListeningStarted() {
        if (stage == ConversationStage.ASKING_DETAIL) setStage(ConversationStage.LISTENING)
    }

    fun onHeard(text: String) {
        if (stage != ConversationStage.LISTENING) return
        listens++
        setStage(ConversationStage.ANALYZING)
        val thisRequest = ++requestId
        val thisSession = session.epoch
        val request = AnalysisRequest(
            text = text,
            accepted = session.current(),
            draft = session.pending(),
            correcting = correcting,
            lastQuestion = lastQuestion
        )
        analyzer.analyze(request) { result ->
            // Solo cuenta la respuesta de la petición vigente y del mismo cliente.
            if (thisRequest != requestId || thisSession != session.epoch || stage != ConversationStage.ANALYZING) {
                logEvent("intent_ignored", listOf("reason" to "stale_response"))
                return@analyze
            }
            result.fold(
                onSuccess = ::handleAnalysis,
                onFailure = { error ->
                    logEvent("intent_failed", listOf("error" to error::class.simpleName))
                    retryOrGiveUp(RETRY_QUESTION)
                }
            )
        }
    }

    fun onNothingHeard() {
        if (stage != ConversationStage.LISTENING) return
        listens++
        logEvent("listen_empty", emptyList())
        retryOrGiveUp(RETRY_QUESTION)
    }

    fun onConfirmed() {
        val action = pendingAction ?: return
        if (stage != ConversationStage.CONFIRMING) return
        logEvent("confirmation", listOf("answer" to "yes", "need" to session.pending()?.need?.id))
        // Recién ahora el borrador pasa a la memoria aceptada, confirmado para esa necesidad y revisión.
        session.commitPending(markConfirmed = true, nowMs = clock())
        execute(action)
    }

    fun onCorrectionRequested() {
        if (stage != ConversationStage.CONFIRMING) return
        pendingAction = null
        logEvent("confirmation", listOf("answer" to "correct", "need" to session.pending()?.need?.id))
        // Lo rechazado se queda en el borrador para corregirlo, nunca en lo aceptado ni en el historial.
        correcting = true
        analyzer.forgetLastTurn()
        // La corrección siempre tiene al menos una escucha, aunque ya se hayan gastado intentos.
        failedAttempts = minOf(failedAttempts, MAX_FAILED_ATTEMPTS - 1)
        listens = minOf(listens, MAX_LISTENS - 1)
        ask(CORRECTION_QUESTION)
    }

    fun onConfirmationTimeout() {
        if (stage != ConversationStage.CONFIRMING) return
        logEvent("confirmation", listOf("answer" to "timeout"))
        abandonTurn()
        finish()
    }

    // Resultado del coordinador: la acción terminó, o necesita que el cliente responda algo más.
    fun onExecutionFinished(followUpQuestion: String? = null) {
        if (stage != ConversationStage.EXECUTING && stage != ConversationStage.WAITING_ADVISOR) return
        if (followUpQuestion == null) finish() else retryOrGiveUp(followUpQuestion)
    }

    // La pantalla se fue o el robot empezó a moverse: se corta la conversación; lo aceptado se
    // conserva y el borrador sin confirmar se descarta.
    fun cancel() {
        if (stage == ConversationStage.IDLE) return
        abandonTurn()
        finish()
    }

    fun effectHandled() {
        _effect.value = null
    }

    private fun handleAnalysis(analysis: IntentAnalysis) {
        val wasCorrecting = correcting
        correcting = false
        val change = session.prepareChange(analysis, correcting = wasCorrecting, nowMs = clock())
        val target = change.candidate.activeNeed
        logEvent(
            "intent_classified",
            listOf("intent" to analysis.assistanceType, "operation" to change.operation) +
                AssistanceEventLog.needFields(target).toList()
        )

        // Se refirió a algo guardado pero no queda claro a qué: se pregunta antes de tocar la memoria.
        if (change.ambiguousNeeds.isNotEmpty()) {
            logEvent("need_ambiguous", listOf("options" to change.ambiguousNeeds.joinToString(",") { it.id }))
            if (listens < MAX_LISTENS) ask(describe.askWhich(change.ambiguousNeeds)) else retryOrGiveUp(RETRY_QUESTION)
            return
        }
        if (change.resumeNotFound) {
            logEvent("need_not_found", emptyList())
            retryOrGiveUp(RESUME_NOT_FOUND_QUESTION)
            return
        }

        // Frase sin nada útil ("eh", ruido): no hubo progreso, cuenta como intento fallido.
        if (analysis.assistanceType == AssistanceType.CLARIFICATION && change.changedFields.isEmpty()) {
            correcting = wasCorrecting
            retryOrGiveUp(analysis.nextQuestion ?: RouteAssistanceUseCase.DEFAULT_CLARIFICATION)
            return
        }

        setStage(ConversationStage.ROUTING)
        val action = route(target, analysis.assistanceType, analysis.nextQuestion)
        logEvent("assistance_routed", listOf("action" to action.logName(), "need" to target?.id))

        when {
            // Falta un dato: el borrador sigue abierto y la próxima respuesta lo completa.
            action is AssistanceAction.AskClarification -> {
                // Si el cliente aportó datos nuevos, preguntar lo que falta es avanzar, no fallar.
                if (change.changedFields.isNotEmpty() && listens < MAX_LISTENS) {
                    ask(action.question)
                } else {
                    retryOrGiveUp(action.question)
                }
            }
            action.needsConfirmation() && change.requiresConfirmation -> {
                pendingAction = action
                val summary = describe(target, action, change.additionalNeeds)
                setStage(ConversationStage.CONFIRMING, question = summary)
                _effect.value = AssistanceEffect.Confirm(summary)
            }
            else -> {
                // Detalle sobre una necesidad ya confirmada: se guarda sin volver a preguntar.
                session.commitPending(markConfirmed = action.needsConfirmation(), nowMs = clock())
                execute(action)
            }
        }
    }

    private fun execute(action: AssistanceAction) {
        pendingAction = null
        val next = if (action == AssistanceAction.RequestAdvisor) {
            ConversationStage.WAITING_ADVISOR
        } else {
            ConversationStage.EXECUTING
        }
        setStage(next)
        _effect.value = AssistanceEffect.Execute(action, session.current())
    }

    private fun retryOrGiveUp(question: String) {
        failedAttempts++
        if (failedAttempts >= MAX_FAILED_ATTEMPTS || listens >= MAX_LISTENS) {
            logEvent("assistance_gave_up", listOf("listens" to listens))
            abandonTurn()
            finish()
            _effect.value = AssistanceEffect.GiveUp(FINAL_ATTEMPT_TEXT)
            return
        }
        ask(question)
    }

    // Invalida la petición en curso y descarta el borrador sin confirmar.
    private fun abandonTurn() {
        requestId++
        pendingAction = null
        correcting = false
        session.discardPending()
    }

    private fun ask(question: String) {
        lastQuestion = question
        setStage(ConversationStage.ASKING_DETAIL, question = question)
        _effect.value = AssistanceEffect.AskAndListen(question)
    }

    private fun finish() {
        setStage(ConversationStage.IDLE)
    }

    private fun setStage(stage: ConversationStage, question: String? = _state.value?.question) {
        stageChangedAtMs = clock()
        _state.value = AssistanceUiState(stage = stage, context = session.current(), question = question)
    }

    // Solo se confirma antes de acciones que muestran productos o mueven a Temi.
    private fun AssistanceAction.needsConfirmation(): Boolean =
        this is AssistanceAction.SearchExactProduct ||
            this is AssistanceAction.StartCategoryFlow ||
            this is AssistanceAction.NavigateToCategory

    private fun AssistanceAction.logName(): String = when (this) {
        is AssistanceAction.SearchExactProduct -> "SearchExactProduct"
        is AssistanceAction.StartCategoryFlow -> "StartCategoryFlow:$destination"
        is AssistanceAction.NavigateToCategory -> "NavigateToCategory:$destination"
        is AssistanceAction.AskClarification -> "AskClarification"
        AssistanceAction.RequestAdvisor -> "RequestAdvisor"
        AssistanceAction.JustBrowsing -> "JustBrowsing"
        AssistanceAction.Unsupported -> "Unsupported"
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 2
        // tope de escuchas por conversación, aunque cada turno aporte datos (costo de Azure)
        const val MAX_LISTENS = 4
        const val SESSION_IDLE_TIMEOUT_MS = 60_000L
        // más que la escucha (10 s) + dos intentos a Gemini (13 s c/u) + lo que dura una pregunta
        const val STALE_CONVERSATION_MS = 45_000L

        const val GREETING =
            "Hola, bienvenido. Estoy aquí para ayudarte mientras mi compañera Liliana está disponible. " +
                "¿Qué estás buscando? ¿Sanitarios, griferías o pisos y paredes?"
        const val FOLLOW_UP_QUESTION = "Claro, dime. ¿En qué más te puedo ayudar?"
        const val RETRY_QUESTION =
            "No te entendí bien. ¿Qué estás buscando: sanitarios, griferías o pisos y paredes?"
        const val CORRECTION_QUESTION = "Claro, ¿qué quieres corregir?"
        const val RESUME_NOT_FOUND_QUESTION =
            "Eso no lo tengo anotado en esta visita. ¿Quieres que lo busquemos? Dime qué producto necesitas."
        const val FINAL_ATTEMPT_TEXT = "No logré entenderte. Puedes tocar una categoría en mi pantalla."
    }
}
