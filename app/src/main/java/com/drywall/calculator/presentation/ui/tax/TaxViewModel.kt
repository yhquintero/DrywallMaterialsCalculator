package com.drywall.calculator.presentation.ui.tax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.TaxSetting
import com.drywall.calculator.data.repository.TaxRepository
import com.drywall.calculator.data.repository.CompanyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaxViewModel @Inject constructor(
    private val repository: TaxRepository,
    private val companyRepository: CompanyRepository
) : ViewModel() {
    val taxSetting = repository.getTaxSetting().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val companyProfile = companyRepository.getCompanyProfile().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun saveTaxSetting(setting: TaxSetting) {
        viewModelScope.launch { repository.saveTaxSetting(setting) }
    }
}
