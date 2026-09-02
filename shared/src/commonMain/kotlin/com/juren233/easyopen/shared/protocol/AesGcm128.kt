package com.juren233.easyopen.shared.protocol

/**
 * Small AES-128-GCM implementation for the portable QR envelope.
 *
 * This is deliberately limited to the parameters used by [EasyOpenQrCodec]:
 * a 16-byte key, a 12-byte nonce, no AAD and a 16-byte authentication tag.
 * The opener wire protocol continues to use [Aes128] ECB separately.
 */
internal object AesGcm128 {
    private const val BLOCK_BYTES = 16
    private const val TAG_BYTES = 16

    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == BLOCK_BYTES) { "AES-128-GCM requires a 16-byte key" }
        require(nonce.size == 12) { "AES-GCM requires a 12-byte nonce" }

        val hashSubkey = aesBlock(key, ByteArray(BLOCK_BYTES))
        val initialCounter = initialCounter(nonce)
        val ciphertext = gctr(key, increment32(initialCounter), plaintext)
        val tag = gctr(
            key = key,
            initialCounter = initialCounter,
            input = ghash(hashSubkey, ciphertext),
        )
        return ciphertext + tag.copyOf(TAG_BYTES)
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, encrypted: ByteArray): ByteArray? {
        require(key.size == BLOCK_BYTES) { "AES-128-GCM requires a 16-byte key" }
        require(nonce.size == 12) { "AES-GCM requires a 12-byte nonce" }
        if (encrypted.size < TAG_BYTES) return null

        val ciphertext = encrypted.copyOf(encrypted.size - TAG_BYTES)
        val suppliedTag = encrypted.copyOfRange(encrypted.size - TAG_BYTES, encrypted.size)
        val hashSubkey = aesBlock(key, ByteArray(BLOCK_BYTES))
        val initialCounter = initialCounter(nonce)
        val expectedTag = gctr(
            key = key,
            initialCounter = initialCounter,
            input = ghash(hashSubkey, ciphertext),
        ).copyOf(TAG_BYTES)
        if (!constantTimeEquals(suppliedTag, expectedTag)) return null
        return gctr(key, increment32(initialCounter), ciphertext)
    }

    private fun aesBlock(key: ByteArray, input: ByteArray): ByteArray =
        Aes128.encryptEcbNoPadding(key, input)

    private fun initialCounter(nonce: ByteArray): ByteArray =
        nonce + byteArrayOf(0, 0, 0, 1)

    private fun gctr(key: ByteArray, initialCounter: ByteArray, input: ByteArray): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        var counter = initialCounter
        val output = ByteArray(input.size)
        var offset = 0
        while (offset < input.size) {
            val stream = aesBlock(key, counter)
            val blockLength = minOf(BLOCK_BYTES, input.size - offset)
            repeat(blockLength) { index ->
                output[offset + index] = (input[offset + index].toInt() xor stream[index].toInt()).toByte()
            }
            offset += blockLength
            if (offset < input.size) counter = increment32(counter)
        }
        return output
    }

    private fun ghash(hashSubkey: ByteArray, ciphertext: ByteArray): ByteArray {
        var accumulator = ByteArray(BLOCK_BYTES)
        processBlocks(ciphertext) { block ->
            accumulator = multiply(accumulator xor block, hashSubkey)
        }
        val lengths = ByteArray(BLOCK_BYTES).also { output ->
            writeUnsignedLong(output, 8, ciphertext.size.toLong() * 8L)
        }
        return multiply(accumulator xor lengths, hashSubkey)
    }

    private inline fun processBlocks(input: ByteArray, consume: (ByteArray) -> Unit) {
        var offset = 0
        while (offset < input.size) {
            val length = minOf(BLOCK_BYTES, input.size - offset)
            val block = ByteArray(BLOCK_BYTES)
            input.copyInto(block, endIndex = offset + length, destinationOffset = 0, startIndex = offset)
            consume(block)
            offset += length
        }
    }

    /** GF(2^128) multiplication as defined by GHASH, using big-endian bits. */
    private fun multiply(left: ByteArray, right: ByteArray): ByteArray {
        var accumulator = ByteArray(BLOCK_BYTES)
        var value = right.copyOf()
        repeat(128) { bitIndex ->
            val bit = (left[bitIndex / 8].toInt() ushr (7 - (bitIndex % 8))) and 1
            if (bit == 1) accumulator = accumulator xor value
            val leastSignificantBit = value[15].toInt() and 1
            for (index in 15 downTo 1) {
                value[index] = (
                    ((value[index].toInt() and 0xff) ushr 1) or
                        ((value[index - 1].toInt() and 1) shl 7)
                    ).toByte()
            }
            value[0] = ((value[0].toInt() and 0xff) ushr 1).toByte()
            if (leastSignificantBit != 0) {
                value[0] = (value[0].toInt() xor 0xe1).toByte()
            }
        }
        return accumulator
    }

    private fun increment32(counter: ByteArray): ByteArray = counter.copyOf().also { output ->
        for (index in 15 downTo 12) {
            output[index] = (output[index] + 1).toByte()
            if (output[index].toInt() and 0xff != 0) break
        }
    }

    private fun writeUnsignedLong(output: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            output[offset + index] = (value ushr (56 - index * 8)).toByte()
        }
    }

    private infix fun ByteArray.xor(other: ByteArray): ByteArray {
        require(size == other.size)
        return ByteArray(size) { index -> (this[index].toInt() xor other[index].toInt()).toByte() }
    }

    private fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        if (left.size != right.size) return false
        var difference = 0
        left.indices.forEach { index -> difference = difference or (left[index].toInt() xor right[index].toInt()) }
        return difference == 0
    }
}
