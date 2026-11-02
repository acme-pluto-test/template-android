package dev.pluto.template.webview

import dev.pluto.template.bridge.AuthRefreshRequest
import dev.pluto.template.bridge.AuthToken
import dev.pluto.template.bridge.Bridge
import dev.pluto.template.bridge.BridgeException
import dev.pluto.template.bridge.NativeToWebMessage
import dev.pluto.template.bridge.NavigationReady
import dev.pluto.template.bridge.SafeAreaChanged
import dev.pluto.template.bridge.ThemeChanged
import dev.pluto.template.bridge.ThemeChangedMode
import dev.pluto.template.domain.Token
import dev.pluto.template.domain.TokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where the host answers: the reply proxy of the WebView listener, or a fake in tests. */
fun interface PageChannel {
    fun post(text: String)
}

/**
 * The native side of the bridge. It parses what the page sends with the generated codec, answers `navigation.ready`
 * with the theme, the safe area and the token, answers `auth.refresh.request` with a new token, and ignores the rest.
 * Nothing here touches the Android framework, so the JVM tests prove it (door 4).
 */
class BridgeHost(
    private val policy: OriginPolicy,
    private val tokens: TokenProvider,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) {
    private var channel: PageChannel? = null
    private var theme: ThemeChanged? = null
    private var safeArea: SafeAreaChanged? = null

    /** A message the page posted. `sourceOrigin` is what the WebView reports as the sender's origin. */
    fun onPageMessage(
        text: String,
        sourceOrigin: String,
        reply: PageChannel,
    ) {
        if (!policy.allowsOrigin(sourceOrigin)) {
            log("ignored a message from an origin outside the allowlist")
            return
        }
        val message =
            try {
                Bridge.parseWebToNative(text)
            } catch (e: BridgeException) {
                log("ignored a message: ${e.message}")
                return
            }
        channel = reply
        when (message) {
            is NavigationReady -> answerReady()
            is AuthRefreshRequest -> scope.launch { refreshAndSend() }
        }
    }

    /** The system theme changed (or is known for the first time). Sent when it differs from the last one. */
    fun sendTheme(
        dark: Boolean,
        accentArgb: Int,
    ) {
        val next = ThemeChanged(if (dark) ThemeChangedMode.DARK else ThemeChangedMode.LIGHT, hex(accentArgb))
        if (next == theme) return
        theme = next
        send(next)
    }

    /** The window insets, in dp (CSS pixels). Sent when they differ from the last ones. */
    fun sendSafeArea(
        top: Double,
        right: Double,
        bottom: Double,
        left: Double,
    ) {
        val next = SafeAreaChanged(top, right, bottom, left)
        if (next == safeArea) return
        safeArea = next
        send(next)
    }

    // A login that fails (the network is down) must not crash the app: the page just gets no token and may ask again.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun refreshAndSend() {
        val token =
            try {
                tokens.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("token refresh failed: ${e.javaClass.simpleName}")
                null
            }
        token?.let { send(it.toMessage()) }
    }

    /** The window insets in pixels and the screen density; the page gets them in dp (CSS pixels). */
    fun sendInsetsPx(
        top: Int,
        right: Int,
        bottom: Int,
        left: Int,
        density: Float,
    ) {
        sendSafeArea(top / density.toDouble(), right / density.toDouble(), bottom / density.toDouble(), left / density.toDouble())
    }

    private fun answerReady() {
        theme?.let { send(it) }
        safeArea?.let { send(it) }
        tokens.current()?.let { send(it.toMessage()) }
    }

    private fun send(message: NativeToWebMessage) {
        channel?.post(Bridge.encode(message))
    }

    private fun Token.toMessage() = AuthToken(accessToken, expiresAt)

    private fun hex(argb: Int): String = "#%06X".format(argb and RGB_MASK)

    private companion object {
        const val RGB_MASK = 0xFFFFFF
    }
}
