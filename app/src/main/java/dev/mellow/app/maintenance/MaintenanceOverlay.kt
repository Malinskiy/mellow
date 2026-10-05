package dev.mellow.app.maintenance

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mellow.core.designsystem.component.maintenance.DatabaseMaintenanceScreen
import dev.mellow.core.designsystem.component.maintenance.rememberPlexusClock
import dev.mellow.core.designsystem.theme.LocalBatterySaverActive
import dev.mellow.core.designsystem.theme.LocalFoldableState
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.rememberFoldableState
import dev.mellow.core.designsystem.theme.rememberIsBatterySaverActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val TAG = "MaintenanceScreen"

/**
 * The maintenance screen while [work] runs. The plexus plays at least until its logo has formed after [work] is done
 * (see [maintenanceExit]), then the screen fades out from the held logo, growing slightly, and uncovers what's under
 * it. It takes every touch until then.
 *
 * If [work] fails, the failure is logged and rethrown: the app fails as it would have without the screen.
 *
 * Half folded, it's laid out around the hinge from LocalFoldableState; it fills the window.
 *
 * @param onContentReady [work] is done and the logo holds still: compose the content to uncover now, under the screen.
 * @param onFinished the screen has faded away.
 */
@Composable
fun MaintenanceOverlay(
    work: suspend () -> Unit,
    onContentReady: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = rememberPlexusClock()
    var exiting by remember { mutableStateOf(false) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val exit by animateFloatAsState(
        targetValue = if (exiting) 1f else 0f,
        animationSpec = tween(EXIT_MILLIS),
        label = "maintenanceExit",
        finishedListener = { if (it == 1f) currentOnFinished() },
    )
    val currentWork by rememberUpdatedState(work)
    val currentOnContentReady by rememberUpdatedState(onContentReady)

    LaunchedEffect(clock) {
        try {
            currentWork()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Database maintenance failed", e)
            throw e
        }
        val doneAt = clock.seconds
        val animated = maintenanceExit(doneAt, still = false)
        val still = maintenanceExit(doneAt, still = true)
        val planned = if (clock.isStill) still else animated
        Log.i(TAG, "Work done at $doneAt s on the plexus clock; leaving at ${planned.exitAt} s")
        snapshotFlow { clock.seconds >= (if (clock.isStill) still else animated).contentAt }.first { it }
        currentOnContentReady()
        snapshotFlow { clock.seconds >= (if (clock.isStill) still else animated).exitAt }.first { it }
        Log.i(TAG, "Leaving at ${clock.seconds} s")
        exiting = true
    }

    DatabaseMaintenanceScreen(
        modifier = modifier
            .graphicsLayer {
                alpha = 1f - exit
                scaleX = 1f + (EXIT_SCALE - 1f) * exit
                scaleY = scaleX
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        clock = clock,
    )
}

/**
 * [content], after the maintenance screen while [migrate] migrates the database at startup. The content is composed
 * only once the migration is done, under the screen's held logo, and is uncovered as the screen fades away.
 *
 * @param lowPowerMode the app's low power setting: like battery saver, the screen then shows a still logo.
 */
@Composable
fun StartupMaintenanceGate(
    migrate: suspend () -> Unit,
    lowPowerMode: Flow<Boolean>,
    content: @Composable () -> Unit,
) {
    var contentShown by remember { mutableStateOf(false) }
    var maintenanceShown by remember { mutableStateOf(true) }
    val foldableState by rememberFoldableState()
    val batterySaver = rememberIsBatterySaverActive()
    // Read from disk: the screen doesn't wait for it, it turns still if the setting is on.
    val lowPower by lowPowerMode.collectAsStateWithLifecycle(initialValue = false)

    Box(modifier = Modifier.fillMaxSize().background(MellowTheme.colors.background)) {
        if (contentShown) content()
        if (maintenanceShown) {
            CompositionLocalProvider(
                LocalBatterySaverActive provides (batterySaver || lowPower),
                LocalFoldableState provides foldableState,
            ) {
                MaintenanceOverlay(
                    work = migrate,
                    onContentReady = { contentShown = true },
                    onFinished = { maintenanceShown = false },
                )
            }
        }
    }
}
