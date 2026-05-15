package org.example

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun `main prints greeting and indexed lines`() {
        val originalOut = System.out
        val output = ByteArrayOutputStream()

        try {
            System.setOut(PrintStream(output))
            main()
        } finally {
            System.setOut(originalOut)
        }

        assertEquals(
            expectedOutput(),
            output.toString(StandardCharsets.UTF_8),
        )
    }

    private fun expectedOutput(): String =
        """
        Hello, Kotlin!
        i = 1
        i = 2
        i = 3
        i = 4
        i = 5

        """.trimIndent()
}
