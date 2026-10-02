package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.Client
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY registrationDate DESC")
    fun getAllClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE name LIKE '%' || :query || '%' OR ni LIKE '%' || :query || '%' OR surnames LIKE '%' || :query || '%'")
    fun searchClients(query: String): Flow<List<Client>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client)

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: String): Client?
}
