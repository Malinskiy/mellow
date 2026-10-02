package dev.mellow.core.update

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle

// Bridges between the minSdk 26 platform and compileSdk 36. Every deprecated call below sits on the branch for the
// older API levels that only have that call; newer levels take the replacement API.

/** `getPackageInfo`; throws [PackageManager.NameNotFoundException] like the platform call. */
internal fun PackageManager.packageInfoCompat(packageName: String, flags: Int): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }

/** `getPackageArchiveInfo`: null when [path] is not a parseable APK. */
internal fun PackageManager.packageArchiveInfoCompat(path: String, flags: Int): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageArchiveInfo(path, flags)
    }

/** Flags that make `getPackageInfo` report the installed package's signers. */
internal fun installedSignerFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        legacyGetSignatures()
    }

/**
 * Flags that make `getPackageArchiveInfo` report an archive's signers. Before Android 13 the archive parser only
 * collects certificates when `GET_SIGNATURES` is set, even if `GET_SIGNING_CERTIFICATES` is
 * (https://issuetracker.google.com/159537841).
 */
internal fun archiveSignerFlags(): Int = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> PackageManager.GET_SIGNING_CERTIFICATES
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
        PackageManager.GET_SIGNING_CERTIFICATES or legacyGetSignatures()
    else -> legacyGetSignatures()
}

/** The certificates that signed the APK contents (`signingInfo.apkContentsSigners` where available). */
internal fun PackageInfo.apkContentsSignersCompat(): List<Signature> {
    val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.apkContentsSigners ?: legacySignatures()
    } else {
        legacySignatures()
    }
    return signers.orEmpty().toList()
}

internal fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

/**
 * `Intent.EXTRA_INTENT` from a status bundle. The typed `getParcelable` exists since API 33 but is unreliable there
 * (androidx `BundleCompat` uses it only from API 34 for the same reason).
 */
internal fun Bundle.intentExtraCompat(): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getParcelable(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable<Intent>(Intent.EXTRA_INTENT)
    }

@Suppress("DEPRECATION")
private fun legacyGetSignatures(): Int = PackageManager.GET_SIGNATURES

@Suppress("DEPRECATION")
private fun PackageInfo.legacySignatures(): Array<Signature>? = signatures
