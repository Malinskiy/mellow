package dev.mellow.app.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.search.SearchContent

/**
 * Opening Search leaves the field unfocused, so the keyboard doesn't cover recent searches or the genre grid. Tapping
 * the Search tab again (a focus request) focuses the field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class SearchFieldFocusTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val focusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun searchField() = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun openingSearchLeavesTheFieldUnfocused() {
        showSearch()

        searchField().assertIsNotFocused()
    }

    @Test
    fun aFocusRequestFocusesTheField() {
        showSearch()

        focusRequests.tryEmit(Unit)
        composeTestRule.waitForIdle()

        searchField().assertIsFocused()
    }

    private fun showSearch() {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                SearchContent(recentSearches = listOf("radiohead"), focusRequests = focusRequests)
            }
        }
        composeTestRule.waitForIdle()
    }
}
