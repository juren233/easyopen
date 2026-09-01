package com.juren233.easyopen.shared.protocol

internal object Md5 {
    private val shifts = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val constants = intArrayOf(
        0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(), 0xf57c0faf.toInt(), 0x4787c62a,
        0xa8304613.toInt(), 0xfd469501.toInt(), 0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
        0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821, 0xf61e2562.toInt(), 0xc040b340.toInt(),
        0x265e5a51, 0xe9b6c7aa.toInt(), 0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
        0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed, 0xa9e3e905.toInt(), 0xfcefa3f8.toInt(),
        0x676f02d9, 0x8d2a4c8a.toInt(), 0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
        0xa4beea44.toInt(), 0x4bdecfa9.toInt(), 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(), 0x289b7ec6, 0xeaa127fa.toInt(),
        0xd4ef3085.toInt(), 0x04881d05, 0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
        0xf4292244.toInt(), 0x432aff97, 0xab9423a7.toInt(), 0xfc93a039.toInt(), 0x655b59c3, 0x8f0ccc92.toInt(),
        0xffeff47d.toInt(), 0x85845dd1.toInt(), 0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
        0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt(),
    )

    fun digest(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8L
        val paddedSize = ((input.size + 9 + 63) / 64) * 64
        val message = ByteArray(paddedSize)
        input.copyInto(message)
        message[input.size] = 0x80.toByte()
        for (i in 0 until 8) message[paddedSize - 8 + i] = (bitLength ushr (8 * i)).toByte()

        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val words = IntArray(16)

        for (offset in message.indices step 64) {
            for (i in 0 until 16) {
                val index = offset + i * 4
                words[i] = (message[index].toInt() and 0xff) or
                    ((message[index + 1].toInt() and 0xff) shl 8) or
                    ((message[index + 2].toInt() and 0xff) shl 16) or
                    ((message[index + 3].toInt() and 0xff) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                val (function, index) = when (i) {
                    in 0..15 -> ((b and c) or (b.inv() and d)) to i
                    in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * i + 1) % 16)
                    in 32..47 -> (b xor c xor d) to ((3 * i + 5) % 16)
                    else -> (c xor (b or d.inv())) to ((7 * i) % 16)
                }
                val next = d
                d = c
                c = b
                b += rotateLeft(a + function + constants[i] + words[index], shifts[i])
                a = next
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
        }

        val result = ByteArray(16)
        writeLittleEndian(result, 0, a0)
        writeLittleEndian(result, 4, b0)
        writeLittleEndian(result, 8, c0)
        writeLittleEndian(result, 12, d0)
        return result
    }

    private fun rotateLeft(value: Int, distance: Int): Int =
        (value shl distance) or (value ushr (32 - distance))

    private fun writeLittleEndian(output: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) output[offset + i] = (value ushr (8 * i)).toByte()
    }
}
