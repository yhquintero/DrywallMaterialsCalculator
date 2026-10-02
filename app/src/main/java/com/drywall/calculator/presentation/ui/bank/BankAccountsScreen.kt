package com.drywall.calculator.presentation.ui.bank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.RotateRight
import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.drywall.calculator.data.local.entity.BankAccount
import com.drywall.calculator.presentation.ui.components.ScanType
import com.drywall.calculator.presentation.ui.components.ScannerDialog
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import com.drywall.calculator.presentation.ui.components.ImageDetailDialog
import com.drywall.calculator.presentation.ui.components.PermissionGate
import com.drywall.calculator.utils.ImageUtils
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountsScreen(
    onShowSnackbar: (String) -> Unit,
    viewModel: BankAccountViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<BankAccount?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<BankAccount?>(null) }

    var alias by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("Cliente") }
    var cardImageUri by remember { mutableStateOf<String?>(null) }

    var showScanner by remember { mutableStateOf(false) }
    var showImageDetail by remember { mutableStateOf<String?>(null) }
    var showDeleteImageConfirm by remember { mutableStateOf(false) }
    var showBankCardSuccessAlert by remember { mutableStateOf(false) }
    
    LaunchedEffect(showBankCardSuccessAlert) {
        if (showBankCardSuccessAlert) {
            delay(3000)
            showBankCardSuccessAlert = false
        }
    }
    
    var submitted by remember { mutableStateOf(false) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var onPermissionGrantedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var lastExtractedData by remember { mutableStateOf<com.drywall.calculator.utils.ScannerUtils.IdCardData?>(null) }

    fun clearFields() {
        alias = ""; bankName = ""; accountNumber = ""; currency = ""; mobile = ""
        accountType = "Cliente"; cardImageUri = null
        lastExtractedData = null
        editingAccount = null
        submitted = false
    }

    LaunchedEffect(editingAccount) {
        editingAccount?.let {
            alias = it.alias; bankName = it.bankName; accountNumber = it.accountNumber
            currency = it.currency; mobile = it.mobileNumber
            accountType = it.accountType; cardImageUri = it.cardImageUri
        }
    }

    if (showScanner) {
        ScannerDialog(
            scanType = ScanType.BANK_CARD,
            onDismiss = { showScanner = false },
            onResult = { data ->
                lastExtractedData = data
                bankName = data.name ?: bankName
                accountNumber = data.ni ?: accountNumber
                currency = data.country ?: currency
                cardImageUri = data.imageUri
                showScanner = false
                if (data.imageUri != null) showBankCardSuccessAlert = true
            }
        )
    }

    if (showBankCardSuccessAlert) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(16.dp).widthIn(max = 300.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Imagen de tarjeta cargada correctamente",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showImageDetail != null) {
        ImageDetailDialog(
            imageUri = showImageDetail!!, 
            onDismiss = { showImageDetail = null },
            onImageUpdated = {
                val timestamp = "?t=${System.currentTimeMillis()}"
                if (cardImageUri == it) cardImageUri = it + timestamp
                showImageDetail = null
            }
        )
    }

    pendingPermission?.let { permission ->
        PermissionGate(
            permission = permission,
            rationale = when(permission) {
                Manifest.permission.CAMERA -> "Para escanear tarjetas bancarias, necesitamos acceso a la cámara."
                else -> "Esta acción requiere permiso para continuar."
            },
            onPermissionGranted = { onPermissionGrantedAction?.invoke() },
            onDismiss = { 
                pendingPermission = null
                onPermissionGrantedAction = null
            }
        )
    }

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
                        clearFields()
                        showDialog = true
                    },
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.AccountBalance, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir Nueva Cuenta", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Buscador arriba pegado al borde
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it.replace("\n", "").replace("\r", "")) },
                    label = { Text("Buscar Cuenta (Alias, Banco, Número)") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = MaterialTheme.shapes.medium
                )
            }

            // Lista ajustable
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (accounts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                            Text("No hay cuentas bancarias registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(accounts) { account ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${account.alias} (${account.accountType})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Banco: ${account.bankName}", style = MaterialTheme.typography.bodyMedium)
                                    Text("No: ${account.accountNumber} - ${account.currency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Row {
                                    IconButton(onClick = { editingAccount = account; showDialog = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { showDeleteConfirm = account }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar Cuenta") },
            text = { Text("¿Desea eliminar la cuenta ${showDeleteConfirm!!.alias}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") }
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; clearFields() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (editingAccount == null) "Nueva Cuenta" else "Editar Cuenta", style = MaterialTheme.typography.headlineSmall)
                        Text("Datos bancarios y transferencia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val context = LocalContext.current
                    IconButton(onClick = { 
                        val permission = Manifest.permission.CAMERA
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            showScanner = true
                        } else {
                            pendingPermission = permission
                            onPermissionGrantedAction = { showScanner = true }
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Escanear Tarjeta", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Detalles de la Cuenta", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = accountType == "Cliente", onClick = { accountType = "Cliente" })
                        Text("Cliente", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = accountType == "Proveedor", onClick = { accountType = "Proveedor" })
                        Text("Proveedor", style = MaterialTheme.typography.bodyMedium)
                    }

                    ValidatedTextField(value = alias, onValueChange = { alias = it }, label = "Alias / Nombre del Titular", isError = submitted && alias.isBlank())
                    ValidatedTextField(value = bankName, onValueChange = { bankName = it }, label = "Entidad Bancaria", isError = submitted && bankName.isBlank())
                    ValidatedTextField(
                        value = accountNumber,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == ' ' }) accountNumber = it },
                        label = "Número de Cuenta / Tarjeta",
                        isError = submitted && accountNumber.isBlank(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(value = currency, onValueChange = { currency = it }, label = "Moneda (CUP, USD, MLC)", modifier = Modifier.weight(1f))
                        ValidatedTextField(
                            value = mobile,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '+' }) mobile = it },
                            label = "Móvil (Pago Enlínea)",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                    
                    Text("ID Móvil (Transfermóvil/Enzona)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                    if (lastExtractedData != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Verificación de Extracción (IA)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                Text("Banco Detectado: ${lastExtractedData?.name ?: "N/D"}", style = MaterialTheme.typography.bodySmall)
                                Text("Número Detectado: ${lastExtractedData?.ni ?: "N/D"}", style = MaterialTheme.typography.bodySmall)
                                Text("Moneda Detectada: ${lastExtractedData?.country ?: "N/D"}", style = MaterialTheme.typography.bodySmall)
                                Text("Si los datos son incorrectos, puede editarlos arriba.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    
                    cardImageUri?.let { uri ->
                        Text("Imagen de la Tarjeta (Clic para ver):", style = MaterialTheme.typography.labelMedium)
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp))) {
                            androidx.compose.foundation.Image(
                                painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, uri)),
                                contentDescription = "Tarjeta",
                                modifier = Modifier.fillMaxSize().clickable { showImageDetail = uri },
                                contentScale = ContentScale.Fit
                            )
                            Row(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { 
                                        scope.launch {
                                            val fullPath = ImageUtils.resolveUri(context, uri)
                                            if (fullPath != null) {
                                                val bitmap = ImageUtils.uriToBitmap(context, Uri.fromFile(java.io.File(fullPath)))
                                                if (bitmap != null) {
                                                    val rotated = ImageUtils.rotateBitmap(bitmap, 90f)
                                                    ImageUtils.saveBitmap(context, rotated, java.io.File(fullPath).name)
                                                    rotated.recycle()
                                                    bitmap.recycle()
                                                    cardImageUri = (cardImageUri?.substringBefore("?") ?: "") + "?t=${System.currentTimeMillis()}"
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { showDeleteImageConfirm = true },
                                    modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    submitted = true
                    if (alias.isNotBlank() && bankName.isNotBlank() && accountNumber.isNotBlank()) {
                        val acc = BankAccount(
                            id = editingAccount?.id ?: UUID.randomUUID().toString(),
                            alias = alias, bankName = bankName, accountNumber = accountNumber,
                            currency = currency, mobileNumber = mobile,
                            accountType = accountType, cardImageUri = cardImageUri
                        )
                        if (editingAccount == null) viewModel.addAccount(acc)
                        else viewModel.updateAccount(acc)
                        onShowSnackbar("Cuenta bancaria guardada")
                        showDialog = false; clearFields()
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false; clearFields() }) { Text("Cancelar") } }
        )
    }

    if (showDeleteImageConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteImageConfirm = false },
            title = { Text("Eliminar Imagen de Tarjeta") },
            text = { Text("¿Desea eliminar la imagen de la tarjeta bancaria?") },
            confirmButton = {
                Button(
                    onClick = {
                        cardImageUri = null
                        showDeleteImageConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteImageConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
