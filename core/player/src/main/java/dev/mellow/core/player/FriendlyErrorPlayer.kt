package dev.mellow.core.player

import android.content.Context
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import java.net.UnknownHostException

/**
 * Wraps the session's player so its errors carry a message meant for people. Media3 shows the player error's message
 * as is on system surfaces such as Android Auto, where ExoPlayer's own messages ("Source error") explain nothing.
 *
 * The error is replaced in the player state, so every reader (state queries and listener events, the app's own
 * controller and system controllers) sees the same error. Its code and cause are kept, so code that inspects them
 * keeps working.
 */
internal class FriendlyErrorPlayer(
    player: Player,
    private val context: Context,
) : ForwardingSimpleBasePlayer(player) {

    // One friendly error per player error, so state comparisons see a stable value.
    private var lastSourceError: PlaybackException? = null
    private var lastFriendlyError: PlaybackException? = null

    override fun getState(): State {
        val state = super.getState()
        val error = state.playerError ?: return state
        return state.buildUpon().setPlayerError(friendlyError(error)).build()
    }

    private fun friendlyError(error: PlaybackException): PlaybackException {
        if (error === lastSourceError) lastFriendlyError?.let { return it }
        val friendly = PlaybackException(playbackErrorMessage(error), error.cause, error.errorCode)
        lastSourceError = error
        lastFriendlyError = friendly
        return friendly
    }

    private fun playbackErrorMessage(error: PlaybackException): String = context.getString(
        when (playbackErrorKind(error.errorCode, httpStatusCode(error), hasUnknownHostCause(error))) {
            PlaybackErrorKind.ServerUnreachable -> R.string.playback_error_server_unreachable
            PlaybackErrorKind.TrackRemoved -> R.string.playback_error_track_removed
            PlaybackErrorKind.SignedOut -> R.string.playback_error_signed_out
            PlaybackErrorKind.Other -> R.string.playback_error_generic
        },
    )

    private fun httpStatusCode(error: PlaybackException): Int? =
        generateSequence(error.cause) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode

    private fun hasUnknownHostCause(error: PlaybackException): Boolean =
        generateSequence(error.cause) { it.cause }.any { it is UnknownHostException }
}

internal enum class PlaybackErrorKind { ServerUnreachable, TrackRemoved, SignedOut, Other }

/**
 * Classifies a playback error. Downloaded tracks play from the local cache, so a network failure means the track
 * isn't downloaded and the server can't be reached.
 */
internal fun playbackErrorKind(errorCode: Int, httpStatusCode: Int?, unknownHost: Boolean): PlaybackErrorKind = when {
    httpStatusCode == 404 -> PlaybackErrorKind.TrackRemoved
    httpStatusCode == 401 || httpStatusCode == 403 -> PlaybackErrorKind.SignedOut
    unknownHost ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackErrorKind.ServerUnreachable
    else -> PlaybackErrorKind.Other
}
