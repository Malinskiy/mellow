package dev.mellow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.mellow.app.maintenance.StartupMaintenanceGate
import dev.mellow.app.maintenance.StartupMigration
import dev.mellow.app.navigation.MellowNavHost
import dev.mellow.app.ui.theme.MellowTheme
import dev.mellow.core.data.preferences.DisplayPreferences
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var startupMigration: StartupMigration

    @Inject
    lateinit var displayPreferences: DisplayPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Before anything opens the database: if opening it will migrate it, the maintenance screen covers that, and
        // the app (its view model, its first queries) starts only once it's done.
        val migrationPending = startupMigration.isPending()
        setContent {
            MellowTheme {
                if (migrationPending) {
                    StartupMaintenanceGate(
                        migrate = startupMigration::run,
                        lowPowerMode = displayPreferences.lowPowerMode,
                    ) {
                        MellowNavHost()
                    }
                } else {
                    MellowNavHost()
                }
            }
        }
    }
}
