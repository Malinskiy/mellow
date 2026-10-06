package dev.mellow.feature.player

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.runtime.Immutable
import dev.mellow.core.designsystem.component.PageTurnCover
import dev.mellow.core.designsystem.component.PageTurnButtons
import dev.mellow.core.designsystem.component.PageTurnDirection
import dev.mellow.core.designsystem.component.PageTurnPose
import dev.mellow.core.designsystem.component.PageTurnTarget
import dev.mellow.core.designsystem.component.rememberPageTurnButtons
import dev.mellow.core.designsystem.component.ArtworkBackground
import dev.mellow.core.designsystem.component.AnimatedHeartIcon
import dev.mellow.core.designsystem.component.AnimatedPlayPauseButton
import dev.mellow.core.designsystem.component.QualityBadge
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlin.math.roundToInt

enum class PlayerLayout {
    Compact,
    Landscape,
    ExpandedWithQueue,
    Tabletop,
}

/**
 * What swiping the cover does: left turns it like a record album page onto [nextImageUrl] and calls [onNext]; right
 * brings back the page of [previousImageUrl] and calls [onPrevious]. [trackKey] identifies the current track.
 * A drag down that starts on the cover goes to the sheet the player sits in through [onSheetDrag] (px, down positive)
 * and [onSheetDragEnd] (px/s); null when the player isn't in a sheet. [onGestureLog] gets one line per gesture on the
 * cover, for tuning (debug builds only). [pose] holds the page still mid-turn, for previews and screenshots.
 * [buttonNext] and [buttonPrevious], when set, are what the player's Next and Previous buttons do: skip, and return
 * where the player went (null if it stayed, e.g. Previous restarting the track), so the page turns there.
 */
@Immutable
data class CoverSwipe(
    val trackKey: Any? = null,
    val nextImageUrl: String? = null,
    val previousImageUrl: String? = null,
    val canGoNext: Boolean = false,
    val canGoPrevious: Boolean = false,
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val pose: PageTurnPose? = null,
    val onSheetDrag: ((Float) -> Unit)? = null,
    val onSheetDragEnd: (Float) -> Unit = {},
    val onGestureLog: ((String) -> Unit)? = null,
    val buttonNext: (() -> PageTurnTarget?)? = null,
    val buttonPrevious: (() -> PageTurnTarget?)? = null,
)

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    layout: PlayerLayout = PlayerLayout.Compact,
    tabletopTopHeight: Dp = 0.dp,
    splitPaneWidth: Dp = Dp.Unspecified,
    embedded: Boolean = false,
    artModifier: Modifier = Modifier,
    trackName: String = "",
    artistName: String = "",
    albumName: String = "",
    albumImageUrl: String? = null,
    isPlaying: Boolean = false,
    progress: Float = 0f,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    error: String? = null,
    onCollapse: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onSkipNextClick: () -> Unit = {},
    onSkipPreviousClick: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    shuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onPlayDownloadedClick: () -> Unit = {},
    codec: String? = null,
    coverSwipe: CoverSwipe = CoverSwipe(),
    sidePanelContent: @Composable (() -> Unit)? = null,
) {
    val pageTurns = rememberPageTurnButtons()
    val skipNext: () -> Unit = coverSwipe.buttonNext
        ?.let { skip -> { pageTurns.turn(PageTurnDirection.Next, skip) } }
        ?: onSkipNextClick
    val skipPrevious: () -> Unit = coverSwipe.buttonPrevious
        ?.let { skip -> { pageTurns.turn(PageTurnDirection.Previous, skip) } }
        ?: onSkipPreviousClick
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.background(MellowTheme.colors.background)),
    ) {
        if (!embedded && albumImageUrl != null) {
            ArtworkBackground(
                artworkKey = albumImageUrl,
                imageUrl = albumImageUrl,
                modifier = Modifier.fillMaxSize(),
                blurRadius = 120.dp,
                imageAlpha = 0.35f,
                overlayColors = listOf(0.5f to 0f, 0.85f to 1f),
            )
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)),
        ) {
        if (layout == PlayerLayout.Tabletop) {
            TabletopPlayerLayout(
                trackName = trackName,
                artistName = artistName,
                albumName = albumName,
                albumImageUrl = albumImageUrl,
                artModifier = artModifier,
                coverSwipe = coverSwipe,
                pageTurns = pageTurns,
                isPlaying = isPlaying,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                isFavorite = isFavorite,
                isDownloaded = isDownloaded,
                onCollapse = onCollapse,
                onQueueClick = onQueueClick,
                onLyricsClick = onLyricsClick,
                onPlayPauseClick = onPlayPauseClick,
                onSkipNextClick = skipNext,
                onSkipPreviousClick = skipPrevious,
                onSeekTo = onSeekTo,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
                onFavoriteClick = onFavoriteClick,
                codec = codec,
                topHeight = tabletopTopHeight,
            )
        } else when (layout) {
            PlayerLayout.ExpandedWithQueue -> {
                val useHingeSplit = splitPaneWidth.isSpecified && sidePanelContent != null
                Row(modifier = Modifier.fillMaxSize()) {
                    val leftModifier = if (useHingeSplit) {
                        Modifier.width(splitPaneWidth)
                    } else {
                        Modifier.weight(1f)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = leftModifier.fillMaxHeight(),
                    ) {
                        val hasSidePanel = sidePanelContent != null
                        NowPlayingTopBar(albumName, onCollapse, onQueueClick, showQueueButton = !hasSidePanel)
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(3f, fill = false)
                                .padding(horizontal = MellowSpacing.Sp8),
                        ) {
                            PlayerCover(
                                albumImageUrl = albumImageUrl,
                                coverSwipe = coverSwipe,
                                pageTurns = pageTurns,
                                modifier = artModifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f),
                                fallbackIconSize = 64.dp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        PlayerTrackInfo(trackName, artistName, isFavorite, isDownloaded, onFavoriteClick)
                        PlayerProgressBar(progress, positionMs, durationMs, onSeekTo)
                        PlayerPlaybackControls(
                            isPlaying = isPlaying,
                            onPlayPauseClick = onPlayPauseClick,
                            onSkipPreviousClick = skipPrevious,
                            onSkipNextClick = skipNext,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            onShuffleClick = onShuffleClick,
                            onRepeatClick = onRepeatClick,
                        )
                        PlayerBottomActions(codec = codec, onLyricsClick = onLyricsClick, reducedBottomPadding = true, showLyricsButton = !hasSidePanel)
                        Spacer(Modifier.weight(1f))
                    }
                    if (sidePanelContent != null) {
                        Box(modifier = if (useHingeSplit) Modifier.weight(1f) else Modifier) {
                            sidePanelContent()
                        }
                    }
                }
            }
            PlayerLayout.Landscape -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(
                                    start = MellowSpacing.Sp8,
                                    end = MellowSpacing.Sp4,
                                ),
                        ) {
                            NowPlayingCollapseButton(onCollapse)
                            PlayerCover(
                                albumImageUrl = albumImageUrl,
                                coverSwipe = coverSwipe,
                                pageTurns = pageTurns,
                                modifier = artModifier
                                    .fillMaxHeight(0.75f)
                                    .aspectRatio(1f),
                                fallbackIconSize = 48.dp,
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            if (albumName.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
                                    modifier = Modifier.padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp1),
                                ) {
                                    Text(
                                        "Playing from",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MellowTheme.colors.muted,
                                        letterSpacing = 0.1.sp,
                                    )
                                    Text(
                                        albumName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MellowTheme.colors.foreground,
                                    )
                                }
                            }
                            PlayerTrackInfo(trackName, artistName, isFavorite, isDownloaded, onFavoriteClick)
                            PlayerProgressBar(progress, positionMs, durationMs, onSeekTo, compactVerticalPadding = true)
                            PlayerPlaybackControls(
                                isPlaying = isPlaying,
                                onPlayPauseClick = onPlayPauseClick,
                                onSkipPreviousClick = skipPrevious,
                                onSkipNextClick = skipNext,
                                shuffleEnabled = shuffleEnabled,
                                repeatMode = repeatMode,
                                onShuffleClick = onShuffleClick,
                                onRepeatClick = onRepeatClick,
                                compactVerticalPadding = true,
                            )
                            PlayerBottomActions(codec = codec, onLyricsClick = onLyricsClick, compactVerticalPadding = true)
                        }
                    }
                    IconButton(
                        onClick = onQueueClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(MellowSpacing.Sp2),
                    ) {
                        Icon(PhosphorIcons.Queue, "Queue", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
                    }
                }
            }
            PlayerLayout.Compact -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    NowPlayingTopBar(albumName, onCollapse, onQueueClick)
                    AlbumArt(
                        albumImageUrl = albumImageUrl,
                        coverSwipe = coverSwipe,
                        pageTurns = pageTurns,
                        artModifier = artModifier,
                        modifier = Modifier.weight(1f),
                    )
                    PlayerTrackInfo(trackName, artistName, isFavorite, isDownloaded, onFavoriteClick)
                    PlayerProgressBar(progress, positionMs, durationMs, onSeekTo)
                    PlayerPlaybackControls(
                        isPlaying = isPlaying,
                        onPlayPauseClick = onPlayPauseClick,
                        onSkipPreviousClick = skipPrevious,
                        onSkipNextClick = skipNext,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                    )
                    PlayerBottomActions(codec = codec, onLyricsClick = onLyricsClick)
                }
            }
            PlayerLayout.Tabletop -> Unit
        }
        }

        if (error != null) {
            PlaybackErrorOverlay(
                error = error,
                onRetryClick = onRetryClick,
                onPlayDownloadedClick = onPlayDownloadedClick,
            )
        }
    }
}

@Composable
private fun PlaybackErrorOverlay(
    error: String,
    onRetryClick: () -> Unit,
    onPlayDownloadedClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background.copy(alpha = 0.92f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(MellowSpacing.Sp8),
        ) {
            Icon(
                PhosphorIcons.CloudSlash,
                contentDescription = null,
                tint = MellowTheme.colors.muted,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(MellowSpacing.Sp4))
            Text(
                "Can't reach server",
                style = MaterialTheme.typography.headlineSmall,
                color = MellowTheme.colors.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(MellowSpacing.Sp2))
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MellowTheme.colors.muted,
            )
            Spacer(Modifier.height(MellowSpacing.Sp6))
            Button(
                onClick = onRetryClick,
                shape = MellowShapes.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MellowTheme.colors.foreground,
                    contentColor = MellowTheme.colors.background,
                ),
            ) {
                Text("Retry")
            }
            Spacer(Modifier.height(MellowSpacing.Sp3))
            OutlinedButton(
                onClick = onPlayDownloadedClick,
                shape = MellowShapes.Full,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MellowTheme.colors.foreground,
                ),
            ) {
                Icon(
                    PhosphorIcons.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MellowSpacing.Sp2))
                Text("Play downloaded music")
            }
        }
    }
}

@Composable
fun NowPlayingTopBar(albumName: String, onCollapse: () -> Unit, onQueueClick: () -> Unit, showQueueButton: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        IconButton(onClick = onCollapse) {
            Icon(PhosphorIcons.CaretDown, "Collapse", tint = MellowTheme.colors.foreground)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            Text(
                albumName.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.labelMedium,
                color = MellowTheme.colors.foreground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showQueueButton) {
            IconButton(onClick = onQueueClick) {
                Icon(PhosphorIcons.Queue, "Queue", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun NowPlayingCollapseButton(onCollapse: () -> Unit, onQueueClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MellowSpacing.Sp2),
    ) {
        IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(PhosphorIcons.CaretDown, "Collapse", tint = MellowTheme.colors.foreground)
        }
        if (onQueueClick != null) {
            IconButton(onClick = onQueueClick, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(PhosphorIcons.Queue, "Queue", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun AlbumArt(
    albumImageUrl: String?,
    coverSwipe: CoverSwipe,
    pageTurns: PageTurnButtons,
    artSize: Dp = 320.dp,
    artModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Fixed gaps keep the cover off the top bar and the title: on a short phone the cover shrinks instead.
        val gaps = compactCoverGapFraction(maxHeight)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = MellowSpacing.Sp8,
                    end = MellowSpacing.Sp8,
                    top = COMPACT_COVER_GAP_ABOVE * gaps,
                    bottom = COMPACT_COVER_GAP_BELOW * gaps,
                ),
        ) {
            PlayerCover(
                albumImageUrl = albumImageUrl,
                coverSwipe = coverSwipe,
                pageTurns = pageTurns,
                modifier = artModifier
                    .sizeIn(maxWidth = artSize, maxHeight = artSize)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                fallbackIconSize = 64.dp,
            )
        }
    }
}

/** Compact: the gap above the cover, which with the top bar's own bottom padding (Sp3) makes 24 dp… */
private val COMPACT_COVER_GAP_ABOVE = MellowSpacing.Sp3

/** …and the gap between the cover and the title. */
private val COMPACT_COVER_GAP_BELOW = MellowSpacing.Sp6

/** Compact: below this the cover would hardly be a cover (nor something to swipe), so the gaps give way first. */
private val COMPACT_COVER_MIN = 120.dp

/**
 * Compact: the fraction of the gaps around the cover that fit in a slot [height] tall, 1 unless the screen is so short
 * that the full gaps would leave the cover smaller than [COMPACT_COVER_MIN].
 */
internal fun compactCoverGapFraction(height: Dp): Float =
    ((height - COMPACT_COVER_MIN) / (COMPACT_COVER_GAP_ABOVE + COMPACT_COVER_GAP_BELOW)).coerceIn(0f, 1f)

/** The expanded player's cover: swipe it to turn to the next or previous track (see [CoverSwipe]). */
@Composable
private fun PlayerCover(
    albumImageUrl: String?,
    coverSwipe: CoverSwipe,
    pageTurns: PageTurnButtons,
    fallbackIconSize: Dp,
    modifier: Modifier = Modifier,
) {
    PageTurnCover(
        current = albumImageUrl,
        modifier = modifier,
        trackKey = coverSwipe.trackKey ?: albumImageUrl,
        next = coverSwipe.nextImageUrl,
        previous = coverSwipe.previousImageUrl,
        canGoNext = coverSwipe.canGoNext,
        canGoPrevious = coverSwipe.canGoPrevious,
        onNext = coverSwipe.onNext,
        onPrevious = coverSwipe.onPrevious,
        contentDescription = "Album art",
        shape = MellowShapes.Large,
        fallbackIconSize = fallbackIconSize,
        pose = coverSwipe.pose,
        onSheetDrag = coverSwipe.onSheetDrag,
        onSheetDragEnd = coverSwipe.onSheetDragEnd,
        onGestureLog = coverSwipe.onGestureLog,
        buttons = pageTurns,
    )
}

@Composable
fun PlayerTrackInfo(
    trackName: String,
    artistName: String,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    onFavoriteClick: () -> Unit = {},
    artists: List<Pair<String, String>> = emptyList(),
    onArtistClick: (String) -> Unit = {},
    onMoreArtistsClick: () -> Unit = {},
) {
    // The heart and the Downloaded check are centred on the title's first line, however many lines it wraps to.
    val titleLayout = remember { TextLayoutHolder() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alignBy(TitleFirstLineCentre),
        ) {
            Row {
                Text(
                    trackName.ifEmpty { "No track" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MellowTheme.colors.foreground,
                    onTextLayout = { titleLayout.result = it },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignBy(TitleFirstLineCentre)
                        .firstLineCentre(titleLayout),
                )
                if (isDownloaded) {
                    Spacer(Modifier.width(MellowSpacing.Sp2))
                    Icon(
                        PhosphorIcons.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MellowTheme.colors.success,
                        modifier = Modifier
                            .size(18.dp)
                            .alignBy { it.measuredHeight / 2 },
                    )
                }
            }
            if (artists.size > 1) {
                MultiArtistText(
                    artists = artists,
                    onArtistClick = onArtistClick,
                    onMoreArtistsClick = onMoreArtistsClick,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(
                    artistName.ifEmpty { "Unknown artist" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MellowTheme.colors.accentStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        AnimatedHeartIcon(
            isFavorite = isFavorite,
            onToggle = onFavoriteClick,
            modifier = Modifier.alignBy { it.measuredHeight / 2 },
            iconSize = 24.dp,
        )
    }
}

/** The vertical centre of the track title's first line, from [firstLineCentre]. */
private val TitleFirstLineCentre = HorizontalAlignmentLine(merger = ::minOf)

/** The latest layout of a [Text], set from its `onTextLayout`, which runs while the text is measured. */
private class TextLayoutHolder {
    var result: TextLayoutResult? = null
}

/**
 * Provides [TitleFirstLineCentre] for the [Text] this modifies, from the layout in [holder]: the middle of its first
 * line as laid out (so it follows font scaling and taller fallback fonts), or of the whole text before there is one.
 */
private fun Modifier.firstLineCentre(holder: TextLayoutHolder): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val text = holder.result
    val centre = if (text != null && text.lineCount > 0) {
        ((text.getLineTop(0) + text.getLineBottom(0)) / 2f).roundToInt()
    } else {
        placeable.height / 2
    }
    layout(placeable.width, placeable.height, mapOf(TitleFirstLineCentre to centre)) {
        placeable.place(0, 0)
    }
}

@Composable
private fun MultiArtistText(
    artists: List<Pair<String, String>>,
    onArtistClick: (String) -> Unit,
    onMoreArtistsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MellowTheme.colors.accentStrong
    val displayArtists = artists.take(2)
    val remaining = artists.size - 2

    val annotatedString = buildAnnotatedString {
        displayArtists.forEachIndexed { index, (id, name) ->
            if (index > 0) {
                withStyle(SpanStyle(color = accentColor)) {
                    append(", ")
                }
            }
            pushStringAnnotation(tag = "artist", annotation = id)
            withStyle(SpanStyle(color = accentColor)) {
                append(name)
            }
            pop()
        }
        if (remaining > 0) {
            withStyle(SpanStyle(color = accentColor)) {
                append(" ")
            }
            pushStringAnnotation(tag = "more", annotation = "")
            withStyle(SpanStyle(color = accentColor)) {
                append("+$remaining more")
            }
            pop()
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations("artist", offset, offset)
                .firstOrNull()?.let { onArtistClick(it.item) }
                ?: annotatedString.getStringAnnotations("more", offset, offset)
                    .firstOrNull()?.let { onMoreArtistsClick() }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerProgressBar(progress: Float, positionMs: Long, durationMs: Long, onSeekTo: (Long) -> Unit, compactVerticalPadding: Boolean = false) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isSeeking) seekProgress else progress

    LaunchedEffect(progress) {
        if (isSeeking && kotlin.math.abs(progress - seekProgress) < 0.02f) {
            isSeeking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = if (compactVerticalPadding) MellowSpacing.Sp1 else MellowSpacing.Sp5),
    ) {
        Slider(
            value = displayProgress.coerceIn(0f, 1f),
            onValueChange = { value ->
                isSeeking = true
                seekProgress = value
            },
            onValueChangeFinished = {
                val seekMs = (seekProgress * durationMs).toLong()
                onSeekTo(seekMs)
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .shadow(2.dp, CircleShape)
                        .background(MellowTheme.colors.foreground, CircleShape),
                )
            },
            track = { sliderState ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MellowPalette.Stone700),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sliderState.value.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(MellowTheme.colors.foreground),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val displayMs = if (isSeeking) (seekProgress * durationMs).toLong() else positionMs
            Text(formatMs(displayMs), style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            Text(formatMs(durationMs), style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
        }
    }
}

@Composable
fun PlayerPlaybackControls(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    shuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    compactVerticalPadding: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = if (compactVerticalPadding) MellowSpacing.Sp1 else MellowSpacing.Sp5),
    ) {
        IconButton(onClick = onShuffleClick, modifier = Modifier.size(36.dp)) {
            Icon(
                PhosphorIcons.Shuffle,
                "Shuffle",
                tint = if (shuffleEnabled) MellowTheme.colors.accentStrong else MellowTheme.colors.inactive,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onSkipPreviousClick, modifier = Modifier.size(44.dp)) {
            Icon(PhosphorIcons.SkipBack, "Previous", tint = MellowTheme.colors.foreground, modifier = Modifier.size(28.dp))
        }
        AnimatedPlayPauseButton(
            isPlaying = isPlaying,
            onToggle = onPlayPauseClick,
            buttonSize = 64.dp,
        )
        IconButton(onClick = onSkipNextClick, modifier = Modifier.size(44.dp)) {
            Icon(PhosphorIcons.SkipForward, "Next", tint = MellowTheme.colors.foreground, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = onRepeatClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (repeatMode == 1) PhosphorIcons.RepeatOnce else PhosphorIcons.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatMode != 0) MellowTheme.colors.accentStrong else MellowTheme.colors.inactive,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun PlayerBottomActions(codec: String? = null, onLyricsClick: () -> Unit = {}, reducedBottomPadding: Boolean = false, compactVerticalPadding: Boolean = false, showLyricsButton: Boolean = true) {
    val qualityLabel = when (codec?.lowercase()) {
        "flac", "alac", "wav", "pcm" -> "Lossless"
        "aac", "mp3", "opus", "vorbis", "ogg" -> "Lossy"
        else -> codec?.uppercase() ?: "Unknown"
    }

    if (compactVerticalPadding) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp5, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp1)) {
                Icon(PhosphorIcons.DeviceMobile, "Device", tint = MellowTheme.colors.muted, modifier = Modifier.size(16.dp))
                Text("This device", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp1),
                modifier = Modifier.clickable(onClick = onLyricsClick),
            ) {
                Icon(PhosphorIcons.TextAa, "Lyrics", tint = MellowTheme.colors.muted, modifier = Modifier.size(16.dp))
                Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
            QualityBadge(codec = codec?.uppercase() ?: "\u2014")
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp2)
                .padding(bottom = if (reducedBottomPadding) MellowSpacing.Sp4 else MellowSpacing.Sp8),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(PhosphorIcons.DeviceMobile, "Device", tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text("This device", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
            if (showLyricsButton) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onLyricsClick),
                ) {
                    Icon(PhosphorIcons.TextAa, "Lyrics", tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QualityBadge(codec = codec?.uppercase() ?: "\u2014")
                Spacer(Modifier.height(4.dp))
                Text(qualityLabel, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
        }
    }
}



fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun TabletopPlayerLayout(
    trackName: String,
    artistName: String,
    albumName: String,
    albumImageUrl: String?,
    artModifier: Modifier,
    coverSwipe: CoverSwipe,
    pageTurns: PageTurnButtons,
    isPlaying: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    onCollapse: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    codec: String?,
    topHeight: Dp,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(topHeight)
                .padding(horizontal = MellowSpacing.Sp6),
        ) {
            NowPlayingTopBar(albumName, onCollapse, onQueueClick)
            Spacer(Modifier.weight(1f))
            // The cover, the gap and a fixed-width text slot stay centred as one unit: the cover never moves
            // when the title (and so the text's width) changes, e.g. mid page turn.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cover = maxHeight * TABLETOP_COVER_HEIGHT_FRACTION
                val gap = MellowSpacing.Sp8
                val textSlot = tabletopTextSlotWidth(rowWidth = maxWidth, cover = cover, gap = gap)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PlayerCover(
                        albumImageUrl = albumImageUrl,
                        coverSwipe = coverSwipe,
                        pageTurns = pageTurns,
                        modifier = artModifier.size(cover),
                        fallbackIconSize = 48.dp,
                    )
                    Spacer(Modifier.width(gap))
                    Column(modifier = Modifier.width(textSlot)) {
                        Text(
                            trackName,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MellowTheme.colors.foreground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(MellowSpacing.Sp1))
                        Text(
                            artistName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MellowTheme.colors.accentStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (albumName.isNotEmpty()) {
                            Spacer(Modifier.height(MellowSpacing.Sp1))
                            Text(
                                albumName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MellowTheme.colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = MellowSpacing.Sp6),
        ) {
            PlayerProgressBar(progress, positionMs, durationMs, onSeekTo)
            PlayerPlaybackControls(
                isPlaying = isPlaying,
                onPlayPauseClick = onPlayPauseClick,
                onSkipPreviousClick = onSkipPreviousClick,
                onSkipNextClick = onSkipNextClick,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
            )
            PlayerBottomActions(codec = codec, onLyricsClick = onLyricsClick)
        }
    }
}

/** Tabletop: the cover's side as a fraction of the height above the hinge (below the top bar). */
private const val TABLETOP_COVER_HEIGHT_FRACTION = 0.7f

/** Tabletop: the text slot beside the cover is this fraction of the row's width… */
private const val TABLETOP_TEXT_SLOT_FRACTION = 0.4f

/** …kept between these widths, and squeezed down to the minimum before the cover is. */
private val TABLETOP_TEXT_SLOT_MIN = 240.dp
private val TABLETOP_TEXT_SLOT_MAX = 400.dp
private val TABLETOP_TEXT_SLOT_SQUEEZED = 160.dp

/**
 * The width of the text slot beside the tabletop cover: 40 % of the row, 240–400 dp, independent of the text. On a
 * row too narrow for that next to the [cover] and [gap], it gives way first, down to 160 dp.
 */
internal fun tabletopTextSlotWidth(rowWidth: Dp, cover: Dp, gap: Dp): Dp {
    val preferred = (rowWidth * TABLETOP_TEXT_SLOT_FRACTION).coerceIn(TABLETOP_TEXT_SLOT_MIN, TABLETOP_TEXT_SLOT_MAX)
    val room = rowWidth - cover - gap
    return minOf(preferred, room).coerceAtLeast(TABLETOP_TEXT_SLOT_SQUEEZED)
}
