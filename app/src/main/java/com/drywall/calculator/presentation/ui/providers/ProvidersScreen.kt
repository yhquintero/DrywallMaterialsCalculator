package com.drywall.calculator.presentation.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.Provider
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(viewModel: ProvidersViewModel = hiltViewModel()) {
    val providers by viewModel.providers.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<Provider?>(null) }
    var showCatalogFor by remember { mutableStateOf<Provider?>(null) }
    var providerToDelete by remember { mutableStateOf<Provider?>(null) }
    
    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    onClick = { 
                        editingProvider = null
                        showDialog = true 
                    },
                    shape = MaterialTheme.shapes.large
                ) { 
                    Icon(Icons.Default.ContactPage, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir Nuevo Proveedor", fontWeight = FontWeight.Bold) 
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (providers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay proveedores registrados.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(providers) { provider ->
                            ProviderCard(
                                provider, 
                                onEdit = { 
                                    editingProvider = provider
                                    showDialog = true 
                                },
                                onDelete = { providerToDelete = provider },
                                onViewCatalog = { showCatalogFor = provider }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ProviderDialog(
            provider = editingProvider,
            onDismiss = { showDialog = false }, 
            onSave = { 
                if (editingProvider == null) viewModel.addProvider(it)
                else viewModel.updateProvider(it)
            }
        )
    }

    if (showCatalogFor != null) {
        ProviderCatalogDialog(
            provider = showCatalogFor!!,
            catalogFlow = viewModel.getProviderCatalog(showCatalogFor!!.id),
            onDismiss = { showCatalogFor = null }
        )
    }

    providerToDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text("Eliminar Proveedor") },
            text = { Text("¿Desea eliminar al proveedor '${provider.name}' definitivamente?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProvider(provider)
                        providerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ProviderCard(provider: Provider, onEdit: () -> Unit, onDelete: () -> Unit, onViewCatalog: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        provider.name, 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        provider.category, 
                        style = MaterialTheme.typography.labelLarge, 
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Row {
                    IconButton(onClick = onViewCatalog) {
                        Icon(Icons.Default.Inventory2, contentDescription = "Ver Catálogo", tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ContactPhone, 
                    contentDescription = null, 
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    provider.contactName + " (" + provider.phone + ")", 
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Email, 
                    contentDescription = null, 
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    provider.email, 
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                repeat(5) { index ->
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = if (index < provider.rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderCatalogDialog(
    provider: Provider, 
    catalogFlow: kotlinx.coroutines.flow.Flow<List<com.drywall.calculator.data.local.entity.Material>>, 
    onDismiss: () -> Unit
) {
    val catalog by catalogFlow.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Catálogo de Materiales")
                Text(provider.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (catalog.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Este proveedor no tiene materiales vinculados.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(catalog) { material ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val locale = LocalConfiguration.current.locales[0]
                                        Text(material.name, fontWeight = FontWeight.Bold)
                                        Text("Precio Venta: $${String.format(locale, "%.2f", material.salePrice)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("${material.quantity} ${material.unitType}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun ProviderDialog(provider: Provider?, onDismiss: () -> Unit, onSave: (Provider) -> Unit) {
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var contact by remember { mutableStateOf(provider?.contactName ?: "") }
    var phone by remember { mutableStateOf(provider?.phone ?: "") }
    var email by remember { mutableStateOf(provider?.email ?: "") }
    var category by remember { mutableStateOf(provider?.category ?: "") }
    var rating by remember { mutableIntStateOf(provider?.rating ?: 5) }
    
    var businessType by remember { mutableStateOf(if(provider?.category?.contains(" - ") == true) provider.category.split(" - ")[0] else "MiPyme") }
    val categories = listOf("Empresa", "TCP", "MiPyme")
    var expandedType by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (provider == null) "Añadir Proveedor" else "Editar Proveedor") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                @OptIn(ExperimentalMaterial3Api::class)
                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType }
                ) {
                    OutlinedTextField(
                        value = businessType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de Negocio") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) }
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        categories.forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = { businessType = type; expandedType = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it.replace("\n", "").replace("\r", "") }, 
                    label = { Text("Nombre del Negocio") }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = contact, 
                    onValueChange = { 
                        // Solo permitir letras
                        val clean = it.filter { c -> c.isLetter() || c.isWhitespace() }
                        contact = clean 
                    }, 
                    label = { Text("Nombre de Contacto (Solo letras)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { 
                        val clean = it.replace("\n", "").replace("\r", "")
                        if (clean.all { c -> c.isDigit() || c == '+' }) phone = clean 
                    },
                    label = { Text("Teléfono / Móvil") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.replace("\n", "").replace("\r", "") },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = category, 
                    onValueChange = { category = it.replace("\n", "").replace("\r", "") }, 
                    label = { Text("Categoría (ej. Placas, Perfiles)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text("Clasificación (Estrellas)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    repeat(5) { index ->
                        IconButton(onClick = { rating = index + 1 }) {
                            Icon(
                                if (index < rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (index < rating) Color(0xFFFFC107) else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalCategory = "$businessType - $category"
                onSave(Provider(
                    id = provider?.id ?: 0,
                    name = name, 
                    contactName = contact, 
                    phone = phone, 
                    email = email, 
                    category = finalCategory,
                    rating = rating
                ))
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
