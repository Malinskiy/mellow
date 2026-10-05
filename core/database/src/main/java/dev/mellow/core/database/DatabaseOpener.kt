package dev.mellow.core.database

import dagger.Lazy
import javax.inject.Inject

/** Opens the database ahead of its first query, which runs its pending migrations. */
class DatabaseOpener @Inject constructor(
    private val database: Lazy<MellowDatabase>,
) {
    /**
     * Opens the database, migrating it if it's older than the app's schema. Blocks until done: call it off the main
     * thread. Anything else that opens the database meanwhile waits for it.
     */
    fun open() {
        val db = database.get().openHelper.writableDatabase
        // With write-ahead logging, a migration's commit may still sit in the log: move it into the file, so the
        // file's header has the new version when the next start reads it (see isMigrationPending).
        db.query("PRAGMA wal_checkpoint(PASSIVE)").use { it.moveToFirst() }
    }
}
