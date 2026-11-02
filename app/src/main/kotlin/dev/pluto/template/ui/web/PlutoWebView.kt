package dev.pluto.template.ui.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import dev.pluto.template.webview.WebViewFactory

/** The real page, drawn by the WebView the factory builds. It also feeds the theme and the safe area to the bridge. */
@Composable
fun PlutoWebView(
    viewModel: WebViewViewModel,
    url: String,
    reloads: Int,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val insets = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val top = insets.getTop(density)
    val bottom = insets.getBottom(density)
    val left = insets.getLeft(density, direction)
    val right = insets.getRight(density, direction)

    LaunchedEffect(dark, accent) { viewModel.host.sendTheme(dark, accent) }
    LaunchedEffect(top, right, bottom, left, density.density) {
        viewModel.host.sendInsetsPx(top, right, bottom, left, density.density)
    }

    AndroidView(
        modifier = modifier,
        factory = { context -> WebViewFactory.create(context, viewModel.allowlist, viewModel.host, viewModel) },
        update = { view ->
            if (view.tag != reloads) {
                view.tag = reloads
                view.loadUrl(url)
            }
        },
    )
}
