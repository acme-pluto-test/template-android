package dev.pluto.template.webview

import java.net.URI
import java.util.Locale

/**
 * The exact https origins the WebView may load and talk to. An entry is `https://host` or `https://host:port`:
 * no path, no wildcard, no other scheme (door 4).
 */
class Allowlist(
    entries: Collection<String>,
) {
    val origins: Set<String> = entries.map { normalise(it) ?: throw IllegalArgumentException("not an exact https origin: $it") }.toSet()

    init {
        require(origins.isNotEmpty()) { "the allowlist is empty" }
    }

    companion object {
        /** Parses the comma separated list that BuildConfig holds. */
        fun parse(list: String): Allowlist = Allowlist(list.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }
}

/** Decides every navigation: allowed only when the URL's origin equals an entry of the allowlist. */
class OriginPolicy(
    private val allowlist: Allowlist,
) {
    fun allows(url: String): Boolean {
        val origin = originOf(url) ?: return false
        return origin in allowlist.origins
    }

    /** True when `origin` (as a WebView reports it) is one of the allowed origins. */
    fun allowsOrigin(origin: String): Boolean = normalise(origin) in allowlist.origins
}

private const val HTTPS_PORT = 443
private const val MAX_PORT = 65_535
private val ORIGIN = Regex("^https://[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]{1,5})?$")

/** The origin of an https URL as `https://host` or `https://host:port`, or null for anything else. */
internal fun originOf(url: String): String? {
    val uri = if (url.isBlank() || url != url.trim()) null else runCatching { URI(url) }.getOrNull()
    val host = uri?.host
    val plainHttps = uri != null && uri.scheme?.lowercase(Locale.ROOT) == "https" && uri.rawUserInfo == null
    return if (host == null || !plainHttps) {
        null
    } else {
        val port = uri.port
        "https://" + host.lowercase(Locale.ROOT) + if (port == -1 || port == HTTPS_PORT) "" else ":$port"
    }
}

/** An exact origin (no path, no query) normalised, or null when it is not one. */
internal fun normalise(origin: String): String? {
    val lower = origin.lowercase(Locale.ROOT)
    val port = lower.substringAfterLast(':', "").takeIf { lower.substringAfter("://").contains(':') }?.toIntOrNull()
    if (!ORIGIN.matches(lower) || (lower.substringAfter("://").contains(':') && port !in 1..MAX_PORT)) return null
    return originOf(lower)
}

/** What the WebView screen needs to know about the web app: where it starts and which origins it may use. */
class WebConfig(
    val startUrl: String,
    val allowlist: Allowlist,
) {
    init {
        require(OriginPolicy(allowlist).allows(startUrl)) { "the start URL must be inside the allowlist" }
    }
}
