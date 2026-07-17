package dev.mellow.core.designsystem.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val LOW_BATTERY_THRESHOLD = 15

@Composable
fun rememberIsBatterySaverActive(): Boolean {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isPowerSaver by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    var isBatteryLow by remember {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        mutableStateOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) <= LOW_BATTERY_THRESHOLD)
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        isPowerSaver = powerManager.isPowerSaveMode
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        val pct = if (scale > 0) (level * 100) / scale else 100
                        isBatteryLow = pct <= LOW_BATTERY_THRESHOLD
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return isPowerSaver || isBatteryLow
}
