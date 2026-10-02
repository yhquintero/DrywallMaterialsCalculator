package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.Client
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val db: AppDatabase
) {
    fun getAllClients(): Flow<List<Client>> = db.clientDao().getAllClients()

    fun searchClients(query: String): Flow<List<Client>> = db.clientDao().searchClients(query)

    suspend fun saveClient(client: Client) {
        db.clientDao().insertClient(client) // Room REPLACE strategy = atomic upsert
    }

    suspend fun deleteClient(client: Client) = db.clientDao().deleteClient(client)
    
    suspend fun getClientById(id: String) = db.clientDao().getClientById(id)
}
