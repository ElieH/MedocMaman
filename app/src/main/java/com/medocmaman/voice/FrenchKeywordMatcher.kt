package com.medocmaman.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.text.Normalizer
import java.util.Locale

enum class VoiceIntent {
    EMERGENCY_VITE,
    AFFIRMATIVE_TAKEN,
    NEGATIVE_OR_DELAY,
    UNCLEAR
}

/**
 * Deterministic French keyword matcher and SpeechRecognizer wrapper (zero LLM cost).
 * Scans transcripts for VITE stroke/carotid emergency keywords first, then Yes/No responses.
 */
class FrenchKeywordMatcher(
    private val context: Context,
    private val onResult: (transcript: String, intent: VoiceIntent) -> Unit,
    private val onErrorOrTimeout: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        stopListening()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorOrTimeout()
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        onErrorOrTimeout()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestTranscript = matches?.firstOrNull().orEmpty()
                        if (bestTranscript.isBlank()) {
                            onErrorOrTimeout()
                        } else {
                            val detected = classifyTranscript(bestTranscript)
                            onResult(bestTranscript, detected)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank() && classifyTranscript(partial) == VoiceIntent.EMERGENCY_VITE) {
                            stopListening()
                            onResult(partial, VoiceIntent.EMERGENCY_VITE)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(recognizerIntent)
        } catch (_: Exception) {
            onErrorOrTimeout()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    companion object {
        // Emergency keywords (VITE protocol - Carotid / Stroke / Fall / Unwell)
        val EMERGENCY_KEYWORDS = listOf(
            "tourne",
            "vertige",
            "tombee",
            "tombe",
            "faiblesse",
            "bras",
            "jambe",
            "visage",
            "parler",
            "vue",
            "voir",
            "mal a la tete",
            "poitrine",
            "etourdie",
            "etourdi",
            "pas bien",
            "mal",
            " secours",
            "urgence"
        )

        val NEGATIVE_KEYWORDS = listOf(
            "pas encore",
            "non",
            "oublie",
            "plus tard",
            "probleme",
            "attends"
        )

        val AFFIRMATIVE_KEYWORDS = listOf(
            "oui",
            "pris",
            "c'est fait",
            "c est fait",
            "deja",
            "bien sur"
        )

        fun normalizeFrench(input: String): String {
            val decomposed = Normalizer.normalize(input.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
            return decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .replace("’", "'")
                .trim()
        }

        fun classifyTranscript(transcript: String): VoiceIntent {
            if (transcript.isBlank()) return VoiceIntent.UNCLEAR
            val normalized = normalizeFrench(transcript)

            // 1. Always prioritize VITE / Emergency keywords
            if (EMERGENCY_KEYWORDS.any { kw -> normalized.contains(kw) }) {
                return VoiceIntent.EMERGENCY_VITE
            }

            // 2. Check Negative / Delay before Affirmative so "Non, pas encore pris" is NEGATIVE
            if (NEGATIVE_KEYWORDS.any { kw -> normalized.contains(kw) }) {
                return VoiceIntent.NEGATIVE_OR_DELAY
            }

            // 3. Check Affirmative
            if (AFFIRMATIVE_KEYWORDS.any { kw -> normalized.contains(kw) }) {
                return VoiceIntent.AFFIRMATIVE_TAKEN
            }

            return VoiceIntent.UNCLEAR
        }
    }
}
