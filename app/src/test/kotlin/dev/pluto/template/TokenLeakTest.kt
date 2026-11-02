package dev.pluto.template

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.pluto.template.bridge.AuthToken
import dev.pluto.template.data.InMemoryTokenProvider
import dev.pluto.template.domain.Token
import dev.pluto.template.ui.home.HomeUiState
import dev.pluto.template.ui.home.HomeViewModel
import dev.pluto.template.ui.web.WebViewUiState
import dev.pluto.template.ui.web.WebViewViewModel
import dev.pluto.template.webview.Allowlist
import dev.pluto.template.webview.BridgeHost
import dev.pluto.template.webview.OriginPolicy
import dev.pluto.template.webview.WebConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenLeakTest {
    private val secret = "super-secret-token-value"

    @Test
    fun `keeps the token out of URLs, logs, preferences and saved state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokens = InMemoryTokenProvider()
        tokens.signIn()
        val real = assertNotNull(tokens.current()).accessToken
        val logs = mutableListOf<String>()
        val channel = RecordingChannel()
        val config = WebConfig("$APP_ORIGIN/", Allowlist(listOf(APP_ORIGIN)))
        val host = BridgeHost(OriginPolicy(config.allowlist), tokens, TestScope(UnconfinedTestDispatcher()), logs::add)

        host.onPageMessage(READY, APP_ORIGIN, channel)
        host.onPageMessage("""{"version":1,"type":"auth.refresh.request","payload":{}}""", APP_ORIGIN, channel)
        host.onPageMessage("garbage with $real inside", APP_ORIGIN, channel)
        host.onPageMessage(READY, "https://evil.com", channel)
        assertTrue(channel.posted.any { "demo-token" in it }, "the token goes out through the bridge, and only there")

        // no URL the WebView screen loads
        val webVm = WebViewViewModel(tokens, config)
        webVm.host.onPageMessage(READY, APP_ORIGIN, channel)
        webVm.host.onPageMessage("garbage with $real inside", APP_ORIGIN, channel)
        webVm.host.onPageMessage(READY, "https://evil.com", channel)
        val webState = webVm.uiState.value
        assertFalse(real in webState.url)
        // no log line
        assertTrue(logs.none { real in it }, "logs: $logs")
        val androidLogs = ShadowLog.getLogs().map { it.msg.orEmpty() }
        assertTrue(androidLogs.any { "ignored a message" in it } && androidLogs.none { real in it }, "android.util.Log: $androidLogs")
        // no state a screen holds or could save
        assertFalse(real in HomeViewModel(tokens).uiState.value.toString())
        assertFalse(real in webState.toString())
        // a state that could be saved implicitly (Bundle, Parcel) would carry it away: none of them can be
        for (type in listOf(Token::class.java, HomeUiState::class.java, WebViewUiState::class.java)) {
            assertFalse(java.io.Serializable::class.java.isAssignableFrom(type), "${type.simpleName} is Serializable")
            assertFalse(android.os.Parcelable::class.java.isAssignableFrom(type), "${type.simpleName} is Parcelable")
        }
        assertFalse(real in webState.url, "the URL the screen loads stays the configured one")
        // no preferences or file the app wrote
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs")
        assertTrue(!prefs.exists() || prefs.listFiles().orEmpty().isEmpty(), "shared_prefs must stay empty")
        // no toString of a token or of a message that carries one
        assertFalse(real in Token(real, 1L).toString())
        assertFalse(real in AuthToken(real, 1L).toString())
        assertFalse(secret in AuthToken(secret, 1L).toString())
    }

    @Test
    fun `forgets the token when the process is recreated`() {
        val before = InMemoryTokenProvider()
        before.signIn()
        assertNotNull(before.current())
        val after = InMemoryTokenProvider() // a new process builds a new provider
        assertNull(after.current())
        assertEquals(false, HomeViewModel(after).uiState.value.signedIn)
    }
}
