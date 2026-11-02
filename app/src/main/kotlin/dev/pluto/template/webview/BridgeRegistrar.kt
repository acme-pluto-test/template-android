package dev.pluto.template.webview

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** The WebView side of the listener, behind an interface so the registration rule can be proven on the JVM. */
fun interface ListenerRegistry {
    fun add(
        name: String,
        allowedOriginRules: Set<String>,
        listener: (text: String, sourceOrigin: String, isMainFrame: Boolean, reply: PageChannel) -> Unit,
    )
}

/** Registers the bridge listener with the allowlist as its allowed origin rules, and nothing else. */
class BridgeRegistrar(
    private val registry: ListenerRegistry,
) {
    fun register(
        allowlist: Allowlist,
        host: BridgeHost,
    ) {
        require("*" !in allowlist.origins) { "the listener must not accept every origin" }
        registry.add(JS_OBJECT_NAME, allowlist.origins) { text, origin, isMainFrame, reply ->
            if (isMainFrame) host.onPageMessage(text, origin, reply)
        }
    }

    companion object {
        /** The name of the object the page sees: `window.pluto`. */
        const val JS_OBJECT_NAME = "pluto"
    }
}

/** The real registry: `WebViewCompat.addWebMessageListener`. Needs a device, so the reference scenario proves it. */
class WebViewListenerRegistry(
    private val webView: WebView,
) : ListenerRegistry {
    override fun add(
        name: String,
        allowedOriginRules: Set<String>,
        listener: (text: String, sourceOrigin: String, isMainFrame: Boolean, reply: PageChannel) -> Unit,
    ) {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) { "this WebView cannot host the bridge" }
        WebViewCompat.addWebMessageListener(webView, name, allowedOriginRules) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            listener(message.data.orEmpty(), sourceOrigin.toString(), isMainFrame) { replyProxy.postMessage(it) }
        }
    }
}
