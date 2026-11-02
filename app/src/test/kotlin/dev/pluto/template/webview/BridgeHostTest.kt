package dev.pluto.template.webview

import dev.pluto.template.APP_ORIGIN
import dev.pluto.template.FakeTokens
import dev.pluto.template.READY
import dev.pluto.template.RecordingChannel
import dev.pluto.template.bridge.AuthToken
import dev.pluto.template.bridge.Bridge
import dev.pluto.template.bridge.NativeToWebMessage
import dev.pluto.template.bridge.SafeAreaChanged
import dev.pluto.template.bridge.ThemeChanged
import dev.pluto.template.bridge.ThemeChangedMode
import dev.pluto.template.domain.Token
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeHostTest {
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val tokens = FakeTokens()
    private val logs = mutableListOf<String>()
    private val host = BridgeHost(OriginPolicy(Allowlist(listOf(APP_ORIGIN))), tokens, scope, logs::add)
    private val channel = RecordingChannel()

    private fun ready(origin: String = APP_ORIGIN) = host.onPageMessage(READY, origin, channel)

    private fun sent(): List<NativeToWebMessage> = channel.posted.map { Bridge.parseNativeToWeb(it) }

    @Test
    fun `sends dark theme with the accent colour`() {
        ready()
        host.sendTheme(dark = true, accentArgb = 0xFF0A84FF.toInt())
        assertEquals(listOf<NativeToWebMessage>(ThemeChanged(ThemeChangedMode.DARK, "#0A84FF")), sent())
    }

    @Test
    fun `sends light theme`() {
        ready()
        host.sendTheme(dark = false, accentArgb = 0xFF0B57D0.toInt())
        host.sendTheme(dark = false, accentArgb = 0xFF0B57D0.toInt())
        assertEquals(listOf<NativeToWebMessage>(ThemeChanged(ThemeChangedMode.LIGHT, "#0B57D0")), sent())
    }

    @Test
    fun `sends the safe area and again when it changes`() {
        ready()
        host.sendSafeArea(top = 47.0, right = 0.0, bottom = 34.0, left = 0.0)
        host.sendSafeArea(top = 47.0, right = 0.0, bottom = 34.0, left = 0.0)
        host.sendSafeArea(top = 24.0, right = 0.0, bottom = 0.0, left = 0.0)
        assertEquals(listOf<NativeToWebMessage>(SafeAreaChanged(47.0, 0.0, 34.0, 0.0), SafeAreaChanged(24.0, 0.0, 0.0, 0.0)), sent())
    }

    @Test
    fun `answers navigation ready with theme, safe area and token`() {
        tokens.token = Token("secret-token-1", 1_900_000_000L)
        host.sendTheme(dark = true, accentArgb = 0xFF0A84FF.toInt())
        host.sendSafeArea(47.0, 0.0, 34.0, 0.0)
        assertTrue(channel.posted.isEmpty(), "nothing is sent before the page is ready")
        ready()
        assertEquals(
            listOf<NativeToWebMessage>(
                ThemeChanged(ThemeChangedMode.DARK, "#0A84FF"),
                SafeAreaChanged(47.0, 0.0, 34.0, 0.0),
                AuthToken("secret-token-1", 1_900_000_000L),
            ),
            sent(),
        )
    }

    @Test
    fun `answers navigation ready without a token when there is none`() {
        host.sendTheme(dark = false, accentArgb = 0xFF0B57D0.toInt())
        ready()
        assertEquals(listOf<NativeToWebMessage>(ThemeChanged(ThemeChangedMode.LIGHT, "#0B57D0")), sent())
    }

    @Test
    fun `answers auth refresh request with the new token`() {
        ready()
        tokens.next = Token("secret-token-2", 1_900_000_100L)
        host.onPageMessage("""{"version":1,"type":"auth.refresh.request","payload":{"reason":"expired"}}""", APP_ORIGIN, channel)
        assertEquals(1, tokens.refreshes)
        assertEquals(listOf<NativeToWebMessage>(AuthToken("secret-token-2", 1_900_000_100L)), sent())
    }

    @Test
    fun `sends nothing when refresh returns no token`() {
        ready()
        host.onPageMessage("""{"version":1,"type":"auth.refresh.request","payload":{}}""", APP_ORIGIN, channel)
        assertEquals(1, tokens.refreshes)
        assertTrue(channel.posted.isEmpty())
    }

    @Test
    fun `ignores invalid, oversized and foreign-origin messages`() {
        tokens.token = Token("secret-token-3", 1_900_000_000L)
        host.sendTheme(dark = true, accentArgb = 0xFF0A84FF.toInt())
        ready()
        channel.posted.clear()
        val refreshesBefore = tokens.refreshes

        val bad =
            mapOf(
                "an auth.token sent by the page" to """{"version":1,"type":"auth.token","payload":{"accessToken":"x","expiresAt":1}}""",
                "65537 bytes" to """{"version":1,"type":"navigation.ready","payload":{"route":"/${"a".repeat(65_537)}"}}""",
                "not JSON" to "this is not json",
                "an unknown field" to """{"version":1,"type":"navigation.ready","payload":{"route":"/","extra":1}}""",
                "version 2" to """{"version":2,"type":"navigation.ready","payload":{"route":"/"}}""",
            )
        for ((what, text) in bad) {
            host.onPageMessage(text, APP_ORIGIN, channel)
            assertTrue(channel.posted.isEmpty(), "$what must send nothing")
        }
        for (origin in listOf("https://evil.com", "http://app.example.com", "https://app.example.com.evil.com", "null", "")) {
            host.onPageMessage(READY, origin, channel)
            host.onPageMessage("""{"version":1,"type":"auth.refresh.request","payload":{}}""", origin, channel)
            assertTrue(channel.posted.isEmpty(), "a message from '$origin' must send nothing")
        }
        assertEquals(refreshesBefore, tokens.refreshes, "a refused message must not ask for a token")
        assertTrue(logs.isNotEmpty() && logs.none { "secret-token-3" in it }, "refusals are logged without the token")
    }

    @Test
    fun `the main frame only registrar forwards nothing from a subframe`() {
        val registered = mutableListOf<(String, String, Boolean, dev.pluto.template.webview.PageChannel) -> Unit>()
        BridgeRegistrar { _, _, listener -> registered += listener }.register(Allowlist(listOf(APP_ORIGIN)), host)
        registered.single()(READY, APP_ORIGIN, false, channel)
        assertTrue(channel.posted.isEmpty() && logs.isEmpty(), "a message from a subframe is not even looked at")
        host.sendTheme(dark = true, accentArgb = 0xFF0A84FF.toInt())
        registered.single()(READY, APP_ORIGIN, true, channel)
        assertEquals(1, channel.posted.size)
    }

    @Test
    fun `survives a refresh that throws`() {
        ready()
        tokens.failure = java.io.IOException("network is down, token secret-token-9")
        host.onPageMessage("""{"version":1,"type":"auth.refresh.request","payload":{}}""", APP_ORIGIN, channel)
        assertEquals(1, tokens.refreshes)
        assertTrue(channel.posted.isEmpty(), "the page gets no token")
        assertTrue(
            logs.any { "token refresh failed" in it } && logs.none { "secret-token-9" in it },
            "the failure is logged without its message",
        )
        tokens.failure = null
        tokens.next = Token("secret-token-10", 1_900_000_200L)
        host.onPageMessage("""{"version":1,"type":"auth.refresh.request","payload":{}}""", APP_ORIGIN, channel)
        val again = listOf<NativeToWebMessage>(AuthToken("secret-token-10", 1_900_000_200L))
        assertEquals(again, sent(), "the page may ask again")
    }

    @Test
    fun `sends insets given in pixels as dp`() {
        ready()
        host.sendInsetsPx(top = 94, right = 0, bottom = 68, left = 12, density = 2f)
        assertEquals(listOf<NativeToWebMessage>(SafeAreaChanged(47.0, 0.0, 34.0, 6.0)), sent())
        host.sendInsetsPx(top = 94, right = 0, bottom = 68, left = 12, density = 2f)
        assertEquals(1, channel.posted.size, "the same insets are not sent twice")
    }

    @Test
    fun `accepts a message of exactly 65536 bytes and ignores one more byte`() {
        host.sendTheme(dark = true, accentArgb = 0xFF0A84FF.toInt())

        fun message(bytes: Int): String {
            val frame = """{"version":1,"type":"navigation.ready","payload":{"route":""}}"""
            return frame.replace("\"route\":\"\"", "\"route\":\"${"a".repeat(bytes - frame.length)}\"")
        }
        assertEquals(65_536, message(65_536).toByteArray().size)
        host.onPageMessage(message(65_536), APP_ORIGIN, channel)
        assertEquals(1, channel.posted.size, "the largest allowed message is answered")
        channel.posted.clear()
        host.onPageMessage(message(65_537), APP_ORIGIN, channel)
        assertTrue(channel.posted.isEmpty(), "one byte more is ignored")
        host.onPageMessage(message(65_536).replaceFirst("a", "é"), APP_ORIGIN, channel)
        assertTrue(channel.posted.isEmpty(), "bytes are counted, not characters")
    }
}
