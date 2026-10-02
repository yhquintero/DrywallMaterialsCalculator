package com.drywall.calculator.presentation.ui.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.BankAccount
import com.drywall.calculator.data.repository.BankAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankAccountViewModel @Inject constructor(
    private val repository: BankAccountRepository
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val accounts = _searchQuery.flatMapLatest { query ->
        repository.getAllAccounts().map { list ->
            if (query.isEmpty()) list
            else list.filter { 
                it.alias.contains(query, ignoreCase = true) || 
                it.bankName.contains(query, ignoreCase = true) ||
                it.accountNumber.contains(query)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addAccount(account: BankAccount) {
        viewModelScope.launch { repository.addAccount(account) }
    }

    fun updateAccount(account: BankAccount) {
        viewModelScope.launch { repository.updateAccount(account) }
    }

    fun deleteAccount(account: BankAccount) {
        viewModelScope.launch { repository.deleteAccount(account) }
    }
}
