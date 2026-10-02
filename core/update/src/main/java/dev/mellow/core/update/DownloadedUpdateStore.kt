package dev.mellow.core.update

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A verified APK sitting in the updates directory. [sha256] is the lowercase hex digest recorded at verification. */
data class DownloadedApk(val tag: String, val path: String, val sha256: String?)

/**
 * Remembers which verified APK is on disk in `cacheDir/updates/` (persisted in [UpdatePreferences]) and owns that
 * directory's layout: the APK for a tag is always `mellow-<tag>.apk`.
 */
@Singleton
class DownloadedUpdateStore internal constructor(
    private val preferences: UpdatePreferences,
    private val directoryProvider: () -> File,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(@ApplicationContext context: Context, preferences: UpdatePreferences) : this(
        preferences = preferences,
        directoryProvider = { File(context.cacheDir, UPDATES_DIRECTORY) },
        ioDispatcher = Dispatchers.IO,
    )

    /** The recorded entry as persisted; the file is not checked. */
    val entry: Flow<DownloadedApk?> = preferences.downloadedApk

    /** Where the APK for [tag] is (or would be) stored. */
    suspend fun apkFile(tag: String): File = withContext(ioDispatcher) { File(directoryProvider(), fileNameFor(tag)) }

    /** The recorded APK if it is for [tag] and its file still exists. Does not hash the file. */
    suspend fun forTag(tag: String): DownloadedApk? {
        val apk = entry.first()?.takeIf { it.tag == tag } ?: return null
        val exists = withContext(ioDispatcher) { File(apk.path).isFile }
        return apk.takeIf { exists }
    }

    /** Records [file] as the verified APK for [tag], hashing it so that a later replacement is noticed. */
    suspend fun record(tag: String, file: File): DownloadedApk {
        val apk = withContext(ioDispatcher) { DownloadedApk(tag, file.absolutePath, file.sha256Hex()) }
        preferences.setDownloadedApk(apk)
        return apk
    }

    /**
     * The recorded APK if its file exists and still has the recorded digest. Otherwise the entry is cleared — only
     * the entry; files are left for the next download's purge — and null is returned.
     */
    suspend fun validate(): DownloadedApk? {
        val apk = entry.first() ?: return null
        val intact = withContext(ioDispatcher) {
            val file = File(apk.path)
            file.isFile && (apk.sha256 == null || file.sha256Hex().equals(apk.sha256, ignoreCase = true))
        }
        if (intact) return apk
        Log.w(LOG_TAG, "Downloaded APK for ${apk.tag} at ${apk.path} is missing or was modified; forgetting it")
        clear()
        return null
    }

    /** Forgets the entry; leaves files alone. */
    suspend fun clear() {
        preferences.setDownloadedApk(null)
    }

    /**
     * Deletes everything in the updates directory except `mellow-<tag>.apk`, so there is never more than one APK on
     * disk, and forgets an entry recorded for a different tag.
     */
    suspend fun purgeExcept(tag: String) {
        val keep = fileNameFor(tag)
        withContext(ioDispatcher) {
            directoryProvider().listFiles()
                ?.filter { it.name != keep }
                ?.forEach { stale ->
                    if (!stale.deleteRecursively()) Log.w(LOG_TAG, "Could not delete stale update file $stale")
                }
        }
        if (entry.first()?.let { it.tag != tag } == true) clear()
    }

    /** Forgets the entry and deletes the whole updates directory. */
    suspend fun deleteAll() {
        clear()
        withContext(ioDispatcher) {
            val directory = directoryProvider()
            if (directory.exists() && !directory.deleteRecursively()) {
                Log.w(LOG_TAG, "Could not delete $directory")
            }
        }
    }

    companion object {
        internal const val UPDATES_DIRECTORY = "updates"
        private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

        /** `mellow-<tag>.apk`, with anything that could escape the directory replaced. */
        fun fileNameFor(tag: String): String = "mellow-${tag.replace(UNSAFE_FILE_NAME_CHARS, "_")}.apk"
    }
}
