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

        const val IMPORTED_MODEL_FILE_NAME = "imported_model.gguf"

        /**
         * Asset path for the optional nano model bundled inside the APK.
         * Place the GGUF file at:
         *   app/src/main/assets/models/smollm2-135m-instruct-v0.2-q4_k_m.gguf
         * and build the APK — no download required.
         */
        const val BUNDLED_MODEL_ASSET = "models/smollm2-135m-instruct-v0.2-q4_k_m.gguf"
        const val BUNDLED_MODEL_FILE  = "smollm2-135m-instruct-v0.2-q4_k_m.gguf"

        /** Map of quality mode to (file-name, download-URL).
         *  Note: All quality modes use the same model file since only one variant is available.
         */
        val MODEL_CATALOGUE: Map<QualityMode, Pair<String, String>> = mapOf(
            QualityMode.LITE to Pair(
                "ggml-model-i2_s.gguf",
                "$HF_BASE/ggml-model-i2_s.gguf"
            ),
            QualityMode.BALANCED to Pair(
                "ggml-model-i2_s.gguf",
                "$HF_BASE/ggml-model-i2_s.gguf"
            ),
            QualityMode.PRO to Pair(
                "ggml-model-i2_s.gguf",
                "$HF_BASE/ggml-model-i2_s.gguf"
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

    /**
     * If this APK was built with a bundled nano GGUF at [BUNDLED_MODEL_ASSET],
     * copy it to [modelsDir] once so the LLM engine can load it without a
     * network download.  Safe to call every startup — skips the copy if the
     * target file already exists.
     *
     * Returns `true` if the model is now on disk (either already was, or just
     * copied), `false` if no bundled asset was found in this build.
     */
    suspend fun copyBundledModelIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val target = File(modelsDir, BUNDLED_MODEL_FILE)
        if (target.exists() && target.length() > 0) {
            Log.i(TAG, "Bundled nano model already on disk: ${target.name}")
            return@withContext true
        }
        return@withContext try {
            context.assets.open(BUNDLED_MODEL_ASSET).use { input ->
                val tmp = File(modelsDir, "$BUNDLED_MODEL_FILE.tmp")
                FileOutputStream(tmp).use { input.copyTo(it, bufferSize = 65_536) }
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return@withContext false
                }
            }
            Log.i(TAG, "Bundled nano model copied → ${target.absolutePath} (${target.length()} B)")
            true
        } catch (e: IOException) {
            // Asset not included in this build — expected for release builds.
            Log.d(TAG, "No bundled nano model in assets (${e.message})")
            false
        }
    }

    /**
     * Download a model from an arbitrary [url], storing it under [modelsDir] using the
     * last URL path segment as the filename.
     *
     * [onProgress] is called on every buffer write with:
     *   - progress  : 0.0–1.0 (or 0 if content-length unknown)
     *   - bytesDownloaded : cumulative bytes written so far
     *   - totalBytes      : full file size (-1 if unknown)
     *
     * Returns `true` on success, `false` on any error.
     */
    suspend fun downloadWithProgress(
        url: String,
        onProgress: (progress: Float, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val fileName = url.substringAfterLast("/").ifBlank { "model.gguf" }
        val target = File(modelsDir, fileName)

        if (target.exists() && target.length() > 0) {
            Log.i(TAG, "Model already cached: ${target.absolutePath}")
            onProgress(1f, target.length(), target.length())
            return@withContext true
        }

        Log.i(TAG, "Downloading from $url → $fileName")
        val tmpFile = File(modelsDir, "$fileName.tmp")

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP ${response.code} for $url")
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()   // -1 if unknown
                var totalRead = 0L

                FileOutputStream(tmpFile).use { fos ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val progress =
                                if (contentLength > 0) totalRead.toFloat() / contentLength else 0f
                            onProgress(progress, totalRead, contentLength)
                        }
                    }
                }
            }

            if (!tmpFile.renameTo(target)) {
                Log.e(TAG, "Rename failed for $fileName")
                tmpFile.delete()
                return@withContext false
            }

            Log.i(TAG, "Download complete: ${target.absolutePath} (${target.length()} B)")
            onProgress(1f, target.length(), target.length())
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "downloadWithProgress I/O error: ${e.message}")
            tmpFile.delete()
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "downloadWithProgress unexpected error: ${e.message}")
            tmpFile.delete()
            return@withContext false
        }
    }
}
