package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntry(
    @PrimaryKey val id: String,
    val nickname: String,
    val bankName: String,
    val cardholderName: String,
    val cardNumber: String,
    val expiryMonth: String,
    val expiryYear: String,
    val cvv: String,
    val network: String,
    val isFavorite: Boolean,
    val updatedAt: Long,
    /** Absolute paths of attached photos, stored only in this app's private storage. */
    val images: List<String> = emptyList()
)
