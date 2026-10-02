package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.BankAccount
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankAccountRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllAccounts(): Flow<List<BankAccount>> = db.bankAccountDao().getAll()
    suspend fun addAccount(account: BankAccount) = db.bankAccountDao().insert(account)
    suspend fun updateAccount(account: BankAccount) = db.bankAccountDao().update(account)
    suspend fun deleteAccount(account: BankAccount) = db.bankAccountDao().delete(account)
}
