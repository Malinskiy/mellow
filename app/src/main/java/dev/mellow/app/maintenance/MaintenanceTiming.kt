package dev.mellow.app.maintenance

import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOGO_HOLD_END_SECONDS
import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOGO_HOLD_START_SECONDS
import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOOP_SECONDS
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How long the formed logo holds, at least, before the maintenance screen leaves (as long as the hold lasts). */
internal const val MIN_LOGO_HOLD_SECONDS = 1f

/** With a still logo (battery saver, animations off) the maintenance screen stays at least this long. */
internal const val MIN_STILL_SECONDS = 1.5f

/** The maintenance screen fades out, uncovering what's under it, over this long. */
internal const val EXIT_MILLIS = 400

/** How much the plexus grows while it fades out. */
internal const val EXIT_SCALE = 1.04f

/**
 * When the maintenance screen starts to leave, in seconds on the plexus clock, for work that finished at [doneAt]: on
 * the first logo hold — [holdStart] to [holdEnd] of every [loop] — that hasn't ended by [doneAt], once the logo has
 * held [minHold] (counted from [doneAt] if the work finished during the hold), and at the latest when the hold ends.
 */
internal fun exitTime(doneAt: Float, holdStart: Float, holdEnd: Float, loop: Float, minHold: Float): Float {
    val loops = if (doneAt <= holdEnd) 0f else ceil((doneAt - holdEnd) / loop)
    val start = holdStart + loops * loop
    val end = holdEnd + loops * loop
    return min(max(doneAt, start) + minHold, end)
}

/**
 * When the maintenance screen does what, in seconds on the plexus clock.
 *
 * @property contentAt the work is done and the logo holds still: the content to uncover can be composed under it.
 * @property exitAt the screen starts to fade out.
 */
internal data class MaintenanceExit(val contentAt: Float, val exitAt: Float)

/**
 * The maintenance screen's exit for work that finished at [doneAt]: on the plexus's logo hold (see [exitTime]), or,
 * with a [still] logo, as soon as the work is done but not before [MIN_STILL_SECONDS].
 */
internal fun maintenanceExit(doneAt: Float, still: Boolean): MaintenanceExit {
    if (still) return MaintenanceExit(contentAt = doneAt, exitAt = max(doneAt, MIN_STILL_SECONDS))
    val exitAt = exitTime(
        doneAt = doneAt,
        holdStart = PLEXUS_LOGO_HOLD_START_SECONDS,
        holdEnd = PLEXUS_LOGO_HOLD_END_SECONDS,
        loop = PLEXUS_LOOP_SECONDS,
        minHold = MIN_LOGO_HOLD_SECONDS,
    )
    val holdStart = floor(exitAt / PLEXUS_LOOP_SECONDS) * PLEXUS_LOOP_SECONDS + PLEXUS_LOGO_HOLD_START_SECONDS
    return MaintenanceExit(contentAt = max(doneAt, holdStart), exitAt = exitAt)
}
