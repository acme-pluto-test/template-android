package dev.pluto.template.ui.web

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pluto.template.domain.TokenProvider
import dev.pluto.template.webview.BridgeHost
import dev.pluto.template.webview.OriginPolicy
import dev.pluto.template.webview.PageListener
import dev.pluto.template.webview.WebConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class PageStatus { LOADING, LOADED, FAILED }

/** Everything the WebView screen shows; `reloads` changes to make the page load again. */
data class WebViewUiState(
    val url: String,
    val status: PageStatus = PageStatus.LOADING,
    val reloads: Int = 0,
)

@HiltViewModel
class WebViewViewModel
    @Inject
    constructor(
        tokens: TokenProvider,
        config: WebConfig,
    ) : ViewModel(),
        PageListener {
        val policy = OriginPolicy(config.allowlist)
        val allowlist = config.allowlist
        val host = BridgeHost(policy, tokens, viewModelScope) { Log.d(TAG, it) }

        private val state = MutableStateFlow(WebViewUiState(config.startUrl))
        val uiState: StateFlow<WebViewUiState> = state.asStateFlow()

        override fun onPageFinished() {
            if (state.value.status == PageStatus.LOADING) state.value = state.value.copy(status = PageStatus.LOADED)
        }

        override fun onLoadFailed() {
            state.value = state.value.copy(status = PageStatus.FAILED)
        }

        fun onRetry() {
            state.value = state.value.copy(status = PageStatus.LOADING, reloads = state.value.reloads + 1)
        }
    }

private const val TAG = "PlutoBridge"
