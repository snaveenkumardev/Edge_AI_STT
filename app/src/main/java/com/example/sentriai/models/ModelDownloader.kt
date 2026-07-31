package com.example.sentriai.models

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads model files to `filesDir`.
 *
 * Written against the awkward parts of fetching a ~280 MB file to a phone:
 *
 * - **Resume.** Bytes land in `<name>.part` and a restart continues with a `Range` request,
 *   so a dropped connection three quarters of the way through a 280 MB download does not
 *   start over.
 * - **Atomic promotion.** `.part` is renamed to the real name only after the digest matches,
 *   so a loader can never open a half-written model.
 * - **Verification.** A captive portal or an expired signed URL answers with `200 OK` and an
 *   HTML body. Without the digest check that HTML gets saved as `model.pte` and surfaces
 *   later as an unreadable-model crash with no obvious cause.
 * - **Manual redirects.** Storage backends redirect to a signed CDN URL, and the auth header
 *   is deliberately not replayed to a different host.
 */
object ModelDownloader {

    private const val TAG = "ModelDownloader"

    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val BUFFER_BYTES = 1 shl 16

    /** Progress is reported at most this often, to avoid flooding Compose with recompositions. */
    private const val PROGRESS_INTERVAL_BYTES = 512L * 1024L

    /** Refuse to start unless this much headroom remains beyond the file itself. */
    private const val FREE_SPACE_MARGIN_BYTES = 64L * 1024L * 1024L

    /**
     * Fetches [asset] unless it is already present.
     *
     * @param baseUrl directory URL the asset's [ModelAsset.remotePath] hangs off.
     * @param authToken sent as `Authorization: Bearer …` to [baseUrl]'s host only. Needed for
     *   gated HuggingFace repos; leave null when self-hosting on public storage.
     * @param onProgress called with (bytes on disk, total expected). Total is 0 when the server
     *   sends no length and the catalog has none.
     */
    suspend fun download(
        context: Context,
        asset: ModelAsset,
        baseUrl: String,
        authToken: String? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File> {
        if (baseUrl.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "No model download URL configured. Set MODEL_BASE_URL in gradle.properties."
                )
            )
        }
        return downloadFrom(
            context = context,
            asset = asset,
            sourceUrl = baseUrl.trimEnd('/') + "/" + asset.remotePath.trimStart('/'),
            authToken = authToken,
            onProgress = onProgress,
        )
    }

    /**
     * Fetches [asset] from an absolute [sourceUrl].
     *
     * Used by the debug-only Hugging Face download button, which resolves its own hub URL
     * rather than hanging off a configured base.
     */
    suspend fun downloadFrom(
        context: Context,
        asset: ModelAsset,
        sourceUrl: String,
        authToken: String? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = ModelStore.localFile(context, asset)
        if (ModelStore.isAvailable(context, asset)) {
            onProgress(target.length(), target.length())
            return@withContext ModelStore.resolve(context, asset)
        }

        val part = ModelStore.partFile(context, asset)
        val url = URL(sourceUrl)

        asset.sizeBytes?.let { needed ->
            val free = context.filesDir.usableSpace
            if (free < needed - part.length() + FREE_SPACE_MARGIN_BYTES) {
                return@withContext Result.failure(
                    IOException(
                        "Not enough free space for ${asset.displayName}: " +
                            "needs ${needed / 1_048_576} MB, ${free / 1_048_576} MB available"
                    )
                )
            }
        }

        try {
            fetch(url, part, asset, authToken, onProgress)

            asset.sha256?.let { expected ->
                val actual = ModelStore.sha256(part)
                if (!actual.equals(expected, ignoreCase = true)) {
                    part.delete()
                    return@withContext Result.failure(
                        IOException(
                            "${asset.displayName} failed verification — the download was " +
                                "corrupted or the URL served the wrong file. Expected " +
                                "${expected.take(12)}…, got ${actual.take(12)}…"
                        )
                    )
                }
            }

            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                return@withContext Result.failure(IOException("Could not finalize ${asset.fileName}"))
            }

            Log.i(TAG, "Downloaded ${asset.fileName} (${target.length()} bytes)")
            onProgress(target.length(), target.length())
            Result.success(target)
        } catch (e: Exception) {
            // The .part file is deliberately left in place so the next attempt resumes.
            Log.e(TAG, "Download of ${asset.fileName} failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Streams the body into [part], appending when the server honours a range request. */
    private suspend fun fetch(
        url: URL,
        part: File,
        asset: ModelAsset,
        authToken: String?,
        onProgress: (Long, Long) -> Unit,
    ) {
        val resumeFrom = if (part.isFile) part.length() else 0L
        val connection = open(url, resumeFrom, authToken)

        try {
            val status = connection.responseCode

            if (status == 416) {
                // The .part is at least as long as the resource — it is junk from a changed
                // file on the server rather than a resumable prefix.
                connection.disconnect()
                part.delete()
                fetch(url, part, asset, authToken, onProgress)
                return
            }
            if (status == 401 || status == 403) {
                val reason = connection.getHeaderField("x-error-message")
                    ?: connection.getHeaderField("x-error-code")
                    ?: connection.responseMessage
                // Hugging Face reports a scope problem and an unaccepted licence with the same
                // "not in the authorized list" text, and a fine-grained token scoped only to
                // your own user hits it even after you accept. Name both fixes.
                throw IOException(
                    "Access denied for ${asset.displayName}: $reason\n\n" +
                        "Check both: (1) the token has the global permission \"Read access to " +
                        "the contents of all public gated repos you can access\" — a " +
                        "fine-grained token scoped only to your own user is not enough; " +
                        "(2) the same account has accepted the licence on the model page."
                )
            }
            if (status !in 200..299) {
                throw IOException("HTTP $status fetching ${asset.fileName} (${connection.responseMessage})")
            }

            val contentType = connection.contentType.orEmpty()
            if (contentType.startsWith("text/html")) {
                // A sign-in wall or an error page. Saving it would produce a "model" that only
                // fails much later, inside the runtime.
                throw IOException(
                    "${asset.displayName} URL returned a web page, not a file — " +
                        "the model may be gated or the URL wrong"
                )
            }

            val append = status == 206
            if (!append && resumeFrom > 0) {
                Log.w(TAG, "Server ignored Range for ${asset.fileName}; restarting download")
            }

            val alreadyOnDisk = if (append) resumeFrom else 0L
            val remaining = connection.contentLengthLong.takeIf { it > 0 } ?: 0L
            val total = when {
                remaining > 0 -> alreadyOnDisk + remaining
                asset.sizeBytes != null -> asset.sizeBytes
                else -> 0L
            }

            var written = alreadyOnDisk
            var lastReported = 0L
            onProgress(written, total)

            connection.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (written - lastReported >= PROGRESS_INTERVAL_BYTES) {
                            lastReported = written
                            onProgress(written, total)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(written, total)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens [url], following redirects by hand so the auth header is never replayed to a host
     * other than the one it was issued for.
     */
    private fun open(url: URL, resumeFrom: Long, authToken: String?): HttpURLConnection {
        var current = url
        var redirects = 0

        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
                if (!authToken.isNullOrBlank() && current.host == url.host) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                }
            }

            val status = connection.responseCode
            if (status !in listOf(301, 302, 303, 307, 308)) return connection

            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank() || ++redirects > MAX_REDIRECTS) {
                throw IOException("Too many redirects fetching $url")
            }
            current = URL(current, location)
        }
    }
}
