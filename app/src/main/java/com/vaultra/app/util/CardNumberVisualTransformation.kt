package com.vaultra.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Displays raw digits grouped as "1234 5678 9012 3456" WITHOUT altering the
 * underlying text value. This is the fix for the cursor-jumping bug: the
 * previous approach rewrote the actual field text (inserting spaces) on every
 * keystroke, which fights Compose's cursor tracking. Here the state always
 * holds plain digits, and only the on-screen *display* is grouped, with an
 * OffsetMapping so the cursor lands in the right visual position.
 */
class CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(19)
        val formatted = digits.chunked(4).joinToString(" ")

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val spaces = (offset - 1) / 4
                return (offset + spaces).coerceIn(0, formatted.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val spaces = (offset - 1) / 5
                return (offset - spaces).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
