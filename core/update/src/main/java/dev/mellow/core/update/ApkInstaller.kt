package dev.mellow.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.common.MellowResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a verified APK to the system installer through a `PackageInstaller` session. The outcome arrives
 * asynchronously in [InstallStateHolder] via [UpdateInstallReceiver].
 */
@Singleton
class ApkInstaller internal constructor(
    private val context: Context,
    private val installStateHolder: InstallStateHolder,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        installStateHolder: InstallStateHolder,
    ) : this(context, installStateHolder, Dispatchers.IO)

    /** Whether the user allows Mellow to install apps; check before [install] and again on resume. */
    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for Mellow. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Resets [InstallStateHolder] to [InstallState.Idle], then writes [file] into a new session and commits it.
     * Success means "committed": the result follows in [InstallStateHolder]. On failure the session is abandoned, an
     * [InstallState.Failure] is published and the error returned.
     */
    suspend fun install(file: File): MellowResult<Unit> = withContext(ioDispatcher) {
        val packageInstaller = context.packageManager.packageInstaller
        abandonStaleSessions(packageInstaller)
        var sessionId = InstallStateHolder.NO_SESSION
        var session: PackageInstaller.Session? = null
        try {
            val length = file.length()
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(length)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
                }
            }
            sessionId = packageInstaller.createSession(params)
            installStateHolder.begin(sessionId)
            val openSession = packageInstaller.openSession(sessionId)
            session = openSession
            openSession.openWrite(SESSION_APK_NAME, 0, length).use { output ->
                file.inputStream().use { input -> input.copyTo(output, IO_BUFFER_SIZE) }
                openSession.fsync(output)
            }
            openSession.commit(statusReceiver(sessionId).intentSender)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Install session $sessionId for $file failed", e)
            abandon(packageInstaller, session, sessionId)
            installStateHolder.publish(InstallState.Failure(PackageInstaller.STATUS_FAILURE, e.message))
            MellowResult.Error(e)
        } finally {
            session?.closeQuietly()
        }
    }

    /**
     * Sessions this app created earlier and never finished: a previous attempt the user hid, or one orphaned by
     * process death. Each would otherwise hold staged bytes and count against the per-app session quota, and its
     * late result would be mistaken for the new attempt's (the receiver filters by session id as a second guard).
     */
    private fun abandonStaleSessions(packageInstaller: PackageInstaller) {
        val stale = try {
            packageInstaller.mySessions
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not list install sessions", e)
            return
        }
        for (info in stale) {
            Log.i(LOG_TAG, "Abandoning stale install session ${info.sessionId}")
            abandon(packageInstaller, session = null, sessionId = info.sessionId)
        }
    }

    private fun abandon(packageInstaller: PackageInstaller, session: PackageInstaller.Session?, sessionId: Int) {
        if (session != null) {
            try {
                session.abandon()
                return
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Could not abandon install session $sessionId via its handle", e)
            }
        }
        if (sessionId == InstallStateHolder.NO_SESSION) return
        try {
            packageInstaller.abandonSession(sessionId)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not abandon install session $sessionId", e)
        }
    }

    private fun PackageInstaller.Session.closeQuietly() {
        try {
            close()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not close install session", e)
        }
    }

    /**
     * Explicit and mutable: the installer fills in status extras, which API 34+ allows only on explicit intents.
     * The session id doubles as request code so each session gets its own PendingIntent instead of overwriting the
     * previous one's.
     */
    private fun statusReceiver(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    private companion object {
        const val SESSION_APK_NAME = "mellow.apk"
    }
}
