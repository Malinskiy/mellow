package dev.mellow.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface VerifyResult {
    data class Ok(val versionCode: Long) : VerifyResult

    /** Missing, unreadable, or not a parseable APK. */
    data object NotAnApk : VerifyResult

    /** An APK for some other application. */
    data object WrongPackage : VerifyResult

    /** Its versionCode is not above the installed one (checked only when this build's versionCode is above 1). */
    data object NotNewer : VerifyResult

    /** Signed with a different key than the installed app: Android would refuse the update. */
    data object SignatureMismatch : VerifyResult
}

/**
 * Pre-install gate: the APK must be this app, newer, and signed by exactly the installed app's signers, so that the
 * system installer is never handed something it would reject with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
 */
@Singleton
class ApkVerifier internal constructor(
    private val context: Context,
    private val config: UpdateConfig,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(@ApplicationContext context: Context, config: UpdateConfig) : this(context, config, Dispatchers.IO)

    suspend fun verify(file: File): VerifyResult {
        val result = try {
            withContext(ioDispatcher) { inspect(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not inspect $file", e)
            VerifyResult.NotAnApk
        }
        if (result !is VerifyResult.Ok) Log.w(LOG_TAG, "$file failed verification: $result")
        return result
    }

    private fun inspect(file: File): VerifyResult {
        if (!file.isFile) return VerifyResult.NotAnApk
        val packageManager = context.packageManager
        val archive = packageManager.packageArchiveInfoCompat(file.absolutePath, archiveSignerFlags())
            ?: return VerifyResult.NotAnApk
        if (archive.packageName != context.packageName) return VerifyResult.WrongPackage
        val versionCode = archive.longVersionCodeCompat()
        if (config.currentVersionCode > 1 && versionCode <= config.currentVersionCode) return VerifyResult.NotNewer
        val installed = packageManager.packageInfoCompat(context.packageName, installedSignerFlags())
        val archiveSigners = archive.signerDigests()
        if (archiveSigners.isEmpty() || archiveSigners != installed.signerDigests()) {
            return VerifyResult.SignatureMismatch
        }
        return VerifyResult.Ok(versionCode)
    }

    private fun PackageInfo.signerDigests(): Set<String> =
        apkContentsSignersCompat().map { signature -> newSha256().digest(signature.toByteArray()).toLowerHex() }.toSet()
}
