package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class VoiceLanguageOption(
    val tag: String,
    val emoji: String,
    val label: String,
    val samplePrompt: String
)

object SupportedVoiceLanguages {
    val options: List<VoiceLanguageOption> = listOf(
        VoiceLanguageOption("en-US", "🇬🇧", "English", "I just had a coffee and 200 ml water"),
        VoiceLanguageOption("hi-IN", "🇮🇳", "हिन्दी (Hindi)", "मैंने अभी 200 ml पानी और एक कप कॉफी ली"),
        VoiceLanguageOption("es-ES", "🇪🇸", "Español", "Acabo de tomar un café y 200 ml de agua"),
        VoiceLanguageOption("fr-FR", "🇫🇷", "Français", "Je viens de boire un café et 200 ml d'eau"),
        VoiceLanguageOption("de-DE", "🇩🇪", "Deutsch", "Ich habe gerade einen Kaffee und 200 ml Wasser getrunken"),
        VoiceLanguageOption("te-IN", "🇮🇳", "తెలుగు", "నేను ఇప్పుడే కాఫీ మరియు 200 ml నీరు తాగాను"),
        VoiceLanguageOption("ta-IN", "🇮🇳", "தமிழ்", "நான் இப்போது காபி மற்றும் 200 ml தண்ணீர் குடித்தேன்"),
        VoiceLanguageOption("ar-SA", "🇸🇦", "العربية", "لقد شربت قهوة و200 مل من الماء للتو")
    )

    fun byTag(tag: String): VoiceLanguageOption =
        options.firstOrNull { it.tag == tag } ?: options.first()
}

class VoiceAssistantManager(context: Context) {
    private val appContext = context.applicationContext

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    val isSpeechRecognitionAvailable: Boolean
        get() = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

    private fun ensureTts(onReady: () -> Unit) {
        if (tts != null && ttsReady) {
            onReady()
            return
        }
        tts = TextToSpeech(appContext) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                onReady()
            } else {
                _voiceError.value = "Text-to-Speech engine is unavailable on this device."
            }
        }
    }

    fun speak(text: String, languageTag: String) {
        val cleanSpeech = stripEmojisForSpeech(text).trim()
        if (cleanSpeech.isEmpty()) return
        _voiceError.value = null
        ensureTts {
            val locale = Locale.forLanguageTag(languageTag)
            val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_MISSING_DATA
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.speak(
                cleanSpeech,
                TextToSpeech.QUEUE_FLUSH,
                null,
                UUID.randomUUID().toString()
            )
        }
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    fun startListening(
        languageTag: String,
        onFinalTranscript: (String) -> Unit
    ) {
        _voiceError.value = null
        _partialTranscript.value = ""
        if (!isSpeechRecognitionAvailable) {
            _voiceError.value = "On-device speech recognizer is unavailable here. You can type or tap a quick command below."
            return
        }
        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _voiceError.value = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try speaking again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and speak clearly."
                            else -> "Speech recognition unavailable (code $error). You can type your request."
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty().trim()
                        if (text.isNotEmpty()) {
                            _partialTranscript.value = ""
                            onFinalTranscript(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        }.onFailure { err ->
            _isListening.value = false
            _voiceError.value = err.localizedMessage ?: "Unable to start speech recognition."
        }
    }

    fun stopListening() {
        runCatching { speechRecognizer?.stopListening() }
        _isListening.value = false
    }

    fun clearError() {
        _voiceError.value = null
    }

    fun release() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
    }

    private fun stripEmojisForSpeech(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)
            val type = Character.getType(cp)
            val isSymbolOrEmoji = type == Character.OTHER_SYMBOL.toInt() ||
                type == Character.SURROGATE.toInt() ||
                cp in 0x1F300..0x1FAFF ||
                cp in 0x2600..0x27BF ||
                cp == 0xFE0F
            if (!isSymbolOrEmoji) {
                sb.appendCodePoint(cp)
            }
            i += charCount
        }
        return sb.toString().replace(Regex("\\s+"), " ")
    }
}
