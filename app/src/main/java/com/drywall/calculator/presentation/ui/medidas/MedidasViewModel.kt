package com.drywall.calculator.presentation.ui.medidas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.drywall.calculator.data.provider.MaterialDataProvider
import com.drywall.calculator.domain.measure.MaterialCategory
import com.drywall.calculator.domain.measure.MaterialGroup
import com.drywall.calculator.domain.measure.MaterialItem
import com.drywall.calculator.domain.measure.MaterialMeasure
import com.drywall.calculator.domain.measure.StandardRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MedidasViewModel @Inject constructor() : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    var selectedCategory by mutableStateOf<MaterialCategory?>(null)
        private set

    var selectedItem by mutableStateOf<MaterialItem?>(null)
        private set

    var selectedMeasure by mutableStateOf<MaterialMeasure?>(null)
        private set

    var selectedStandard by mutableStateOf(StandardRegion.EU)
        private set

    val groups: List<MaterialGroup> = MaterialDataProvider.allGroups

    val categories: List<MaterialCategory> = MaterialCategory.entries.toList()

    val allMeasures: List<MaterialMeasure> = MaterialDataProvider.allMeasures

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
    }

    fun onCategorySelected(category: MaterialCategory?) {
        selectedCategory = category
        selectedItem = null
        selectedMeasure = null
    }

    fun onItemSelected(item: MaterialItem?) {
        selectedItem = item
        selectedMeasure = null
    }

    fun onMeasureSelected(measure: MaterialMeasure?) {
        selectedMeasure = measure
    }

    fun onStandardSelected(standard: StandardRegion) {
        selectedStandard = standard
    }

    fun clearSelection() {
        selectedCategory = null
        selectedItem = null
        selectedMeasure = null
        searchQuery = ""
    }

    fun getMeasuresByCategory(category: MaterialCategory): List<MaterialMeasure> =
        MaterialDataProvider.filterByCategoryAndStandard(category, selectedStandard)

    fun searchMeasures(query: String): List<MaterialMeasure> =
        MaterialDataProvider.search(query)

    fun getMeasuresForSelectedItem(): List<MaterialMeasure> {
        return selectedItem?.let { MaterialDataProvider.getMeasuresByMaterialId(it.id) } ?: emptyList()
    }
}
