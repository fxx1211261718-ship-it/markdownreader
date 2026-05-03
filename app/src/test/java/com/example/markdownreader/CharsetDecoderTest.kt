package com.example.markdownreader

import com.example.markdownreader.data.CharsetDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class CharsetDecoderTest {

    @Test
    fun decodesUtf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "# 标题\nHello".toByteArray(Charsets.UTF_8)

        val result = CharsetDecoder.decode(bytes)

        assertEquals("UTF-8", result?.charsetName)
        assertEquals("# 标题\nHello", result?.text)
    }

    @Test
    fun decodesGb18030ChineseText() {
        val bytes = "中文内容\n第二行".toByteArray(Charset.forName("GB18030"))

        val result = CharsetDecoder.decode(bytes)

        assertEquals("GB18030", result?.charsetName)
        assertEquals("中文内容\n第二行", result?.text)
    }

    @Test
    fun normalizesNewlines() {
        val bytes = "line1\r\nline2\rline3".toByteArray(Charsets.UTF_8)

        val result = CharsetDecoder.decode(bytes)

        assertEquals("line1\nline2\nline3", result?.text)
    }

    @Test
    fun returnsEmptyStringForEmptyBytes() {
        val result = CharsetDecoder.decode(byteArrayOf())

        assertTrue(result != null)
        assertEquals("", result?.text)
    }
}
