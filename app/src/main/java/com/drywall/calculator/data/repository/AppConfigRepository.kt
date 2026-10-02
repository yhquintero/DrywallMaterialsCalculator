package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.AppConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfigRepository @Inject constructor(private val db: AppDatabase) {
    /**
     * Obtiene el flujo de la configuración de la aplicación desde la base de datos.
     *
     * @return Flow que emite el objeto AppConfig actual o null si no existe.
     */
    fun getConfig(): Flow<AppConfig?> = db.appConfigDao().getConfig()

    /**
     * Guarda o actualiza la configuración de la aplicación en la base de datos.
     *
     * @param config El objeto AppConfig con los nuevos valores a persistir.
     */
    suspend fun saveConfig(config: AppConfig) = db.appConfigDao().insert(config)
}
