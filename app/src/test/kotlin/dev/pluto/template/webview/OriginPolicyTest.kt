package dev.pluto.template.webview

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginPolicyTest {
    private val policy = OriginPolicy(Allowlist(listOf("https://app.example.com")))

    @Test
    fun `allows an https origin of the allowlist`() {
        val allowed =
            listOf(
                "https://app.example.com",
                "https://app.example.com/a",
                "https://APP.example.com/x?y=1#z",
                "https://app.example.com:443/",
            )
        for (url in allowed) assertTrue(policy.allows(url), url)
        val withPort = OriginPolicy(Allowlist(listOf("https://app.example.com:8443")))
        assertTrue(withPort.allows("https://app.example.com:8443/path"))
        assertFalse(withPort.allows("https://app.example.com/path"))
        assertTrue(policy.allowsOrigin("https://app.example.com"))
        assertFalse(policy.allowsOrigin("https://evil.com"))
        assertFalse(policy.allowsOrigin("https://app.example.com/"), "an origin has no path, a trailing slash fails closed")
        assertFalse(policy.allowsOrigin("null"))
    }

    @Test
    fun `drops every URL outside the allowlist`() {
        val dropped =
            listOf(
                "http://app.example.com",
                "https://app.example.com.evil.com",
                "https://evil.com/?https://app.example.com",
                "https://evil.com#https://app.example.com",
                "https://app.example.com@evil.com/",
                "https://user:pass@app.example.com/",
                "https://app.example.com:8443",
                "https://sub.app.example.com",
                "file:///android_asset/index.html",
                "javascript:alert(1)",
                "intent://x#Intent;scheme=https;end",
                "data:text/html,hi",
                "",
                "   ",
                " https://app.example.com",
                "not a url",
            )
        for (url in dropped) assertFalse(policy.allows(url), "should drop '$url'")
        assertFalse(policy.allowsOrigin("*"))
        assertFalse(policy.allowsOrigin("https://app.example.com/path"))
    }

    @Test
    fun `rejects an allowlist entry that is not an exact https origin`() {
        val entries =
            listOf(
                "*",
                "https://*.example.com",
                "http://app.example.com",
                "https://app.example.com/path",
                "app.example.com",
                "https://",
                "",
            )
        for (entry in entries) {
            assertFailsWith<IllegalArgumentException>(entry) { Allowlist(listOf(entry)) }
        }
        assertFailsWith<IllegalArgumentException> { Allowlist(emptyList()) }
        val parsed = Allowlist.parse(" https://A.example.com , https://b.example.com:8443,")
        assertEquals(setOf("https://a.example.com", "https://b.example.com:8443"), parsed.origins)
        for (port in listOf("0", "65536", "99999")) {
            assertFailsWith<IllegalArgumentException>(port) { Allowlist(listOf("https://app.example.com:$port")) }
        }
        assertTrue(Allowlist(listOf("https://app.example.com:65535")).origins.isNotEmpty())
    }

    @Test
    fun `a start URL outside the allowlist is refused`() {
        assertFailsWith<IllegalArgumentException> { WebConfig("https://evil.com/", Allowlist(listOf("https://app.example.com"))) }
        WebConfig("https://app.example.com/", Allowlist(listOf("https://app.example.com")))
    }
}
