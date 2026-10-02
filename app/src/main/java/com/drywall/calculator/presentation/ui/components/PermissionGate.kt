package com.drywall.calculator.presentation.ui.components

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(
    permission: String,
    rationale: String,
    onPermissionGranted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionState = rememberPermissionState(permission)

    if (permissionState.status.isGranted) {
        LaunchedEffect(Unit) {
            onPermissionGranted()
            onDismiss()
        }
    } else {
        val showSettingsPrompt = permissionState.status.shouldShowRationale.not() && !permissionState.status.isGranted

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = when (permission) {
                        Manifest.permission.CAMERA -> "Acceso a Cámara"
                        Manifest.permission.READ_CONTACTS -> "Acceso a Contactos"
                        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE -> "Acceso a Fotos"
                        else -> "Permiso Requerido"
                    }
                )
            },
            text = {
                Column {
                    Text(rationale)
                    if (showSettingsPrompt) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Parece que el permiso ha sido denegado permanentemente. Por favor, actívelo manualmente en los ajustes de la aplicación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (showSettingsPrompt) {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } else {
                        permissionState.launchPermissionRequest()
                    }
                }) {
                    Text(if (showSettingsPrompt) "Ir a Ajustes" else "Permitir")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
}
