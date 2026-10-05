package dev.mellow.app.maintenance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.database.DatabaseOpener
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.isMigrationPending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "StartupMigration"

/** The database's migration at startup, which the maintenance screen covers. */
class StartupMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opener: DatabaseOpener,
) {
    /**
     * Whether opening the database will migrate it, for any schema change: the version in the database file's header
     * is older than the app's. Reads a few bytes; ask before anything opens the database.
     */
    fun isPending(): Boolean = isMigrationPending(context.getDatabasePath(MellowDatabase.NAME))

    /** Opens the database, which migrates it; anything else that opens it meanwhile waits for that. */
    suspend fun run() {
        withContext(Dispatchers.IO) {
            val started = SystemClock.elapsedRealtime()
            opener.open()
            Log.i(TAG, "Database opened and migrated in ${SystemClock.elapsedRealtime() - started} ms")
        }
    }
}
