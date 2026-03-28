package com.stocksense.app.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "BitNetModelDownloader"

/**
 * Downloads Microsoft BitNet 1-bit LLM GGUF model files to device storage.
 *
 * The model is fetched from HuggingFace on first launch and cached in
 * `<internal-storage>/models/`. Subsequent launches skip the download.
 *
 * ### Model selection
 * [QualityMode] determines which variant is downloaded:
 * | Mode       | Model                                  | ~Size  |
 * |------------|----------------------------------------|--------|
 * | LITE       | bitnet-b1.58-2B-4T (TQ1_0)             | ~400MB |
 * | BALANCED   | bitnet-b1.58-2B-4T (TQ2_0)             | ~600MB |
 * | PRO        | bitnet-b1.58-2B-4T (Q4_0)              | ~1.2GB |
 */
class BitNetModelDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    /** Directory where downloaded models are stored. */
    val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    // ── Model catalogue ──────────────────────────────────────────────

    companion object {
        /** HuggingFace base URL for the Microsoft BitNet GGUF models. */
        private const val HF_BASE =
            "https://huggingface.co/microsoft/bitnet-b1.58-2B-4T-gguf/resolve/main"

        /** Map of quality mode to (file-name, download-URL). */
        val MODEL_CATALOGUE: Map<QualityMode, Pair<String, String>> = mapOf(
            QualityMode.LITE to Pair(
                "bitnet-b1.58-2B-4T-TQ1_0.gguf",
                "$HF_BASE/bitnet-b1.58-2B-4T-TQ1_0.gguf"
            ),
            QualityMode.BALANCED to Pair(
                "bitnet-b1.58-2B-4T-TQ2_0.gguf",
                "$HF_BASE/bitnet-b1.58-2B-4T-TQ2_0.gguf"
            ),
            QualityMode.PRO to Pair(
                "bitnet-b1.58-2B-4T-Q4_0.gguf",
                "$HF_BASE/bitnet-b1.58-2B-4T-Q4_0.gguf"
            )
        )
    }

    // ── Public API ───────────────────────────────────────────────────

    /** Check whether the model file for [mode] already exists on disk. */
    fun isModelAvailable(mode: QualityMode): Boolean {
        val (fileName, _) = MODEL_CATALOGUE[mode] ?: return false
        val file = File(modelsDir, fileName)
        return file.exists() && file.length() > 0
    }

    /** Return the local [File] for the given mode (may not yet exist). */
    fun modelFileFor(mode: QualityMode): File {
        val (fileName, _) = MODEL_CATALOGUE[mode]
            ?: throw IllegalArgumentException("Unknown mode: $mode")
        return File(modelsDir, fileName)
    }

    /**
     * Download the BitNet model for [mode].
     *
     * @param onProgress optional callback receiving progress as 0.0–1.0.
     * @return `true` if the model was downloaded (or already present).
     */
    suspend fun download(
        mode: QualityMode,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val (fileName, url) = MODEL_CATALOGUE[mode]
            ?: return@withContext false

        val target = File(modelsDir, fileName)
        if (target.exists() && target.length() > 0) {
            Log.i(TAG, "Model already available: ${target.absolutePath}")
            onProgress?.invoke(1f)
            return@withContext true
        }

        Log.i(TAG, "Downloading $fileName from $url")
        val tmpFile = File(modelsDir, "$fileName.tmp")

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: HTTP ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                var totalRead = 0L

                FileOutputStream(tmpFile).use { fos ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                onProgress?.invoke(totalRead.toFloat() / contentLength)
                            }
                        }
                    }
                }
            }

            // Atomic rename so partially-downloaded files don't get used.
            if (!tmpFile.renameTo(target)) {
                Log.e(TAG, "Failed to rename tmp file")
                tmpFile.delete()
                return@withContext false
            }

            Log.i(TAG, "Download complete: ${target.absolutePath} (${target.length()} bytes)")
            onProgress?.invoke(1f)
            return@withContext true

        } catch (e: IOException) {
            Log.e(TAG, "Download error: ${e.message}")
            tmpFile.delete()
            return@withContext false
        }
    }

    /** Delete all downloaded model files to free storage. */
    fun clearModels() {
        modelsDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "All model files deleted")
    }
}
