package me.dgol.friday.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Minimal Vosk wrapper:
 * - Loads a model from a directory
 * - Captures 16kHz PCM mono via AudioRecord
 * - Emits partial and final text
 */
class VoskEngine(
    private val appContext: Context,
    private val modelDir: File,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var recognizer: Recognizer? = null
    @Volatile private var model: Model? = null
    @Volatile private var running = false

    fun isRunning(): Boolean = running

    fun start() {
        if (running) return
        running = true

        scope.launch {
            try {
                val sampleRate = 16000 // align with your model
                model = Model(modelDir.absolutePath)
                recognizer = Recognizer(model, sampleRate.toFloat())

                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = max(minBuf, 4096)

                // VOICE_RECOGNITION reduces OS audio processing versus MIC on many devices.
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                audioRecord = record

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized (buffer=$bufferSize)")
                }

                record.startRecording()

                val buf = ByteArray(bufferSize)
                while (running && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val n = record.read(buf, 0, buf.size)
                    if (n <= 0) continue

                    val rec = recognizer ?: break
                    val accepted = rec.acceptWaveForm(buf, n)
                    val json = if (accepted) rec.result else rec.partialResult
                    parseAndDispatch(json, accepted)
                }

                stopInternal()
            } catch (t: Throwable) {
                onError(t)
                stopInternal()
            }
        }
    }

    fun stop() {
        running = false
        scope.launch { stopInternal() }
    }

    fun release() {
        running = false
        scope.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
                release()
            }
        } catch (_: Throwable) { /* no-op */ }
        finally { audioRecord = null }

        try { recognizer?.close() } catch (_: Throwable) {}
        finally { recognizer = null }

        try { model?.close() } catch (_: Throwable) {}
        finally { model = null }
    }

    private fun parseAndDispatch(json: String?, isFinal: Boolean) {
        if (json.isNullOrBlank()) return
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val key = if (isFinal) "text" else "partial"
        val text = obj.optString(key, "")
        if (text.isBlank()) return

        if (isFinal) onFinal(text) else onPartial(text)
    }
}
