package com.juren233.easyopen.shared.protocol

/** URL-safe, unpadded Base64 used by the QR payload. */
internal object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val output = StringBuilder((input.size * 4 + 2) / 3)
        var index = 0
        while (index < input.size) {
            val first = input[index++].toInt() and 0xff
            val second = input.getOrNull(index++)?.toInt()?.and(0xff)
            val third = input.getOrNull(index++)?.toInt()?.and(0xff)
            output.append(ALPHABET[first ushr 2])
            output.append(ALPHABET[((first and 0x03) shl 4) or ((second ?: 0) ushr 4)])
            if (second != null) {
                output.append(ALPHABET[((second and 0x0f) shl 2) or ((third ?: 0) ushr 6)])
                if (third != null) output.append(ALPHABET[third and 0x3f])
            }
        }
        return output.toString()
    }

    fun decode(input: String): ByteArray? {
        if (input.isEmpty()) return ByteArray(0)
        if (input.any { it == '=' || it.isWhitespace() }) return null
        if (input.length % 4 == 1) return null

        val output = ByteArray(input.length * 3 / 4 + 2)
        var outputIndex = 0
        var bitBuffer = 0
        var bitCount = 0
        input.forEach { character ->
            val value = ALPHABET.indexOf(character)
            if (value < 0) return null
            bitBuffer = (bitBuffer shl 6) or value
            bitCount += 6
            if (bitCount >= 8) {
                bitCount -= 8
                output[outputIndex++] = (bitBuffer ushr bitCount).toByte()
                bitBuffer = bitBuffer and ((1 shl bitCount) - 1)
            }
        }
        return output.copyOf(outputIndex)
    }
}
