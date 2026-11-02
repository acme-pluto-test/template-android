package dev.pluto.template.webview

/** The web app the build points at: one exact https origin and a start URL inside it (door 2 of reference-scenario). */
object WebOrigin {
    const val PLACEHOLDER_ORIGIN = "https://app.example.com"
    const val PLACEHOLDER_START_URL = "https://app.example.com/"

    /** The configuration for what the build was given; the allowlist holds exactly that origin and nothing else. */
    fun config(
        origin: String,
        startUrl: String,
    ): WebConfig {
        require(!origin.contains(',')) { "the build takes one origin, got a list" }
        return WebConfig(startUrl, Allowlist(listOf(origin)))
    }
}
