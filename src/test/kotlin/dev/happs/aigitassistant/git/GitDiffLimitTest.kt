package dev.happs.aigitassistant.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitDiffLimitTest {
    @Test
    fun `default limit keeps prompt input compact`() {
        val limit = GitDiffLimit()

        val result = limit.apply("x".repeat(4_001))

        assertEquals(4_000 + "\n...[truncated 1 chars]".length, result.text.length)
        assertTrue(result.truncated)
        assertEquals(4_001, result.originalLength)
    }

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
