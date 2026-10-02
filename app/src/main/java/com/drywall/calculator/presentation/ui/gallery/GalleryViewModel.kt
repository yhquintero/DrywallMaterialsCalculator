package com.drywall.calculator.presentation.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.ProjectPhoto
import com.drywall.calculator.data.repository.ProjectPhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: ProjectPhotoRepository
) : ViewModel() {

    fun getPhotos(projectId: String) = repository.getPhotosForProject(projectId)

    fun addPhoto(photo: ProjectPhoto) {
        viewModelScope.launch { repository.insert(photo) }
    }

    fun deletePhoto(photo: ProjectPhoto) {
        viewModelScope.launch { repository.delete(photo) }
    }
}
