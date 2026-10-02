package com.drywall.calculator.presentation.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.data.repository.ProjectRepository
import com.drywall.calculator.data.repository.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val projects = repository.getAllProjects().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allClients = clientRepository.getAllClients().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val clients = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty()) flowOf(emptyList())
        else clientRepository.searchClients(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addProject(name: String, client: String, phone: String, email: String, area: Double, type: String) {
        viewModelScope.launch {
            repository.insert(Project(
                name = name, 
                clientName = client, 
                clientPhone = phone,
                clientEmail = email,
                totalAreaM2 = area, 
                constructionType = type
            ))
        }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch {
            repository.update(project)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.delete(project)
        }
    }
}
