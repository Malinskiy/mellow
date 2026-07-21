package dev.mellow.app.screenshot

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mellow.core.designsystem.theme.FoldableState
import dev.mellow.core.designsystem.theme.LocalFoldableState
import dev.mellow.core.designsystem.theme.LocalWindowWidthClass
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream

abstract class ScreenshotCapture {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    abstract val deviceFolder: String
    abstract val windowWidthClass: WindowWidthClass
    open val foldableState: FoldableState = FoldableState()

    private val snapshotDir: File by lazy {
        var dir = File(System.getProperty("user.dir")!!)
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        File(dir, ".marathon-snapshots/current/android/$deviceFolder").also { it.mkdirs() }
    }

    protected fun capture(targetId: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalWindowWidthClass provides windowWidthClass,
                LocalFoldableState provides foldableState,
            ) {
                MellowTheme(darkTheme = true) {
                    content()
                }
            }
        }

        composeTestRule.waitForIdle()

        val rootView = composeTestRule.activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).also { rootView.draw(it) }

        FileOutputStream(File(snapshotDir, "$targetId.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
