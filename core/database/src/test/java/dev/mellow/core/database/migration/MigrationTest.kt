package dev.mellow.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import dev.mellow.core.database.MellowDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [MigrationChecks] under Robolectric. MigrationDeviceTest runs them on a device's own SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MellowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val checks = MigrationChecks(helper)

    @Test
    fun `11 to 12 keeps the library and adds the sync pass table`() =
        checks.migration11To12KeepsTheLibraryAndAddsTheSyncPassTable()

    @Test
    fun `12 to 13 keeps rebuilt tables and their children, and sorts names ignoring case`() =
        checks.migration12To13KeepsRebuiltTablesAndTheirChildrenAndSortsNamesIgnoringCase()

    @Test
    fun `12 to 13 keeps every cascading child even with foreign keys on`() =
        checks.migration12To13KeepsEveryCascadingChildEvenWithForeignKeysOn()
}
