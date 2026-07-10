package dev.mellow.core.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.database.dao.ServerDao
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URL

class ArtworkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ArtworkEntryPoint {
        fun serverDao(): ServerDao
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val itemId = uri.lastPathSegment ?: return null
        val ctx = context ?: return null

        val cacheDir = File(ctx.cacheDir, "artwork")
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "$itemId.jpg")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            ArtworkEntryPoint::class.java,
        )
        val server = runBlocking { entryPoint.serverDao().getActiveServer() } ?: return null
        val imageUrl = "${server.url}/Items/$itemId/Images/Primary?maxWidth=600&quality=90&api_key=${server.accessToken}"

        return try {
            val tmpFile = File(cacheDir, "$itemId.tmp")
            URL(imageUrl).openStream().use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tmpFile.renameTo(cacheFile)
            ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: Exception) {
            null
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<String>?, s: String?, sa: Array<String>?, so: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<String>?): Int = 0
}
