package com.drywall.keygen.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LicenseDao {
    @Query("SELECT * FROM issued_licenses ORDER BY dateIssued DESC")
    fun getAllLicenses(): Flow<List<IssuedLicense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicense(license: IssuedLicense): Long

    @Update
    suspend fun updateLicense(license: IssuedLicense)

    @Delete
    suspend fun deleteLicense(license: IssuedLicense)

    @Query("SELECT * FROM rate_history ORDER BY timestamp ASC")
    fun getRateHistory(): kotlinx.coroutines.flow.Flow<List<RateHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRateHistory(history: RateHistory)
}
