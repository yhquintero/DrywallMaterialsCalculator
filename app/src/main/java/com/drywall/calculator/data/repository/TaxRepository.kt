package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.TaxSetting
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaxRepository @Inject constructor(private val db: AppDatabase) {
    fun getTaxSetting(): Flow<TaxSetting?> = db.taxSettingDao().getTaxSetting()
    suspend fun saveTaxSetting(setting: TaxSetting) = db.taxSettingDao().upsert(setting)
}
