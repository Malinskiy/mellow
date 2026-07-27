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
import dev.mellow.core.data.ArtworkPreCacher
import dev.mellow.core.database.dao.ServerDao
import kotlinx.coroutines.runBlocking

class ArtworkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ArtworkEntryPoint {
        fun serverDao(): ServerDao
        fun artworkPreCacher(): ArtworkPreCacher
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val itemId = uri.lastPathSegment ?: return null
        val ctx = context ?: return null

        val entryPoint = EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            ArtworkEntryPoint::class.java,
        )
        val server = runBlocking { entryPoint.serverDao().getActiveServer() } ?: return null
        val file = entryPoint.artworkPreCacher().resolveArtwork(server.url, server.accessToken, itemId)
            ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/webp"
    override fun query(uri: Uri, p: Array<String>?, s: String?, sa: Array<String>?, so: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<String>?): Int = 0
}
