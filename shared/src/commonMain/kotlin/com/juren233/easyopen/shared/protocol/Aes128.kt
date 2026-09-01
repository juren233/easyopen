package com.juren233.easyopen.shared.protocol

/**
 * Minimal AES-128 ECB implementation for the opener's legacy wire protocol.
 *
 * This is compatibility code, not a general-purpose cryptography API. The
 * protocol explicitly uses AES/ECB/NoPadding and pads its UTF-8 command to a
 * whole block with zero bytes, so the implementation is intentionally limited
 * to that mode and key size.
 */
internal object Aes128 {
    private val sBox = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
    )

    private val roundConstants = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36)

    fun encryptEcbNoPadding(key: ByteArray, input: ByteArray): ByteArray {
        require(key.size == 16) { "AES-128 requires a 16-byte key" }
        require(input.size % 16 == 0) { "AES/ECB/NoPadding input must be block aligned" }
        val expandedKey = expandKey(key)
        val output = ByteArray(input.size)
        var offset = 0
        while (offset < input.size) {
            val state = input.copyOfRange(offset, offset + 16)
            addRoundKey(state, expandedKey, 0)
            for (round in 1 until 10) {
                subBytes(state)
                shiftRows(state)
                mixColumns(state)
                addRoundKey(state, expandedKey, round)
            }
            subBytes(state)
            shiftRows(state)
            addRoundKey(state, expandedKey, 10)
            state.copyInto(output, destinationOffset = offset)
            offset += 16
        }
        return output
    }

    private fun expandKey(key: ByteArray): ByteArray {
        val expanded = ByteArray(176)
        key.copyInto(expanded)
        var bytesGenerated = 16
        var round = 0
        val temp = ByteArray(4)
        while (bytesGenerated < expanded.size) {
            for (i in 0 until 4) temp[i] = expanded[bytesGenerated - 4 + i]
            if (bytesGenerated % 16 == 0) {
                val first = temp[0]
                temp[0] = temp[1]
                temp[1] = temp[2]
                temp[2] = temp[3]
                temp[3] = first
                for (i in 0 until 4) temp[i] = sBox[temp[i].toInt() and 0xff].toByte()
                temp[0] = (temp[0].toInt() xor roundConstants[round++]).toByte()
            }
            for (i in 0 until 4) {
                expanded[bytesGenerated] = (expanded[bytesGenerated - 16].toInt() xor temp[i].toInt()).toByte()
                bytesGenerated++
            }
        }
        return expanded
    }

    private fun addRoundKey(state: ByteArray, expandedKey: ByteArray, round: Int) {
        val offset = round * 16
        for (i in 0 until 16) {
            state[i] = (state[i].toInt() xor expandedKey[offset + i].toInt()).toByte()
        }
    }

    private fun subBytes(state: ByteArray) {
        for (i in state.indices) state[i] = sBox[state[i].toInt() and 0xff].toByte()
    }

    private fun shiftRows(state: ByteArray) {
        val source = state.copyOf()
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                state[column * 4 + row] = source[((column + row) % 4) * 4 + row]
            }
        }
    }

    private fun mixColumns(state: ByteArray) {
        for (column in 0 until 4) {
            val offset = column * 4
            val a0 = state[offset].toInt() and 0xff
            val a1 = state[offset + 1].toInt() and 0xff
            val a2 = state[offset + 2].toInt() and 0xff
            val a3 = state[offset + 3].toInt() and 0xff
            state[offset] = (galoisMultiply(a0, 2) xor galoisMultiply(a1, 3) xor a2 xor a3).toByte()
            state[offset + 1] = (a0 xor galoisMultiply(a1, 2) xor galoisMultiply(a2, 3) xor a3).toByte()
            state[offset + 2] = (a0 xor a1 xor galoisMultiply(a2, 2) xor galoisMultiply(a3, 3)).toByte()
            state[offset + 3] = (galoisMultiply(a0, 3) xor a1 xor a2 xor galoisMultiply(a3, 2)).toByte()
        }
    }

    private fun galoisMultiply(left: Int, right: Int): Int {
        var a = left
        var b = right
        var result = 0
        repeat(8) {
            if ((b and 1) != 0) result = result xor a
            a = if ((a and 0x80) != 0) (a shl 1) xor 0x11b else a shl 1
            b = b ushr 1
        }
        return result and 0xff
    }
}
