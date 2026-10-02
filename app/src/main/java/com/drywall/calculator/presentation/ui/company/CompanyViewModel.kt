package com.drywall.calculator.presentation.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.CompanyProfile
import com.drywall.calculator.data.repository.CompanyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompanyViewModel @Inject constructor(
    private val repository: CompanyRepository
) : ViewModel() {
    val profile = repository.getCompanyProfile().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun saveProfile(profile: CompanyProfile) {
        viewModelScope.launch {
            repository.saveCompanyProfile(profile)
        }
    }
}
