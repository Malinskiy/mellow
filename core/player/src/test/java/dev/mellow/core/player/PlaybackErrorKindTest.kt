package dev.mellow.core.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorKindTest {

    @Test
    fun `connection failure is server unreachable`() {
        assertEquals(
            PlaybackErrorKind.ServerUnreachable,
            playbackErrorKind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, false),
        )
    }

    @Test
    fun `connection timeout is server unreachable`() {
        assertEquals(
            PlaybackErrorKind.ServerUnreachable,
            playbackErrorKind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null, false),
        )
    }

    @Test
    fun `unresolvable host is server unreachable whatever the code`() {
        assertEquals(
            PlaybackErrorKind.ServerUnreachable,
            playbackErrorKind(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, true),
        )
    }

    @Test
    fun `404 is a removed track`() {
        assertEquals(
            PlaybackErrorKind.TrackRemoved,
            playbackErrorKind(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404, false),
        )
    }

    @Test
    fun `401 and 403 mean signed out`() {
        assertEquals(PlaybackErrorKind.SignedOut, playbackErrorKind(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 401, false))
        assertEquals(PlaybackErrorKind.SignedOut, playbackErrorKind(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403, false))
    }

    @Test
    fun `anything else is generic`() {
        assertEquals(PlaybackErrorKind.Other, playbackErrorKind(PlaybackException.ERROR_CODE_DECODING_FAILED, null, false))
        assertEquals(PlaybackErrorKind.Other, playbackErrorKind(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 500, false))
    }
}
