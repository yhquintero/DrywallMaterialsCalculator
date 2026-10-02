package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.Provider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllProviders(): Flow<List<Provider>> = db.providerDao().getAllProviders()
    suspend fun insertProvider(provider: Provider) = db.providerDao().insert(provider)
    suspend fun updateProvider(provider: Provider) = db.providerDao().update(provider)
    suspend fun deleteProvider(provider: Provider) = db.providerDao().delete(provider)
}
