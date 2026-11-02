package dev.pluto.template.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import dev.pluto.template.ui.home.HomeScreen
import dev.pluto.template.ui.home.HomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun start(
        signedIn: Boolean = false,
        onSignIn: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        rule.setContent {
            PlutoNavHost(
                controller = rememberNavController(),
                home = { openWeb -> HomeScreen(HomeUiState(signedIn = signedIn), openWeb, onSignIn, onSignOut) },
                web = { Box(Modifier.testTag("web page")) },
            )
        }
    }

    @Test
    fun `shows the title and opens the web app`() {
        start()
        rule.onNodeWithText("Pluto").assertIsDisplayed()
        rule.onNodeWithText("Open web app").assertIsDisplayed()
        rule.onNodeWithText("Signed out").assertIsDisplayed()
        rule.onNodeWithText("Open web app").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("web page").assertExists()
    }

    @Test
    fun `returns to the home screen on back`() {
        start()
        rule.onNodeWithText("Open web app").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("web page").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Pluto").assertIsDisplayed()
    }

    @Test
    fun `the sign in button follows the state and calls the event`() {
        var signedIn = 0
        var signedOut = 0
        start(onSignIn = { signedIn++ }, onSignOut = { signedOut++ })
        rule.onNodeWithText("Sign in").performClick()
        kotlin.test.assertEquals(1, signedIn)
        kotlin.test.assertEquals(0, signedOut)
    }

    @Test
    fun `a signed in user can sign out`() {
        var signedOut = 0
        start(signedIn = true, onSignOut = { signedOut++ })
        rule.onNodeWithText("Signed in").assertIsDisplayed()
        rule.onNodeWithText("Sign out").performClick()
        kotlin.test.assertEquals(1, signedOut)
    }
}
