package dev.pluto.template.webview

import dev.pluto.template.BuildConfig
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebOriginConfigTest {
    @Test
    fun `uses the origin given to the build`() {
        val config = WebOrigin.config("https://localhost:8443", "https://localhost:8443/")
        assertEquals(setOf("https://localhost:8443"), config.allowlist.origins)
        assertEquals("https://localhost:8443/", config.startUrl)
        val policy = OriginPolicy(config.allowlist)
        assertTrue(policy.allows("https://localhost:8443/anything"))
        assertFalse(policy.allows("https://app.example.com/"), "the placeholder is not allowed once another origin is given")
        assertFalse(policy.allows("https://localhost/"), "the port is part of the origin")
        assertFalse(policy.allows("http://localhost:8443/"), "https only")

        for ((origin, start) in listOf(
            "https://a.test,https://b.test" to "https://a.test/",
            "*" to "https://a.test/",
            "https://*.test" to "https://a.test/",
            "http://a.test" to "http://a.test/",
            "https://a.test/path" to "https://a.test/path",
            "https://a.test" to "https://evil.test/",
            "https://a.test" to "https://a.test.evil.test/",
        )) {
            assertFailsWith<IllegalArgumentException>("$origin $start") { WebOrigin.config(origin, start) }
        }
    }

    @Test
    fun `keeps the placeholder without the properties`() {
        assertEquals("https://app.example.com", WebOrigin.PLACEHOLDER_ORIGIN)
        assertEquals("https://app.example.com/", WebOrigin.PLACEHOLDER_START_URL)
        // The build always produces a valid pair; the placeholder pair is the default when no property was given.
        val built = WebOrigin.config(BuildConfig.WEB_ALLOWLIST, BuildConfig.WEB_START_URL)
        assertEquals(setOf(BuildConfig.WEB_ALLOWLIST), built.allowlist.origins)
        val given = System.getProperty("plutoWebOrigin")
        if (given == null) {
            assertEquals(WebOrigin.PLACEHOLDER_ORIGIN, BuildConfig.WEB_ALLOWLIST)
            assertEquals(WebOrigin.PLACEHOLDER_START_URL, BuildConfig.WEB_START_URL)
        } else {
            assertEquals(given, BuildConfig.WEB_ALLOWLIST)
        }
    }
}
