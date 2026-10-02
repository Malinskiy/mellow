package dev.mellow.core.update

import android.util.Log
import dev.mellow.core.update.di.GitHubClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** [totalBytes] is -1 when the size is unknown. */
data class DownloadProgress(val bytesRead: Long, val totalBytes: Long)

class DownloadException(
    val reason: Reason,
    cause: Throwable? = null,
) : IOException("APK download failed: $reason", cause) {

    sealed interface Reason {
        data class Http(val code: Int) : Reason

        /** Network or storage failure. */
        data object Io : Reason

        data class SizeMismatch(val expected: Long, val actual: Long) : Reason

        data class ChecksumMismatch(val expected: String, val actual: String) : Reason
    }
}

/** Streams a release asset to `cacheDir/updates/mellow-<tag>.apk`, checking its size and published SHA-256. */
@Singleton
class ApkDownloader internal constructor(
    private val client: OkHttpClient,
    private val store: DownloadedUpdateStore,
    private val verifier: ApkVerifier,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        @GitHubClient client: OkHttpClient,
        store: DownloadedUpdateStore,
        verifier: ApkVerifier,
    ) : this(client, store, verifier, Dispatchers.IO)

    /**
     * Emits progress while downloading (at most every ~100 ms or 1%). Completing normally — the last emission has
     * `bytesRead == totalBytes` — means the APK is at [DownloadedUpdateStore.apkFile] with the expected size and
     * digest; call [verifyAndRecord] next. Failures throw [DownloadException]. Cancelling the collector stops the
     * transfer. A failed or cancelled download leaves no partial file and never touches an existing
     * `mellow-<tag>.apk`; files for other tags are deleted before it starts.
     */
    fun download(asset: ReleaseAsset, tag: String): Flow<DownloadProgress> = flow {
        store.purgeExcept(tag)
        val target = store.apkFile(tag)
        var partial: File? = null
        try {
            val directory = target.parentFile ?: throw IOException("$target has no parent directory")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
            val temporary = File.createTempFile(PARTIAL_PREFIX, PARTIAL_SUFFIX, directory)
            partial = temporary
            val digest = newSha256()
            val written = transfer(asset, temporary, digest)
            val length = temporary.length()
            if (asset.sizeBytes >= 0 && length != asset.sizeBytes) {
                throw DownloadException(DownloadException.Reason.SizeMismatch(asset.sizeBytes, length))
            }
            val expectedSha256 = asset.sha256
            if (expectedSha256 != null) {
                val actual = digest.digest().toLowerHex()
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    throw DownloadException(DownloadException.Reason.ChecksumMismatch(expectedSha256, actual))
                }
            } else {
                Log.i(LOG_TAG, "${asset.name} has no published SHA-256; relying on the size and signature checks")
            }
            // A verified APK for this tag is about to be replaced by unverified bytes.
            if (store.entry.first()?.tag == tag) store.clear()
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            partial = null
            emit(DownloadProgress(written, written))
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            Log.w(LOG_TAG, "Downloading ${asset.name} for $tag failed", e)
            throw e
        } catch (e: Exception) {
            // A read failing because the call was cancelled is a cancellation, not an error.
            currentCoroutineContext().ensureActive()
            Log.w(LOG_TAG, "Downloading ${asset.name} for $tag failed", e)
            throw DownloadException(DownloadException.Reason.Io, e)
        } finally {
            partial?.let { file ->
                if (!file.delete() && file.exists()) Log.w(LOG_TAG, "Could not delete partial download $file")
            }
        }
    }.flowOn(ioDispatcher)

    /**
     * Runs [ApkVerifier] on the downloaded APK for [tag]. Only when it passes is the APK recorded in
     * [DownloadedUpdateStore] (so it survives restarts and can be installed later without downloading again);
     * otherwise the file is deleted.
     */
    suspend fun verifyAndRecord(tag: String): VerifyResult {
        val file = store.apkFile(tag)
        val result = verifier.verify(file)
        if (result is VerifyResult.Ok) {
            store.record(tag, file)
        } else {
            if (store.entry.first()?.tag == tag) store.clear()
            withContext(ioDispatcher) {
                if (!file.delete() && file.exists()) Log.w(LOG_TAG, "Could not delete rejected APK $file")
            }
        }
        return result
    }

    private suspend fun FlowCollector<DownloadProgress>.transfer(
        asset: ReleaseAsset,
        destination: File,
        digest: MessageDigest,
    ): Long = coroutineScope {
        val call = client.newCall(Request.Builder().url(asset.downloadUrl).build())
        // Cancels the HTTP call as soon as the collector goes away, unblocking a read stuck on a stalled connection.
        val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw DownloadException(DownloadException.Reason.Http(response.code))
                val body = response.body ?: throw IOException("Empty response body")
                val total = if (asset.sizeBytes >= 0) asset.sizeBytes else body.contentLength()
                copy(body.byteStream(), destination, digest, asset.sizeBytes, total)
            }
        } finally {
            canceller.cancel()
        }
    }

    private suspend fun FlowCollector<DownloadProgress>.copy(
        input: InputStream,
        destination: File,
        digest: MessageDigest,
        expectedSize: Long,
        total: Long,
    ): Long {
        var written = 0L
        var lastEmittedBytes = 0L
        var lastEmittedAtNanos = System.nanoTime()
        emit(DownloadProgress(0L, total))
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(IO_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    written += read
                    if (expectedSize >= 0 && written > expectedSize) {
                        throw DownloadException(DownloadException.Reason.SizeMismatch(expectedSize, written))
                    }
                    val now = System.nanoTime()
                    val onePercentMore = total > 0 && (written - lastEmittedBytes) * 100 >= total
                    if (onePercentMore || now - lastEmittedAtNanos >= PROGRESS_INTERVAL_NANOS) {
                        emit(DownloadProgress(written, total))
                        lastEmittedBytes = written
                        lastEmittedAtNanos = now
                    }
                }
                output.flush()
                output.fd.sync()
            }
        }
        return written
    }

    private companion object {
        const val PARTIAL_PREFIX = "download-"
        const val PARTIAL_SUFFIX = ".part"
        const val PROGRESS_INTERVAL_NANOS = 100_000_000L
    }
}
