package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_profile")
data class CompanyProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1,
    val country: String,
    val businessName: String,
    val logoUri: String? = null,
    val address: String,
    val phone: String,
    val email: String,
    val bankType: String = "",
    val accountNumber: String = "",
    val currencyType: String = "",
    val mobileBank: String = "",
    val signatureUri: String? = null,
    val ownerPhotoUri: String? = null
)
