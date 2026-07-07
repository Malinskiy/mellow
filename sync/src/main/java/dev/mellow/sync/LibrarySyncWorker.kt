package dev.mellow.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // TODO: Sync library metadata from Jellyfin server to Room database
        // 1. Fetch albums, artists, tracks from Jellyfin API (paginated)
        // 2. Upsert into Room database
        // 3. Sync favorites and play counts bidirectionally
        return Result.success()
    }
}
