package dev.mellow.core.network.datasource

import dev.mellow.core.network.JellyfinClientWrapper
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.AuthenticateUserByName

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

    @Suppress("unused")
    suspend fun reportPlaybackStarted(itemId: UUID) {
        // TODO: Wire to playStateApi once request body shape is confirmed
    }

    @Suppress("unused")
    suspend fun reportPlaybackProgress(itemId: UUID, positionTicks: Long) {
        // TODO: Wire to playStateApi once request body shape is confirmed
    }

    @Suppress("unused")
    suspend fun reportPlaybackStopped(itemId: UUID, positionTicks: Long) {
        // TODO: Wire to playStateApi once request body shape is confirmed
    }

    data class AuthResult(
        val userId: String,
        val accessToken: String,
        val serverId: String,
        val serverName: String,
    )
}
