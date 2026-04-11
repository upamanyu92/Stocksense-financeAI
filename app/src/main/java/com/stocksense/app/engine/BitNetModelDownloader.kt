package com.stocksense.app.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
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
        private val foregroundDownloadLock = Any()
        private var nextForegroundSessionId = 1L
        private var activeForegroundDownload: ForegroundDownloadSession? = null

        private data class ForegroundDownloadSession(
            val sessionId: Long,
            val modelDisplayName: String,
            val targetFileName: String,
            var call: Call? = null
        )

        /** HuggingFace base URLs for each quality-mode model. */
        private const val HF_SMOLLM2_URL =
            "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
        private const val HF_PHI2_URL =
            "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf"
        private const val HF_MISTRAL_URL =
            "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf"

        const val IMPORTED_MODEL_FILE_NAME = "imported_model.gguf"
        val LEGACY_MODEL_FILE_NAMES = listOf(
            "ggml-model-i2_s.gguf",
            "SmolLM2-135M-Instruct-Q4_K_M.gguf"
        )

        /**
         * Asset path for the optional nano model bundled inside the APK.
         * Place the GGUF file at:
         *   app/src/main/assets/models/smollm2-135m-instruct-v0.2-q4_k_m.gguf
         * and build the APK — no download required.
         */
        const val BUNDLED_MODEL_ASSET = "models/smollm2-135m-instruct-v0.2-q4_k_m.gguf"
        const val BUNDLED_MODEL_FILE  = "smollm2-135m-instruct-v0.2-q4_k_m.gguf"

        /**
         * Map of quality mode to (file-name, download-URL).
         *
         * | Mode     | Model                              | ~Size  |
         * |----------|------------------------------------|--------|
         * | LITE     | SmolLM2 135M Q4_K_M (nano)         | ~80 MB |
         * | BALANCED | Phi-2 2.7B Q4_K_M                 | ~1.6 GB|
         * | PRO      | Mistral 7B Instruct v0.2 Q4_K_M   | ~4.1 GB|
         */
        val MODEL_CATALOGUE: Map<QualityMode, Pair<String, String>> = mapOf(
            QualityMode.LITE to Pair(
                "smollm2-135m-instruct-v0.2-q4_k_m.gguf",
                HF_SMOLLM2_URL
            ),
            QualityMode.BALANCED to Pair(
                "phi-2.Q4_K_M.gguf",
                HF_PHI2_URL
            ),
            QualityMode.PRO to Pair(
                "mistral-7b-instruct-v0.2.Q4_K_M.gguf",
                HF_MISTRAL_URL
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

    /** Check whether any usable GGUF model is already present on disk. */
    fun hasAnyDiscoveredModel(): Boolean = discoverExistingModels().isNotEmpty()

    /** True when an interactive foreground model download is in progress. */
    fun hasActiveForegroundDownload(): Boolean = synchronized(foregroundDownloadLock) {
        activeForegroundDownload != null
    }

    /** Start a new foreground download session, cancelling any previous one and cleaning stale temp files. */
    fun prepareForegroundDownload(modelDisplayName: String, targetFileName: String): Long {
        val sessionId = synchronized(foregroundDownloadLock) {
            activeForegroundDownload?.call?.cancel()
            val id = nextForegroundSessionId++
            activeForegroundDownload = ForegroundDownloadSession(
                sessionId = id,
                modelDisplayName = modelDisplayName,
                targetFileName = targetFileName
            )
            id
        }
        cleanupTemporaryModelFiles()
        Log.i(TAG, "download_started session=$sessionId model=$modelDisplayName target=$targetFileName")
        return sessionId
    }

    /** Cancel the current foreground download session if it matches [sessionId], then clean temp files. */
    fun cancelForegroundDownload(sessionId: Long? = null, reason: String = "cancelled"): Boolean {
        val cancelled = synchronized(foregroundDownloadLock) {
            val active = activeForegroundDownload ?: return@synchronized false
            if (sessionId != null && active.sessionId != sessionId) return@synchronized false
            active.call?.cancel()
            activeForegroundDownload = null
            true
        }
        if (cancelled) {
            cleanupTemporaryModelFiles()
            Log.i(TAG, "download_cancelled_stale reason=$reason session=${sessionId ?: "active"}")
        }
        return cancelled
    }

    /** Delete stale partial model downloads from [modelsDir]. */
    fun cleanupTemporaryModelFiles(exceptFileNames: Set<String> = emptySet()): Int {
        var deleted = 0
        modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".tmp") && it.name !in exceptFileNames }
            ?.forEach { tmp ->
                if (tmp.delete()) {
                    deleted++
                    Log.i(TAG, "tmp_cleanup_deleted file=${tmp.name}")
                }
            }
        if (deleted > 0) {
            Log.i(TAG, "tmp_cleanup_complete deleted=$deleted")
        }
        return deleted
    }

    /** Return the local [File] for the given mode (may not yet exist). */
    fun modelFileFor(mode: QualityMode): File {
        val (fileName, _) = MODEL_CATALOGUE[mode]
            ?: throw IllegalArgumentException("Unknown mode: $mode")
        return File(modelsDir, fileName)
    }

    /**
     * Return all non-empty GGUF model files currently discoverable on disk.
     *
     * Priority order:
     *  1. Exact catalogue model for the requested [mode], if supplied.
     *  2. Imported model file.
     *  3. Bundled SmolLM2 file.
     *  4. Known legacy file names from older app versions.
     *  5. Any other completed `.gguf` in [modelsDir].
     */
    fun discoverExistingModels(mode: QualityMode? = null): List<File> {
        fun existing(file: File): File? = file.takeIf { it.exists() && it.length() > 0L && it.extension.equals("gguf", ignoreCase = true) }

        val ordered = mutableListOf<File>()

        if (mode != null) {
            existing(modelFileFor(mode))?.let(ordered::add)
        }

        existing(File(modelsDir, IMPORTED_MODEL_FILE_NAME))?.let(ordered::add)
        existing(File(modelsDir, BUNDLED_MODEL_FILE))?.let(ordered::add)

        LEGACY_MODEL_FILE_NAMES
            .map { File(modelsDir, it) }
            .mapNotNull(::existing)
            .forEach(ordered::add)

        modelsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L && it.extension.equals("gguf", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach(ordered::add)

        return ordered.distinctBy { it.absolutePath }
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

        return@withContext downloadToFile(fileName, url, onProgress)
    }

    /** Download an exact model file to the provided canonical [fileName]. */
    suspend fun downloadToFile(
        fileName: String,
        url: String,
        onProgress: ((Float) -> Unit)? = null,
        foregroundSessionId: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
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
            val call = client.newCall(request)
            if (foregroundSessionId != null && !attachForegroundCall(foregroundSessionId, call)) {
                return@withContext false
            }
            call.execute().use { response ->
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
                            if (foregroundSessionId != null && !isForegroundSessionActive(foregroundSessionId)) {
                                tmpFile.delete()
                                return@withContext false
                            }
                            fos.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                onProgress?.invoke(totalRead.toFloat() / contentLength)
                            }
                        }
                    }
                }
            }

            if (foregroundSessionId != null && !isForegroundSessionActive(foregroundSessionId)) {
                tmpFile.delete()
                return@withContext false
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
            if (foregroundSessionId != null && !isForegroundSessionActive(foregroundSessionId)) {
                Log.i(TAG, "download_cancelled_stale session=$foregroundSessionId target=$fileName")
            } else {
                Log.e(TAG, "Download error: ${e.message}")
            }
            tmpFile.delete()
            return@withContext false
        } finally {
            if (foregroundSessionId != null) finishForegroundDownload(foregroundSessionId)
        }
    }

    /** Delete all downloaded model files to free storage. */
    fun clearModels() {
        cancelForegroundDownload(reason = "clear_models")
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
        targetFileName: String = url.substringAfterLast("/").ifBlank { "model.gguf" },
        foregroundSessionId: Long? = null,
        onProgress: (progress: Float, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val target = File(modelsDir, targetFileName)

        if (target.exists() && target.length() > 0) {
            Log.i(TAG, "Model already cached: ${target.absolutePath}")
            onProgress(1f, target.length(), target.length())
            return@withContext true
        }

        Log.i(TAG, "Downloading from $url → $targetFileName")
        val tmpFile = File(modelsDir, "$targetFileName.tmp")

        try {
            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            if (foregroundSessionId != null && !attachForegroundCall(foregroundSessionId, call)) {
                return@withContext false
            }
            call.execute().use { response ->
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
                            if (foregroundSessionId != null && !isForegroundSessionActive(foregroundSessionId)) {
                                tmpFile.delete()
                                return@withContext false
                            }
                            fos.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val progress =
                                if (contentLength > 0) totalRead.toFloat() / contentLength else 0f
                            onProgress(progress, totalRead, contentLength)
                        }
                    }
                }
            }

            if (foregroundSessionId != null && !isForegroundSessionActive(foregroundSessionId)) {
                tmpFile.delete()
                return@withContext false
            }

            if (!tmpFile.renameTo(target)) {
                Log.e(TAG, "Rename failed for $targetFileName")
                tmpFile.delete()
                return@withContext false
            }

            Log.i(TAG, "Download complete: ${target.absolutePath} (${target.length()} B)")
            onProgress(1f, target.length(), target.length())
            return@withContext true
        } catch (e: IOException) {
            if (foregroundSessionId != null && !isForegroundSessionActive(foregroundSessionId)) {
                Log.i(TAG, "download_cancelled_stale session=$foregroundSessionId target=$targetFileName")
            } else {
                Log.e(TAG, "downloadWithProgress I/O error: ${e.message}")
            }
            tmpFile.delete()
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "downloadWithProgress unexpected error: ${e.message}")
            tmpFile.delete()
            return@withContext false
        } finally {
            if (foregroundSessionId != null) finishForegroundDownload(foregroundSessionId)
        }
    }

    private fun attachForegroundCall(sessionId: Long, call: Call): Boolean = synchronized(foregroundDownloadLock) {
        val active = activeForegroundDownload
        if (active?.sessionId == sessionId) {
            active.call = call
            true
        } else {
            call.cancel()
            false
        }
    }

    private fun isForegroundSessionActive(sessionId: Long): Boolean = synchronized(foregroundDownloadLock) {
        activeForegroundDownload?.sessionId == sessionId
    }

    private fun finishForegroundDownload(sessionId: Long) {
        synchronized(foregroundDownloadLock) {
            if (activeForegroundDownload?.sessionId == sessionId) {
                activeForegroundDownload = null
            }
        }
    }
}
