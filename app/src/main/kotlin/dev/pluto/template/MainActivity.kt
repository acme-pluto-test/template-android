package dev.pluto.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.pluto.template.ui.PlutoNavHost
import dev.pluto.template.ui.home.HomeScreen
import dev.pluto.template.ui.home.HomeViewModel
import dev.pluto.template.ui.theme.PlutoTheme
import dev.pluto.template.ui.web.PlutoWebView
import dev.pluto.template.ui.web.WebViewScreen
import dev.pluto.template.ui.web.WebViewViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlutoTheme {
                PlutoNavHost(
                    controller = rememberNavController(),
                    home = { openWeb ->
                        val vm: HomeViewModel = hiltViewModel()
                        HomeScreen(vm.uiState.collectAsStateWithLifecycle().value, openWeb, vm::onSignIn, vm::onSignOut)
                    },
                    web = {
                        val vm: WebViewViewModel = hiltViewModel()
                        WebViewScreen(vm.uiState.collectAsStateWithLifecycle().value, vm::onRetry) { url, reloads ->
                            PlutoWebView(vm, url, reloads)
                        }
                    },
                )
            }
        }
    }
}
