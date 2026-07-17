package dev.mellow.app.dev

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mellow.core.designsystem.component.AnimatedAlbumDownloadIndicator
import dev.mellow.core.designsystem.component.AnimatedHeartIcon
import dev.mellow.core.designsystem.component.AnimatedPlayPauseButton
import dev.mellow.core.designsystem.component.AnimatedPlayPauseIcon
import dev.mellow.core.designsystem.component.AnimatedSongDownloadIcon
import dev.mellow.core.designsystem.component.DownloadIconState
import dev.mellow.core.designsystem.component.GrainientBackground
import dev.mellow.core.designsystem.component.IridescenceBackground
import dev.mellow.core.designsystem.component.PixelBlastPlaceholder
import dev.mellow.core.designsystem.component.Shimmer
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private val TerminalGreen = Color(0xFF8ec8a0)
private val TerminalGreenDim = Color(0xFF5a8a68)
private val TerminalBorder = Color(0xFF3a5a44)
private val SeverityCritical = Color(0xFFc95555)
private val SeverityMajor = Color(0xFFc98a55)
private val SeverityMinor = Color(0xFF8ec8a0)
private val DoorRedBorder = Color(0xFF8a5a5a)

private val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace)

private enum class BugSeverity { Critical, Major, Minor }
private enum class BugFixStatus { Fixed, Patched }

private data class NarratorEntry(
    val date: String,
    val story: String,
    val commit: String? = null,
)

private data class MetricEntry(
    val label: String,
    val value: String,
    val commentary: String,
    val wide: Boolean = false,
)

private data class GraveyardEntry(
    val feature: String,
    val epitaph: String,
    val commit: String? = null,
)

private data class BugEntry(
    val title: String,
    val severity: BugSeverity,
    val fixStatus: BugFixStatus,
    val story: String,
    val commit: String,
)

private val narratorEntries = listOf(
    NarratorEntry(
        "July 7",
        "The first commit. 21,000 lines of Kotlin were about to be written. Nobody warned you.",
    ),
    NarratorEntry(
        "July 8",
        "You added a connection status dot. Then you moved it. Then you moved it again. It ended up exactly where it started.",
        "b13ef89 \u2192 9ca040e \u2014 identical messages",
    ),
    NarratorEntry(
        "July 9",
        "You built favorites syncing. The server returns hearts as UserData.IsFavorite. You checked this field in four different places, three of which were wrong.",
    ),
    NarratorEntry(
        "July 10",
        "You built a shared element transition for album art. It took 4 commits to get it right. The user will never notice.",
        "shared element work \u2014 4 commits, 0 gratitude",
    ),
    NarratorEntry(
        "July 11",
        "You paginated the Android Auto browse tree. Then you reverted it. Then you capped it at 500. Sometimes the right answer is the boring one.",
        "74a81ab \u2192 e3db8d7 \u2192 f630857",
    ),
    NarratorEntry(
        "July 12",
        "You restructured the navigation graph. Again. MellowNavHost.kt gained 200 lines. It did not thank you.",
    ),
    NarratorEntry(
        "July 13",
        "A quiet day. Only 6 commits. You refactored the download manager. It worked on the first try. You didn\u2019t trust it.",
    ),
    NarratorEntry(
        "July 14",
        "15 commits in one day. You were in the zone. Or spiraling. Hard to tell.",
    ),
    NarratorEntry(
        "July 15",
        "17 commits. You broke your own record. The bugs kept multiplying. You fixed them faster than they appeared. Barely.",
    ),
    NarratorEntry(
        "July 16",
        "You opened this menu. The system noticed. It always notices.",
    ),
)

private val metricEntries = listOf(
    MetricEntry("Total commits", "92", "In 10 days. That\u2019s 9.2 per day. Each one a promise. Some kept."),
    MetricEntry("Days elapsed", "10", "You could have been outside."),
    MetricEntry(
        "Features shipped", "36",
        "For every feature you added, you fixed 0.89 bugs. This is either impressive or concerning.",
    ),
    MetricEntry("Bugs fixed", "32", "Some were your fault. All were your responsibility."),
    MetricEntry(
        "Lines of Kotlin", "21,044",
        "Twenty-one thousand lines. Not one of them is a comment.",
        wide = true,
    ),
    MetricEntry(
        "Kotlin files", "146",
        "146 files. 47 with @Composable. 32% UI. The rest: regret.",
    ),
    MetricEntry(
        "God File", "MellowNavHost.kt",
        "1,717 lines. You know you should split it. You won\u2019t.",
    ),
)

private val graveyardEntries = listOf(
    GraveyardEntry(
        "The Folders Tab",
        "Born July 12. Removed the same day. A placeholder that never found its purpose.",
    ),
    GraveyardEntry(
        "Background Mode Toggle",
        "Lived in dev builds. Stripped before release. Some secrets are best kept.",
    ),
    GraveyardEntry(
        "Full Song Pagination",
        "Android Auto\u2019s browse tree was going to be paginated. It was reverted after one commit. Cap at 500 it is.",
    ),
    GraveyardEntry(
        "Certificate Toggle (v1)",
        "First attempt at self-signed certificate support. Replaced by a better version 3 commits later. The first one trusted everything. That was the problem.",
        "replaced \u2014 trust nothing",
    ),
)

private val bugEntries = listOf(
    BugEntry(
        "The Autofill Incident",
        BugSeverity.Critical,
        BugFixStatus.Patched,
        "Android insisted on painting your login fields yellow. You suppressed it with a deprecated API and a prayer. The prayer worked. The API might not, next update.",
        "ea69f39",
    ),
    BugEntry(
        "The Tablet Crash",
        BugSeverity.Critical,
        BugFixStatus.Fixed,
        "anchoredDraggable with empty anchors. The player sheet would expand into the void. On tablets only. Because tablets exist to remind you that your assumptions are wrong.",
        "b19cf40",
    ),
    BugEntry(
        "The Artist Name Mismatch",
        BugSeverity.Major,
        BugFixStatus.Fixed,
        "Jellyfin uses / between artists. You expected +. Neither was right. The correct separator was a lie agreed upon by committee.",
        "eae4fd6",
    ),
    BugEntry(
        "The Cold Start",
        BugSeverity.Critical,
        BugFixStatus.Fixed,
        "MediaService woke up, but the Jellyfin session didn\u2019t. Music played from the void. Or rather, it didn\u2019t play at all, but the notification said it was playing. The notification lied.",
        "4b78fcc",
    ),
    BugEntry(
        "The Infinite Scroll",
        BugSeverity.Minor,
        BugFixStatus.Fixed,
        "The library loaded albums endlessly. Each scroll spawned a new request. The server was patient. Your RAM was not.",
        "pagination rework",
    ),
)

private val typewriterLines = listOf(
    "You chose to stay.",
    "",
    "The app respects your decision.",
    "Nothing will happen.",
    "",
    "You could close the app.",
    "You could press the back button.",
    "You could lock your phone and go for a walk.",
    "",
    "But you won\u2019t.",
    "",
    "Because you want to see if something happens.",
    "",
    "It won\u2019t.",
    "",
    "Probably.",
)

@Composable
fun DevIconComparisonScreen(onBack: () -> Unit) {
    var stayForever by remember { mutableStateOf(false) }

    if (stayForever) {
        StayForeverOverlay(onBack = onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        DevTopBar(onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            item { HeroSection() }
            item { SectionDivider() }

            item { SectionHeader("01", "The Narrator\u2019s Log") }
            item { NarratorTimeline() }
            item { SectionDivider() }

            item { SectionHeader("02", "The Metrics Room") }
            item {
                Text(
                    text = "These numbers are real. That is the unsettling part.",
                    style = MonoStyle.copy(fontSize = 11.sp),
                    color = MellowPalette.Stone600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                )
            }
            item { MetricsGrid() }
            item { SectionDivider() }

            item { SectionHeader("03", "The Graveyard") }
            item {
                Text(
                    text = "Features that lived. Briefly.",
                    style = MonoStyle.copy(fontSize = 11.sp),
                    color = MellowPalette.Stone600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                )
            }
            item { GraveyardSection() }
            item { SectionDivider() }

            item { SectionHeader("04", "The Bug Wall") }
            item {
                Text(
                    text = "Every application is a collection of bugs that happen to produce useful behavior.",
                    style = MonoStyle.copy(fontSize = 11.sp),
                    color = MellowPalette.Stone600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                )
            }
            item { BugWallSection() }
            item { SectionDivider() }

            item { SectionHeader("05", "The Art Gallery") }
            item {
                Text(
                    text = "You spent time making these. Time you could have spent fixing bugs. You don\u2019t regret it.",
                    style = MonoStyle.copy(fontSize = 11.sp),
                    color = MellowPalette.Stone600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                )
            }
            item { SectionHeader("05a", "Background Shaders") }
            item { BackgroundShaderDemo() }
            item { SectionDivider() }
            item { SectionHeader("05b", "Loading Placeholders") }
            item { LoadingPlaceholderDemo() }
            item { SectionDivider() }
            item { SectionHeader("05c", "Play / Pause \u2014 Filled") }
            item { PlayPauseFilledDemo() }
            item { SectionDivider() }
            item { SectionHeader("05d", "Play / Pause \u2014 Outlined") }
            item { PlayPauseOutlinedDemo() }
            item { SectionDivider() }
            item { SectionHeader("05e", "Heart / Favorite") }
            item { HeartFavoriteDemo() }
            item { SectionDivider() }
            item { SectionHeader("05f", "Song Download Status") }
            item { SongDownloadDemo() }
            item { SectionDivider() }
            item { SectionHeader("05g", "Album Download") }
            item { AlbumDownloadDemo() }
            item { SectionDivider() }

            item { SectionHeader("06", "The Choice") }
            item {
                ChoiceSection(
                    onBack = onBack,
                    onStayForever = { stayForever = true },
                )
            }
            item { Spacer(Modifier.height(MellowSpacing.Sp16)) }
        }
    }
}

@Composable
private fun DevTopBar(onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "topBarPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp3),
    ) {
        IconButton(onClick = onBack) {
            Icon(PhosphorIcons.ArrowLeft, "Back", tint = MellowTheme.colors.foreground)
        }
        Text(
            text = "dev/menu",
            style = MonoStyle.copy(fontSize = 14.sp, letterSpacing = 0.04.sp),
            color = MellowPalette.Stone400,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .padding(end = MellowSpacing.Sp4)
                .size(6.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(TerminalGreen),
        )
    }
}

@Composable
private fun HeroSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp6),
    ) {
        Text(
            text = "The Developer\u2019s Parable",
            style = MonoStyle.copy(fontSize = 16.sp),
            color = MellowPalette.Stone200,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MellowSpacing.Sp2))
        Text(
            text = "A HIDDEN RECORD OF WHAT HAPPENED HERE",
            style = MonoStyle.copy(fontSize = 11.sp, letterSpacing = 0.06.sp),
            color = MellowPalette.Stone600,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(1.dp)
                .background(TerminalBorder),
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Text(
            text = buildAnnotatedString {
                append("You opened the developer menu. Not many do. Perhaps you were curious. Perhaps you were lost. Either way, ")
                withStyle(SpanStyle(color = MellowPalette.Stone300)) {
                    append("the system has been watching.")
                }
            },
            style = MonoStyle.copy(fontSize = 12.5.sp),
            color = MellowPalette.Stone500,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

@Composable
private fun SectionDivider() {
    val dotColor = TerminalBorder
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MellowPalette.Stone800),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = MellowSpacing.Sp2)
                .size(4.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MellowPalette.Stone800),
        )
    }
}

@Composable
private fun SectionHeader(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        modifier = Modifier.padding(
            start = MellowSpacing.Sp4,
            end = MellowSpacing.Sp4,
            top = MellowSpacing.Sp4,
            bottom = MellowSpacing.Sp2,
        ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .border(1.dp, TerminalBorder, RoundedCornerShape(4.dp)),
        ) {
            Text(
                text = number,
                style = MonoStyle.copy(fontSize = 10.sp),
                color = TerminalGreenDim,
            )
        }
        Text(
            text = title.uppercase(),
            style = MonoStyle.copy(fontSize = 12.sp, letterSpacing = 0.12.sp),
            color = MellowPalette.Stone500,
        )
    }
}

@Composable
private fun NarratorTimeline() {
    val lineColor = TerminalBorder
    val dotBorderColor = TerminalGreenDim
    val bgColor = MellowTheme.colors.background

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MellowSpacing.Sp4 + 14.dp, end = MellowSpacing.Sp4)
            .padding(vertical = MellowSpacing.Sp2),
    ) {
        narratorEntries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val isFirst = index == 0
                    val isLast = index == narratorEntries.lastIndex
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val topY = if (isFirst) 4.dp.toPx() else 0f
                        val bottomY = if (isLast) 4.dp.toPx() + 9.dp.toPx() else size.height
                        val gradientTop = if (isFirst) {
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, lineColor),
                                startY = topY,
                                endY = topY + 16.dp.toPx(),
                            )
                        } else null
                        val gradientBottom = if (isLast) {
                            Brush.verticalGradient(
                                colors = listOf(lineColor, Color.Transparent),
                                startY = bottomY - 16.dp.toPx(),
                                endY = bottomY,
                            )
                        } else null

                        if (isFirst) {
                            drawLine(
                                brush = gradientTop!!,
                                start = Offset(centerX, topY),
                                end = Offset(centerX, 4.dp.toPx() + 4.5.dp.toPx()),
                                strokeWidth = 1.dp.toPx(),
                            )
                            drawLine(
                                color = lineColor,
                                start = Offset(centerX, 4.dp.toPx() + 4.5.dp.toPx()),
                                end = Offset(centerX, bottomY),
                                strokeWidth = 1.dp.toPx(),
                            )
                        } else if (isLast) {
                            drawLine(
                                color = lineColor,
                                start = Offset(centerX, topY),
                                end = Offset(centerX, 4.dp.toPx()),
                                strokeWidth = 1.dp.toPx(),
                            )
                            drawLine(
                                brush = gradientBottom!!,
                                start = Offset(centerX, 4.dp.toPx()),
                                end = Offset(centerX, bottomY),
                                strokeWidth = 1.dp.toPx(),
                            )
                        } else {
                            drawLine(
                                color = lineColor,
                                start = Offset(centerX, topY),
                                end = Offset(centerX, bottomY),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        val dotCenterY = 4.dp.toPx() + 4.5.dp.toPx()
                        val dotRadius = 4.5.dp.toPx()
                        drawCircle(
                            color = bgColor,
                            radius = dotRadius,
                            center = Offset(centerX, dotCenterY),
                        )
                        drawCircle(
                            color = dotBorderColor,
                            radius = dotRadius,
                            center = Offset(centerX, dotCenterY),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MellowSpacing.Sp3, bottom = MellowSpacing.Sp4),
                ) {
                    Text(
                        text = entry.date,
                        style = MonoStyle.copy(fontSize = 11.sp),
                        color = TerminalGreenDim,
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp1))
                    Text(
                        text = entry.story,
                        style = MonoStyle.copy(fontSize = 12.5.sp),
                        color = MellowPalette.Stone400,
                    )
                    if (entry.commit != null) {
                        Spacer(Modifier.height(MellowSpacing.Sp1))
                        Text(
                            text = entry.commit,
                            style = MonoStyle.copy(fontSize = 10.sp),
                            color = MellowPalette.Stone600,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
    ) {
        val pairedEntries = mutableListOf<List<MetricEntry>>()
        var i = 0
        while (i < metricEntries.size) {
            val entry = metricEntries[i]
            if (entry.wide) {
                pairedEntries.add(listOf(entry))
                i++
            } else if (i + 1 < metricEntries.size && !metricEntries[i + 1].wide) {
                pairedEntries.add(listOf(entry, metricEntries[i + 1]))
                i += 2
            } else {
                pairedEntries.add(listOf(entry))
                i++
            }
        }

        pairedEntries.forEach { row ->
            if (row.size == 1 && row[0].wide) {
                MetricCard(entry = row[0], modifier = Modifier.fillMaxWidth())
            } else if (row.size == 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MetricCard(entry = row[0], modifier = Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { entry ->
                        MetricCard(entry = entry, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(entry: MetricEntry, modifier: Modifier = Modifier) {
    val isGodFile = entry.label == "God File"
    Column(
        modifier = modifier
            .clip(MellowShapes.Medium)
            .border(1.dp, MellowPalette.Stone800, MellowShapes.Medium)
            .background(MellowTheme.colors.surface)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4),
    ) {
        Text(
            text = entry.label.uppercase(),
            style = MonoStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.sp),
            color = MellowPalette.Stone500,
        )
        Spacer(Modifier.height(MellowSpacing.Sp2))
        Text(
            text = entry.value,
            style = if (isGodFile) {
                MonoStyle.copy(fontSize = 14.sp)
            } else {
                MonoStyle.copy(fontSize = 24.sp)
            },
            color = if (isGodFile) MellowPalette.Stone300 else TerminalGreen,
        )
        Spacer(Modifier.height(MellowSpacing.Sp3))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MellowPalette.Stone800),
        )
        Spacer(Modifier.height(MellowSpacing.Sp3))
        Text(
            text = entry.commentary,
            style = MonoStyle.copy(fontSize = 11.sp),
            color = MellowPalette.Stone500,
        )
    }
}

@Composable
private fun GraveyardSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
    ) {
        graveyardEntries.forEach { entry ->
            GraveyardCard(entry = entry)
        }
    }
}

@Composable
private fun GraveyardCard(entry: GraveyardEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MellowShapes.Medium)
            .border(1.dp, MellowPalette.Stone800, MellowShapes.Medium)
            .background(MellowTheme.colors.surface)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4),
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(MellowShapes.Small)
                .background(TerminalGreen.copy(alpha = 0.08f)),
        ) {
            Text(
                text = "\u2020",
                style = MonoStyle.copy(fontSize = 18.sp),
                color = TerminalGreenDim,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
        ) {
            Text(
                text = entry.feature,
                style = MonoStyle.copy(fontSize = 13.sp),
                color = MellowPalette.Stone300,
                textDecoration = TextDecoration.LineThrough,
            )
            Text(
                text = entry.epitaph,
                style = MonoStyle.copy(fontSize = 11.sp),
                color = MellowPalette.Stone500,
            )
            if (entry.commit != null) {
                Text(
                    text = entry.commit,
                    style = MonoStyle.copy(fontSize = 10.sp),
                    color = MellowPalette.Stone600,
                )
            }
        }
    }
}

@Composable
private fun BugWallSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
    ) {
        bugEntries.forEach { entry ->
            BugCard(entry = entry)
        }
    }
}

@Composable
private fun BugCard(entry: BugEntry) {
    val severityColor = when (entry.severity) {
        BugSeverity.Critical -> SeverityCritical
        BugSeverity.Major -> SeverityMajor
        BugSeverity.Minor -> SeverityMinor
    }
    val fixBgColor = when (entry.fixStatus) {
        BugFixStatus.Fixed -> TerminalGreen.copy(alpha = 0.1f)
        BugFixStatus.Patched -> SeverityMajor.copy(alpha = 0.1f)
    }
    val fixTextColor = when (entry.fixStatus) {
        BugFixStatus.Fixed -> TerminalGreen
        BugFixStatus.Patched -> SeverityMajor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MellowShapes.Medium)
            .border(1.dp, MellowPalette.Stone800, MellowShapes.Medium)
            .background(MellowTheme.colors.surface)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(severityColor),
            )
            Text(
                text = entry.title,
                style = MonoStyle.copy(fontSize = 13.sp),
                color = MellowPalette.Stone300,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = entry.story,
            style = MonoStyle.copy(fontSize = 11.sp),
            color = MellowPalette.Stone500,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
        ) {
            Text(
                text = entry.commit,
                style = MonoStyle.copy(fontSize = 10.sp),
                color = MellowPalette.Stone600,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(fixBgColor)
                    .padding(horizontal = MellowSpacing.Sp2, vertical = 2.dp),
            ) {
                Text(
                    text = entry.fixStatus.name.lowercase(),
                    style = MonoStyle.copy(fontSize = 9.sp),
                    color = fixTextColor,
                )
            }
        }
    }
}

@Composable
private fun ChoiceSection(onBack: () -> Unit, onStayForever: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp6),
    ) {
        Text(
            text = "You have reached the end. The Narrator has shown you everything. There are two doors. You already know which one you\u2019ll pick. You always did.",
            style = MonoStyle.copy(fontSize = 13.sp),
            color = MellowPalette.Stone400,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
            modifier = Modifier.fillMaxWidth(),
        ) {
            DoorButton(
                label = "Return to\nSettings",
                borderColor = TerminalGreenDim,
                knobColor = TerminalGreenDim,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            DoorButton(
                label = "Stay here\nforever",
                borderColor = DoorRedBorder,
                knobColor = DoorRedBorder,
                onClick = onStayForever,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DoorButton(
    label: String,
    borderColor: Color,
    knobColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            val archWidth = size.width * 0.7f
            val archLeft = (size.width - archWidth) / 2f
            drawArc(
                color = borderColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(archLeft, 0f),
                size = Size(archWidth, size.height * 2f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MellowShapes.Medium)
                .background(MellowTheme.colors.surface)
                .border(1.5.dp, MellowPalette.Stone700, MellowShapes.Medium)
                .clickable(onClick = onClick)
                .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp5),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            ) {
                Text(
                    text = label,
                    style = MonoStyle.copy(fontSize = 12.sp),
                    color = MellowPalette.Stone400,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(knobColor),
                )
            }
        }
    }
}

@Composable
private fun StayForeverOverlay(onBack: () -> Unit) {
    val fullText = typewriterLines.joinToString("\n")
    var visibleCharCount by remember { mutableIntStateOf(0) }
    var typingDone by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    val transition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )

    LaunchedEffect(Unit) {
        for (charIdx in 1..fullText.length) {
            delay(45L)
            visibleCharCount = charIdx
        }
        typingDone = true
    }

    LaunchedEffect(typingDone) {
        if (!typingDone) return@LaunchedEffect
        while (isActive) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(MellowSpacing.Sp6),
        ) {
            Row {
                Text(
                    text = fullText.take(visibleCharCount),
                    style = MonoStyle.copy(fontSize = 13.sp),
                    color = MellowPalette.Stone400,
                    textAlign = TextAlign.Center,
                )
                if (!typingDone) {
                    Box(
                        modifier = Modifier
                            .padding(start = 1.dp)
                            .width(8.dp)
                            .height(15.dp)
                            .alpha(cursorAlpha)
                            .background(TerminalGreen),
                    )
                }
            }

            if (typingDone) {
                Spacer(Modifier.height(MellowSpacing.Sp6))
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                Text(
                    text = "You have been here for $minutes:${seconds.toString().padStart(2, '0')}",
                    style = MonoStyle.copy(fontSize = 11.sp),
                    color = MellowPalette.Stone600,
                )
            }

            Spacer(Modifier.height(MellowSpacing.Sp6))
            Text(
                text = "Actually, go back",
                style = MonoStyle.copy(fontSize = 11.sp),
                color = TerminalGreenDim,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
    }
}

private val presetColors = listOf(
    Color(0xFF6366F1),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFFEF4444),
    Color(0xFFF97316),
    Color(0xFF22C55E),
    Color(0xFF06B6D4),
    Color(0xFF3B82F6),
)

@Composable
private fun BackgroundShaderDemo() {
    var selectedColor by remember { mutableStateOf(presetColors[0]) }
    var animated by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp6),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Color", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            Spacer(Modifier.weight(1f))
            Text("Animate", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            Spacer(Modifier.size(MellowSpacing.Sp2))
            Switch(checked = animated, onCheckedChange = { animated = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2)) {
            presetColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (color == selectedColor) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier,
                        )
                        .clickable { selectedColor = color },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ShaderTile("Grainient", Modifier.weight(1f)) {
                GrainientBackground(
                    modifier = Modifier.fillMaxSize(),
                    baseColor = selectedColor,
                    animated = animated,
                )
            }
            ShaderTile("Iridescence", Modifier.weight(1f)) {
                IridescenceBackground(
                    modifier = Modifier.fillMaxSize(),
                    color = selectedColor,
                    animated = animated,
                )
            }
        }

        Spacer(Modifier.height(MellowSpacing.Sp2))
        Text(
            "Multi-color iridescence (album palette)",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val secondary = presetColors[(presetColors.indexOf(selectedColor) + 3) % presetColors.size]
            ShaderTile("Dual-color", Modifier.weight(1f)) {
                IridescenceBackground(
                    modifier = Modifier.fillMaxSize(),
                    color = selectedColor,
                    secondaryColor = secondary,
                    animated = animated,
                )
            }
            ShaderTile("Simple fallback", Modifier.weight(1f)) {
                val light = androidx.compose.ui.graphics.lerp(selectedColor, Color.White, 0.35f)
                val dark = androidx.compose.ui.graphics.lerp(selectedColor, Color.Black, 0.45f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(light, selectedColor, dark),
                            ),
                        ),
                )
            }
        }

        Spacer(Modifier.height(MellowSpacing.Sp2))
        Text(
            "Album background modes (as seen behind player)",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val secondary = presetColors[(presetColors.indexOf(selectedColor) + 3) % presetColors.size]
            ShaderTile("Player — Grainient", Modifier.weight(1f)) {
                Box(Modifier.fillMaxSize().background(MellowTheme.colors.background)) {
                    GrainientBackground(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.52f },
                        baseColor = selectedColor,
                        grainAmount = 0f,
                        animated = animated,
                    )
                }
            }
            ShaderTile("Player — Iridescence", Modifier.weight(1f)) {
                Box(Modifier.fillMaxSize().background(MellowTheme.colors.background)) {
                    IridescenceBackground(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.52f },
                        color = selectedColor,
                        secondaryColor = secondary,
                        animated = animated,
                        speed = 0.2f,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShaderTile(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MellowShapes.Medium),
        ) {
            content()
        }
        Spacer(Modifier.height(MellowSpacing.Sp1))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
    }
}

@Composable
private fun LoadingPlaceholderDemo() {
    val pixelColor = MellowTheme.colors.foreground.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp6),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        Text(
            "Cycles load \u2192 image \u2192 reset with random delay",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LoadingDemoTile(
                label = "Shimmer",
                placeholder = { mod -> Shimmer(mod) },
                modifier = Modifier.weight(1f),
            )
            LoadingDemoTile(
                label = "Pixel Blast",
                placeholder = { mod ->
                    PixelBlastPlaceholder(modifier = mod, color = pixelColor)
                },
                modifier = Modifier.weight(1f),
            )
            LoadingDemoTile(
                label = "Pixel Blast (dense)",
                placeholder = { mod ->
                    PixelBlastPlaceholder(modifier = mod, color = pixelColor, patternDensity = 0.9f)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LoadingDemoTile(
    label: String,
    placeholder: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (isActive) {
            isLoading = true
            delay(Random.nextLong(800, 3000))
            isLoading = false
            delay(Random.nextLong(2000, 3500))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MellowShapes.Medium)
                .background(MellowTheme.colors.surface),
        ) {
            if (isLoading) {
                placeholder(Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(MellowPalette.Stone700, MellowPalette.Stone500),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PhosphorIcons.MusicNote,
                        contentDescription = null,
                        tint = MellowPalette.Stone300,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(MellowSpacing.Sp1))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
    }
}

@Composable
private fun PlayPauseFilledDemo() {
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        Text(
            if (isPlaying) "Playing \u2014 tap to pause" else "Paused \u2014 tap to play",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Small" to 40.dp, "Default" to 64.dp, "Large" to 72.dp).forEach { (label, btnSize) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedPlayPauseButton(
                        isPlaying = isPlaying,
                        onToggle = { isPlaying = !isPlaying },
                        buttonSize = btnSize,
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp1))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun PlayPauseOutlinedDemo() {
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        Text(
            if (isPlaying) "Playing \u2014 tap to pause" else "Paused \u2014 tap to play",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Small" to 20.dp, "Default" to 24.dp, "Large" to 32.dp).forEach { (label, iconSz) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        onToggle = { isPlaying = !isPlaying },
                        iconSize = iconSz,
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp1))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun HeartFavoriteDemo() {
    var isFavorite by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        Text(
            if (isFavorite) "Favorited \u2014 tap to remove" else "Not favorited \u2014 tap to add",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Track row" to 22.dp, "Default" to 32.dp, "Now Playing" to 44.dp).forEach { (label, iconSize) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedHeartIcon(
                        isFavorite = isFavorite,
                        onToggle = { isFavorite = !isFavorite },
                        iconSize = iconSize,
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp1))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
                }
            }
        }
    }
}

private enum class SongDlState { Idle, Downloading, Done }

@Composable
private fun SongDownloadDemo() {
    var row1State by remember { mutableStateOf(SongDlState.Idle) }
    var row1Progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        SongDownloadRow("Track 1 \u2014 Idle \u2192 Animate", row1State, row1Progress)
        HorizontalDivider(color = MellowTheme.colors.border)
        SongDownloadRow("Track 2 \u2014 Downloading", SongDlState.Downloading, 0.63f)
        HorizontalDivider(color = MellowTheme.colors.border)
        SongDownloadRow("Track 3 \u2014 Done", SongDlState.Done, 1f)
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MellowTheme.colors.surface)
                    .clickable {
                        if (row1State != SongDlState.Idle) {
                            row1State = SongDlState.Idle
                            row1Progress = 0f
                            return@clickable
                        }
                        scope.launch {
                            row1State = SongDlState.Downloading
                            for (i in 1..20) {
                                delay(80)
                                row1Progress = i / 20f
                            }
                            row1State = SongDlState.Done
                            row1Progress = 1f
                        }
                    }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            ) {
                Text(
                    if (row1State == SongDlState.Idle) "Simulate download" else "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    color = MellowTheme.colors.foreground,
                )
            }
        }
    }
}

@Composable
private fun SongDownloadRow(title: String, state: SongDlState, progress: Float) {
    val dlState = when (state) {
        SongDlState.Idle -> DownloadIconState.Idle
        SongDlState.Downloading -> DownloadIconState.Downloading
        SongDlState.Done -> DownloadIconState.Done
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        AnimatedSongDownloadIcon(state = dlState, progress = progress, modifier = Modifier.size(32.dp))
    }
}

private enum class AlbumDlState { Idle, Downloading, Done }

@Composable
private fun AlbumDownloadDemo() {
    var state by remember { mutableStateOf(AlbumDlState.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    var tracksDone by remember { mutableIntStateOf(0) }
    val totalTracks = 12
    val scope = rememberCoroutineScope()

    val dlState = when (state) {
        AlbumDlState.Idle -> DownloadIconState.Idle
        AlbumDlState.Downloading -> DownloadIconState.Downloading
        AlbumDlState.Done -> DownloadIconState.Done
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        AnimatedAlbumDownloadIndicator(
            state = dlState,
            progress = progress,
            tracksDone = tracksDone,
            totalTracks = totalTracks,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(MellowSpacing.Sp2))
        Text(
            when (state) {
                AlbumDlState.Idle -> "Tap Simulate to start"
                AlbumDlState.Downloading -> "$tracksDone/$totalTracks tracks"
                AlbumDlState.Done -> "Downloaded"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state == AlbumDlState.Done) MellowTheme.colors.success else MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MellowTheme.colors.surface)
                    .clickable {
                        if (state != AlbumDlState.Idle) return@clickable
                        scope.launch {
                            state = AlbumDlState.Downloading
                            for (i in 1..totalTracks) {
                                delay(120)
                                tracksDone = i
                                progress = i.toFloat() / totalTracks
                            }
                            state = AlbumDlState.Done
                        }
                    }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            ) {
                Text("Simulate", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.foreground)
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MellowTheme.colors.surface)
                    .clickable {
                        state = AlbumDlState.Idle
                        progress = 0f
                        tracksDone = 0
                    }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            ) {
                Text("Reset", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.foreground)
            }
        }
    }
}
