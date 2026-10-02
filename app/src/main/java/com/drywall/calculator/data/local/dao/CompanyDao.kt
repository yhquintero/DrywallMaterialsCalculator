package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.CompanyProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM company_profile WHERE id = 1")
    fun getCompanyProfile(): Flow<CompanyProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CompanyProfile)
}
