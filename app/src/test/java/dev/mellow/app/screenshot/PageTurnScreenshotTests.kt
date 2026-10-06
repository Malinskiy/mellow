package dev.mellow.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.mellow.core.designsystem.component.PageTurnDirection
import dev.mellow.core.designsystem.component.PageTurnPose
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.CoverSwipe
import dev.mellow.feature.player.PlayerLayout
import dev.mellow.feature.player.PlayerScreen
import org.junit.Test

/** The now-playing cover held mid-turn: three distinct covers stand in for the current, next and previous tracks. */
abstract class PageTurnScreenshotTests : ScreenshotCapture() {

    @Test
    fun turningToNext() = capture("player-cover-turn-next-45") {
        Player(PageTurnPose(PageTurnDirection.Next, 45f))
    }

    @Test
    fun turnedPastUpright() = capture("player-cover-turn-next-120") {
        Player(PageTurnPose(PageTurnDirection.Next, 120f))
    }

    @Test
    fun previousComingBack() = capture("player-cover-turn-previous-60") {
        Player(PageTurnPose(PageTurnDirection.Previous, 60f))
    }

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Player(pose: PageTurnPose) {
        val covers = AsyncImagePreviewHandler { request ->
            when (request.data) {
                NEXT -> cover(0xFF2F5D62.toInt(), 0xFF5E8B7E.toInt(), "NEXT")
                PREVIOUS -> cover(0xFF3B3561.toInt(), 0xFF7E6BA8.toInt(), "PREV")
                else -> cover(0xFFE8DCC4.toInt(), 0xFFC9A66B.toInt(), "NOW")
            }.asImage()
        }
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides covers,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MellowTheme.colors.background),
            ) {
                PlayerScreen(
                    embedded = true,
                    layout = PlayerLayout.Compact,
                    trackName = "Reckoner",
                    artistName = "Radiohead",
                    albumName = "In Rainbows",
                    albumImageUrl = CURRENT,
                    isPlaying = true,
                    progress = 0.4f,
                    positionMs = 120000L,
                    durationMs = 300000L,
                    codec = "flac",
                    coverSwipe = CoverSwipe(
                        trackKey = "reckoner",
                        nextImageUrl = NEXT,
                        previousImageUrl = PREVIOUS,
                        canGoNext = true,
                        canGoPrevious = true,
                        pose = pose,
                    ),
                )
            }
        }
    }

    private fun cover(top: Int, bottom: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val size = COVER_PX.toFloat()
        canvas.drawRect(
            0f,
            0f,
            size,
            size,
            Paint().apply { shader = LinearGradient(0f, 0f, size, size, top, bottom, Shader.TileMode.CLAMP) },
        )
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC000000.toInt()
            textSize = size * 0.22f
            isFakeBoldText = true
        }
        canvas.drawText(label, size * 0.08f, size * 0.9f, text)
        return bitmap
    }

    private companion object {
        const val CURRENT = "cover://current"
        const val NEXT = "cover://next"
        const val PREVIOUS = "cover://previous"
        const val COVER_PX = 600
    }
}
