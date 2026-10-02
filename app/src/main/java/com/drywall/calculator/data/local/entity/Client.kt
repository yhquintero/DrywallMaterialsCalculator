package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(), // ID de base de datos
    val ni: String = "", // Número de Identidad (11 dígitos) - Identificador Principal
    val name: String = "",
    val surnames: String = "",
    val fatherName: String? = null,
    val motherName: String? = null,
    val sex: String? = null,
    val birthDate: String? = null,
    val expirationDate: String? = null,
    val emissionDate: String? = null, // Fecha de emisión
    val municipality: String? = null, // Municipio
    val province: String? = null, // Provincia
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val registrationDate: Long = System.currentTimeMillis(),
    val frontImageUri: String? = null,
    val backImageUri: String? = null,
    val tomo: String? = null,
    val folio: String? = null,
    val year: String? = null,
    val civilRegistry: String? = null,
    val signatureImageUri: String? = null,
    val barcodeImageUri: String? = null,
    val qrCodeImageUri: String? = null,
    val profileImageUri: String? = null
)
