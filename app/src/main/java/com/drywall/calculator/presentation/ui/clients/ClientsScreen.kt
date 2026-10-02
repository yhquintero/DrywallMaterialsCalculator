package com.drywall.calculator.presentation.ui.clients

import androidx.compose.foundation.background
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.drywall.calculator.utils.ImageUtils
import com.drywall.calculator.utils.ScannerUtils
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.drywall.calculator.data.local.entity.Client
import com.drywall.calculator.presentation.ui.components.ScanType
import com.drywall.calculator.presentation.ui.components.ScannerDialog
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import com.drywall.calculator.presentation.ui.components.ImageDetailDialog
import com.drywall.calculator.presentation.ui.components.PermissionGate
import com.drywall.calculator.utils.ValidationUtils
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen for managing clients.
 * Allows adding, editing, deleting and searching clients.
 * Integrates an ID scanner for automatic data entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    viewModel: ClientsViewModel = hiltViewModel(),
) {
    // Collect state from ViewModel
    val clients by viewModel.clients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // UI visibility state
    var showDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<Client?>(null) }
    
    // Form fields state
    var ni by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var surnames by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var municipality by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var motherName by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var expirationDate by remember { mutableStateOf("") }
    var emissionDate by remember { mutableStateOf("") }
    var tomo by remember { mutableStateOf("") }
    var folio by remember { mutableStateOf("") }
    var year by remember { 
        mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) 
    }
    var civilRegistry by remember { mutableStateOf("") }
    var duplicateNiError by remember { mutableStateOf<String?>(null) }
    
    // Validation state
    var niError by remember { mutableStateOf<String?>(null) }

    // Image URIs state
    var frontImageUri by remember { mutableStateOf<String?>(null) }
    var backImageUri by remember { mutableStateOf<String?>(null) }
    var signatureUri by remember { mutableStateOf<String?>(null) }
    var barcodeUri by remember { mutableStateOf<String?>(null) }
    var qrUri by remember { mutableStateOf<String?>(null) }

    var showScanner by remember { mutableStateOf(false) }
    var scanType by remember { mutableStateOf(ScanType.ID_FRONT) }
    var showImageDetail by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Client?>(null) }
    var imageToDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    
    var showFrontSuccessAlert by remember { mutableStateOf(false) }
    var showBackSuccessAlert by remember { mutableStateOf(false) }

    LaunchedEffect(showFrontSuccessAlert) {
        if (showFrontSuccessAlert) {
            delay(3000.milliseconds)
            showFrontSuccessAlert = false
        }
    }
    LaunchedEffect(showBackSuccessAlert) {
        if (showBackSuccessAlert) {
            delay(3000.milliseconds)
            showBackSuccessAlert = false
        }
    }

    var profileUri by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var onPermissionGrantedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    /**
     * Helper to show snackbar messages.
     */
    fun localShowSnackbar(message: String) {
        scope.launch { 
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message) 
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bitmap = ImageUtils.uriToBitmap(context, it)
                bitmap?.let { b ->
                    val data = ScannerUtils.processFront(b)
                    ni = data.ni ?: ni
                    name = data.name ?: name
                    surnames = data.surnames ?: surnames
                    sex = data.sex ?: sex
                    birthDate = data.birthDate ?: birthDate
                    expirationDate = data.expiryDate ?: expirationDate
                    emissionDate = data.emissionDate ?: emissionDate
                    fatherName = data.father ?: fatherName
                    motherName = data.mother ?: motherName
                    tomo = data.tomo ?: tomo
                    folio = data.folio ?: folio
                    year = data.year ?: year
                    frontImageUri = ImageUtils.copyUriToPath(context, it, "frontal")
                    localShowSnackbar("Carnet importado de galería")
                }
            }
        }
    }

    val profileImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                profileUri = ImageUtils.copyUriToPath(context, it, "perfil")
            }
        }
    }

    val signatureImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                signatureUri = ImageUtils.copyUriToPath(context, it, "firma")
            }
        }
    }

    /**
     * Resets all form fields.
     */
    fun clearFields() {
        ni = ""; name = ""; surnames = ""; phone = ""; email = ""; address = ""
        municipality = ""; province = ""; fatherName = ""; motherName = ""
        sex = ""; birthDate = ""; expirationDate = ""; emissionDate = ""
        tomo = ""; folio = ""; year = Calendar.getInstance().get(Calendar.YEAR).toString(); civilRegistry = ""
        frontImageUri = null; backImageUri = null
        signatureUri = null; barcodeUri = null; qrUri = null; profileUri = null
        editingClient = null
        submitted = false
        niError = null
    }

    // Effect to populate fields when editing a client
    LaunchedEffect(editingClient) {
        editingClient?.let {
            ni = it.ni; name = it.name; surnames = it.surnames
            phone = it.phone ?: ""; email = it.email ?: ""; address = it.address ?: ""
            municipality = it.municipality ?: ""; province = it.province ?: ""
            fatherName = it.fatherName ?: ""; motherName = it.motherName ?: ""
            sex = it.sex ?: ""; birthDate = it.birthDate ?: ""; expirationDate = it.expirationDate ?: ""
            emissionDate = it.emissionDate ?: ""; tomo = it.tomo ?: ""; folio = it.folio ?: ""
            year = it.year ?: ""; civilRegistry = it.civilRegistry ?: ""
            frontImageUri = it.frontImageUri; backImageUri = it.backImageUri
            signatureUri = it.signatureImageUri; barcodeUri = it.barcodeImageUri; qrUri = it.qrCodeImageUri
            profileUri = it.profileImageUri
        }
    }

    // Effect to validate NI and extract sex on the fly
    LaunchedEffect(ni) {
        if (ni.length >= 10) {
            val sexDigitChar = ni[9]
            if (sexDigitChar.isDigit()) {
                val sexDigit = sexDigitChar.toString().toInt()
                sex = if (sexDigit % 2 == 0) "M" else "F"
            }
        } else {
            sex = "" // Clear if less than 10 digits
        }

        if (ni.length == 11) {
            val result = ValidationUtils.validateIdNumber(ni)
            if (!result.isValid) {
                niError = result.message
            } else {
                niError = null
            }
            
            val exists = clients.any { it.ni == ni && it.id != editingClient?.id }
            duplicateNiError = if (exists) "Ya existe un cliente con este número de identidad" else null
        } else if (ni.length > 0 && ni.length < 11) {
            niError = "Faltan dígitos (${ni.length}/11)"
            duplicateNiError = null
        } else {
            niError = null
            duplicateNiError = null
        }
    }

    // Scanner Dialog integration
    if (showScanner) {
        ScannerDialog(
            scanType = scanType,
            onDismiss = { showScanner = false },
            onResult = { data ->
                if (scanType == ScanType.ID_FRONT) {
                    ni = data.ni ?: ni
                    name = data.name ?: name
                    surnames = data.surnames ?: surnames
                    sex = data.sex ?: sex
                    birthDate = data.birthDate ?: birthDate
                    expirationDate = data.expiryDate ?: expirationDate
                    emissionDate = data.emissionDate ?: emissionDate
                    fatherName = data.father ?: fatherName
                    motherName = data.mother ?: motherName
                    tomo = data.tomo ?: tomo
                    folio = data.folio ?: folio
                    year = data.year ?: year
                    frontImageUri = data.imageUri
                    signatureUri = data.signatureImageUri ?: signatureUri
                    profileUri = data.profileImageUri ?: profileUri
                    scanType = ScanType.ID_BACK
                    if (data.imageUri != null) showFrontSuccessAlert = true
                } else {
                    address = data.address ?: address
                    municipality = data.municipality ?: municipality
                    province = data.province ?: province
                    civilRegistry = data.civilRegistry ?: civilRegistry
                    backImageUri = data.imageUri
                    barcodeUri = data.barcodeImageUri ?: barcodeUri
                    qrUri = data.qrCodeImageUri ?: qrUri
                    showScanner = false
                    if (data.imageUri != null) showBackSuccessAlert = true
                    localShowSnackbar("Carnet procesado con éxito")
                }
            },
        )
    }

    // Notificaciones temporales (Self-disappearing)
    if (showFrontSuccessAlert || showBackSuccessAlert) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(16.dp).widthIn(max = 300.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (showFrontSuccessAlert) "Imagen FRONTAL cargada correctamente" 
                        else "Imagen REVERSO cargada correctamente",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar Cliente") },
            text = { Text("¿Está seguro de que desea eliminar al cliente ${showDeleteConfirm?.name} definitivamente?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm?.let { viewModel.deleteClient(it) }
                        showDeleteConfirm = null
                        localShowSnackbar("Cliente eliminado")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") }
            },
        )
    }

    if (showImageDetail != null) {
        val detailUri = showImageDetail!!
        ImageDetailDialog(
            imageUri = detailUri, 
            onDismiss = { showImageDetail = null },
            onImageUpdated = { newUri ->
                if (frontImageUri?.substringBefore("?") == detailUri.substringBefore("?")) frontImageUri = newUri
                if (backImageUri?.substringBefore("?") == detailUri.substringBefore("?")) backImageUri = newUri
                if (profileUri?.substringBefore("?") == detailUri.substringBefore("?")) profileUri = newUri
                if (signatureUri?.substringBefore("?") == detailUri.substringBefore("?")) signatureUri = newUri
                showImageDetail = null
            }
        )
    }

    pendingPermission?.let { permission ->
        PermissionGate(
            permission = permission,
            rationale = when(permission) {
                Manifest.permission.CAMERA -> "Para escanear el carnet de identidad y capturar fotos, la aplicación necesita acceder a la cámara."
                Manifest.permission.READ_CONTACTS -> "Para importar datos de clientes desde su agenda, necesitamos acceso a los contactos."
                else -> "Esta acción requiere un permiso especial para continuar."
            },
            onPermissionGranted = { onPermissionGrantedAction?.invoke() },
            onDismiss = { 
                pendingPermission = null
                onPermissionGrantedAction = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Botón inferior ajustable arriba de la barra inferior
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
                Icon(Icons.Default.PersonAdd, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar Nuevo Cliente", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

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
                    placeholder = { Text("Buscar cliente...") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            }

            // Lista ajustable
            if (clients.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No hay clientes registrados", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(clients) { client ->
                        ClientCard(client, onEdit = { editingClient = client; showDialog = true }, onDelete = { showDeleteConfirm = client })
                    }
                }
            }
        }
    }

    var showScanMenu by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; clearFields() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (editingClient == null) "Nuevo Cliente" else "Editar Cliente", style = MaterialTheme.typography.headlineSmall)
                        Text("Complete los datos del carnet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val permission = Manifest.permission.CAMERA
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            scanType = ScanType.ID_FRONT
                            showScanner = true 
                        } else {
                            pendingPermission = permission
                            onPermissionGrantedAction = { 
                                scanType = ScanType.ID_FRONT
                                showScanner = true 
                            }
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, "Escanear", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Datos Personales", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    
                    Column {
                        ValidatedTextField(
                            ni, 
                            { if (it.length <= 11) ni = it }, 
                            "Número de Identidad (11 dígitos)", 
                            isError = (submitted && ni.isBlank()) || niError != null || duplicateNiError != null, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (niError != null) {
                            Text(niError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
                        }
                        if (duplicateNiError != null) {
                            Text(duplicateNiError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(
                            name, 
                            { if (it.all { c -> c.isLetter() || c.isWhitespace() }) name = it }, 
                            "Nombres", 
                            isError = submitted && name.isBlank(),
                            modifier = Modifier.weight(1f)
                        )
                        ValidatedTextField(
                            surnames, 
                            { if (it.all { c -> c.isLetter() || c.isWhitespace() }) surnames = it }, 
                            "Apellidos", 
                            isError = submitted && surnames.isBlank(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(
                            fatherName, 
                            { if (it.all { c -> c.isLetter() || c.isWhitespace() }) fatherName = it }, 
                            "Nombre del Padre",
                            modifier = Modifier.weight(1f)
                        )
                        ValidatedTextField(
                            motherName, 
                            { if (it.all { c -> c.isLetter() || c.isWhitespace() }) motherName = it }, 
                            "Nombre de la Madre",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(
                            birthDate, 
                            { birthDate = it }, 
                            "Nacimiento (DD/MM/AAAA)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = "Ej: 15/05/1990",
                            modifier = Modifier.weight(1.2f)
                        )
                        ValidatedTextField(
                            sex, 
                            { sex = it }, 
                            "Sexo",
                            enabled = false,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    
                    ValidatedTextField(
                        expirationDate, 
                        { expirationDate = it }, 
                        "Fecha de Vencimiento (DD/MM/AAAA)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = "Ej: 15/05/2030"
                    )

                    Text("Ubicación y Registro", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ValidatedTextField(civilRegistry, { civilRegistry = it }, "Registro Civil")
                    ValidatedTextField(address, { address = it }, "Dirección Particular (Residencial)")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(municipality, { municipality = it }, "Municipio", Modifier.weight(1f))
                        ValidatedTextField(province, { province = it }, "Provincia", Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(tomo, { tomo = it }, "Tomo", Modifier.weight(1f))
                        ValidatedTextField(folio, { folio = it }, "Folio", Modifier.weight(1f))
                        ValidatedTextField(year, { year = it }, "Año", Modifier.weight(1f))
                    }

                    Text("Imágenes y Firma", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhotoSlot("Frontal", frontImageUri, { scanType = ScanType.ID_FRONT; showScanner = true }, { frontImageUri = null }, { showImageDetail = it }, { p, action -> pendingPermission = p; onPermissionGrantedAction = action }, { galleryLauncher.launch("image/*") }, { frontImageUri = it })
                        PhotoSlot("Reverso", backImageUri, { scanType = ScanType.ID_BACK; showScanner = true }, { backImageUri = null }, { showImageDetail = it }, { p, action -> pendingPermission = p; onPermissionGrantedAction = action }, { galleryLauncher.launch("image/*") }, { backImageUri = it })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Foto de Perfil", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (profileUri != null) {
                                    androidx.compose.foundation.Image(
                                        painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, profileUri)),
                                        contentDescription = "Foto de Perfil",
                                        modifier = Modifier.fillMaxSize().clickable { showImageDetail = profileUri },
                                        contentScale = ContentScale.Fit,
                                    )
                                    Row(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { 
                                                scope.launch {
                                                    val fullPath = ImageUtils.resolveUri(context, profileUri)
                                                    if (fullPath != null) {
                                                        val bitmap = ImageUtils.uriToBitmap(context, Uri.fromFile(java.io.File(fullPath)))
                                                        if (bitmap != null) {
                                                            val rotated = ImageUtils.rotateBitmap(bitmap, 90f)
                                                            ImageUtils.saveBitmap(context, rotated, java.io.File(fullPath).name)
                                                            rotated.recycle()
                                                            bitmap.recycle()
                                                            profileUri = (profileUri?.substringBefore("?") ?: "") + "?t=${System.currentTimeMillis()}"
                                                        }
                                                    }
                                                }
                                            }, 
                                            modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.RotateRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { imageToDelete = "Foto de Perfil" to { profileUri = null } }, 
                                            modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else {
                                    IconButton(onClick = { profileImageLauncher.launch("image/*") }, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Firma Extraída", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (signatureUri != null) {
                                    androidx.compose.foundation.Image(
                                        painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, signatureUri)),
                                        contentDescription = "Firma",
                                        modifier = Modifier.fillMaxSize().clickable { showImageDetail = signatureUri },
                                        contentScale = ContentScale.Fit,
                                    )
                                    Row(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { 
                                                scope.launch {
                                                    val fullPath = ImageUtils.resolveUri(context, signatureUri)
                                                    if (fullPath != null) {
                                                        val bitmap = ImageUtils.uriToBitmap(context, Uri.fromFile(java.io.File(fullPath)))
                                                        if (bitmap != null) {
                                                            val rotated = ImageUtils.rotateBitmap(bitmap, 90f)
                                                            ImageUtils.saveBitmap(context, rotated, java.io.File(fullPath).name)
                                                            rotated.recycle()
                                                            bitmap.recycle()
                                                            signatureUri = (signatureUri?.substringBefore("?") ?: "") + "?t=${System.currentTimeMillis()}"
                                                        }
                                                    }
                                                }
                                            }, 
                                            modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.RotateRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { imageToDelete = "Firma Digital" to { signatureUri = null } }, 
                                            modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else {
                                    IconButton(onClick = { signatureImageLauncher.launch("image/*") }, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Default.Gesture, null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                    
                    Text("Contacto", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ValidatedTextField(
                        phone,
                        { if (it.all { c -> c.isDigit() || c == '+' }) phone = it },
                        "Teléfono",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    ValidatedTextField(email, { email = it }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        submitted = true
                        if (ni.isNotBlank() && name.isNotBlank() && niError == null && duplicateNiError == null) {
                            viewModel.saveClient(
                                Client(
                                    id = editingClient?.id ?: UUID.randomUUID().toString(),
                                    ni = ni, name = name, surnames = surnames,
                                    phone = phone, email = email, address = address,
                                    municipality = municipality, province = province,
                                    fatherName = fatherName, motherName = motherName,
                                    sex = sex, birthDate = birthDate, expirationDate = expirationDate,
                                    emissionDate = emissionDate, tomo = tomo, folio = folio, year = year,
                                    civilRegistry = civilRegistry, frontImageUri = frontImageUri,
                                    backImageUri = backImageUri, signatureImageUri = signatureUri,
                                    barcodeImageUri = barcodeUri, qrCodeImageUri = qrUri,
                                    profileImageUri = profileUri,
                                ),
                            )
                            localShowSnackbar("Cliente guardado")
                            showDialog = false; clearFields()
                        }
                    },
                    enabled = ni.isNotBlank() && name.isNotBlank() && niError == null && duplicateNiError == null
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false; clearFields() }) { Text("Cancelar") } },
        )
    }

    imageToDelete?.let { (type, onDelete) ->
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = { Text("Eliminar $type") },
            text = { Text("¿Desea eliminar esta $type?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        imageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { imageToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ClientCard(client: Client, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Profile image in the list
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (client.profileImageUri != null) {
                    androidx.compose.foundation.Image(
                        painter = rememberAsyncImagePainter(ImageUtils.resolveUri(LocalContext.current, client.profileImageUri)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("${client.name} ${client.surnames}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("CI: ${client.ni}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun RowScope.PhotoSlot(
    label: String, 
    uri: String?, 
    onScan: () -> Unit, 
    onDelete: () -> Unit, 
    onView: (String) -> Unit,
    onPermissionRequired: (String, () -> Unit) -> Unit,
    onImport: () -> Unit,
    onUpdate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSlotMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar Foto $label") },
            text = { Text("¿Desea eliminar la foto $label del carnet?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
    
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (uri != null) {
                androidx.compose.foundation.Image(
                    painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, uri)),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clickable { onView(uri) },
                    contentScale = ContentScale.Fit,
                )
                
                // Overlay de controles
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                                        // Cache busting
                                        val newUri = (uri.substringBefore("?") ) + "?t=${System.currentTimeMillis()}"
                                        onUpdate(newUri)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true }, 
                        modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(onClick = { showSlotMenu = true }, modifier = Modifier.fillMaxSize()) { 
                        Icon(Icons.Default.AddAPhoto, null) 
                    }
                    DropdownMenu(expanded = showSlotMenu, onDismissRequest = { showSlotMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Tomar Foto") },
                            leadingIcon = { Icon(Icons.Default.Camera, null) },
                            onClick = {
                                showSlotMenu = false
                                val permission = Manifest.permission.CAMERA
                                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                    onScan()
                                } else {
                                    onPermissionRequired(permission, onScan)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Importar de Galería") },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                            onClick = {
                                showSlotMenu = false
                                onImport()
                            }
                        )
                    }
                }
            }
        }
    }
}
