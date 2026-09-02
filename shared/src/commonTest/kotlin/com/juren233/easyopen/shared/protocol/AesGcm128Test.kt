package com.juren233.easyopen.shared.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class AesGcm128Test {
    @Test
    fun matchesNistEmptyPlaintextVector() {
        val key = hex("00000000000000000000000000000000")
        val nonce = hex("000000000000000000000000")
        val encrypted = AesGcm128.encrypt(key, nonce, ByteArray(0))

        assertContentEquals(hex("58e2fccefa7e3061367f1d57a4e7455a"), encrypted)
        assertContentEquals(ByteArray(0), AesGcm128.decrypt(key, nonce, encrypted))
    }

    @Test
    fun matchesNistSingleBlockVectorAndRejectsTampering() {
        val key = hex("00000000000000000000000000000000")
        val nonce = hex("000000000000000000000000")
        val plaintext = hex("00000000000000000000000000000000")
        val encrypted = AesGcm128.encrypt(key, nonce, plaintext)

        assertContentEquals(
            hex("0388dace60b6a392f328c2b971b2fe78ab6e47d42cec13bdf53a67b21257bddf"),
            encrypted,
        )
        assertContentEquals(plaintext, AesGcm128.decrypt(key, nonce, encrypted))
        assertNull(AesGcm128.decrypt(key, nonce, encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
