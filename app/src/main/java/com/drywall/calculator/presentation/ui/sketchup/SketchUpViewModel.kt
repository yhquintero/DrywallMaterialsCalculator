package com.drywall.calculator.presentation.ui.sketchup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.data.local.entity.Zone
import com.drywall.calculator.data.repository.MaterialRepository
import com.drywall.calculator.data.repository.CalculationRepository
import com.drywall.calculator.data.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.gson.Gson
import android.content.Context
import java.io.InputStreamReader

@HiltViewModel
class SketchUpViewModel @Inject constructor(
    private val zoneRepository: ZoneRepository,
    private val materialRepository: MaterialRepository,
    private val calculationRepository: CalculationRepository
) : ViewModel() {

    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    private val _totalArea = MutableStateFlow(0.0)
    val totalArea: StateFlow<Double> = _totalArea.asStateFlow()

    fun importFromJson(context: Context, inputStream: InputStreamReader) {
        viewModelScope.launch {
            val data = Gson().fromJson(inputStream, ZoneImportData::class.java)
            val projectId = data.projectId ?: "default"
            // Clear old zones for this project
            zoneRepository.deleteZonesForProject(projectId)
            val zoneList = data.zones.map { Zone(projectId = projectId, name = it.name, areaM2 = it.areaM2) }
            zoneList.forEach { zoneRepository.insertZone(it) }
            _zones.value = zoneList
            _totalArea.value = zoneList.sumOf { it.areaM2 }
        }
    }

    data class ZoneImportData(
        val projectId: String?,
        val zones: List<ZoneItem>
    )
    data class ZoneItem(val name: String, val areaM2: Double)
}
