package com.stocksense.app.engine

import android.content.Context
import android.util.Log
import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.PredictionResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "PredictionEngine"
private const val MODEL_FILE = "stock_prediction.tflite"

/** Number of historical closing prices fed to the model. */
private const val SEQUENCE_LENGTH = 30

/**
 * PredictionEngine – runs on-device TFLite inference for stock trend prediction.
 *
 * Model input : float32[1, SEQUENCE_LENGTH]  – normalised closing prices
 * Model output: float32[1, 3]               – softmax over [DOWN, NEUTRAL, UP]
 */
class PredictionEngine(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val inferenceExecutor = Executors.newSingleThreadExecutor()

    // ---------- Lifecycle ----------

    fun loadModel() {
        if (interpreter != null) return
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                numThreads = 4
                // Try GPU acceleration
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    gpuDelegate = GpuDelegate(delegateOptions)
                    addDelegate(gpuDelegate!!)
                    Log.i(TAG, "GPU delegate enabled")
                } else {
                    Log.i(TAG, "Running on CPU")
                }
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load TFLite model (asset missing?): ${e.message}")
            // Allow graceful degradation – predict() will use heuristic fallback
        }
    }

    fun unloadModel() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    // ---------- Inference ----------

    /**
     * Predict the next-day direction and price for [symbol] given its recent [history].
     * Returns a [PredictionResult] – falls back to a heuristic if the TFLite model is absent.
     */
    suspend fun predict(symbol: String, history: List<HistoryPoint>): PredictionResult {
        if (history.size < 2) {
            return PredictionResult(symbol, 0.0, 0f, "NEUTRAL")
        }

        return withContext(inferenceExecutor.asCoroutineDispatcher()) {
            val tflite = interpreter
            if (tflite == null) {
                heuristicPredict(symbol, history)
            } else {
                tflitePredict(tflite, symbol, history)
            }
        }
    }

    // ---------- Private helpers ----------

    private fun tflitePredict(
        tflite: Interpreter,
        symbol: String,
        history: List<HistoryPoint>
    ): PredictionResult {
        val closes = history.takeLast(SEQUENCE_LENGTH).map { it.close.toFloat() }
        val minPrice = closes.min()
        val maxPrice = closes.max()
        val range = (maxPrice - minPrice).takeIf { it > 0f } ?: 1f

        // Prepare input buffer: shape [1, SEQUENCE_LENGTH]
        val inputBuffer = ByteBuffer.allocateDirect(4 * SEQUENCE_LENGTH).apply {
            order(java.nio.ByteOrder.nativeOrder())
            // Pad with first value if history shorter than sequence length
            val padded = if (closes.size < SEQUENCE_LENGTH) {
                List(SEQUENCE_LENGTH - closes.size) { closes.first() } + closes
            } else closes
            padded.forEach { putFloat((it - minPrice) / range) }
            rewind()
        }

        // Output buffer: shape [1, 3] – [DOWN, NEUTRAL, UP]
        val outputBuffer = Array(1) { FloatArray(3) }
        tflite.run(inputBuffer, outputBuffer)

        val probs = outputBuffer[0]
        val directionIdx = probs.indices.maxByOrNull { probs[it] } ?: 1
        val direction = when (directionIdx) {
            0 -> "DOWN"
            2 -> "UP"
            else -> "NEUTRAL"
        }
        val confidence = probs[directionIdx]
        val lastClose = closes.last()
        val predictedPrice = when (direction) {
            "UP" -> lastClose * (1 + confidence * 0.02f)
            "DOWN" -> lastClose * (1 - confidence * 0.02f)
            else -> lastClose.toDouble()
        }

        return PredictionResult(
            symbol = symbol,
            predictedPrice = predictedPrice,
            confidence = confidence,
            direction = direction,
            features = mapOf("down" to probs[0], "neutral" to probs[1], "up" to probs[2])
        )
    }

    /** Simple momentum-based fallback when TFLite model is unavailable. */
    private fun heuristicPredict(symbol: String, history: List<HistoryPoint>): PredictionResult {
        val recent = history.takeLast(5).map { it.close }
        val momentum = if (recent.size >= 2) recent.last() - recent.first() else 0.0
        val lastClose = history.last().close
        val direction = when {
            momentum > lastClose * 0.005 -> "UP"
            momentum < -lastClose * 0.005 -> "DOWN"
            else -> "NEUTRAL"
        }
        val confidence = (abs(momentum) / (lastClose * 0.05)).coerceIn(0.1, 0.8).toFloat()
        val predictedPrice = lastClose + momentum * 0.5
        return PredictionResult(symbol, predictedPrice, confidence, direction)
    }

    /** Memory-map the TFLite model asset for zero-copy loading. */
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        return FileInputStream(fileDescriptor.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
}
