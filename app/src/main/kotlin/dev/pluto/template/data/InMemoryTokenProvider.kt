package dev.pluto.template.data

import dev.pluto.template.domain.Token
import dev.pluto.template.domain.TokenProvider
import javax.inject.Inject
import javax.inject.Singleton

/** A fake login for the template: signing in makes a token that expires in an hour. Held in memory only. */
@Singleton
class InMemoryTokenProvider
    @Inject
    constructor() : TokenProvider {
        private var token: Token? = null
        private var counter = 0

        override fun current(): Token? = token

        override suspend fun refresh(): Token? = if (token == null) null else newToken().also { token = it }

        override fun signIn() {
            token = newToken()
        }

        override fun signOut() {
            token = null
        }

        private fun newToken(): Token {
            counter += 1
            return Token("demo-token-$counter", System.currentTimeMillis() / MILLIS + HOUR_SECONDS)
        }

        private companion object {
            const val MILLIS = 1000L
            const val HOUR_SECONDS = 3600L
        }
    }
