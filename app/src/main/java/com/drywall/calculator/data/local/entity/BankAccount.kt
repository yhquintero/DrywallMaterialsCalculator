package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "bank_accounts")
data class BankAccount(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val alias: String,
    val bankName: String,
    val accountNumber: String,
    val currency: String,
    val mobileNumber: String,
    val accountType: String = "Cliente", // "Cliente" or "Proveedor"
    val cardImageUri: String? = null
)
