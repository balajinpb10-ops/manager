package com.vaultra.app.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Decode(input: String): ByteArray {
        val clean = input.trim().uppercase().replace("=", "").replace(" ", "")
        val bits = StringBuilder()
        for (c in clean) {
            val idx = ALPHABET.indexOf(c)
            if (idx < 0) continue
            bits.append(idx.toString(2).padStart(5, '0'))
        }
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i + 8 <= bits.length) {
            bytes.add(bits.substring(i, i + 8).toInt(2).toByte())
            i += 8
        }
        return bytes.toByteArray()
    }

    /** Returns null if the secret is blank or invalid. */
    fun currentCode(secretBase32: String, period: Long = 30, digits: Int = 6): String? {
        if (secretBase32.isBlank()) return null
        return try {
            val keyBytes = base32Decode(secretBase32)
            if (keyBytes.isEmpty()) return null
            val counter = System.currentTimeMillis() / 1000 / period
            val counterBytes = ByteArray(8)
            var value = counter
            for (i in 7 downTo 0) {
                counterBytes[i] = (value and 0xff).toByte()
                value = value shr 8
            }
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
            val hash = mac.doFinal(counterBytes)
            val offset = hash[hash.size - 1].toInt() and 0xf
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
            val code = binary % Math.pow(10.0, digits.toDouble()).toInt()
            code.toString().padStart(digits, '0')
        } catch (e: Exception) {
            null
        }
    }

    /** Seconds remaining until the current code rotates — useful for a countdown ring. */
    fun secondsRemaining(period: Long = 30): Long {
        val now = System.currentTimeMillis() / 1000
        return period - (now % period)
    }
}
