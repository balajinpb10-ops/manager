package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey val id: String,
    /** Links to Vehicle.id when the entry was logged against a saved vehicle profile. Null for legacy entries. */
    val vehicleId: String? = null,
    val vehicleName: String,
    val vehicleType: String,
    val fuelType: String,
    val odometer: Long,
    val previousOdometer: Long,
    val distance: Long,
    val fuelQuantity: Double,
    val pricePerLiter: Double,
    val totalAmount: Double,
    val station: String,
    val timestamp: Long,
    val notes: String,
    val receiptPath: String?,
    val location: String?,
    val updatedAt: Long,
    /** How the fuel was paid for, e.g. "Cash", "Card", "UPI". Empty string for legacy entries. */
    val paymentMethod: String = ""
)
