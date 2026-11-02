package dev.pluto.template.domain

/** An access token and the second (epoch seconds) it expires at. */
class Token(
    val accessToken: String,
    val expiresAt: Long,
) {
    // keeps the credential out of logs and saved state (FR-MOB-01)
    override fun toString(): String = "Token(accessToken=<redacted>, expiresAt=$expiresAt)"
}

/**
 * Where the app gets the token it hands to the web app. The token lives in memory only: no preferences, no URL, no log.
 * The real login (FR-MOB-01) replaces the fake implementation in `data`.
 */
interface TokenProvider {
    /** The token held now, or null when signed out. */
    fun current(): Token?

    /** A new token, or null when none can be had (signed out, or the login refused). */
    suspend fun refresh(): Token?

    fun signIn()

    fun signOut()
}
