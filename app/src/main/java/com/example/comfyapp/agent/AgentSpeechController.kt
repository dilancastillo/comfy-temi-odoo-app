// maneja la sintesis y el reconocimiento de voz usando azure con fallback al tts de temi
package com.example.comfyapp.agent

import android.os.Handler
import android.os.Looper
import com.example.comfyapp.logging.PersistentLog as Log
import com.example.comfyapp.BuildConfig
import com.example.comfyapp.core.NetworkStatus
import com.microsoft.cognitiveservices.speech.CancellationDetails
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AgentSpeechController {

    sealed class ListenResult {
        data class Text(val value: String) : ListenResult()
        data object Silence : ListenResult()
        data class Error(val message: String) : ListenResult()
    }

    private val robot = Robot.getInstance()
    // Hablar y escuchar en hilos distintos: una síntesis colgada (sin red) no puede bloquear la
    // escucha, y cada frase nueva tiene su propio hilo aunque la anterior siga esperando a Azure.
    private val speakExecutor = Executors.newCachedThreadPool()
    private val listenExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val speechGeneration = AtomicLong()
    private val listenGeneration = AtomicLong()
    private val speechLock = Any()

    @Volatile private var destroyed = false
    @Volatile private var recognizer: SpeechRecognizer? = null
    @Volatile private var synthesizer: SpeechSynthesizer? = null
    private var isSpeaking = false
    private var queuedSpeech: QueuedSpeech? = null

    private data class QueuedSpeech(
        val text: String,
        val onComplete: () -> Unit
    )

    val hasAzure: Boolean
        get() = BuildConfig.AZURE_SPEECH_KEY.isNotBlank()

    fun speak(text: String, onComplete: () -> Unit = {}) {
        stopListening()
        stopSpeaking()
        val requestId = speechGeneration.incrementAndGet()
        if (text.isBlank()) {
            if (isCurrentSpeech(requestId)) onComplete()
            return
        }
        synchronized(speechLock) { isSpeaking = true }
        trace("AGENTE: $text")
        if (!hasAzure || !NetworkStatus.isOnline()) {
            speakWithTemi(requestId, text, onComplete)
            return
        }

        // La frase termina una sola vez: por Azure, o por la voz de Temi si Azure falla o no responde.
        val done = AtomicBoolean(false)
        val audioStarted = AtomicBoolean(false)
        val finish = {
            if (done.compareAndSet(false, true)) mainHandler.post { completeSpeech(requestId, onComplete) }
        }
        val fallbackToTemi = { reason: String ->
            // Si la frase ya se cortó a propósito (otra frase o cambio de pantalla) no se repite.
            if (done.compareAndSet(false, true) && isCurrentSpeech(requestId)) {
                Log.w(TAG, "Azure TTS $reason; se usa TTS de Temi")
                mainHandler.post { if (isCurrentSpeech(requestId)) speakWithTemi(requestId, text, onComplete) }
            }
        }
        // Con red lenta Azure puede quedarse esperando sin sonar: si en unos segundos no llega audio,
        // habla Temi y la conversación sigue (el contador de escucha no queda congelado).
        mainHandler.postDelayed({
            if (isCurrentSpeech(requestId) && !audioStarted.get() && !done.get()) {
                try { synthesizer?.StopSpeakingAsync() } catch (_: Exception) { }
                fallbackToTemi("sin audio en ${AZURE_FIRST_AUDIO_TIMEOUT_MS} ms")
            }
        }, AZURE_FIRST_AUDIO_TIMEOUT_MS)

        speakExecutor.execute {
            if (!isCurrentSpeech(requestId)) return@execute
            var currentSynthesizer: SpeechSynthesizer? = null
            try {
                val speechConfig = speechConfig()
                val audioConfig = AudioConfig.fromDefaultSpeakerOutput()
                val activeSynthesizer = SpeechSynthesizer(speechConfig, audioConfig)
                // Cualquiera de los dos indica que el servicio respondió; así una frase que sí está
                // sonando nunca se corta para repetirla con la voz de Temi.
                activeSynthesizer.SynthesisStarted.addEventListener { _, _ -> audioStarted.set(true) }
                activeSynthesizer.Synthesizing.addEventListener { _, _ -> audioStarted.set(true) }
                currentSynthesizer = activeSynthesizer
                synthesizer = activeSynthesizer
                if (!isCurrentSpeech(requestId)) {
                    activeSynthesizer.close(); audioConfig.close(); speechConfig.close()
                    return@execute
                }
                val result = activeSynthesizer.SpeakText(text)
                val completed = result.reason == ResultReason.SynthesizingAudioCompleted
                result.close(); activeSynthesizer.close(); audioConfig.close(); speechConfig.close()
                // Cancelado (por ejemplo sin red) no sonó nada: se dice con la voz de Temi.
                if (completed) finish() else fallbackToTemi("finalizó con ${result.reason}")
            } catch (error: Exception) {
                Log.w(TAG, "Azure TTS no disponible", error)
                fallbackToTemi("con error")
            } finally {
                if (synthesizer === currentSynthesizer) synthesizer = null
            }
        }
    }

    // La voz de Temi no avisa cuándo termina: se estima por el largo del texto.
    private fun speakWithTemi(requestId: Long, text: String, onComplete: () -> Unit) {
        robot.speak(TtsRequest.create(text, false))
        val estimatedMs = maxOf(TEMI_TTS_MIN_MS, text.length * TEMI_TTS_MS_PER_CHAR)
        mainHandler.postDelayed({ completeSpeech(requestId, onComplete) }, estimatedMs)
    }

    fun speakAfterCurrent(text: String, onComplete: () -> Unit = {}) {
        val shouldSpeakNow = synchronized(speechLock) {
            if (isSpeaking) {
                queuedSpeech = QueuedSpeech(text, onComplete)
                false
            } else {
                true
            }
        }
        if (shouldSpeakNow) speak(text, onComplete)
    }

    fun listen(timeoutSeconds: Int = 7, callback: (ListenResult) -> Unit) {
        stopListening()
        val requestId = listenGeneration.incrementAndGet()
        if (!hasAzure) {
            if (isCurrentListen(requestId)) callback(ListenResult.Error("Azure Speech no configurado"))
            return
        }
        // Sin red Azure no puede reconocer: se responde de inmediato en vez de esperar el timeout.
        if (!NetworkStatus.isOnline()) {
            trace("STT_ERROR: sin conexión")
            mainHandler.post { if (isCurrentListen(requestId)) callback(ListenResult.Error("Sin conexión a internet")) }
            return
        }
        listenExecutor.execute {
            if (!isCurrentListen(requestId)) return@execute
            val result = try {
                val speechConfig = speechConfig().apply {
                    speechRecognitionLanguage = "es-CO"
                }
                val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
                val currentRecognizer = SpeechRecognizer(speechConfig, audioConfig)
                recognizer = currentRecognizer
                val recognition = currentRecognizer.recognizeOnceAsync()
                    .get(timeoutSeconds.toLong() + 2L, TimeUnit.SECONDS)
                val mapped = when (recognition.reason) {
                    ResultReason.RecognizedSpeech ->
                        recognition.text.trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let(ListenResult::Text) ?: ListenResult.Silence
                    ResultReason.NoMatch -> ListenResult.Silence
                    ResultReason.Canceled -> {
                        val details = CancellationDetails.fromResult(recognition)
                        ListenResult.Error(details.errorDetails ?: "Reconocimiento cancelado")
                    }
                    else -> ListenResult.Silence
                }
                recognition.close(); currentRecognizer.close()
                audioConfig.close(); speechConfig.close()
                when (mapped) {
                    is ListenResult.Text -> trace("USUARIO: ${mapped.value}")
                    ListenResult.Silence -> trace("USUARIO: <silencio>")
                    is ListenResult.Error -> trace("STT_ERROR: ${mapped.message}")
                }
                mapped
            } catch (error: Exception) {
                Log.w(TAG, "Error durante la ventana de escucha", error)
                ListenResult.Silence
            } finally {
                recognizer = null
            }
            mainHandler.post { if (isCurrentListen(requestId)) callback(result) }
        }
    }

    fun stopListening() {
        listenGeneration.incrementAndGet()
        try { recognizer?.close() } catch (_: Exception) { } finally { recognizer = null }
    }

    fun stopSpeaking() {
        speechGeneration.incrementAndGet()
        synchronized(speechLock) {
            isSpeaking = false
            queuedSpeech = null
        }
        try { synthesizer?.StopSpeakingAsync() } catch (_: Exception) { }
        synthesizer = null
        robot.cancelAllTtsRequests()
    }

    private fun completeSpeech(requestId: Long, onComplete: () -> Unit) {
        if (!isCurrentSpeech(requestId)) return
        val nextSpeech = synchronized(speechLock) {
            if (!isCurrentSpeech(requestId)) return
            isSpeaking = false
            queuedSpeech.also { queuedSpeech = null }
        }
        onComplete()
        nextSpeech?.let { speak(it.text, it.onComplete) }
    }

    fun destroy() {
        destroyed = true
        stopListening()
        stopSpeaking()
        speakExecutor.shutdownNow()
        listenExecutor.shutdownNow()
    }

    private fun isCurrentSpeech(requestId: Long) = !destroyed && requestId == speechGeneration.get()
    private fun isCurrentListen(requestId: Long) = !destroyed && requestId == listenGeneration.get()

    private fun speechConfig(): SpeechConfig =
        SpeechConfig.fromSubscription(
            BuildConfig.AZURE_SPEECH_KEY,
            BuildConfig.AZURE_SPEECH_REGION
        ).apply {
            speechSynthesisLanguage = "es-CO"
            speechSynthesisVoiceName = BuildConfig.AZURE_SPEECH_VOICE
        }

    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d(TRACE_TAG, message)
    }

    companion object {
        val shared: AgentSpeechController by lazy { AgentSpeechController() }
        private const val TAG = "AgentSpeech"
        private const val TRACE_TAG = "AgentTrace"
        private const val AZURE_FIRST_AUDIO_TIMEOUT_MS = 5_000L
        private const val TEMI_TTS_MIN_MS = 2_500L
        private const val TEMI_TTS_MS_PER_CHAR = 65L
    }
}
