package com.drywall.calculator.presentation.ui.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.drywall.calculator.data.local.entity.ProjectPhoto
import com.drywall.calculator.presentation.ui.components.ImageDetailDialog
import com.drywall.calculator.presentation.ui.components.PermissionGate
import com.drywall.calculator.presentation.ui.projects.ProjectsViewModel
import com.drywall.calculator.utils.ImageUtils
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    galleryViewModel: GalleryViewModel = hiltViewModel(),
    projectsViewModel: ProjectsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val projects by projectsViewModel.projects.collectAsState(initial = emptyList())
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    val photos by galleryViewModel.getPhotos(selectedProjectId ?: "").collectAsState(initial = emptyList())
    var expandedProjects by remember { mutableStateOf(false) }
    var showImageDetail by remember { mutableStateOf<String?>(null) }
    var photoToDelete by remember { mutableStateOf<ProjectPhoto?>(null) }
    var showPermissionGate by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            val path = ImageUtils.cropAndSaveImage(context, it, "project_photo")
            if (path != null && selectedProjectId != null) {
                galleryViewModel.addPhoto(ProjectPhoto(projectId = selectedProjectId!!, photoUri = path))
            }
        }
    }

    if (showImageDetail != null) {
        ImageDetailDialog(imageUri = showImageDetail!!, onDismiss = { showImageDetail = null })
    }

    if (photoToDelete != null) {
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Eliminar Foto") },
            text = { Text("¿Desea eliminar esta foto permanentemente?") },
            confirmButton = {
                Button(
                    onClick = {
                        galleryViewModel.deletePhoto(photoToDelete!!)
                        photoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (showPermissionGate) {
        PermissionGate(
            permission = Manifest.permission.CAMERA,
            rationale = "Para tomar fotos de los avances de la obra, necesitamos permiso para usar la cámara.",
            onPermissionGranted = { cameraLauncher.launch(null) },
            onDismiss = { showPermissionGate = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expandedProjects,
            onExpandedChange = { expandedProjects = !expandedProjects }
        ) {
            OutlinedTextField(
                value = projects.find { it.id == selectedProjectId }?.name ?: "Seleccione Proyecto",
                onValueChange = {},
                readOnly = true,
                label = { Text("Proyecto") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProjects) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expandedProjects,
                onDismissRequest = { expandedProjects = false }
            ) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            selectedProjectId = project.id
                            expandedProjects = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (selectedProjectId != null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos) { photo ->
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { showImageDetail = photo.photoUri }
                        ) {
                            Box {
                                Image(
                                    painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, photo.photoUri)),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { photoToDelete = photo },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Seleccione un proyecto para ver sus fotos", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (selectedProjectId != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showPermissionGate = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.AddAPhoto, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tomar Foto de Avance", fontWeight = FontWeight.Bold)
            }
        }
    }
}
