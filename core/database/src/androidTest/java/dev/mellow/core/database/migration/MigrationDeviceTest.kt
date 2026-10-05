package dev.mellow.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mellow.core.database.MellowDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MigrationChecks] on the device's own SQLite (see QueryPlanDeviceTest for when and how to run it). 12 to 13 rebuilds
 * tables with ALTER TABLE ... RENAME TO, whose rules changed in SQLite 3.25 and 3.26, after Android 8's 3.18.
 */
@RunWith(AndroidJUnit4::class)
class MigrationDeviceTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MellowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val checks = MigrationChecks(helper)

    @Test
    fun migration11To12KeepsTheLibraryAndAddsTheSyncPassTable() =
        checks.migration11To12KeepsTheLibraryAndAddsTheSyncPassTable()

    @Test
    fun migration12To13KeepsRebuiltTablesAndTheirChildrenAndSortsNamesIgnoringCase() =
        checks.migration12To13KeepsRebuiltTablesAndTheirChildrenAndSortsNamesIgnoringCase()

    @Test
    fun migration12To13KeepsEveryCascadingChildEvenWithForeignKeysOn() =
        checks.migration12To13KeepsEveryCascadingChildEvenWithForeignKeysOn()
}
