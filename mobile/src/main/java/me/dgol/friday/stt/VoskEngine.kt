package me.dgol.friday.stt

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Minimal, robust Vosk wrapper.
 * - Runs capture + recognition on a single background thread.
 * - Safe stop(): joins the thread before freeing native objects.
 * - Never throws to callers; all errors are sent to onError.
 */
class VoskEngine(
    private val appContext: android.content.Context,
    private val modelDir: File,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    companion object { private const val TAG = "VoskEngine" }

    private val running = AtomicBoolean(false)
    private val workerRef = AtomicReference<Thread?>()
    private val sampleRate = 16000 // Vosk models expect 16k

    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "start() ignored; already running")
            return
        }

        val worker = Thread({
            var model: Model? = null
            var rec: Recognizer? = null
            var ar: AudioRecord? = null
            try {
                // 1) Init model + recognizer
                model = Model(modelDir.absolutePath)
                rec = Recognizer(model, sampleRate.toFloat())

                // 2) Create AudioRecord
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) throw IllegalStateException("Invalid buffer size: $minBuf")

                ar = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    (minBuf * 2).coerceAtLeast(8192)
                )
                if (ar.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized (state=${ar.state})")
                }

                // 3) Start capture loop
                val buffer = ByteArray(minBuf.coerceAtLeast(4096))
                ar.startRecording()
                Log.d(TAG, "Recording started")

                while (running.get()) {
                    val n = ar.read(buffer, 0, buffer.size)
                    if (n <= 0) continue

                    // Feed to recognizer
                    try {
                        val json = if (rec.acceptWaveForm(buffer, n)) rec.result else rec.partialResult
                        handleResultJson(json)
                    } catch (t: Throwable) {
                        // Catch JSON or recognizer errors without bailing the thread
                        Log.e(TAG, "Recognizer error", t)
                        onError(t)
                    }
                }

                // 4) Final result after loop ends
                try {
                    val finalJson = rec.finalResult
                    handleResultJson(finalJson, final = true)
                } catch (t: Throwable) {
                    Log.w(TAG, "finalResult failed", t)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Engine thread failed", t)
                onError(t)
            } finally {
                // Always stop & free in this order on the same thread
                try { ar?.stop() } catch (_: Throwable) {}
                try { ar?.release() } catch (_: Throwable) {}
                try { rec?.close() } catch (_: Throwable) {}
                try { model?.close() } catch (_: Throwable) {}
                Log.d(TAG, "Recording stopped and resources released")
                running.set(false)
                workerRef.set(null)
            }
        }, "VoskEngine")

        workerRef.set(worker)
        worker.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        // Wait briefly for the thread to release native resources
        workerRef.getAndSet(null)?.join(1500)
    }

    fun release() {
        // Same as stop; keep for API parity
        stop()
    }

    private fun handleResultJson(json: String, final: Boolean = false) {
        // Typical JSON:
        //  partial: {"partial":"hello wor"}
        //  final:   {"text":"hello world"}
        try {
            val o = JSONObject(json)
            val partial = o.optString("partial", null)
            val text = o.optString("text", null)
            when {
                !text.isNullOrBlank() -> onFinal(text)
                !partial.isNullOrBlank() && !final -> onPartial(partial)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Bad JSON: $json", t)
        }
    }
}
