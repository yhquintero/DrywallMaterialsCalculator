package com.drywall.calculator.presentation.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.WorkDiary
import com.drywall.calculator.data.repository.WorkDiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val repository: WorkDiaryRepository
) : ViewModel() {

    fun getDiaryEntries(projectId: String) = repository.getDiaryForProject(projectId)

    fun addEntry(entry: WorkDiary) {
        viewModelScope.launch { repository.insertEntry(entry) }
    }

    fun updateEntry(entry: WorkDiary) {
        viewModelScope.launch { repository.updateEntry(entry) }
    }

    fun deleteEntry(entry: WorkDiary) {
        viewModelScope.launch { repository.deleteEntry(entry) }
    }
}
