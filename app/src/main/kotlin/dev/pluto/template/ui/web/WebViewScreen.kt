package dev.pluto.template.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val PAGE_TAG = "page"

/** The WebView screen. `page` draws the web page itself; it is a parameter so the JVM tests can stand in for it. */
@Composable
fun WebViewScreen(
    state: WebViewUiState,
    onRetry: () -> Unit,
    page: @Composable (url: String, reloads: Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().testTag(PAGE_TAG)) {
        page(state.url, state.reloads)
        if (state.status == PageStatus.FAILED) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("The page could not be loaded")
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}
