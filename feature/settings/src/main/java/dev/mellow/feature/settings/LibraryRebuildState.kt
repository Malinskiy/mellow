package dev.mellow.feature.settings

import dev.mellow.core.data.SyncProgress

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

/**
 * The progress the "Last Synced" row shows, or null for none. During a rebuild the "Rebuild Library" row shows it, so
 * it isn't repeated here.
 */
internal fun lastSyncedProgress(
    isSyncing: Boolean,
    syncProgress: SyncProgress?,
    rebuildState: LibraryRebuildState,
): SyncProgress? = syncProgress?.takeIf { isSyncing && it.total > 0 && rebuildState != LibraryRebuildState.Running }
