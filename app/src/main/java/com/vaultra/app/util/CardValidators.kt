package com.vaultra.app.util

object CardValidators {

    /** Detects the card network from its number prefix, for display only. */
    fun detectNetwork(cardNumber: String): String {
        val digits = cardNumber.replace(" ", "")
        return when {
            digits.startsWith("4") -> "Visa"
            digits.take(2).toIntOrNull()?.let { it in 51..55 } == true -> "Mastercard"
            digits.take(4).toIntOrNull()?.let { it in 2221..2720 } == true -> "Mastercard"
            digits.startsWith("34") || digits.startsWith("37") -> "Amex"
            digits.startsWith("6") -> "RuPay"
            digits.startsWith("81") || digits.startsWith("82") || digits.startsWith("508") -> "RuPay"
            else -> "Card"
        }
    }

    /** Groups digits into 4s for display: "4111 1111 1111 1111". */
    fun formatCardNumber(raw: String): String =
        raw.filter { it.isDigit() }.chunked(4).joinToString(" ")

    /** No length or Luhn restriction — users may store loyalty cards, virtual cards,
     *  or partial numbers. Only requires that something was entered. */
    fun cardNumberError(number: String): String? {
        val digits = number.filter { it.isDigit() }
        if (digits.isBlank()) return "Enter a card number"
        return null
    }

    fun expiryError(month: String, year: String): String? {
        val m = month.toIntOrNull() ?: return "Enter a valid month (01-12)"
        if (m !in 1..12) return "Enter a valid month (01-12)"
        val y = year.toIntOrNull() ?: return "Enter a valid year"
        if (year.length != 2 && year.length != 4) return "Use a 2 or 4 digit year"
        return null
    }

    fun cvvError(cvv: String): String? {
        if (!cvv.all { it.isDigit() } || cvv.length !in 3..4) return "CVV should be 3-4 digits"
        return null
    }

    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (alternate) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
