// maneja la sintesis y el reconocimiento de voz usando azure con fallback al tts de temi
package com.example.comfyapp.agent

import android.os.Handler
import android.os.Looper
import com.example.comfyapp.logging.PersistentLog as Log
import com.example.comfyapp.BuildConfig
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
import java.util.concurrent.atomic.AtomicLong

class AgentSpeechController {

    sealed class ListenResult {
        data class Text(val value: String) : ListenResult()
        data object Silence : ListenResult()
        data class Error(val message: String) : ListenResult()
    }

    private val robot = Robot.getInstance()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val speechGeneration = AtomicLong()
    private val listenGeneration = AtomicLong()

    @Volatile private var destroyed = false
    @Volatile private var recognizer: SpeechRecognizer? = null
    @Volatile private var synthesizer: SpeechSynthesizer? = null

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
        trace("AGENTE: $text")
        if (!hasAzure) {
            robot.speak(TtsRequest.create(text, false))
            mainHandler.postDelayed({ if (isCurrentSpeech(requestId)) onComplete() }, 3_000)
            return
        }
        executor.execute {
            if (!isCurrentSpeech(requestId)) return@execute
            var currentSynthesizer: SpeechSynthesizer? = null
            try {
                val speechConfig = speechConfig()
                val audioConfig = AudioConfig.fromDefaultSpeakerOutput()
                val activeSynthesizer = SpeechSynthesizer(speechConfig, audioConfig)
                currentSynthesizer = activeSynthesizer
                synthesizer = activeSynthesizer
                if (!isCurrentSpeech(requestId)) {
                    activeSynthesizer.close(); audioConfig.close(); speechConfig.close()
                    return@execute
                }
                val result = activeSynthesizer.SpeakText(text)
                if (result.reason != ResultReason.SynthesizingAudioCompleted) {
                    Log.w(TAG, "Azure TTS finalizó con ${result.reason}")
                }
                result.close(); activeSynthesizer.close(); audioConfig.close(); speechConfig.close()
            } catch (error: Exception) {
                Log.w(TAG, "Azure TTS no disponible; se usa TTS de Temi", error)
                mainHandler.post {
                    if (isCurrentSpeech(requestId)) {
                        robot.speak(TtsRequest.create(text, false))
                        mainHandler.postDelayed({ if (isCurrentSpeech(requestId)) onComplete() }, 3_000)
                    }
                }
                return@execute
            } finally {
                if (synthesizer === currentSynthesizer) synthesizer = null
            }
            mainHandler.post { if (isCurrentSpeech(requestId)) onComplete() }
        }
    }

    fun listen(timeoutSeconds: Int = 7, callback: (ListenResult) -> Unit) {
        stopListening()
        val requestId = listenGeneration.incrementAndGet()
        if (!hasAzure) {
            if (isCurrentListen(requestId)) callback(ListenResult.Error("Azure Speech no configurado"))
            return
        }
        executor.execute {
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
        try { synthesizer?.StopSpeakingAsync() } catch (_: Exception) { }
        synthesizer = null
        robot.cancelAllTtsRequests()
    }

    fun destroy() {
        destroyed = true
        stopListening()
        stopSpeaking()
        executor.shutdownNow()
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
    }
}
