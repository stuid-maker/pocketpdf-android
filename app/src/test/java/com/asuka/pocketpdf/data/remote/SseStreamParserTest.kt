package com.asuka.pocketpdf.data.remote

import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖 [SseStreamParser] 的 SSE/NDJSON 协议解析。
 *
 * 用 [Buffer] 模拟 OkHttp 的 BufferedSource，避免依赖真实网络。
 */
class SseStreamParserTest {

    private val moshi = Moshi.Builder().build()
    private val parser = SseStreamParser(moshi)

    @Test
    fun `parses single data line as content token`() = runTest {
        val source = Buffer().writeUtf8(
            """data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Hello"}}]}

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Hello"), tokens)
    }

    @Test
    fun `parses multiple chunks in sequence`() = runTest {
        val source = Buffer().writeUtf8(
            """
            data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Hello"}}]}

            data: {"id":"2","object":"chat.completion.chunk","created":2,"model":"g","choices":[{"index":0,"delta":{"content":" world"}}]}

            data: {"id":"3","object":"chat.completion.chunk","created":3,"model":"g","choices":[{"index":0,"delta":{"content":"!"}}]}

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Hello", " world", "!"), tokens)
    }

    @Test
    fun `skips empty lines and comment lines`() = runTest {
        val source = Buffer().writeUtf8(
            """
            : this is a comment

            data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Hello"}}]}

            : another comment

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Hello"), tokens)
    }

    @Test
    fun `stops on DONE marker`() = runTest {
        val source = Buffer().writeUtf8(
            """
            data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Hello"}}]}

            data: [DONE]

            data: {"id":"2","object":"chat.completion.chunk","created":2,"model":"g","choices":[{"index":0,"delta":{"content":"ShouldNotAppear"}}]}

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Hello"), tokens)
    }

    @Test
    fun `silently skips malformed JSON lines`() = runTest {
        val source = Buffer().writeUtf8(
            """
            data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Good"}}]}

            data: {{{broken json}}}

            data: {"id":"2","object":"chat.completion.chunk","created":2,"model":"g","choices":[{"index":0,"delta":{"content":"StillGood"}}]}

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Good", "StillGood"), tokens)
    }

    @Test
    fun `skips chunks with null or empty content`() = runTest {
        val source = Buffer().writeUtf8(
            """
            data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Real"}}]}

            data: {"id":"2","object":"chat.completion.chunk","created":2,"model":"g","choices":[{"index":0,"delta":{"content":null}}]}

            data: {"id":"3","object":"chat.completion.chunk","created":3,"model":"g","choices":[{"index":0,"delta":{"content":""}}]}

            data: {"id":"4","object":"chat.completion.chunk","created":4,"model":"g","choices":[{"index":0,"delta":{"content":"Token"}}]}

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Real", "Token"), tokens)
    }

    @Test
    fun `returns empty list for empty source`() = runTest {
        val source = Buffer() // empty
        val tokens = parser.parse(source).toList()
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `handles DONE without space after data colon`() = runTest {
        val source = Buffer().writeUtf8(
            """
            data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Hi"}}]}

            data:[DONE]

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Hi"), tokens)
    }

    @Test
    fun `skips lines without data prefix`() = runTest {
        val source = Buffer().writeUtf8(
            """
            event: message
            id: 42
            data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"g","choices":[{"index":0,"delta":{"content":"Valid"}}]}

        """.trimIndent()
        )

        val tokens = parser.parse(source).toList()
        assertEquals(listOf("Valid"), tokens)
    }
}
