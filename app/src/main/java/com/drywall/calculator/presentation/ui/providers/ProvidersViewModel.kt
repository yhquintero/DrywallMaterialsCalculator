package com.drywall.calculator.presentation.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.Provider
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.repository.MaterialRepository
import com.drywall.calculator.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repository: ProviderRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {
    val providers = repository.getAllProviders()

    fun getProviderCatalog(providerId: Int): Flow<List<Material>> {
        return materialRepository.getMaterialsByProvider(providerId)
    }

    fun addProvider(provider: Provider) {
        viewModelScope.launch { repository.insertProvider(provider) }
    }

    fun updateProvider(provider: Provider) {
        viewModelScope.launch { repository.updateProvider(provider) }
    }

    fun deleteProvider(provider: Provider) {
        viewModelScope.launch { repository.deleteProvider(provider) }
    }
}
