package dev.pluto.template.ui.web

import dev.pluto.template.APP_ORIGIN
import dev.pluto.template.FakeTokens
import dev.pluto.template.webview.Allowlist
import dev.pluto.template.webview.WebConfig
import org.junit.Test
import kotlin.test.assertEquals

class WebViewViewModelTest {
    private val vm = WebViewViewModel(FakeTokens(), WebConfig("$APP_ORIGIN/", Allowlist(listOf(APP_ORIGIN))))

    @Test
    fun `exposes one immutable state`() {
        val first = vm.uiState.value
        assertEquals(WebViewUiState("$APP_ORIGIN/", PageStatus.LOADING, 0), first)

        vm.onPageFinished()
        assertEquals(PageStatus.LOADED, vm.uiState.value.status)
        vm.onPageFinished()
        assertEquals(PageStatus.LOADED, vm.uiState.value.status)

        vm.onLoadFailed()
        assertEquals(PageStatus.FAILED, vm.uiState.value.status)
        vm.onPageFinished()
        assertEquals(PageStatus.FAILED, vm.uiState.value.status, "a failed page stays failed until the user retries")

        vm.onRetry()
        assertEquals(WebViewUiState("$APP_ORIGIN/", PageStatus.LOADING, 1), vm.uiState.value)
        assertEquals(WebViewUiState("$APP_ORIGIN/", PageStatus.LOADING, 0), first, "the first state is not changed")
    }
}
