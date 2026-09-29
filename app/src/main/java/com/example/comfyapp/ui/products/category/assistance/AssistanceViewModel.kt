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
            pendingAction = null
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
            session.touch()
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
        analyzer.analyze(text, session.current(), lastQuestion) { result ->
            if (stage != ConversationStage.ANALYZING) return@analyze
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
        session.markConfirmed()
        logEvent("confirmation", listOf("answer" to "yes"))
        execute(action)
    }

    fun onCorrectionRequested() {
        if (stage != ConversationStage.CONFIRMING) return
        pendingAction = null
        logEvent("confirmation", listOf("answer" to "correct"))
        // La corrección siempre tiene al menos una escucha, aunque ya se hayan gastado intentos.
        failedAttempts = minOf(failedAttempts, MAX_FAILED_ATTEMPTS - 1)
        listens = minOf(listens, MAX_LISTENS - 1)
        ask(CORRECTION_QUESTION)
    }

    fun onConfirmationTimeout() {
        if (stage != ConversationStage.CONFIRMING) return
        pendingAction = null
        logEvent("confirmation", listOf("answer" to "timeout"))
        finish()
    }

    // Resultado del coordinador: la acción terminó, o necesita que el cliente responda algo más.
    fun onExecutionFinished(followUpQuestion: String? = null) {
        if (stage != ConversationStage.EXECUTING && stage != ConversationStage.WAITING_ADVISOR) return
        if (followUpQuestion == null) finish() else retryOrGiveUp(followUpQuestion)
    }

    // La pantalla se fue o el robot empezó a moverse: la conversación se corta sin perder el contexto.
    fun cancel() {
        if (stage == ConversationStage.IDLE) return
        pendingAction = null
        finish()
    }

    fun effectHandled() {
        _effect.value = null
    }

    private fun handleAnalysis(analysis: IntentAnalysis) {
        val merge = session.merge(analysis)
        logEvent(
            "intent_classified",
            listOf("intent" to analysis.assistanceType) +
                AssistanceEventLog.contextFields(merge.current).toList()
        )

        // Frase sin nada útil ("eh", ruido): no hubo progreso, cuenta como intento fallido.
        if (analysis.assistanceType == AssistanceType.CLARIFICATION && merge.changedFields.isEmpty()) {
            retryOrGiveUp(analysis.nextQuestion ?: RouteAssistanceUseCase.DEFAULT_CLARIFICATION)
            return
        }

        setStage(ConversationStage.ROUTING)
        val action = route(merge.current, analysis.nextQuestion)
        logEvent("assistance_routed", listOf("action" to action.logName()))

        when {
            action is AssistanceAction.AskClarification -> {
                // Si el cliente aportó datos nuevos, preguntar lo que falta es avanzar, no fallar.
                if (merge.changedFields.isNotEmpty() && listens < MAX_LISTENS) {
                    ask(action.question)
                } else {
                    retryOrGiveUp(action.question)
                }
            }
            action.needsConfirmation() && !session.isConfirmed() -> {
                pendingAction = action
                val summary = describe(merge.current, action)
                setStage(ConversationStage.CONFIRMING, question = summary)
                _effect.value = AssistanceEffect.Confirm(summary)
            }
            else -> execute(action)
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
            finish()
            _effect.value = AssistanceEffect.GiveUp(FINAL_ATTEMPT_TEXT)
            return
        }
        ask(question)
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
        const val FINAL_ATTEMPT_TEXT = "No logré entenderte. Puedes tocar una categoría en mi pantalla."
    }
}
