package com.vaultra.app.util

object DocumentValidators {
    val DOC_TYPES = listOf(
        "Aadhaar Card", "PAN Card", "Passport", "Driving License", "Voter ID",
        "Birth Certificate", "SSLC Certificate", "HSC Certificate", "Degree Certificate", "Marksheet",
        "Employee ID", "Company ID Card", "Insurance Policy", "Vehicle RC", "Vehicle Insurance",
        "Bank Passbook", "Credit Card", "Debit Card", "Medical Records", "Vaccination Certificate",
        "Utility Bills", "Rental Agreement", "Tax Documents", "Property Documents", "Other (Custom)"
    )

    /** Returns null if the number looks fine for the given document type (a soft check, not a hard block for "Other"). */
    fun docNumberError(docType: String, number: String): String? {
        val clean = number.replace(" ", "").uppercase()
        return when (docType) {
            "Aadhaar Card" -> if (clean.length == 12 && clean.all { it.isDigit() }) null else "Aadhaar number should be 12 digits"
            "PAN Card" -> if (Regex("^[A-Z]{5}[0-9]{4}[A-Z]$").matches(clean)) null else "PAN format should be like ABCDE1234F"
            else -> if (clean.isBlank()) "Document number is required" else null
        }
    }

    /** Masks all but the last 4 characters for list/detail display before the user taps "view". */
    fun mask(number: String): String {
        val clean = number.replace(" ", "")
        if (clean.length <= 4) return "*".repeat(clean.length)
        return "*".repeat(clean.length - 4) + clean.takeLast(4)
    }
}
