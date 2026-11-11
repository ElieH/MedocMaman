package com.medocmaman.voice

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Handles French Text-To-Speech (`Locale.FRENCH` at 0.9f speed for senior clarity)
 * and routes audio through the speakerphone automatically when the call is answered.
 */
class FrenchVoiceHelper(
    private val context: Context,
    private val onReady: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: Pair<String, (() -> Unit)?>? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.FRENCH)
            tts?.setSpeechRate(0.9f) // Slightly slower for senior auditory clarity
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            onReady?.invoke()
            pendingSpeech?.let { (text, onComplete) ->
                pendingSpeech = null
                speak(text, onComplete)
            }
        } else {
            Log.w(TAG, "French TTS initialization failed with status=$status")
        }
    }

    @Suppress("DEPRECATION")
    fun activateSpeakerphone() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            // Ensure volume is clearly audible (at least 75% of max music/voice volume)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val targetMinVol = (maxVol * 0.75).toInt()
            if (currentVol < targetMinVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetMinVol, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to configure speakerphone: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    fun releaseSpeakerphone() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    fun speak(textFr: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            pendingSpeech = textFr to onDone
            return
        }
        val utteranceId = "utt_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone?.invoke()
            }
        })
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        tts?.speak(textFr, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        releaseSpeakerphone()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "FrenchVoiceHelper"

        fun buildGreetingScript(
            motherName: String,
            medNameFr: String,
            visualDescriptionFr: String,
            dosageFr: String,
            mealTimingFr: String,
            vouvoiement: Boolean
        ): String {
            return if (vouvoiement) {
                "Bonjour $motherName. C'est l'heure de votre médicament : $medNameFr. " +
                    "C'est $visualDescriptionFr que vous voyez sur l'écran. " +
                    "$dosageFr, $mealTimingFr. L'avez-vous déjà pris ?"
            } else {
                "Bonjour $motherName. C'est l'heure de ton médicament : $medNameFr. " +
                    "C'est $visualDescriptionFr que tu vois sur l'écran. " +
                    "$dosageFr, $mealTimingFr. L'as-tu déjà pris ?"
            }
        }

        fun buildTimeoutPrompt(vouvoiement: Boolean): String {
            return if (vouvoiement) {
                "Vous pouvez appuyer sur le bouton vert pour Oui, ou le bouton bleu pour Non."
            } else {
                "Tu peux appuyer sur le bouton vert pour Oui, ou le bouton bleu pour Non."
            }
        }

        fun buildFollowUpPrompt(vouvoiement: Boolean): String {
            return if (vouvoiement) {
                "D'accord. Pouvez-vous le prendre maintenant, ou y a-t-il un problème ?"
            } else {
                "D'accord. Peux-tu le prendre maintenant, ou y a-t-il un problème ?"
            }
        }

        fun buildVitePrompt(vouvoiement: Boolean): String {
            return if (vouvoiement) {
                "Merci de me le dire. Je préviens votre fils et votre fille. " +
                    "Question importante : avez-vous soudain une faiblesse d'un côté du corps, du mal à parler, ou un problème de vue ?"
            } else {
                "Merci de me le dire. Je préviens ton fils et ta fille. " +
                    "Question importante : as-tu soudain une faiblesse d'un côté du corps, du mal à parler, ou un problème de vue ?"
            }
        }

        fun buildDial101Instruction(vouvoiement: Boolean): String {
            return if (vouvoiement) {
                "Appelez immédiatement le 101. Appuyez sur le bouton vert d'appel pour parler au 101."
            } else {
                "Appelle immédiatement le 101. Appuie sur le bouton vert d'appel pour parler au 101."
            }
        }
    }
}
