// pantalla principal: detecta al cliente, conversa con el y ofrece buscar productos o llamar a una asesora
package com.example.comfyapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.example.comfyapp.logging.PersistentLog as Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.comfyapp.agent.AgentAiClient
import com.example.comfyapp.agent.AgentSpeechController
import com.example.comfyapp.core.MqttController
import com.example.comfyapp.core.NetworkStatus
import com.example.comfyapp.core.TemiAdvisorCalls
import com.example.comfyapp.databinding.ActivityCoverBinding
import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.ConversationStage
import com.example.comfyapp.domain.model.ProductFilters
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.robot.TemiSessionManager
import com.example.comfyapp.session.CustomerSessionManager
import com.example.comfyapp.ui.RobotInactivityNavigator
import com.example.comfyapp.ui.SimpleViewModelFactory
import com.example.comfyapp.ui.products.category.ProductsUserActivity
import com.example.comfyapp.ui.products.category.assistance.AssistanceCoordinator
import com.example.comfyapp.ui.products.category.assistance.AssistanceEffect
import com.example.comfyapp.ui.products.category.assistance.AssistanceViewModel
import com.example.comfyapp.ui.products.list.ProductListUserActivity
import com.example.comfyapp.ui.products.tiles.TileCategory
import com.example.comfyapp.ui.products.tiles.TilesListActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.permission.Permission

class CoverActivity : AppCompatActivity(), OnDetectionStateChangedListener {
    private lateinit var binding: ActivityCoverBinding
    private lateinit var mqttController: MqttController
    private lateinit var robotRepository: TemiRobotRepository
    private lateinit var assistanceViewModel: AssistanceViewModel
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
    private val robot: Robot = Robot.getInstance()
    private val speechController = AgentSpeechController.shared
    private val aiClient by lazy { AgentAiClient() }
    private val assistanceCoordinator by lazy {
        AssistanceCoordinator(robotRepository, speechController, assistanceHost)
    }
    private val assistanceHost = object : AssistanceCoordinator.Host {
        // La lista se abre de inmediato y la frase sigue sonando en la pantalla siguiente: así nada
        // que interrumpa la voz (otra frase, la detección) deja al cliente sin ver los productos.
        override fun openProductList(request: ProductListRequest, announcement: String?) {
            if (announcement != null) {
                preserveSharedSpeechOnStop = true
                speechController.speak(announcement)
            }
            if (!isFinishing) {
                leaveScreen()
                startActivity(ProductListUserActivity.createIntent(this@CoverActivity, request))
            }
        }

        override fun returnToScreen() {
            runCatching { applicationContext.startActivity(homeIntent(applicationContext).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Log.e(TAG, "No se pudo volver a la pantalla tras la llamada", it) }
        }

        override fun onAdvisorFlowChanged(active: Boolean) {
            setActionsEnabled(!active)
            if (active) {
                // Sin detección no hay saludo encima del aviso ni durante la llamada.
                deactivateDetectionMode(stopSpeaking = false)
            } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !robotRepository.isNavigationInProgress()
            ) {
                activateDetectionMode()
            }
        }

        override fun openTiles(category: TileCategory?, filters: ProductFilters) {
            preserveSharedSpeechOnStop = true
            leaveScreen()
            startActivity(TilesListActivity.createIntent(this@CoverActivity, category, filters))
        }
    }
    private val confirmationTimeout = Runnable {
        ocultarOverlay()
        assistanceViewModel.onConfirmationTimeout()
    }
    private var pendingStartAgent = false
    private var lastOfflineNoticeAtMs = 0L
    private var detectionModeActivated = false
    private var detectionListenerRegistered = false
    private var preserveSharedSpeechOnStop = false
    private var detectionPausedUntilMs = 0L
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

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "RECORD_AUDIO granted")
        } else {
            Log.w(TAG, "RECORD_AUDIO denied — el agente de voz no funcionará")
        }
    }

    companion object {
        private const val TAG = "CoverActivity"
        // al volver con "Hablar con Temi" desde otra pantalla, la conversación arranca sola
        const val EXTRA_START_AGENT = "extra_start_agent"
        private const val CONFIRMATION_TIMEOUT_MS = 20_000L
        private const val OFFLINE_NOTICE_COOLDOWN_MS = 60_000L
        private const val OFFLINE_TEXT =
            "En este momento no tengo conexión a internet. Puedes tocar Buscar productos en mi pantalla."
        private const val DETECTION_DISTANCE_METERS = 1.5f
        private const val LISTEN_TIMEOUT_SECONDS = 13
        private const val LISTENING_TEXT = "Te estoy escuchando, puedes hablar..."
        private const val THINKING_TEXT = "Pensando..."
        private const val HEAD_TALK_DEGREES = 50
        private const val DISABLED_ALPHA = 0.4f
        private const val DETECTION_PAUSE_AFTER_CLOSE_MS = 30_000L

        // Vuelve a la pantalla principal cerrando las que estén encima.
        fun homeIntent(context: Context): Intent =
            Intent(context, CoverActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        // Botón flotante de las demás pantallas: vuelve aquí y Temi empieza a escuchar.
        fun talkIntent(context: Context): Intent =
            homeIntent(context).putExtra(EXTRA_START_AGENT, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        desactivarLockTask()

        // Solicitar permiso de micrófono para Azure Speech
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "solicitando RECORD_AUDIO")
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            Log.i(TAG, "RECORD_AUDIO ya concedido")
        }

        solicitarPermisosTemi()
        inicializarControladores()

        binding.root.postDelayed({
            try {
                mqttController.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando MQTT", e)
                Toast.makeText(this, "Error MQTT: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, 500)

        robotRepository.start()

        assistanceViewModel = ViewModelProvider(
            this,
            SimpleViewModelFactory { AssistanceViewModel(aiClient) }
        )[AssistanceViewModel::class.java]

        bindActions()
        observeAssistance()
        pendingStartAgent = intent.getBooleanExtra(EXTRA_START_AGENT, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Llega de otra pantalla o del regreso por inactividad: lo que estuviera en curso ya no aplica.
        cancelConfirmationTimeout()
        assistanceViewModel.cancel()
        ocultarOverlay()
        pendingStartAgent = intent.getBooleanExtra(EXTRA_START_AGENT, false)
    }

    override fun onResume() {
        super.onResume()
        setActionsEnabled(!TemiAdvisorCalls.isFlowActive)
        try {
            robot.hideTopBar()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo ocultar TopBar", e)
        }
        if (pendingStartAgent) {
            pendingStartAgent = false
            binding.root.post {
                if (!robotRepository.isNavigationInProgress()) startAssistance(fromDetection = false)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        inactivityNavigator.start()
        try {
            if (isTaskRoot) stopLockTask()
        } catch (e: Exception) {
            Log.d(TAG, "No estaba en Lock Task Mode")
        }
        TemiSessionManager.addNavigationStateListener(navigationStateListener)
        if (!robotRepository.isNavigationInProgress()) activateDetectionMode()
    }

    override fun onStop() {
        val preserveSharedSpeech = preserveSharedSpeechOnStop
        preserveSharedSpeechOnStop = false
        deactivateDetectionMode(stopSpeaking = !preserveSharedSpeech)
        TemiSessionManager.removeNavigationStateListener(navigationStateListener)
        inactivityNavigator.stop()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttController.disconnect()
            robotRepository.stop()
            Log.i(TAG, "Controladores desconectados")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar", e)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        robotRepository.notifyUserInteraction()
    }

    override fun onDetectionStateChanged(state: Int) {
        Log.i(TAG, "onDetectionStateChanged state=$state")
        if (state == OnDetectionStateChangedListener.DETECTED) {
            // Durante el aviso a la asesora y un rato después, el cliente está esperando: no se saluda.
            if (TemiAdvisorCalls.blocksDetection()) {
                Log.i(TAG, "agent_detection_ignored reason=advisor_call")
                return
            }
            if (robotRepository.isNavigationInProgress()) {
                Log.i(TAG, "agent_detection_ignored reason=robot_navigating")
                return
            }
            if (System.currentTimeMillis() < detectionPausedUntilMs) {
                Log.i(TAG, "agent_detection_ignored reason=closed_by_touch")
                return
            }
            startAssistance(fromDetection = true)
        }
    }

    private fun bindActions() = with(binding) {
        btnTalkTemi.setOnClickListener {
            if (!robotRepository.isNavigationInProgress()) startAssistance(fromDetection = false)
        }
        btnSearchProducts.setOnClickListener {
            closeConversation()
            leaveScreen()
            startActivity(Intent(this@CoverActivity, ProductsUserActivity::class.java))
        }
        btnTalkAdvisor.setOnClickListener { requestAdvisor() }
        imgbexit.setOnClickListener {
            finishAffinity()
            System.exit(0)
        }
        // "Puedes hablarme ... o tocar la pantalla": tocar fuera de los botones cierra la conversación.
        overlayDark.setOnClickListener {
            // En la confirmación solo cuentan los botones: un toque fuera sería perder el pedido.
            if (agentConfirmButtons.visibility == View.VISIBLE) return@setOnClickListener
            Log.i(TAG, "conversation_closed_by_touch")
            closeConversation()
            // El cliente sigue enfrente: sin esta pausa la detección volvería a hablarle de inmediato.
            detectionPausedUntilMs = System.currentTimeMillis() + DETECTION_PAUSE_AFTER_CLOSE_MS
        }
        btnConfirmYes.setOnClickListener {
            cancelConfirmationTimeout()
            agentConfirmButtons.visibility = View.GONE
            speechController.stopSpeaking()
            assistanceViewModel.onConfirmed()
        }
        btnConfirmCorrect.setOnClickListener {
            cancelConfirmationTimeout()
            speechController.stopSpeaking()
            assistanceViewModel.onCorrectionRequested()
        }
    }

    // Mientras Temi avisa a la asesora ningún botón responde: ni otra llamada, ni conversación,
    // ni salir de la pantalla a mitad del aviso.
    private fun setActionsEnabled(enabled: Boolean) = with(binding) {
        listOf(btnTalkTemi, btnSearchProducts, btnTalkAdvisor, imgbexit).forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else DISABLED_ALPHA
        }
    }

    private fun startAssistance(fromDetection: Boolean) {
        // Si Temi acompañó al cliente a una zona, la detección no lo vuelve a saludar allá; si
        // necesita algo más usa "Hablar con Temi".
        if (fromDetection && CustomerSessionManager.current().currentLocation != null) {
            Log.i(TAG, "agent_detection_ignored reason=customer_accompanied")
            return
        }
        // Mientras Temi avisa a una asesora no se abre otra conversación.
        if (TemiAdvisorCalls.isFlowActive) {
            Log.i(TAG, "agent_start_ignored reason=advisor_call")
            return
        }
        if (!NetworkStatus.isOnline()) {
            handleOffline(announce = !fromDetection)
            return
        }
        if (!assistanceViewModel.start(fromDetection)) return
        // Detectar a alguien y empezar a hablarle cuenta como interacción real (reinicia el
        // temporizador de inactividad).
        robotRepository.notifyUserInteraction()
        robot.tiltAngle(HEAD_TALK_DEGREES, 1f)
    }

    // Botón "Hablar con asesor": corta lo que se esté conversando y avisa con la llamada.
    private fun requestAdvisor() {
        if (robotRepository.isNavigationInProgress() || TemiAdvisorCalls.isFlowActive) return
        closeConversation()
        robotRepository.notifyUserInteraction()
        assistanceCoordinator.execute(AssistanceAction.RequestAdvisor, CustomerSessionManager.current()) { }
    }

    // Android tarda en cambiar de pantalla (más de 1 s en el robot); mientras tanto esta sigue
    // activa, y la detección le hablaría al cliente encima de la lista ("¿En qué más te puedo ayudar?").
    private fun leaveScreen() {
        deactivateDetectionMode(stopSpeaking = false)
    }

    private fun closeConversation() {
        cancelConfirmationTimeout()
        assistanceViewModel.cancel()
        speechController.stopListening()
        speechController.stopSpeaking()
        ocultarOverlay()
    }

    private fun observeAssistance() {
        assistanceViewModel.effect.observe(this) { effect ->
            effect ?: return@observe
            // Se limpia antes de actuar: ejecutar la acción puede emitir de inmediato el siguiente efecto.
            assistanceViewModel.effectHandled()
            when (effect) {
                is AssistanceEffect.AskAndListen -> askAndListen(effect.question, effect.display)
                is AssistanceEffect.Confirm -> showConfirmation(effect.summary)
                is AssistanceEffect.Execute -> executeAssistance(effect.action, effect)
                is AssistanceEffect.GiveUp -> {
                    ocultarOverlay()
                    speechController.speak(effect.message)
                }
            }
        }
    }

    private fun executeAssistance(action: AssistanceAction, effect: AssistanceEffect.Execute) {
        cancelConfirmationTimeout()
        if (action is AssistanceAction.SearchExactProduct) {
            mostrarOverlay()
            mostrarPregunta("Buscando ${action.query}...")
        } else if (action is AssistanceAction.StartCategoryFlow &&
            (effect.context.activeNeed?.maxPrice != null || !effect.context.activeNeed?.color.isNullOrBlank())
        ) {
            // Se consulta Odoo antes de abrir la zona para confirmar que hay productos con ese precio o color.
            mostrarOverlay()
            mostrarPregunta("Buscando opciones...")
        } else {
            ocultarOverlay()
        }
        val run = {
            assistanceCoordinator.execute(action, effect.context) { followUpQuestion ->
                if (followUpQuestion == null) ocultarOverlay()
                assistanceViewModel.onExecutionFinished(followUpQuestion)
            }
        }
        val announcement = effect.announcement
        if (announcement == null) {
            run()
        } else {
            mostrarOverlay()
            mostrarPregunta(announcement)
            speechController.speak(announcement) { run() }
        }
    }

    private fun askAndListen(question: String, display: String) {
        cancelConfirmationTimeout()
        // Cada turno cuenta como interacción: la conversación no puede quedar cortada por el
        // regreso automático a Centro Sala.
        robotRepository.notifyUserInteraction()
        mostrarOverlay()
        mostrarPregunta(display)
        speechController.speak(question) {
            if (assistanceViewModel.stage != ConversationStage.ASKING_DETAIL) return@speak
            // Sin red no se puede escuchar ni entender: se cierra en vez de dejar el contador quieto.
            if (!NetworkStatus.isOnline()) {
                handleOffline(announce = true)
                return@speak
            }
            assistanceViewModel.onListeningStarted()
            mostrarEscuchando()
            speechController.listen(LISTEN_TIMEOUT_SECONDS) { result ->
                robotRepository.notifyUserInteraction()
                if (result is AgentSpeechController.ListenResult.Text) {
                    mostrarPensando()
                    assistanceViewModel.onHeard(result.value)
                } else {
                    assistanceViewModel.onNothingHeard()
                }
            }
        }
    }

    // Voz, Gemini y Odoo necesitan internet. Por detección el aviso se da como mucho una vez por
    // minuto, para no repetirlo a cada persona que pasa; con el botón se da siempre.
    private fun handleOffline(announce: Boolean) {
        Log.w(TAG, "assistance_offline")
        cancelConfirmationTimeout()
        assistanceViewModel.cancel()
        ocultarOverlay()
        val now = System.currentTimeMillis()
        if (announce || now - lastOfflineNoticeAtMs >= OFFLINE_NOTICE_COOLDOWN_MS) {
            lastOfflineNoticeAtMs = now
            speechController.speak(OFFLINE_TEXT)
        }
    }

    private fun showConfirmation(summary: String) = with(binding) {
        robotRepository.notifyUserInteraction()
        mostrarOverlay()
        mostrarPregunta(summary)
        agentConfirmButtons.visibility = View.VISIBLE
        cancelConfirmationTimeout()
        // La espera cuenta desde que Temi termina de leer la confirmación: si contara desde que
        // empieza, una frase larga dejaría al cliente sin tiempo para tocar un botón.
        speechController.speak(summary) {
            if (assistanceViewModel.stage == ConversationStage.CONFIRMING) {
                root.postDelayed(confirmationTimeout, CONFIRMATION_TIMEOUT_MS)
            }
        }
    }

    private fun cancelConfirmationTimeout() {
        binding.root.removeCallbacks(confirmationTimeout)
    }

    private fun mostrarOverlay() = with(binding) {
        stopListeningCountdown()
        overlayDark.visibility = View.VISIBLE
        agentOverlayTexts.visibility = View.VISIBLE
        tvAgentStatus.visibility = View.GONE
        agentConfirmButtons.visibility = View.GONE
    }

    private fun mostrarPregunta(question: String) {
        binding.tvAgentQuestion.text = question
    }

    private fun mostrarEscuchando() {
        binding.tvAgentStatus.visibility = View.VISIBLE
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
        agentConfirmButtons.visibility = View.GONE
    }

    private fun activateDetectionMode() {
        if (TemiAdvisorCalls.isFlowActive) {
            Log.i(TAG, "detection_mode_skipped reason=advisor_call")
            return
        }
        robot.trackUserOn = false
        if (!detectionListenerRegistered) {
            robot.addOnDetectionStateChangedListener(this)
            detectionListenerRegistered = true
        }
        robot.setDetectionModeOn(true, DETECTION_DISTANCE_METERS)
        detectionModeActivated = true
        Log.i(TAG, "detection_mode_on=true")
    }

    private fun deactivateDetectionMode(stopSpeaking: Boolean = true) {
        cancelConfirmationTimeout()
        assistanceViewModel.cancel()
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

    private fun desactivarLockTask() {
        try {
            stopLockTask()
            Log.d(TAG, "Lock Task Mode desactivado")
        } catch (e: Exception) {
            Log.d(TAG, "No estaba en Lock Task Mode: ${e.message}")
        }
    }

    private fun solicitarPermisosTemi() {
        robot.requestPermissions(
            listOf(Permission.MAP, Permission.SETTINGS, Permission.SEQUENCE, Permission.MEETINGS), 0
        )
    }

    private fun inicializarControladores() {
        mqttController = MqttController(
            onMessage = { mensaje ->
                Log.d(TAG, "Mensaje MQTT recibido: $mensaje")
                runOnUiThread { procesarMensajeMqtt(mensaje) }
            },
            onStatus = { estado ->
                Log.i(TAG, "Estado MQTT: $estado")
                runOnUiThread { Toast.makeText(this, estado, Toast.LENGTH_SHORT).show() }
            }
        )

        robotRepository = TemiRobotRepository(
            context = this,
            onStatus = { estado ->
                Log.i(TAG, "Estado Temi: $estado")
                runOnUiThread {
                    Toast.makeText(this, estado, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun procesarMensajeMqtt(mensaje: String) {
        val ubicacion = mensaje.trim()
        if (ubicacion.isEmpty()) {
            Log.w(TAG, "Mensaje vacío recibido")
            return
        }
        Log.i(TAG, "Ubicación recibida: $ubicacion")
        Toast.makeText(this, "Enviando robot a: $ubicacion", Toast.LENGTH_SHORT).show()

        if (ubicacion.equals("promorevestimientos")) {
            robotRepository.speak("¡Ven sígueme y te mostraré las promociones de revestimientos!")
            robotRepository.goToLocation(ubicacion)
        }
        if (ubicacion.equals("promococina")) {
            robotRepository.speak("¡Ven sígueme y te mostraré las promociones de cocinas!")
            robotRepository.goToLocation(ubicacion)
        }
        if (ubicacion.equals("promolavamanos")) {
            robotRepository.speak("¡Ven sígueme y te mostraré las promociones de lavamanos!")
            robotRepository.goToLocation(ubicacion)
        } else {
            robotRepository.goToLocation(ubicacion)
        }
    }
}
