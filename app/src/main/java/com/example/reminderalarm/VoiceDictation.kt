package com.example.reminderalarm

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import com.example.reminderalarm.databinding.DialogVoiceDictationBinding

/**
 * Continuous voice dictation.
 *
 * Google's [SpeechRecognizer] (and most OEM variants) ignore the
 * EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS hints and aggressively
 * endpoint after ~1s of silence — which keeps cutting users off when
 * they pause to think. The trick is to use that short recognition
 * cycle as a *chunk*: every time the recogniser finalises a segment
 * we append it to an accumulator and immediately call
 * [SpeechRecognizer.startListening] again. The user only sees one
 * long dictation session; internally it's a chain of 1-2s chunks.
 *
 * End-of-speech is the user tapping "Stop". Everything else — silence
 * timeouts, NO_MATCH, SPEECH_TIMEOUT, RECOGNIZER_BUSY — triggers an
 * automatic restart so pauses for thinking don't end the session.
 */
object VoiceDictation {

    fun start(activity: Activity, onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val binding = DialogVoiceDictationBinding.inflate(activity.layoutInflater)
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        val handler = Handler(Looper.getMainLooper())

        val accumulator = StringBuilder()
        var lastPartial = ""
        var userStopped = false
        var finalized = false

        fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        fun finishWith(text: String) {
            if (finalized) return
            finalized = true
            handler.removeCallbacksAndMessages(null)
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
            runCatching { dialog.dismiss() }
            if (text.isNotBlank()) onResult(text.trim())
        }

        fun restartListening(delayMs: Long = 100L) {
            if (userStopped || finalized) return
            handler.postDelayed({
                if (!userStopped && !finalized) {
                    runCatching { recognizer.startListening(buildIntent()) }
                }
            }, delayMs)
        }

        fun currentCombined(): String {
            // What the user sees: accumulator + current partial (still in flight).
            val partial = lastPartial
            return when {
                accumulator.isEmpty() && partial.isEmpty() -> ""
                accumulator.isEmpty() -> partial
                partial.isEmpty() -> accumulator.toString()
                else -> "${accumulator.trim()} $partial"
            }.trim()
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.voiceStatus.setText(R.string.voice_listening)
            }

            override fun onBeginningOfSpeech() {
                binding.voiceStatus.setText(R.string.voice_speaking)
            }

            override fun onRmsChanged(rmsdB: Float) {
                val scale = (1f + (rmsdB.coerceIn(-2f, 10f) + 2f) / 48f)
                binding.voiceIcon.scaleX = scale
                binding.voiceIcon.scaleY = scale
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // The recogniser thinks we stopped — don't finalize yet,
                // wait for onResults / onError and restart if needed.
                binding.voiceStatus.setText(R.string.voice_processing)
            }

            override fun onError(error: Int) {
                if (finalized) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Normal "user paused" — keep the accumulator,
                        // restart listening unless the user tapped Stop.
                        if (userStopped) {
                            finishWith(currentCombined())
                        } else {
                            // Reset the partial since the recogniser
                            // didn't commit it; we'll capture it on
                            // the next chunk.
                            lastPartial = ""
                            restartListening()
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // Android recycling the recogniser — retry with
                        // a slightly longer delay.
                        restartListening(500L)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Toast.makeText(
                            activity,
                            R.string.voice_no_mic_permission,
                            Toast.LENGTH_LONG
                        ).show()
                        finishWith("")
                    }
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_AUDIO -> {
                        // Hard errors — surface what we captured so far.
                        if (accumulator.isNotEmpty()) {
                            finishWith(accumulator.toString())
                        } else {
                            Toast.makeText(
                                activity,
                                R.string.voice_error_generic,
                                Toast.LENGTH_SHORT
                            ).show()
                            finishWith("")
                        }
                    }
                    else -> {
                        // Any other error — try one more restart, then
                        // give up with what we have.
                        if (accumulator.isNotEmpty()) {
                            finishWith(accumulator.toString())
                        } else {
                            restartListening()
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val chunk = matches?.firstOrNull()?.trim().orEmpty()
                if (chunk.isNotBlank()) {
                    if (accumulator.isNotEmpty()) accumulator.append(" ")
                    accumulator.append(chunk)
                    binding.voicePartial.text = accumulator.toString()
                }
                lastPartial = ""
                if (userStopped) {
                    finishWith(accumulator.toString())
                } else {
                    // Key move: immediately restart so the next chunk
                    // feels like a continuation of the same dictation.
                    restartListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return
                if (text.isNotBlank()) {
                    lastPartial = text
                    binding.voicePartial.text = currentCombined()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        binding.btnVoiceCancel.setOnClickListener {
            userStopped = true
            finishWith("")
        }
        binding.btnVoiceStop.setOnClickListener {
            userStopped = true
            // stopListening() asks the recogniser to finalise the current
            // segment and fire onResults — we then commit accumulator.
            runCatching { recognizer.stopListening() }
            // Safety net: if onResults doesn't fire within 1s, finalise
            // with what we have.
            handler.postDelayed({
                if (!finalized) finishWith(currentCombined())
            }, 1200L)
        }

        dialog.setOnDismissListener {
            userStopped = true
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
            handler.removeCallbacksAndMessages(null)
        }

        dialog.show()
        runCatching { recognizer.startListening(buildIntent()) }
    }
}
