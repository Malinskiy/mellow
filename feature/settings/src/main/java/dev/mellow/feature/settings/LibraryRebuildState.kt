package dev.mellow.feature.settings

/** What the "Rebuild Library" row shows. */
internal enum class LibraryRebuildState {
    /** No rebuild pending; the row offers one. */
    Idle,

    /** A rebuild is pending and a sync is running: the rebuild, or the sync it waits for. */
    Running,

    /** A rebuild is pending and hasn't started yet. */
    Pending,

    /** A rebuild is pending and the last attempt failed. */
    Failed,
}

internal fun libraryRebuildState(
    isSyncing: Boolean,
    isRebuildPending: Boolean,
    isLastSyncFailed: Boolean,
): LibraryRebuildState = when {
    !isRebuildPending -> LibraryRebuildState.Idle
    isSyncing -> LibraryRebuildState.Running
    isLastSyncFailed -> LibraryRebuildState.Failed
    else -> LibraryRebuildState.Pending
}
