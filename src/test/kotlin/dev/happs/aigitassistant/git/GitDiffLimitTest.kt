package dev.happs.aigitassistant.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitDiffLimitTest {
    @Test
    fun `returns original diff when within limit`() {
        val limit = GitDiffLimit(maxPrefixCharacters = 10)

        val result = limit.apply("abc")

        assertEquals("abc", result.text)
        assertFalse(result.truncated)
        assertEquals(3, result.originalLength)
    }

    @Test
    fun `truncates diff with deterministic suffix`() {
        val limit = GitDiffLimit(maxPrefixCharacters = 4)

        val result = limit.apply("abcdef")

        assertEquals("abcd\n...[truncated 2 chars]", result.text)
        assertTrue(result.truncated)
        assertEquals(6, result.originalLength)
    }
}
