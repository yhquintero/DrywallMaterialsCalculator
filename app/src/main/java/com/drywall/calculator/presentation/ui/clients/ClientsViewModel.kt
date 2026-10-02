package com.drywall.calculator.presentation.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.Client
import com.drywall.calculator.data.repository.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val repository: ClientRepository
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val clients = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty()) repository.getAllClients()
        else repository.searchClients(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveClient(client: Client) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveClient(client)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteClient(client)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
