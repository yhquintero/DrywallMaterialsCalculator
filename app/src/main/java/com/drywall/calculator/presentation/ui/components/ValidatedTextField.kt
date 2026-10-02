package com.drywall.calculator.presentation.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    errorMessage: String = "Este campo es obligatorio",
    trailingIcon: @Composable (() -> Unit)? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    
    // Prohibir la tecla Enter en todos los campos según requerimiento
    val onKeyEventModifier = Modifier.onKeyEvent { event ->
        if (event.key == Key.Enter) {
            true // Consumir el evento para que no haga nada
        } else {
            false
        }
    }

    // Launcher for phone contact picker
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
            try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val hasPhoneIndex = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        
                        if (idIndex != -1 && hasPhoneIndex != -1) {
                            val id = c.getString(idIndex)
                            val hasPhone = c.getInt(hasPhoneIndex)
                            if (hasPhone > 0) {
                                val phonesCursor = context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                    arrayOf(id),
                                    null
                                )
                                phonesCursor?.use { p ->
                                    if (p.moveToFirst()) {
                                        val numIndex = p.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (numIndex != -1) {
                                            val number = p.getString(numIndex)
                                            onValueChange(number)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al acceder a contactos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher for email contact picker
    val emailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
            try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                        if (idIndex != -1) {
                            val id = c.getString(idIndex)
                            val emailsCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                                arrayOf(id),
                                null
                            )
                            emailsCursor?.use { e ->
                                if (e.moveToFirst()) {
                                    val dataIndex = e.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)
                                    if (dataIndex != -1) {
                                        val email = e.getString(dataIndex)
                                        onValueChange(email)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al acceder a contactos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val finalTrailingIcon = trailingIcon ?: when (keyboardOptions.keyboardType) {
        KeyboardType.Phone -> {
            {
                var showGate by remember { mutableStateOf(false) }
                if (showGate) {
                    PermissionGate(
                        permission = Manifest.permission.READ_CONTACTS,
                        rationale = "Para seleccionar un teléfono directamente de su lista de contactos, necesitamos su permiso.",
                        onPermissionGranted = { phoneLauncher.launch(null) },
                        onDismiss = { showGate = false }
                    )
                }
                IconButton(onClick = { 
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        phoneLauncher.launch(null)
                    } else {
                        showGate = true
                    }
                }) {
                    Icon(Icons.Default.Contacts, "Buscar teléfono")
                }
            }
        }
        KeyboardType.Email -> {
            {
                var showGate by remember { mutableStateOf(false) }
                if (showGate) {
                    PermissionGate(
                        permission = Manifest.permission.READ_CONTACTS,
                        rationale = "Para buscar un correo electrónico entre sus contactos, necesitamos acceso a su agenda.",
                        onPermissionGranted = { emailLauncher.launch(null) },
                        onDismiss = { showGate = false }
                    )
                }
                IconButton(onClick = { 
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        emailLauncher.launch(null)
                    } else {
                        showGate = true
                    }
                }) {
                    Icon(Icons.Default.Email, "Cuentas de correo")
                }
            }
        }
        else -> null
    }

    Column(modifier = modifier.then(onKeyEventModifier)) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Prohibir saltos de línea forzadamente
                var cleanValue = newValue.replace("\n", "").replace("\r", "")
                
                // Validación estricta para campos decimales
                if (keyboardOptions.keyboardType == KeyboardType.Decimal) {
                    // Solo permitir un punto o una coma
                    val hasSeparator = cleanValue.count { it == '.' || it == ',' } > 1
                    if (hasSeparator) return@OutlinedTextField
                    
                    // Asegurar que solo haya un separador y sea consistente (usar . para validación)
                    val normalizedValue = cleanValue.replace(",", ".")
                    
                    // Validar que sea un número válido o esté vacío/separador solo
                    if (normalizedValue.isNotEmpty() && normalizedValue != "." && normalizedValue != "-") {
                        if (normalizedValue.toDoubleOrNull() == null) return@OutlinedTextField
                    }
                    
                    // Limitar a 4 decimales
                    if (normalizedValue.contains(".")) {
                        val decimals = normalizedValue.substringAfter(".")
                        if (decimals.length > 4) return@OutlinedTextField
                    }
                }

                onValueChange(cleanValue)
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))) } },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            isError = isError,
            trailingIcon = finalTrailingIcon,
            leadingIcon = leadingIcon,
            singleLine = true,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        if (isError) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}
