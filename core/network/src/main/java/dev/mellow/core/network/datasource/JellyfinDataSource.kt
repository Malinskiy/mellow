package dev.mellow.core.network.datasource

import dev.mellow.core.network.JellyfinClientWrapper
import android.util.Log
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinDataSource @Inject constructor(
    private val client: JellyfinClientWrapper,
) {
    suspend fun authenticate(username: String, password: String): AuthResult {
        val response by client.api.userApi.authenticateUserByName(
            data = AuthenticateUserByName(username = username, pw = password),
        )
        return AuthResult(
            userId = response.user!!.id.toString(),
            accessToken = response.accessToken!!,
            serverId = response.serverId!!,
            serverName = response.user!!.serverName.orEmpty(),
        )
    }

    suspend fun getAlbums(userId: UUID, startIndex: Int = 0, limit: Int = 200): List<BaseItemDto> {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.DATE_CREATED),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
        )
        return response.items.orEmpty()
    }

    suspend fun getArtists(userId: UUID, startIndex: Int = 0, limit: Int = 200): List<BaseItemDto> {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.OVERVIEW),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
        )
        return response.items.orEmpty()
    }

    suspend fun getTracks(userId: UUID, startIndex: Int = 0, limit: Int = 500): List<BaseItemDto> {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS, ItemFields.DATE_CREATED),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
        )
        return response.items.orEmpty()
    }

    suspend fun getAlbumTracks(userId: UUID, albumId: UUID): List<BaseItemDto> {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            parentId = albumId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            fields = listOf(ItemFields.MEDIA_STREAMS),
            enableUserData = true,
        )
        return response.items.orEmpty()
    }

    suspend fun setFavorite(userId: UUID, itemId: UUID, isFavorite: Boolean) {
        if (isFavorite) {
            client.api.userLibraryApi.markFavoriteItem(userId = userId, itemId = itemId)
        } else {
            client.api.userLibraryApi.unmarkFavoriteItem(userId = userId, itemId = itemId)
        }
    }

    suspend fun reportPlaybackStarted(itemId: UUID) {
        try {
            client.api.playStateApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = itemId,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_PLAY,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report playback started for $itemId", e)
        }
    }

    suspend fun reportPlaybackProgress(itemId: UUID, positionTicks: Long) {
        try {
            client.api.playStateApi.reportPlaybackProgress(
                PlaybackProgressInfo(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_PLAY,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report playback progress for $itemId", e)
        }
    }

    suspend fun reportPlaybackStopped(itemId: UUID, positionTicks: Long) {
        try {
            client.api.playStateApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    failed = false,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report playback stopped for $itemId", e)
        }
    }

    companion object {
        private const val TAG = "JellyfinDataSource"
    }

    suspend fun getFavoriteAlbums(userId: UUID): List<BaseItemDto> {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            isFavorite = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.DATE_CREATED),
            enableUserData = true,
        )
        return response.items.orEmpty()
    }

    suspend fun getFavoriteArtists(userId: UUID): List<BaseItemDto> {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
            recursive = true,
            isFavorite = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.OVERVIEW),
            enableUserData = true,
        )
        return response.items.orEmpty()
    }

    suspend fun getFavoriteTracks(userId: UUID): List<BaseItemDto> {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            isFavorite = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS),
            enableUserData = true,
        )
        return response.items.orEmpty()
    }

    data class AuthResult(
        val userId: String,
        val accessToken: String,
        val serverId: String,
        val serverName: String,
    )
}
