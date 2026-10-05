package com.owlcoders.chitti.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * High-performance Speech-to-Text Manager with low-latency silence detection
 * and instant partial trigger for immediate task execution.
 */
class SpeechToTextManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit = {},
    private val onFinalResult: (String) -> Unit = {},
    private val onRmsLevel: (Float) -> Unit = {},
    private val onErrorMessage: (String) -> Unit = {},
    private val onStateChange: (SpeechState) -> Unit = {}
) {
    private val tag = "ChittiSTT"
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var hasTriggeredFinal = false
    // Some recognizers deliver partial text and then an EMPTY final result list (seen on
    // Android 16 with the Google recognizer). Keep the last partial as a fallback.
    private var lastPartialText = ""

    // The recognizer gives up after about a second of silence before any speech (ERROR_NO_MATCH),
    // which is less time than it takes to start talking after tapping the mic. Until something
    // is recognised (a partial result with words), those give-ups restart it quietly, for up to
    // LISTEN_WINDOW_MS.
    private var sessionActive = false
    private var sessionStartedAt = 0L
    private var heardSpeech = false
    private var listenIntent: Intent? = null
    private val restartRunnable = Runnable { if (sessionActive && !heardSpeech) listenAgain() }

    enum class SpeechState {
        IDLE, READY, LISTENING, PROCESSING, ERROR
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "onReadyForSpeech")
            isListening = true
            hasTriggeredFinal = false
            lastPartialText = ""
            onStateChange(SpeechState.READY)
        }

        override fun onBeginningOfSpeech() {
            // Not proof of words: room noise triggers this too, then ends in ERROR_NO_MATCH.
            Log.d(tag, "onBeginningOfSpeech")
            onStateChange(SpeechState.LISTENING)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // NOTE: must not be named like the override below, otherwise the call recurses into itself (StackOverflowError).
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            onRmsLevel(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "onEndOfSpeech")
            onStateChange(SpeechState.PROCESSING)
        }

        override fun onError(error: Int) {
            if (hasTriggeredFinal) return
            isListening = false
            val nothingYet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (nothingYet && sessionActive && !heardSpeech &&
                SystemClock.uptimeMillis() - sessionStartedAt < LISTEN_WINDOW_MS
            ) {
                Log.d(tag, "Nothing heard yet (code $error); still listening")
                mainHandler.postDelayed(restartRunnable, RESTART_DELAY_MS)
                return
            }
            sessionActive = false
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap mic to speak."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic to try again."
                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue for voice recognition."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy (is a call or another app using it?). Try again in a moment."
                SpeechRecognizer.ERROR_CLIENT -> "Voice recognizer error. Tap mic to try again."
                SpeechRecognizer.ERROR_SERVER -> "Speech service error. Tap mic to try again."
                10 /* ERROR_TOO_MANY_REQUESTS (API 31) */ -> "Too many voice requests. Wait a moment and try again."
                11 /* ERROR_SERVER_DISCONNECTED (API 31) */ -> "Speech service disconnected. Tap mic to try again."
                12, 13 /* ERROR_LANGUAGE_NOT_SUPPORTED / UNAVAILABLE (API 31) */ -> "This language is not available for voice recognition."
                else -> "Speech recognition error ($error)"
            }
            Log.w(tag, "Speech recognition error: $message (code $error)")
            onStateChange(SpeechState.ERROR)
            onErrorMessage(message)
        }

        override fun onResults(results: Bundle?) {
            if (hasTriggeredFinal) return
            isListening = false
            sessionActive = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val finalText = matches?.firstOrNull { !it.isNullOrBlank() }?.trim() ?: ""
            val recognizedText = finalText.ifBlank { lastPartialText }
            Log.d(tag, "Final recognized text: '$finalText' (using: '$recognizedText')")
            onStateChange(SpeechState.IDLE)
            if (recognizedText.isNotBlank()) {
                hasTriggeredFinal = true
                onFinalResult(recognizedText)
            } else {
                onErrorMessage("Could not hear anything clearly.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (hasTriggeredFinal) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()?.trim() ?: ""
            if (partialText.isNotBlank()) {
                heardSpeech = true
                lastPartialText = partialText
                Log.d(tag, "Partial text: $partialText")
                onPartialResult(partialText)

                // Ultra-fast path: If an unambiguous app command is spoken, trigger immediately without waiting for silence!
                val lower = partialText.lowercase()
                val isFastAction = (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) &&
                        (lower.contains("whatsapp") || lower.contains("youtube") || lower.contains("camera") ||
                                lower.contains("settings") || lower.contains("chrome") || lower.contains("spotify") ||
                                lower.contains("instagram") || lower.contains("telegram") || lower.contains("maps"))

                val isFastControl = lower.contains("turn on flashlight") || lower.contains("turn off flashlight") ||
                        lower.contains("torch on") || lower.contains("torch off")

                if ((isFastAction || isFastControl) && isListening) {
                    Log.d(tag, "Instant fast-action triggered on partial transcript: $partialText")
                    hasTriggeredFinal = true
                    stopListening()
                    onFinalResult(partialText)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.let { recognizer ->
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error cleaning up recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }

    fun startListening() {
        runOnMain {
            sessionActive = true
            sessionStartedAt = SystemClock.uptimeMillis()
            heardSpeech = false
            beginRecognizer()
        }
    }

    /** A fresh recognizer, listening. */
    private fun beginRecognizer() {
        runOnMain {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    val msg = "Speech recognition is not available on this device."
                    Log.w(tag, msg)
                    sessionActive = false
                    onErrorMessage(msg)
                    return@runOnMain
                }

                cleanupRecognizer()
                hasTriggeredFinal = false

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
                speechRecognizer = recognizer

                val currentLocale = Locale.getDefault()
                val langTag = if (currentLocale.language.isNotBlank()) currentLocale.toLanguageTag() else "en-US"

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                    // Note: Integer values required by Android speech recognition service
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
                }

                listenIntent = intent
                recognizer.startListening(intent)
                Log.d(tag, "Speech recognition successfully started with locale $langTag")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start listening: ${e.message}", e)
                sessionActive = false
                onErrorMessage("Failed to start microphone: ${e.message}")
            }
        }
    }

    /**
     * Another run on the same recognizer. Replacing it instead would make the old one report
     * ERROR_SERVER_DISCONNECTED as it is destroyed, which ends the session.
     */
    private fun listenAgain() {
        val recognizer = speechRecognizer
        val intent = listenIntent
        if (recognizer == null || intent == null) {
            beginRecognizer()
            return
        }
        try {
            hasTriggeredFinal = false
            recognizer.startListening(intent)
            Log.d(tag, "Listening again")
        } catch (e: Exception) {
            Log.w(tag, "Couldn't listen again, starting over: ${e.message}")
            beginRecognizer()
        }
    }

    fun stopListening() {
        runOnMain {
            sessionActive = false
            mainHandler.removeCallbacks(restartRunnable)
            cleanupRecognizer()
            Log.d(tag, "Stopped speech listening")
        }
    }

    fun destroy() {
        stopListening()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private companion object {
        /** How long a tap on the mic waits for the first word. */
        const val LISTEN_WINDOW_MS = 8_000L
        const val RESTART_DELAY_MS = 120L
    }
}
