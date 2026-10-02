package com.drywall.calculator.presentation.ui.maintenance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.data.repository.ProjectRepository
import com.drywall.calculator.presentation.utils.OptimizedDatabaseBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    val projects = projectRepository.getAllProjects().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage = _uiMessage.asSharedFlow()

    fun updateProjectRaw(project: Project) {
        viewModelScope.launch {
            try {
                projectRepository.update(project)
                _uiMessage.emit("Proyecto '${project.name}' actualizado manualmente con éxito")
            } catch (e: Exception) {
                _uiMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun performBackupRecovery(context: Context) {
        viewModelScope.launch {
            try {
                val destination = java.io.File(context.filesDir, "optimized_backup_${System.currentTimeMillis()}.zip")
                val result = OptimizedDatabaseBackup.backupDatabaseOnly(context, destination, "plain")
                _uiMessage.emit("Backup optimizado completo: $result")
            } catch (e: Exception) {
                _uiMessage.emit("Error en backup optimizado: ${e.message}")
            }
        }
    }

    fun forceReinitializeSecurity(context: Context) {
        viewModelScope.launch {
            try {
                com.drywall.common.utils.DatabaseRecoveryUtils.forceReinitializeLicensingSecurity(context)
                com.drywall.common.utils.DatabaseRecoveryUtils.forceReinitializeKeygenSecurity(context)
                _uiMessage.emit("Seguridad reiniciada correctamente")
            } catch (e: Exception) {
                _uiMessage.emit("Error reinicializando seguridad: ${e.message}")
            }
        }
    }
}
