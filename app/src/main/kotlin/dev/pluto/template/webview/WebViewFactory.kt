package dev.pluto.template.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.pluto.template.BuildConfig

/** What the page tells the screen. The ViewModel implements it, so this package knows nothing about the UI. */
interface PageListener {
    fun onPageFinished()

    fun onLoadFailed()
}

/**
 * The only place a WebView is created (door 4): JavaScript on, file and content access off, every navigation decided by the
 * origin policy, and the bridge listener registered with the allowlist. A device proves it (see the plan).
 */
object WebViewFactory {
    @SuppressLint("SetJavaScriptEnabled") // the web app is the product; only allowlisted https origins can ever run in it
    fun create(
        context: Context,
        allowlist: Allowlist,
        host: BridgeHost,
        listener: PageListener,
    ): WebView =
        WebView(context).apply {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = PolicyClient(OriginPolicy(allowlist), listener)
            BridgeRegistrar(WebViewListenerRegistry(this)).register(allowlist, host)
        }
}

private class PolicyClient(
    private val policy: OriginPolicy,
    private val listener: PageListener,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = !policy.allows(request.url.toString())

    override fun onPageStarted(
        view: WebView,
        url: String?,
        favicon: Bitmap?,
    ) = Unit

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) = listener.onPageFinished()

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) listener.onLoadFailed()
    }

    // A certificate the device does not trust ends the load and shows the failed state; there is no way to continue past it.
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        listener.onLoadFailed()
    }
}
