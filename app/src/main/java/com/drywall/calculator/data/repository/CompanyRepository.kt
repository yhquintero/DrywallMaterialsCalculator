package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.CompanyProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanyRepository @Inject constructor(private val db: AppDatabase) {
    fun getCompanyProfile(): Flow<CompanyProfile?> = db.companyDao().getCompanyProfile()
    suspend fun saveCompanyProfile(profile: CompanyProfile) = db.companyDao().upsert(profile)
}
