package dev.pluto.template.debug

import dev.pluto.template.webview.WebOrigin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DevTokenProviderTest {
    private val asked = mutableListOf<String>()
    private var answer: () -> String = { """{"accessToken":"abc.def.ghi","expiresAt":1900000000}""" }
    private val scope = TestScope(StandardTestDispatcher())
    private val provider =
        DevTokenProvider(
            WebOrigin.config("https://localhost:8443", "https://localhost:8443/home"),
            { url ->
                asked += url
                answer()
            },
            scope,
        )

    @Test
    fun `asks the issuer on the web origin only after signing in`() =
        runTest {
            assertNull(provider.refresh())
            assertNull(provider.current())
            assertEquals(emptyList(), asked)

            provider.signIn()
            scope.advanceUntilIdle()
            assertEquals(listOf("https://localhost:8443/dev/token"), asked, "signing in asks once, in the background")
            val token = assertNotNull(provider.current())
            assertEquals("abc.def.ghi", token.accessToken)
            assertEquals(1_900_000_000L, token.expiresAt)

            answer = { """{"accessToken":"second","expiresAt":1900000100}""" }
            assertEquals("second", provider.refresh()?.accessToken)
            assertEquals("second", provider.current()?.accessToken)
        }

    @Test
    fun `answers null and keeps what it held when the issuer fails or answers badly`() =
        runTest {
            provider.signIn()
            scope.advanceUntilIdle()
            val before = provider.current()
            for (bad in listOf("not json", "{}", """{"accessToken":"","expiresAt":1}""", """{"accessToken":"x"}""", "[]")) {
                answer = { bad }
                assertNull(provider.refresh(), bad)
            }
            answer = { error("connection refused") }
            assertNull(provider.refresh())
            assertEquals(before?.accessToken, provider.current()?.accessToken)
        }

    @Test
    fun `forgets the token on sign out and never prints it`() =
        runTest {
            provider.signIn()
            scope.advanceUntilIdle()
            assertTrue(provider.current().toString().contains("<redacted>"))
            assertTrue(!provider.current().toString().contains("abc.def.ghi"))
            provider.signOut()
            assertNull(provider.current())
            assertNull(provider.refresh())
        }
}
