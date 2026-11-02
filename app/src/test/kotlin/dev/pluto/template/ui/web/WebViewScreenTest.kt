package dev.pluto.template.ui.web

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pluto.template.APP_ORIGIN
import dev.pluto.template.FakeTokens
import dev.pluto.template.webview.Allowlist
import dev.pluto.template.webview.WebConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebViewScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `shows the error and loads again on Try again`() {
        val vm = WebViewViewModel(FakeTokens(), WebConfig("$APP_ORIGIN/", Allowlist(listOf(APP_ORIGIN))))
        rule.setContent {
            WebViewScreen(vm.uiState.collectAsState().value, vm::onRetry) { url, reloads ->
                Box(Modifier.testTag("page $reloads $url"))
            }
        }
        rule.onNodeWithTag("page 0 $APP_ORIGIN/").assertExists()
        rule.onNodeWithText("The page could not be loaded").assertDoesNotExist()

        rule.runOnUiThread { vm.onLoadFailed() }
        rule.waitForIdle()
        rule.onNodeWithText("The page could not be loaded").assertIsDisplayed()

        rule.onNodeWithText("Try again").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("The page could not be loaded").assertDoesNotExist()
        rule.onNodeWithTag("page 1 $APP_ORIGIN/").assertExists()
        assertEquals(PageStatus.LOADING, vm.uiState.value.status)
    }
}
