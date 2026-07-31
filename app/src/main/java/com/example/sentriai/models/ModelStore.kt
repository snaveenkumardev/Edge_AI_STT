package com.example.sentriai.models

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Where model files live on device and how they are found.
 *
 * Both runtimes in this app load by filesystem path — MediaPipe's `LlmInference` and
 * ExecuTorch's `Module` — so every model has to exist as a real file. `filesDir` is the home
 * for downloaded models; the other two locations are fallbacks kept for development.
 *
 * Lookup order:
 * 1. `filesDir/<name>` — where [ModelDownloader] writes.
 * 2. `assets/<name>` — extracted to `filesDir` if you chose to bundle a model anyway.
 * 3. `/data/local/tmp/llm/<name>` — `adb push` target, so a model can be swapped without a
 *    rebuild or a re-download.
 */
object ModelStore {

    private const val TAG = "ModelStore"

    const val ADB_PUSH_DIR = "/data/local/tmp/llm"

    /** Final resting place of a downloaded model. */
    fun localFile(context: Context, asset: ModelAsset): File = File(context.filesDir, asset.fileName)

    /** Partial download, promoted to [localFile] only once complete and verified. */
    fun partFile(context: Context, asset: ModelAsset): File =
        File(context.filesDir, "${asset.fileName}.part")

    /**
     * True when [asset] is on disk and looks complete.
     *
     * Only the size is checked, not the digest: hashing 280 MB on every launch would add
     * seconds to startup. The digest is verified once, when the download finishes.
     */
    fun isAvailable(context: Context, asset: ModelAsset): Boolean {
        val local = localFile(context, asset)
        if (local.isFile && local.length() > 0 &&
            (asset.sizeBytes == null || local.length() == asset.sizeBytes)
        ) {
            return true
        }
        if (assetExists(context, asset.fileName)) return true
        return File(ADB_PUSH_DIR, asset.fileName).let { it.isFile && it.length() > 0 }
    }

    /**
     * @return the file to hand to a model loader, or a failure whose message is safe to show.
     */
    fun resolve(context: Context, asset: ModelAsset): Result<File> {
        val local = localFile(context, asset)
        if (local.isFile && local.length() > 0 &&
            (asset.sizeBytes == null || local.length() == asset.sizeBytes)
        ) {
            return complete(local, asset)
        }

        if (assetExists(context, asset.fileName)) {
            val bundled = assetSize(context, asset.fileName)
            return when {
                // Uncompressed asset: its real length is a cheap staleness check against the
                // copy already in filesDir.
                bundled > 0 && local.length() == bundled -> complete(local, asset)
                // Compressed asset (anything outside `noCompress`, e.g. a .json): there is no
                // length to compare, so an existing non-empty copy is taken on trust. Without
                // this branch a bundled tokenizer.json resolves to "not downloaded yet".
                bundled <= 0 && local.isFile && local.length() > 0 -> complete(local, asset)
                else -> extractAsset(context, asset.fileName, local).flatMap { complete(it, asset) }
            }
        }

        val pushed = File(ADB_PUSH_DIR, asset.fileName)
        if (pushed.isFile && pushed.length() > 0) {
            Log.i(TAG, "Using adb-pushed ${asset.fileName} (${pushed.length()} bytes)")
            return complete(pushed, asset)
        }

        // A leftover .part is the difference between "never downloaded" and "download was
        // interrupted", and the second is worth saying out loud since it resumes.
        val part = partFile(context, asset)
        val detail = if (part.isFile) " (partial download of ${part.length()} bytes present)" else ""
        return Result.failure(IllegalStateException("${asset.displayName} not downloaded yet$detail"))
    }

    /**
     * Last gate before a file reaches a model runtime: its length must match the catalog.
     *
     * Worth the check because of how badly a short file fails. ExecuTorch parses the program
     * header of a truncated `.pte` happily, then throws a C++ exception on the missing weight
     * segment — and fbjni aborts the whole process translating it (`Abort message: 'ptr'`)
     * rather than raising something Kotlin can catch. A size comparison here turns a SIGABRT
     * with a native backtrace into a sentence naming the file.
     *
     * The catalog is authoritative even for bundled assets, so an interrupted copy into
     * `assets/` is caught too — the asset's own length agrees with itself and proves nothing.
     */
    private fun complete(file: File, asset: ModelAsset): Result<File> {
        val expected = asset.sizeBytes ?: return Result.success(file)
        if (file.length() == expected) return Result.success(file)
        return Result.failure(
            IllegalStateException(
                "${asset.displayName} is incomplete: ${asset.fileName} is ${file.length()} bytes, " +
                    "expected $expected. Re-copy or re-download it."
            )
        )
    }

    private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
        fold(onSuccess = transform, onFailure = { Result.failure(it) })

    /** Deletes a model and any partial download of it, so it will be fetched again. */
    fun delete(context: Context, asset: ModelAsset) {
        localFile(context, asset).delete()
        partFile(context, asset).delete()
    }

    /** Lowercase hex SHA-256 of [file], streamed so a 280 MB model never lands in memory. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Whether [name] is bundled in the APK, regardless of how it was stored. */
    private fun assetExists(context: Context, name: String): Boolean =
        runCatching { context.assets.open(name).close() }.isSuccess

    /**
     * Length of a bundled asset, or -1 when it is absent *or* stored compressed — `openFd`
     * only works for assets listed in `noCompress`. Callers must treat -1 as "unknown", not
     * "missing"; [assetExists] is the presence test.
     */
    private fun assetSize(context: Context, name: String): Long =
        runCatching { context.assets.openFd(name).use { it.length } }.getOrDefault(-1L)

    private fun extractAsset(context: Context, name: String, target: File): Result<File> = try {
        Log.i(TAG, "Extracting bundled $name to ${target.absolutePath}")
        context.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
        }
        Result.success(target)
    } catch (e: Exception) {
        Log.w(TAG, "Asset extraction failed for $name: ${e.message}", e)
        Result.failure(e)
    }
}
