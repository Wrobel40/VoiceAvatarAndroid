package com.aiavatar.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.media.AudioManager as SystemAudioManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordingState { IDLE, LISTENING, PROCESSING, SPEAKING }

enum class VoiceGender(val displayName: String, val emoji: String) {
    FEMALE("Żeński", "👩"),
    MALE("Męski", "👨")
}

class AudioManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: android.speech.tts.TextToSpeech? = null
    private var isTtsReady = false
    private val handler = Handler(Looper.getMainLooper())

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText

    private var currentGender = VoiceGender.FEMALE
    private var onSpeechResult: ((String) -> Unit)? = null

    var isContinuousListening: Boolean = false
        private set

    init { initTts() }

    // ── TTS ───────────────────────────────────────────────────────────────────

    private fun initTts() {
        tts = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                isTtsReady = true
                applyVoiceGender(currentGender)
            }
        }
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _recordingState.value = RecordingState.SPEAKING
                simulateAmplitude()
            }
            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _amplitude.value = 0f
                _recordingState.value = RecordingState.IDLE
                // Auto-restart after AI finishes speaking
                scheduleRestartIfContinuous(600)
            }
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _amplitude.value = 0f
                _recordingState.value = RecordingState.IDLE
                scheduleRestartIfContinuous(600)
            }
        })
    }

    fun setVoiceGender(gender: VoiceGender) {
        currentGender = gender
        if (isTtsReady) applyVoiceGender(gender)
    }

    private fun applyVoiceGender(gender: VoiceGender) {
        val engine = tts ?: return
        val voices = engine.voices
        if (voices != null) {
            val genderStr = if (gender == VoiceGender.FEMALE) "female" else "male"
            val match = voices.filter { v ->
                (v.locale.language == "pl" || v.name.contains("pl", ignoreCase = true)) &&
                v.name.contains(genderStr, ignoreCase = true) &&
                !v.isNetworkConnectionRequired
            }.minByOrNull { it.quality }
                ?: voices.filter { v ->
                    v.locale.language == "pl" || v.name.contains("pl", ignoreCase = true)
                }.minByOrNull { it.quality }
            if (match != null) engine.voice = match
            else engine.language = java.util.Locale("pl", "PL")
        } else {
            engine.language = java.util.Locale("pl", "PL")
        }
        when (gender) {
            VoiceGender.FEMALE -> { tts?.setSpeechRate(1.05f); tts?.setPitch(1.25f) }
            VoiceGender.MALE   -> { tts?.setSpeechRate(0.95f); tts?.setPitch(0.75f) }
        }
    }

    fun speak(text: String) {
        if (!isTtsReady) return
        tts?.stop()
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH,
            Bundle(), "utt_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        _amplitude.value = 0f
        _recordingState.value = RecordingState.IDLE
    }

    private fun simulateAmplitude() {
        Thread {
            while (_isSpeaking.value) {
                _amplitude.value = 0.3f + Math.random().toFloat() * 0.7f
                Thread.sleep(80)
            }
            _amplitude.value = 0f
        }.start()
    }

    // ── STT ───────────────────────────────────────────────────────────────────

    fun startListeningContinuous(onResult: (String) -> Unit) {
        isContinuousListening = true
        onSpeechResult = onResult
        doStartListening()
    }

    fun stopContinuousListening() {
        isContinuousListening = false
        onSpeechResult = null
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _recordingState.value = RecordingState.IDLE
        _amplitude.value = 0f
        muteSystemSounds(false)
    }

    // Legacy single-shot
    fun startListening(onResult: (String) -> Unit) {
        onSpeechResult = onResult
        doStartListening()
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _recordingState.value = RecordingState.IDLE
        _amplitude.value = 0f
    }

    fun isListeningContinuous() = isContinuousListening

    private fun scheduleRestartIfContinuous(delayMs: Long = 500) {
        if (!isContinuousListening || onSpeechResult == null) return
        handler.postDelayed({
            if (isContinuousListening && _recordingState.value == RecordingState.IDLE) {
                doStartListening()
            }
        }, delayMs)
    }

    private fun doStartListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _recordingState.value = RecordingState.IDLE
            return
        }

        // Always recreate recognizer — reusing causes issues on many devices
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _recordingState.value = RecordingState.LISTENING
                _amplitude.value = 0f
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                _amplitude.value = (rmsdB.coerceIn(0f, 10f) / 10f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _recordingState.value = RecordingState.PROCESSING
            }
            override fun onError(error: Int) {
                _amplitude.value = 0f
                _recordingState.value = RecordingState.IDLE
                // ERROR_NO_MATCH (7) or ERROR_SPEECH_TIMEOUT (6) — just restart
                // ERROR_RECOGNIZER_BUSY (8) — wait longer
                val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 700L
                scheduleRestartIfContinuous(delay)
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                _transcribedText.value = text
                _amplitude.value = 0f

                if (text.isNotBlank()) {
                    _recordingState.value = RecordingState.PROCESSING
                    onSpeechResult?.invoke(text)
                    // Restart will happen after TTS finishes (onDone)
                    // But if not in speaking mode, restart now
                    scheduleRestartIfContinuous(800)
                } else {
                    _recordingState.value = RecordingState.IDLE
                    scheduleRestartIfContinuous(300)
                }
            }
            override fun onPartialResults(partial: Bundle?) {
                _transcribedText.value =
                    partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        muteSystemSounds(true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun muteSystemSounds(mute: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? SystemAudioManager
                ?: return
            val streams = listOf(
                SystemAudioManager.STREAM_SYSTEM,        // klik mikrofonu
                SystemAudioManager.STREAM_NOTIFICATION,  // powiadomienia
                SystemAudioManager.STREAM_RING            // dzwonek
            )
            val flag = if (mute) SystemAudioManager.ADJUST_MUTE else SystemAudioManager.ADJUST_UNMUTE
            streams.forEach { stream ->
                try { am.adjustStreamVolume(stream, flag, 0) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun destroy() {
        isContinuousListening = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        tts?.shutdown()
    }
}
