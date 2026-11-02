package dev.pluto.template.data

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryTokenProviderTest {
    @Test
    fun `holds a token only after sign in and hands out a new one on refresh`() =
        runTest {
            val tokens = InMemoryTokenProvider()
            assertNull(tokens.current())
            assertNull(tokens.refresh())
            tokens.signIn()
            val first = assertNotNull(tokens.current())
            val second = assertNotNull(tokens.refresh())
            assertNotEquals(first.accessToken, second.accessToken)
            assertEquals(second, tokens.current())
            tokens.signOut()
            assertNull(tokens.current())
            assertEquals("Token(accessToken=<redacted>, expiresAt=${second.expiresAt})", second.toString())
        }
}
