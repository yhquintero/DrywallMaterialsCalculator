package com.drywall.calculator.presentation.ui.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.MaterialMeasurement
import com.drywall.calculator.data.repository.MaterialMeasurementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaterialMeasurementViewModel @Inject constructor(
    private val repository: MaterialMeasurementRepository
) : ViewModel() {
    val measurements = repository.getMeasurements().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveMeasurements(measurements: MaterialMeasurement) {
        viewModelScope.launch { repository.saveMeasurements(measurements) }
    }

    fun deleteMeasurement(measurement: MaterialMeasurement) {
        viewModelScope.launch { repository.deleteMeasurement(measurement) }
    }
}
