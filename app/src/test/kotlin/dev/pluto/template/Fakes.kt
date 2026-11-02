package dev.pluto.template

import dev.pluto.template.domain.Token
import dev.pluto.template.domain.TokenProvider
import dev.pluto.template.webview.PageChannel

class RecordingChannel : PageChannel {
    val posted = mutableListOf<String>()

    override fun post(text: String) {
        posted += text
    }
}

class FakeTokens(
    var token: Token? = null,
    var next: Token? = null,
    var failure: Exception? = null,
) : TokenProvider {
    var refreshes = 0

    override fun current(): Token? = token

    override suspend fun refresh(): Token? {
        refreshes += 1
        failure?.let { throw it }
        return next?.also { token = it }
    }

    override fun signIn() {
        token = Token("signed-in-token", 1_900_000_000L)
    }

    override fun signOut() {
        token = null
    }
}

const val APP_ORIGIN = "https://app.example.com"
const val READY = """{"version":1,"type":"navigation.ready","payload":{"route":"/"}}"""
