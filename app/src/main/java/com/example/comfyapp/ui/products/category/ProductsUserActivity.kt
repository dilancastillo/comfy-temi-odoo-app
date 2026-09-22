// muestra las categorias principales y ejecuta los efectos indicados por su modelo de vista
package com.example.comfyapp.ui.products.category

import android.content.Intent
import android.os.Bundle
import com.example.comfyapp.logging.PersistentLog as Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.agent.AgentAiClient
import com.example.comfyapp.agent.AgentSpeechController
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.model.ProductVideo
import com.example.comfyapp.domain.usecase.SelectProductCategoryUseCase
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.robot.TemiSessionManager
import com.example.comfyapp.ui.products.list.ProductListUserActivity
import com.example.comfyapp.ui.products.tiles.TilesListActivity
import com.example.comfyapp.databinding.ActivityProductsUserBinding
import com.example.comfyapp.ui.SimpleViewModelFactory
import com.example.comfyapp.ui.RobotInactivityNavigator
import com.example.comfyapp.ui.products.tiles.TileCategory
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener

class ProductsUserActivity : AppCompatActivity(), OnDetectionStateChangedListener {

    private lateinit var binding: ActivityProductsUserBinding
    private lateinit var viewModel: ProductsUserViewModel
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
    private val robot = Robot.getInstance()
    private val speechController = AgentSpeechController.shared
    private val aiClient by lazy { AgentAiClient() }
    private var detectionModeActivated = false
    private var detectionListenerRegistered = false
    private var agentAttempt = 0
    @Volatile private var agentRunning = false
    private var waitingToOpenProductList = false
    private var preserveSharedSpeechOnStop = false
    private var listeningCountdownRunnable: Runnable? = null
    private val navigationStateListener: (Boolean) -> Unit = { isNavigating ->
        if (isNavigating) {
            Log.i(TAG, "detection_mode_disabled reason=robot_navigating")
            deactivateDetectionMode()
        } else {
            Log.i(TAG, "detection_mode_enabled reason=robot_navigation_finished")
            activateDetectionMode()
        }
    }

    companion object {
        private const val TAG = "ProductsUserActivity"
        private const val DETECTION_DISTANCE_METERS = 1.5f
        private const val AGENT_GREETING_DELAY_MS = 500L
        private const val LISTEN_TIMEOUT_SECONDS = 10
        private const val MAX_AGENT_ATTEMPTS = 2
        private const val LISTENING_TEXT = "Te escucho..."
        private const val THINKING_TEXT = "Pensando..."
        private const val RETRY_QUESTION_TEXT =
            "No te entendí bien. ¿Qué estás buscando: sanitarios, griferías o pisos y paredes?"
        private const val AGENT_QUESTION_TEXT =
            "Hola, bienvenido. Estoy aquí para ayudarte mientras mi compañera Liliana está disponible. " +
                "¿Qué estás buscando? ¿Sanitarios, griferías o pisos y paredes?"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductsUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            SimpleViewModelFactory {
                TemiRobotRepository(applicationContext).let(::ProductsUserViewModel)
            }
        )[ProductsUserViewModel::class.java]

        // Activar Detection Mode cuando el robot esté listo
        bindActions()
        observeEffects()
    }

    override fun onStop() {
        val preserveSharedSpeech = preserveSharedSpeechOnStop
        preserveSharedSpeechOnStop = false
        deactivateDetectionMode(stopSpeaking = !preserveSharedSpeech)
        TemiSessionManager.removeNavigationStateListener(navigationStateListener)
        inactivityNavigator.stop()
        viewModel.stopRobot()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        inactivityNavigator.start()
        viewModel.startRobot()
        TemiSessionManager.addNavigationStateListener(navigationStateListener)
        if (!viewModel.isRobotNavigating()) activateDetectionMode()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (waitingToOpenProductList) {
            return
        }
        viewModel.onAction(ProductsUserAction.UserInteraction)
    }

    override fun onDetectionStateChanged(state: Int) {
        Log.i(TAG, "onDetectionStateChanged state=$state")
        if (state == OnDetectionStateChangedListener.DETECTED) {
            if (viewModel.isRobotNavigating()) {
                Log.i(TAG, "agent_detection_ignored reason=robot_navigating")
                return
            }
            iniciarFlujoAgente()
        }
    }

    private fun iniciarFlujoAgente() {
        if (agentRunning) return
        agentRunning = true
        agentAttempt = 1
        aiClient.reset()
        // Detectar a alguien y empezar a hablarle cuenta como interacción real: reinicia el
        // temporizador de inactividad para que no se dispare "volver a Centro Sala" a mitad
        // de la conversación de voz.
        viewModel.onAction(ProductsUserAction.UserInteraction)
        Log.i(TAG, "agent_flow_started")
        robot.tiltAngle(50, 1f)

        mostrarOverlay()

        binding.root.postDelayed({
            if (!agentRunning) return@postDelayed
            Log.i(TAG, "agent_question_started")
            mostrarPregunta()
            speechController.speak(
                AGENT_QUESTION_TEXT.replace("\n", " ")
            ) {
                Log.i(TAG, "agent_question_finished_listening")
                mostrarEscuchando()
                speechController.listen(LISTEN_TIMEOUT_SECONDS) { result ->
                    when (result) {
                        is AgentSpeechController.ListenResult.Text -> {
                            Log.i(TAG, "usuario dijo: ${result.value}")
                            mostrarPensando()
                            aiClient.resolver(result.value) { decision ->
                                agentRunning = false
                                ocultarOverlay()
                                decision.fold(
                                    onSuccess = {
                                        handleAgentDecision(resolveDecision(it, result.value))
                                    },
                                    onFailure = {
                                        Log.w(TAG, "Gemini falló", it)
                                        retryAgentQuestion()
                                    }
                                )
                            }
                        }
                        else -> {
                            agentRunning = false
                            ocultarOverlay()
                            retryAgentQuestion()
                        }
                    }
                }
            }
        }, AGENT_GREETING_DELAY_MS)
    }

    private fun handleAgentDecision(decision: AgentAiClient.Decision) {
        if (decision.screenId.isBlank()) {
            // Gemini ya entendió la petición y armó una respuesta (declina, pide aclarar,
            // o pide repetir) — se dice tal cual en vez de una pregunta genérica fija.
            retryAgentQuestion(decision.mensaje)
            return
        }
        navegarSegunDecision(decision)
    }

    private fun retryAgentQuestion(agentMessage: String = "") {
        if (agentAttempt >= MAX_AGENT_ATTEMPTS) {
            agentRunning = false
            ocultarOverlay()
            speechController.speak(
                agentMessage.ifBlank { "No logré entenderte. Puedes tocar una categoría en mi pantalla." }
            )
            return
        }

        val questionToAsk = agentMessage.ifBlank { RETRY_QUESTION_TEXT }
        agentAttempt += 1
        agentRunning = true
        mostrarOverlay()
        mostrarPregunta(questionToAsk)
        Log.i(TAG, "agent_question_retry attempt=$agentAttempt")
        speechController.speak(questionToAsk) {
            if (agentRunning) {
                mostrarEscuchando()
                speechController.listen(LISTEN_TIMEOUT_SECONDS) { result ->
                    when (result) {
                        is AgentSpeechController.ListenResult.Text -> {
                            Log.i(TAG, "usuario dijo retry: ${result.value}")
                            mostrarPensando()
                            aiClient.resolver(result.value) { decision ->
                                agentRunning = false
                                ocultarOverlay()
                                decision.fold(
                                    onSuccess = {
                                        handleAgentDecision(resolveDecision(it, result.value))
                                    },
                                    onFailure = { retryAgentQuestion() }
                                )
                            }
                        }
                        else -> {
                            agentRunning = false
                            ocultarOverlay()
                            retryAgentQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun mostrarOverlay() = with(binding) {
        overlayDark.visibility = View.VISIBLE
        agentOverlayTexts.visibility = View.VISIBLE
        tvAgentStatus.visibility = View.GONE
    }

    private fun mostrarPregunta(question: String = AGENT_QUESTION_TEXT) = with(binding) {
        tvAgentQuestion.text = question
    }

    private fun mostrarEscuchando() = with(binding) {
        tvAgentStatus.visibility = View.VISIBLE
        startListeningCountdown()
    }

    private fun mostrarPensando() = with(binding) {
        stopListeningCountdown()
        tvAgentStatus.text = THINKING_TEXT
        tvAgentStatus.visibility = View.VISIBLE
    }

    private fun startListeningCountdown() {
        stopListeningCountdown()
        var secondsLeft = LISTEN_TIMEOUT_SECONDS
        binding.tvAgentStatus.text = "$LISTENING_TEXT $secondsLeft"
        val runnable = object : Runnable {
            override fun run() {
                secondsLeft--
                if (secondsLeft < 0) return
                binding.tvAgentStatus.text = "$LISTENING_TEXT $secondsLeft"
                if (secondsLeft > 0) {
                    binding.root.postDelayed(this, 1_000L)
                }
            }
        }
        listeningCountdownRunnable = runnable
        binding.root.postDelayed(runnable, 1_000L)
    }

    private fun stopListeningCountdown() {
        listeningCountdownRunnable?.let { binding.root.removeCallbacks(it) }
        listeningCountdownRunnable = null
    }

    private fun ocultarOverlay() = with(binding) {
        stopListeningCountdown()
        overlayDark.visibility = View.GONE
        agentOverlayTexts.visibility = View.GONE
        tvAgentStatus.visibility = View.GONE
    }

    private fun activateDetectionMode() {
        robot.trackUserOn = false
        Log.i(TAG, "track_user_on_actual=${robot.trackUserOn}")
        if (!detectionListenerRegistered) {
            robot.addOnDetectionStateChangedListener(this)
            detectionListenerRegistered = true
        }
        robot.setDetectionModeOn(true, DETECTION_DISTANCE_METERS)
        detectionModeActivated = true
        Log.i(TAG, "detection_mode_on=true")
    }

    private fun deactivateDetectionMode(stopSpeaking: Boolean = true) {
        agentRunning = false
        speechController.stopListening()
        if (stopSpeaking) speechController.stopSpeaking()
        ocultarOverlay()
        if (detectionListenerRegistered) {
            robot.removeOnDetectionStateChangedListener(this)
            detectionListenerRegistered = false
        }
        if (detectionModeActivated) robot.setDetectionModeOn(false, DETECTION_DISTANCE_METERS)
        detectionModeActivated = false
        Log.i(TAG, "detection_mode_on=false")
    }

    private fun navegarSegunDecision(decision: AgentAiClient.Decision) {
        Log.i(TAG, "navegando a screen_id=${decision.screenId}")
        when (decision.screenId) {
            "baños" -> abrirTiles(TileCategory.BATHROOMS)
            "zona_social" -> abrirTiles(TileCategory.SOCIAL_AREAS)
            "exteriores" -> abrirTiles(TileCategory.EXTERIORS)
            "pisos_y_paredes" -> {
                val openScreen = { startActivity(Intent(this, TilesListActivity::class.java)) }
                if (decision.mensaje.isNotBlank()) {
                    // Se espera a que termine de decir la aclaración antes de abrir la
                    // siguiente pantalla — TilesListActivity dice su propio saludo apenas
                    // arranca, y como speak() cancela lo que esté sonando, si se navega de
                    // inmediato esa aclaración se corta a mitad de frase.
                    preserveSharedSpeechOnStop = true
                    speechController.speak(decision.mensaje) { openScreen() }
                } else {
                    openScreen()
                }
            }
            "sanitarios" -> {
                val request = ProductListRequest(
                    category = ProductCategory.SANITARY,
                    title = "Sanitarios y Accesorios",
                    firstColumnTitle = "Combos",
                    secondColumnTitle = "Solos",
                    robotLocation = "sanitarios",
                    video = ProductVideo.SANITARY,
                    pageSize = 15
                )
                val repo = TemiRobotRepository(applicationContext)
                val useCase = SelectProductCategoryUseCase(repo)
                val finalRequest: ProductListRequest = useCase(request)
                openProductList(finalRequest)
            }
            "griferias" -> {
                val request = ProductListRequest(
                    category = ProductCategory.TAPS,
                    title = "Griferías",
                    firstColumnTitle = "Lavamanos",
                    secondColumnTitle = "Lavaplatos",
                    robotLocation = "griferias",
                    video = ProductVideo.TAPS,
                    maxProductsPerColumn = 15
                )
                val repo = TemiRobotRepository(applicationContext)
                val useCase = SelectProductCategoryUseCase(repo)
                val finalRequest: ProductListRequest = useCase(request)
                openProductList(finalRequest)
            }
            else -> {
                val message = decision.mensaje.ifBlank {
                    "Puedes elegir pisos y paredes, sanitarios o griferías en mi pantalla."
                }
                speechController.speak(message)
            }
        }
    }
    private fun abrirTiles(category: TileCategory) {
        preserveSharedSpeechOnStop = true
        startActivity(
            Intent(this, TilesListActivity::class.java).apply {
                putExtra(TilesListActivity.EXTRA_CATEGORY, category.name)
            }
        )
    }

    private fun resolveDecision(
        decision: AgentAiClient.Decision,
        recognizedText: String
    ): AgentAiClient.Decision {
        if (decision.screenId.isNotBlank()) return decision

        val normalizedText = recognizedText.lowercase()
        return if (normalizedText.contains("piso") || normalizedText.contains("pared")) {
            decision.copy(screenId = "pisos_y_paredes")
        } else {
            decision
        }
    }

    private fun bindActions() = with(binding) {
        btnRevestimientos.setOnClickListener {
            handleCategorySelection(ProductsUserAction.SelectFloorAndWall)
        }
        btnBathrooms.setOnClickListener {
            handleCategorySelection(ProductsUserAction.SelectSanitary)
        }
        btnfaucets.setOnClickListener {
            handleCategorySelection(ProductsUserAction.SelectTaps)
        }
        imgbtnback.setOnClickListener {
            viewModel.onAction(ProductsUserAction.Back)
        }
    }

    private fun observeEffects() {
        viewModel.effect.observe(this) { effect ->
            when (effect) {
                ProductsUserEffect.OpenTiles ->
                    startActivity(Intent(this, TilesListActivity::class.java))
                is ProductsUserEffect.OpenProductList -> openProductList(effect.request)
                ProductsUserEffect.Close -> finish()
                null -> return@observe
            }
            viewModel.effectHandled()
        }
    }

    private fun openProductList(request: ProductListRequest) {
        val openScreen = {
            if (!isFinishing) {
                startActivity(ProductListUserActivity.createIntent(this, request))
            }
        }
        if (request.showTravelVideo) {
            waitingToOpenProductList = true
            speechController.speak("¡Perfecto! Acompáñame.") {
                if (waitingToOpenProductList) {
                    waitingToOpenProductList = false
                    openScreen()
                }
            }
        } else {
            openScreen()
        }
    }

    private fun handleCategorySelection(action: ProductsUserAction) {
        if (waitingToOpenProductList) {
            cancelPendingProductLaunch()
            return
        }
        viewModel.onAction(action)
    }

    private fun cancelPendingProductLaunch() {
        if (!waitingToOpenProductList) return
        waitingToOpenProductList = false
        speechController.stopSpeaking()
        TemiSessionManager.cancelNavigationByUser()
    }
}
