package com.zara.assistant.service.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * WakeWordEngine
 *
 * Pipeline: 16kHz mic → STFT → 16-bin Mel spectrogram → log → normalize → [1,16,96] → ONNX
 *
 * Model:  app/src/main/assets/models/wake_word.onnx
 * Input:  float32[1, 16, 96]  (16 mel bins × 96 time frames)
 * Output: float32[1, 2]       (index 1 = wake word score)
 */
class WakeWordEngine(private val context: Context) {

    // ── Constants ──────────────────────────────────────────────────────────
    companion object {
        private const val SAMPLE_RATE       = 16000
        private const val N_FFT             = 512
        private const val HOP_LENGTH        = 160          // 10 ms hop → 100 frames/sec
        private const val N_MEL             = 16           // must match model input dim 0
        private const val N_FRAMES          = 96           // must match model input dim 1
        // Window covers N_FRAMES * HOP_LENGTH + N_FFT samples ≈ 1.28 sec
        private val WINDOW_SAMPLES          = N_FRAMES * HOP_LENGTH + N_FFT
        private const val SLIDE_SAMPLES     = HOP_LENGTH * 8   // advance 8 frames per cycle

        private const val MODEL_ASSET       = "models/wake_word.onnx"
        private const val INPUT_NAME        = "onnx::Flatten_0"
        private const val OUTPUT_NAME       = "39"
        private const val THRESHOLD         = 0.85f
        private const val CONSEC_REQUIRED   = 3            // consecutive frames above threshold
        private const val COOLDOWN_MS       = 2000L

        private const val F_MIN             = 0f
        private val F_MAX                   = SAMPLE_RATE / 2f
    }

    // ── State ──────────────────────────────────────────────────────────────
    private var audioRecord: AudioRecord? = null
    private var ortEnv: OrtEnvironment?   = null
    private var ortSession: OrtSession?   = null
    private var isRunning                 = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pre-computed Mel filterbank [N_MEL × (N_FFT/2+1)]
    private val melFilters: Array<FloatArray> = buildMelFilterbank()

    // Hann window for STFT
    private val hannWindow: FloatArray = FloatArray(N_FFT) { i ->
        (0.5f * (1f - cos(2f * PI.toFloat() * i / (N_FFT - 1))))
    }

    // Reusable buffers
    private val audioWindow  = ShortArray(WINDOW_SAMPLES)
    private val slideBuffer  = ShortArray(SLIDE_SAMPLES)
    private val fftReal      = FloatArray(N_FFT)
    private val fftImag      = FloatArray(N_FFT)
    private val melSpec      = Array(N_MEL) { FloatArray(N_FRAMES) }
    private val inputBuffer  = FloatBuffer.allocate(N_MEL * N_FRAMES)

    private var consecCount  = 0

    init { loadModel() }

    // ── Model ──────────────────────────────────────────────────────────────
    private fun loadModel() {
        try {
            val dest = File(context.cacheDir, "wake_word.onnx")
            if (!dest.exists()) {
                context.assets.open(MODEL_ASSET).use { ins ->
                    FileOutputStream(dest).use { ins.copyTo(it) }
                }
            }
            ortEnv = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
                addNnapi()
            }
            ortSession = ortEnv!!.createSession(dest.absolutePath, opts)
            ZaraLogger.d("WakeWordEngine: ONNX loaded — input[$INPUT_NAME] output[$OUTPUT_NAME]")
        } catch (e: Exception) {
            ZaraLogger.e("WakeWordEngine: model load failed: ${e.message}")
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────
    fun start(onDetected: (Boolean) -> Unit) {
        if (isRunning) return
        isRunning = true

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, minBuf * 4
        ).also { it.startRecording() }

        scope.launch {
            while (isRunning) {
                val read = audioRecord?.read(slideBuffer, 0, SLIDE_SAMPLES) ?: break
                if (read <= 0) continue

                // Slide window: drop oldest, append new
                System.arraycopy(audioWindow, SLIDE_SAMPLES, audioWindow, 0, WINDOW_SAMPLES - SLIDE_SAMPLES)
                System.arraycopy(slideBuffer, 0, audioWindow, WINDOW_SAMPLES - SLIDE_SAMPLES, SLIDE_SAMPLES)

                val score = runInference()
                ZaraLogger.v("WakeWord score: ${"%.3f".format(score)}")

                if (score >= THRESHOLD) {
                    consecCount++
                    if (consecCount >= CONSEC_REQUIRED) {
                        consecCount = 0
                        ZaraLogger.d("WakeWord DETECTED (score=${"%.3f".format(score)})")
                        withContext(Dispatchers.Main) { onDetected(true) }
                        delay(COOLDOWN_MS)
                    }
                } else {
                    consecCount = 0
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        ortSession?.close()
        ortEnv?.close()
        scope.cancel()
    }

    // ── Preprocessing → ONNX ──────────────────────────────────────────────

    private fun runInference(): Float {
        val session = ortSession ?: return 0f
        val env     = ortEnv     ?: return 0f

        computeMelSpectrogram()
        buildInputTensor()

        return try {
            val shape  = longArrayOf(1, N_MEL.toLong(), N_FRAMES.toLong())
            val tensor = OnnxTensor.createTensor(env, inputBuffer, shape)
            val out    = session.run(mapOf(INPUT_NAME to tensor))
            val scores = (out[OUTPUT_NAME].get().value as Array<FloatArray>)[0]
            val score  = scores[1]
            tensor.close()
            out.close()
            score
        } catch (e: Exception) {
            ZaraLogger.e("ONNX inference error: ${e.message}")
            0f
        }
    }

    /**
     * Fills [melSpec] with shape [N_MEL][N_FRAMES].
     * Each column = one STFT hop computed via in-place Cooley-Tukey FFT.
     */
    private fun computeMelSpectrogram() {
        val freqBins = N_FFT / 2 + 1

        for (frame in 0 until N_FRAMES) {
            val offset = frame * HOP_LENGTH

            // Apply Hann window
            for (i in 0 until N_FFT) {
                val sampleIdx = offset + i
                val sample = if (sampleIdx < WINDOW_SAMPLES) audioWindow[sampleIdx] / 32768f else 0f
                fftReal[i] = sample * hannWindow[i]
                fftImag[i] = 0f
            }

            fft(fftReal, fftImag)

            // Power spectrum
            val power = FloatArray(freqBins) { k -> fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k] }

            // Apply Mel filters
            for (m in 0 until N_MEL) {
                var energy = 0f
                for (k in 0 until freqBins) energy += melFilters[m][k] * power[k]
                // Log scaling with floor to avoid -inf
                melSpec[m][frame] = ln(maxOf(energy, 1e-9f))
            }
        }

        // Per-channel mean-variance normalization
        for (m in 0 until N_MEL) {
            val mean = melSpec[m].average().toFloat()
            val std  = sqrt(melSpec[m].map { (it - mean).pow(2) }.average().toFloat() + 1e-8f)
            for (f in 0 until N_FRAMES) melSpec[m][f] = (melSpec[m][f] - mean) / std
        }
    }

    /** Flatten melSpec [N_MEL][N_FRAMES] → FloatBuffer in row-major order */
    private fun buildInputTensor() {
        inputBuffer.rewind()
        for (m in 0 until N_MEL)
            for (f in 0 until N_FRAMES)
                inputBuffer.put(melSpec[m][f])
        inputBuffer.rewind()
    }

    // ── Mel filterbank ─────────────────────────────────────────────────────

    private fun buildMelFilterbank(): Array<FloatArray> {
        val freqBins = N_FFT / 2 + 1
        val melMin   = hzToMel(F_MIN)
        val melMax   = hzToMel(F_MAX)
        val melPoints = FloatArray(N_MEL + 2) { i -> melMin + i * (melMax - melMin) / (N_MEL + 1) }
        val hzPoints  = FloatArray(N_MEL + 2) { i -> melToHz(melPoints[i]) }
        val binPoints = FloatArray(N_MEL + 2) { i -> floor(hzPoints[i] * N_FFT / SAMPLE_RATE).toFloat() }

        return Array(N_MEL) { m ->
            FloatArray(freqBins) { k ->
                val kf = k.toFloat()
                when {
                    kf >= binPoints[m] && kf < binPoints[m + 1] ->
                        (kf - binPoints[m]) / (binPoints[m + 1] - binPoints[m])
                    kf >= binPoints[m + 1] && kf < binPoints[m + 2] ->
                        (binPoints[m + 2] - kf) / (binPoints[m + 2] - binPoints[m + 1])
                    else -> 0f
                }
            }
        }
    }

    private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float): Float = 700f * (10f.pow(mel / 2595f) - 1f)

    // ── Cooley-Tukey in-place FFT ──────────────────────────────────────────

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        // FFT butterfly
        var len = 2
        while (len <= n) {
            val ang = -2f * PI.toFloat() / len
            val wRe = cos(ang); val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1f; var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]; val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe; im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe; im[i + k + len / 2] = uIm - vIm
                    val newRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe; curRe = newRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
