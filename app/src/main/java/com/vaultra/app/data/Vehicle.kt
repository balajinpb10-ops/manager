package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val id: String,
    val name: String,
    val registrationNumber: String,
    val type: String,
    val fuelType: String,
    val tankCapacity: Double,
    val photoPath: String?,
    val isArchived: Boolean = false,
    val updatedAt: Long
)
