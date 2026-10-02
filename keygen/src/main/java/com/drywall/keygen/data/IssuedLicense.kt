package com.drywall.keygen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "issued_licenses")
data class IssuedLicense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userName: String,
    val deviceId: String,
    val planType: String,
    val price: String,
    val dateIssued: Long = System.currentTimeMillis(),
    val licenseJson: String = "",
    val isPaid: Boolean = false,
    val notes: String = ""
)
