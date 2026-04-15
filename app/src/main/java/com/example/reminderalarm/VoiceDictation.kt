package com.example.reminderalarm

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import com.example.reminderalarm.databinding.DialogVoiceDictationBinding

/**
 * Custom voice dictation dialog that replaces the stock
 * [RecognizerIntent] flow. The stock flow ends listening after about
 * a second of silence — too aggressive for users who pause to think
 * mid-sentence. This dialog uses [SpeechRecognizer] directly with
 * partial-results enabled so the user can speak → pause → keep
 * speaking → tap "Stop" when they're genuinely done.
 *
 * Silence timeouts are set to 5 seconds (where the recogniser will
 * honour them); the manual Stop button is the primary end-of-speech
 * signal, so pauses for thinking don't cut you off.
 */
object VoiceDictation {

    /**
     * Opens the dictation dialog. [onResult] receives the final
     * transcription if the user confirmed — empty string or no call
     * if they cancelled or nothing was captured.
     */
    fun start(activity: Activity, onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val binding = DialogVoiceDictationBinding
            .inflate(activity.layoutInflater)
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        var lastPartial = ""
        var finalized = false

        fun finishWith(text: String) {
            if (finalized) return
            finalized = true
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
            runCatching { dialog.dismiss() }
            if (text.isNotBlank()) onResult(text.trim())
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.voiceStatus.setText(R.string.voice_listening)
            }

            override fun onBeginningOfSpeech() {
                binding.voiceStatus.setText(R.string.voice_speaking)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Subtle pulse on the mic icon (scale 1.0 .. 1.25).
                val scale = (1f + (rmsdB.coerceIn(-2f, 10f) + 2f) / 48f)
                binding.voiceIcon.scaleX = scale
                binding.voiceIcon.scaleY = scale
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Recogniser thinks the user stopped. Keep the dialog
                // open — onResults / onError will fire next.
                binding.voiceStatus.setText(R.string.voice_processing)
            }

            override fun onError(error: Int) {
                if (finalized) return
                // NO_MATCH and SPEECH_TIMEOUT after a manual Stop are
                // normal — just close with what we got. Other errors
                // show a toast.
                val text = lastPartial
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        finishWith(text)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Toast.makeText(activity, R.string.voice_no_mic_permission,
                            Toast.LENGTH_LONG).show()
                        finishWith("")
                    }
                    else -> {
                        Toast.makeText(activity, R.string.voice_error_generic,
                            Toast.LENGTH_SHORT).show()
                        finishWith(text)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: lastPartial
                finishWith(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                if (text.isNotBlank()) {
                    lastPartial = text
                    binding.voicePartial.text = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        binding.btnVoiceCancel.setOnClickListener { finishWith("") }
        binding.btnVoiceStop.setOnClickListener {
            // Tell the recogniser we're done; onResults will fire with
            // the final transcription.
            runCatching { recognizer.stopListening() }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Long tolerances — the user may pause mid-sentence. These
            // extras are honoured on most builds; manual Stop is the
            // ultimate fallback.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                30_000L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                5_000L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                5_000L
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        dialog.setOnDismissListener {
            // Safety net in case the dialog is dismissed externally.
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }

        dialog.show()
        runCatching { recognizer.startListening(intent) }
    }
}
