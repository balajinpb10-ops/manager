package com.vaultra.app.util

object Validators {

    /** Master password: at least 8 chars, at least one letter and one number recommended. */
    fun masterPasswordError(pw: String): String? = when {
        pw.length < 8 -> "Use at least 8 characters"
        !pw.any { it.isDigit() } -> "Add at least one number for a stronger password"
        else -> null
    }

    fun confirmMismatch(pw: String, confirm: String): String? =
        if (pw != confirm) "Passwords don't match" else null

    /** Entry name is the only strictly required field. */
    fun entryNameError(name: String): String? =
        if (name.isBlank()) "Name is required" else null

    /** URL is optional but if present should look like a URL. */
    fun urlError(url: String): String? {
        if (url.isBlank()) return null
        val looksValid = Regex("^https?://[^\\s]+\\.[^\\s]+").containsMatchIn(url) ||
            Regex("^[\\w.-]+\\.[a-zA-Z]{2,}").containsMatchIn(url)
        return if (!looksValid) "That doesn't look like a valid URL" else null
    }

    /** TOTP secrets are Base32: A-Z and 2-7, optionally with spaces. Optional field. */
    fun totpSecretError(secret: String): String? {
        if (secret.isBlank()) return null
        val cleaned = secret.replace(" ", "").uppercase()
        if (cleaned.length < 8) return "2FA secret looks too short"
        if (!Regex("^[A-Z2-7]+=*$").matches(cleaned)) return "2FA secret should only contain A-Z and 2-7"
        return null
    }

    /** Soft validation — warns but never blocks saving. */
    fun usernameLooksLikeEmailButIsnt(username: String): Boolean {
        if (username.isBlank()) return false
        val hasAt = username.contains("@")
        val looksEmail = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(username)
        return hasAt && !looksEmail
    }
}
