package dev.mellow.core.update

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether Google's developer-verification component is installed, i.e. whether installs of an unregistered app like
 * Mellow may be blocked. Used only to decide whether to show guidance up front — never to classify a failed install.
 * Needs `<queries><package android:name="com.google.android.verifier" /></queries>` in the app manifest.
 */
@Singleton
class VerifierDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile
    private var present: Boolean? = null

    fun isDeveloperVerifierPresent(): Boolean = present ?: detect().also { present = it }

    private fun detect(): Boolean = try {
        context.packageManager.packageInfoCompat(VERIFIER_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val VERIFIER_PACKAGE = "com.google.android.verifier"
    }
}
