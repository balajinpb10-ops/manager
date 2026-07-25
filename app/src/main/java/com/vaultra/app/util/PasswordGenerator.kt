package com.vaultra.app.util

import java.security.SecureRandom
import kotlin.math.log2

object PasswordGenerator {
    private val random = SecureRandom()

    data class Options(
        val length: Int = 16,
        val lower: Boolean = true,
        val upper: Boolean = true,
        val numbers: Boolean = true,
        val symbols: Boolean = true
    )

    data class Strength(val label: String, val percent: Int, val colorHex: Long)

    fun generate(options: Options): String {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = lower.uppercase()
        val nums = "0123456789"
        val syms = "!@#\$%^&*()_-+=?"
        var pool = ""
        if (options.lower) pool += lower
        if (options.upper) pool += upper
        if (options.numbers) pool += nums
        if (options.symbols) pool += syms
        if (pool.isEmpty()) pool = lower
        return (1..options.length).map { pool[random.nextInt(pool.length)] }.joinToString("")
    }

    fun strengthOf(password: String): Strength {
        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 20
        val entropy = if (poolSize == 0) 0.0 else log2(poolSize.toDouble()) * password.length
        return when {
            entropy < 40 -> Strength("Weak", 28, 0xFFE0223AL)
            entropy < 65 -> Strength("Fair", 55, 0xFFF5A623L)
            entropy < 90 -> Strength("Strong", 80, 0xFF2ECC71L)
            else -> Strength("Excellent", 100, 0xFF2ECC71L)
        }
    }
}
