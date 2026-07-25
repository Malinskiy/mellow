package dev.mellow.core.common

import java.time.Duration

fun formatTrackDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
