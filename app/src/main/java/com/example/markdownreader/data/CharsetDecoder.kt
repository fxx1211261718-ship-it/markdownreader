package com.example.markdownreader.data

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object CharsetDecoder {

    data class DecodedText(
        val text: String,
        val charsetName: String
    )

    private val utf8 = Charsets.UTF_8
    private val utf16Le = Charsets.UTF_16LE
    private val utf16Be = Charsets.UTF_16BE
    private val gb18030 = Charset.forName("GB18030")
    private val gbk = Charset.forName("GBK")

    fun decode(bytes: ByteArray): DecodedText? {
        if (bytes.isEmpty()) {
            return DecodedText(text = "", charsetName = utf8.displayName())
        }

        decodeBom(bytes)?.let { return it }

        val candidates = listOf(utf8, utf16Le, utf16Be, gb18030, gbk)
        val scored = candidates.mapNotNull { charset ->
            decodeStrict(bytes, charset)?.let { decoded ->
                Candidate(decoded = decoded, score = score(decoded.text, charset))
            }
        }

        return scored.minByOrNull { it.score }?.decoded
    }

    private fun decodeBom(bytes: ByteArray): DecodedText? {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return decodeStrict(bytes.copyOfRange(3, bytes.size), utf8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return decodeStrict(bytes.copyOfRange(2, bytes.size), utf16Le)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return decodeStrict(bytes.copyOfRange(2, bytes.size), utf16Be)
        }
        return null
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): DecodedText? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            val buffer: CharBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            DecodedText(
                text = normalizeNewlines(buffer.toString()),
                charsetName = charset.displayName()
            )
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun normalizeNewlines(value: String): String {
        return value.replace("\r\n", "\n").replace("\r", "\n")
    }

    private fun score(text: String, charset: Charset): Int {
        var score = 0
        score += text.count { it == '\uFFFD' } * 100
        score += text.count { it == '\u0000' } * 20
        score += text.count { it.isSuspiciousControl() } * 5
        if (charset == utf16Le || charset == utf16Be) {
            score += text.count { it == '\u0000' } * 50
        }
        return score
    }

    private fun Char.isSuspiciousControl(): Boolean {
        return this < ' ' && this != '\n' && this != '\t'
    }

    private data class Candidate(
        val decoded: DecodedText,
        val score: Int
    )
}
