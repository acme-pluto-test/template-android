package dev.pluto.template.ui.home

import dev.pluto.template.FakeTokens
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest {
    @Test
    fun `exposes one immutable state`() {
        val tokens = FakeTokens()
        val vm = HomeViewModel(tokens)
        val first = vm.uiState.value
        assertEquals(HomeUiState(title = "Pluto", signedIn = false), first)

        vm.onSignIn()
        assertTrue(vm.uiState.value.signedIn)
        assertNotNull(tokens.current())
        assertFalse(first.signedIn, "the earlier state is not changed, a new one replaces it")

        vm.onSignOut()
        assertFalse(vm.uiState.value.signedIn)
        assertNull(tokens.current())
        assertTrue(
            HomeViewModel(
                FakeTokens(
                    token =
                        dev.pluto.template.domain
                            .Token("t", 1L),
                ),
            ).uiState.value.signedIn,
        )
    }
}
