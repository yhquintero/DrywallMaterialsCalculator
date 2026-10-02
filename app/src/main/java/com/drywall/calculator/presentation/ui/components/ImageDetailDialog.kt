package com.drywall.calculator.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.drywall.calculator.utils.ImageUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailDialog(
    imageUri: String,
    onDismiss: () -> Unit,
    onImageUpdated: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rotation by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }
    
    val fullPath = remember(imageUri) { ImageUtils.resolveUri(context, imageUri) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ver Imagen") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    },
                    actions = {
                        IconButton(onClick = { rotation -= 90f }) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Girar Izquierda")
                        }
                        IconButton(onClick = { rotation += 90f }) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Girar Derecha")
                        }
                        if (rotation % 360 != 0f) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        try {
                                            val uri = android.net.Uri.fromFile(java.io.File(fullPath!!))
                                            val bitmap = ImageUtils.uriToBitmap(context, uri)
                                            if (bitmap != null) {
                                                val rotatedBitmap = ImageUtils.rotateBitmap(bitmap, rotation)
                                                val fileName = java.io.File(imageUri).name
                                                ImageUtils.saveBitmap(context, rotatedBitmap, fileName)
                                                rotatedBitmap.recycle()
                                                bitmap.recycle()
                                                val newUri = imageUri.substringBefore("?") + "?t=${System.currentTimeMillis()}"
                                                onImageUpdated?.invoke(newUri)
                                                onDismiss()
                                            }
                                        } catch (_: Exception) {
                                            // Handle error
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                enabled = !isSaving
                            ) {
                                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                else Icon(Icons.Default.Save, contentDescription = "Guardar Rotación")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(padding),
                color = Color.Black
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = rememberAsyncImagePainter(fullPath),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .rotate(rotation),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
