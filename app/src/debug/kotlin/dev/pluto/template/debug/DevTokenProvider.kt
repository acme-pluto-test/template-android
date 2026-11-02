package dev.pluto.template.debug

import android.util.Log
import dev.pluto.template.domain.Token
import dev.pluto.template.domain.TokenProvider
import dev.pluto.template.webview.WebConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject

/** Asks the development issuer for a token: the body of the answer, or an exception. */
fun interface TokenFetch {
    suspend fun fetch(url: String): String
}

/** A POST over https with short timeouts and a small answer; nothing about the call is logged. */
class HttpsTokenFetch : TokenFetch {
    // the caller may be on the main thread: the call itself never is
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) { request(url) }

    private fun request(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            // Android's HttpURLConnection refuses a POST that never opens its body; the issuer ignores it
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(0)
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.outputStream.close()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "the issuer answered ${connection.responseCode}" }
            return connection.inputStream.use { input ->
                val buffer = ByteArray(MAX_BYTES)
                var size = 0
                while (size < MAX_BYTES) {
                    val read = input.read(buffer, size, MAX_BYTES - size)
                    if (read < 0) break
                    size += read
                }
                buffer.decodeToString(0, size)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val MAX_BYTES = 4_096
    }
}

/**
 * Debug builds only (a module in src/debug binds it, src/release binds the fake login instead). Signing in asks the
 * issuer on the web origin's `/dev/token` for a token; the token lives in memory and is never logged.
 */
class DevTokenProvider
    @Inject
    constructor(
        config: WebConfig,
        private val fetcher: TokenFetch,
        private val scope: CoroutineScope,
    ) : TokenProvider {
        private val endpoint = URI(config.startUrl).let { "${it.scheme}://${it.authority}$TOKEN_PATH" }

        @Volatile private var token: Token? = null

        @Volatile private var signedIn = false

        override fun current(): Token? = if (signedIn) token else null

        override suspend fun refresh(): Token? = if (signedIn) request()?.also { token = it } else null

        override fun signIn() {
            signedIn = true
            scope.launch { refresh() }
        }

        override fun signOut() {
            signedIn = false
            token = null
        }

        private suspend fun request(): Token? =
            try {
                val body = Json.parseToJsonElement(fetcher.fetch(endpoint)).jsonObject
                parse(body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // the kind of failure only: never the message, which could carry the address of the call
                Log.w(TAG, "token request failed: ${failure.javaClass.simpleName}")
                null
            }

        private fun parse(body: JsonObject): Token? {
            val accessToken = body["accessToken"]?.jsonPrimitive?.content
            val expiresAt = body["expiresAt"]?.jsonPrimitive?.long
            return if (accessToken.isNullOrEmpty() || expiresAt == null) null else Token(accessToken, expiresAt)
        }

        companion object {
            const val TOKEN_PATH = "/dev/token"
            private const val TAG = "PlutoDev"
        }
    }
