package dev.mellow.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.graphics.withTranslation
import org.robolectric.shadows.ShadowDialog
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
        show(content)
        composeTestRule.waitForIdle()
        snapshot(targetId)
    }

    /** Shows [content] the way [capture] does, for a test that interacts with it before taking a [snapshot]. */
    protected fun show(content: @Composable () -> Unit) {
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
    }

    /** Saves what is on screen now as [targetId]. */
    protected fun snapshot(targetId: String) {
        val rootView = composeTestRule.activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)
        drawTopmostDialog(canvas, rootView.width, rootView.height)

        FileOutputStream(File(snapshotDir, "$targetId.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * Compose `Dialog`s live in their own window, which [android.view.View.draw] on the activity's decor view never
     * reaches. Composite the newest showing dialog on top, centred like the system would place it.
     */
    private fun drawTopmostDialog(canvas: Canvas, screenWidth: Int, screenHeight: Int) {
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return
        val decor = dialog.window?.decorView ?: return
        if (decor.width == 0 || decor.height == 0) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST),
            )
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        }
        val left = (screenWidth - decor.width) / 2f
        val top = (screenHeight - decor.height) / 2f
        canvas.withTranslation(left, top) { decor.draw(this) }
    }
}
