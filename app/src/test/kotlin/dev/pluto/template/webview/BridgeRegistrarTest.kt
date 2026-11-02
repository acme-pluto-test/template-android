package dev.pluto.template.webview

import dev.pluto.template.APP_ORIGIN
import dev.pluto.template.FakeTokens
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BridgeRegistrarTest {
    @Test
    fun `registers the allowlist as the only allowed origins`() {
        val allowlist = Allowlist(listOf(APP_ORIGIN, "https://admin.example.com:8443"))
        val registered = mutableListOf<Pair<String, Set<String>>>()
        val registrar = BridgeRegistrar { name, rules, _ -> registered += name to rules }
        registrar.register(allowlist, BridgeHost(OriginPolicy(allowlist), FakeTokens(), TestScope()))

        assertEquals(1, registered.size)
        assertEquals("pluto", registered.single().first)
        assertEquals(setOf(APP_ORIGIN, "https://admin.example.com:8443"), registered.single().second)
        assertFalse("*" in registered.single().second)
        assertEquals(BridgeRegistrar.JS_OBJECT_NAME, registered.single().first)
    }
}
