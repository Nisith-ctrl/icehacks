package com.icehacks.saythegoal.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.icehacks.saythegoal.core.UserCommand
import com.icehacks.saythegoal.core.VoiceIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Real VoiceIO: an always-on SpeechRecognizer loop ("the ear") feeding [VoiceRouter], and
 * TextToSpeech ("the mouth"). The ear is paused while speaking so the agent never hears itself.
 * SpeechRecognizer must be created and driven on the main thread.
 */
class AndroidVoice(
    private val context: Context,
    private val scope: CoroutineScope,
    val router: VoiceRouter = VoiceRouter(),
) : VoiceIO {

    override val commands: Flow<UserCommand> = router.commands

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val ttsReady = CompletableDeferred<Boolean>()
    private val utterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    @Volatile private var speaking = false
    @Volatile private var listening = false
    @Volatile private var closed = false
    private var endOfSpeechAt = 0L
    private var lastPermissionLog = 0L
    private var onDeviceOk = true // false after the on-device model reports a missing language

    init {
        main.post {
            initTts()
            startEar()
        }
    }

    // ---------- VoiceIO ----------

    override suspend fun say(text: String) {
        router.markActive()
        Log.i(TAG, "say: $text")
        val ready = withTimeoutOrNull(3_000) { ttsReady.await() } ?: false
        val engine = tts
        if (!ready || engine == null) {
            Log.w(TAG, "TTS not ready, skipped speaking")
            return
        }
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        utterances[id] = done
        speaking = true
        main.post { pauseEar() }
        try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            withTimeoutOrNull(30_000) { done.await() }
        } finally {
            utterances.remove(id)
            speaking = false
            router.markActive()
            delay(150) // let the speaker's tail die out before listening again
            main.post { startEar() }
        }
    }

    override suspend fun ask(question: String, choices: List<String>): String? {
        repeat(2) { attempt ->
            val expected = router.expectAnswer()
            say(if (attempt == 0) question else "Sorry, I didn't catch that. $question")
            val answer = router.awaitAnswer(expected, ANSWER_TIMEOUT_MS)
            if (answer != null) {
                val result = if (choices.isEmpty()) answer else ChoiceMatcher.resolve(answer, choices)
                Log.i(TAG, "ask '$question' $choices -> '$answer' => '$result'")
                return result
            }
        }
        Log.i(TAG, "ask '$question' -> no answer")
        return null
    }

    /** Debug hook and tests: handle text exactly as if it had been recognized. */
    fun inject(text: String) {
        val routed = router.onTranscript(text, forceSession = true)
        Log.i(TAG, "inject '$text' -> $routed")
    }

    fun close() {
        closed = true
        main.post {
            recognizer?.destroy()
            recognizer = null
            tts?.shutdown()
        }
    }

    // ---------- the mouth ----------

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.05f)
            } else {
                Log.w(TAG, "TextToSpeech init failed: $status")
            }
            ttsReady.complete(ok)
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { utterances[utteranceId]?.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utterances[utteranceId]?.complete(Unit) }
            override fun onError(utteranceId: String?, errorCode: Int) { utterances[utteranceId]?.complete(Unit) }
        })
    }

    // ---------- the ear (main thread only) ----------

    private fun hasMic() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startEar() {
        if (closed || speaking || listening) return
        if (!hasMic()) {
            val t = SystemClock.uptimeMillis()
            if (t - lastPermissionLog > 30_000) {
                Log.w(TAG, "RECORD_AUDIO not granted; ear off (debug utterances still work)")
                lastPermissionLog = t
            }
            main.postDelayed({ startEar() }, 5_000)
            return
        }
        val r = recognizer ?: createRecognizer()?.also { recognizer = it } ?: return
        listening = true
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            },
        )
    }

    private fun pauseEar() {
        if (!listening) return
        listening = false
        recognizer?.cancel()
    }

    private fun restartEar(afterMs: Long) {
        listening = false
        main.postDelayed({ startEar() }, afterMs)
    }

    private fun createRecognizer(): SpeechRecognizer? {
        val r = when {
            onDeviceOk && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> {
                Log.w(TAG, "No speech recognizer on this device; ear off")
                return null
            }
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { endOfSpeechAt = SystemClock.uptimeMillis() }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (endOfSpeechAt > 0) Log.i(TAG, "latency end-of-speech -> result ${SystemClock.uptimeMillis() - endOfSpeechAt} ms")
                if (!text.isNullOrBlank()) {
                    val routed = router.onTranscript(text)
                    Log.i(TAG, "heard '$text' -> $routed")
                    if (routed == VoiceRouter.Routed.WOKE) scope.launch { say("Yes?") }
                }
                restartEar(100)
            }

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> restartEar(5_000)
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartEar(600)
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartEar(150)
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                        Log.w(TAG, "on-device recognizer has no en-US model (error $error); using the standard recognizer")
                        onDeviceOk = false
                        recognizer?.destroy()
                        recognizer = null
                        restartEar(300)
                    }
                    else -> {
                        Log.w(TAG, "recognizer error $error")
                        recognizer?.destroy()
                        recognizer = null
                        restartEar(1_000)
                    }
                }
            }
        })
        return r
    }

    companion object {
        const val TAG = "STG-Voice"
        const val ANSWER_TIMEOUT_MS = 8_000L
    }
}
