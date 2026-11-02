package dev.pluto.template.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pluto.template.domain.TokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Everything the home screen shows; immutable, replaced as a whole. */
data class HomeUiState(
    val title: String = "Pluto",
    val signedIn: Boolean = false,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val tokens: TokenProvider,
    ) : ViewModel() {
        private val state = MutableStateFlow(HomeUiState(signedIn = tokens.current() != null))
        val uiState: StateFlow<HomeUiState> = state.asStateFlow()

        fun onSignIn() {
            tokens.signIn()
            state.value = state.value.copy(signedIn = true)
        }

        fun onSignOut() {
            tokens.signOut()
            state.value = state.value.copy(signedIn = false)
        }
    }
