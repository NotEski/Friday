package me.dgol.friday.assistant

import android.os.Bundle
import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import me.dgol.friday.shared.stt.ModelManager
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService



class FridayRecognitionService : RecognitionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechService: SpeechService? = null

    override fun onStartListening(intent: Intent?, listener: Callback) {
        scope.launch {
            val ctx = applicationContext

            if (!ModelManager.isModelReady(ctx)) {
                listener.error(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
                return@launch
            }

            val modelPath = withContext(Dispatchers.IO) { ModelManager.ensureModel(ctx) }
            val model = org.vosk.Model(modelPath)

            val recognizer = Recognizer(model, 16000f)

            speechService = SpeechService(recognizer, 16000f).also { service ->
                service.startListening(object : RecognitionListener {

                    override fun onPartialResult(hyp: String?) = Unit   // optional live captions

                    override fun onResult(hyp: String?) {
                        val text = JSONObject(hyp ?: "{}").optString("text")
                        val bundle = Bundle().apply {
                            putStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION,
                                arrayListOf(text)
                            )
                        }
                        listener.results(bundle)
                    }

                    override fun onFinalResult(hyp: String?) = Unit     // not needed yet

                    override fun onTimeout() {
                        listener.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    }

                    override fun onError(exception: Exception?) {
                        listener.error(SpeechRecognizer.ERROR_CLIENT)
                    }
                })
            }
        }
    }

    override fun onStopListening(listener: Callback?) {
        speechService?.stop()
        return          // tells Android "we handled it, you can finish"
    }

    override fun onCancel(listener: Callback?) {
        speechService?.stop()
        return          // tells Android "we handled it, you can finish"
    }
}
