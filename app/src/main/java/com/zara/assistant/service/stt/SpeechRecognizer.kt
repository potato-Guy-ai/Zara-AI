package com.zara.assistant.service.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * SpeechRecognizer — offline STT via Vosk
 *
 * Model directory: app/src/main/assets/vosk-model-small-en-us/
 * Download from: https://alphacephei.com/vosk/models
 * Recommended: vosk-model-small-en-us-0.15 (~40MB, good accuracy)
 *
 * On first launch, model is extracted from assets to internal storage.
 */
class SpeechRecognizer(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MODEL_ASSET_DIR = "vosk-model-small-en-us"
        private const val SILENCE_TIMEOUT_MS = 2000L
        private const val MAX_LISTEN_MS = 10000L
    }

    private var model: Model? = null
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch { initModel() }
    }

    private suspend fun initModel() = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.filesDir, MODEL_ASSET_DIR)
            if (!modelDir.exists()) {
                ZaraLogger.d("STT: Extracting Vosk model…")
                extractAssetDir(MODEL_ASSET_DIR, modelDir)
            }
            model = Model(modelDir.absolutePath)
            ZaraLogger.d("STT: Vosk model ready")
        } catch (e: Exception) {
            ZaraLogger.e("STT: Failed to load Vosk model: ${e.message}")
        }
    }

    /**
     * Listen until silence detected or timeout.
     * Returns recognized text, or null on failure.
     */
    suspend fun listenOnce(): String? = withContext(Dispatchers.IO) {
        val m = model ?: run {
            ZaraLogger.w("STT: Model not ready")
            return@withContext null
        }

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize * 4
        )

        val recognizer = Recognizer(m, SAMPLE_RATE.toFloat())
        val buffer = ByteArray(bufSize)
        var result: String? = null
        var silenceMs = 0L
        val startTime = System.currentTimeMillis()

        try {
            rec.startRecording()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > MAX_LISTEN_MS) break

                val nBytes = rec.read(buffer, 0, buffer.size)
                if (nBytes <= 0) continue

                val isSilent = isSilent(buffer, nBytes)

                if (recognizer.acceptWaveForm(buffer, nBytes)) {
                    val partial = JSONObject(recognizer.result).optString("text")
                    if (partial.isNotBlank()) {
                        result = partial
                        break
                    }
                }

                if (isSilent) {
                    silenceMs += (bufSize * 1000L / (SAMPLE_RATE * 2))
                    if (silenceMs >= SILENCE_TIMEOUT_MS && result != null) break
                } else {
                    silenceMs = 0
                }
            }

            if (result == null) {
                val finalResult = JSONObject(recognizer.finalResult).optString("text")
                if (finalResult.isNotBlank()) result = finalResult
            }
        } catch (e: Exception) {
            ZaraLogger.e("STT listen error: ${e.message}")
        } finally {
            rec.stop()
            rec.release()
            recognizer.close()
        }

        ZaraLogger.d("STT result: $result")
        return@withContext result?.trim()
    }

    private fun isSilent(buffer: ByteArray, nBytes: Int): Boolean {
        var sum = 0L
        for (i in 0 until nBytes - 1 step 2) {
            val sample = (buffer[i + 1].toInt() shl 8 or (buffer[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        val rms = Math.sqrt(sum.toDouble() / (nBytes / 2))
        return rms < 500
    }

    private fun extractAssetDir(assetDir: String, destDir: File) {
        destDir.mkdirs()
        val assets = context.assets.list(assetDir) ?: return
        for (file in assets) {
            val subPath = "$assetDir/$file"
            val dest = File(destDir, file)
            val subList = context.assets.list(subPath)
            if (!subList.isNullOrEmpty()) {
                extractAssetDir(subPath, dest)
            } else {
                try {
                    context.assets.open(subPath).use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: IOException) {
                    ZaraLogger.w("STT extract failed for $subPath: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
