package com.drywall.calculator.presentation.ui.company

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.drywall.calculator.data.local.entity.CompanyProfile
import com.drywall.calculator.presentation.ui.components.ScanType
import com.drywall.calculator.presentation.ui.components.ScannerDialog
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import com.drywall.calculator.presentation.ui.components.PermissionGate
import com.drywall.calculator.utils.ImageUtils
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Screen for managing the company or personal professional profile.
 * Handles contact details, business logo, and banking information for payments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyScreen(
    navController: NavController,
    onShowSnackbar: (String) -> Unit,
    viewModel: CompanyViewModel = hiltViewModel()
) {
    // Collect profile data from ViewModel
    val profile by viewModel.profile.collectAsState()
    
    // UI state for form fields
    var country by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var logoUri by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    
    // Banking data state
    var bankType by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var currencyType by remember { mutableStateOf("") }
    var mobileBank by remember { mutableStateOf("") }
    
    // Profile photo and signature URIs
    var signatureUri by remember { mutableStateOf<String?>(null) }
    var ownerPhotoUri by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var onPermissionGrantedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Initialize fields when profile is loaded
    LaunchedEffect(profile) {
        profile?.let {
            country = it.country
            businessName = it.businessName
            logoUri = it.logoUri
            address = it.address
            phone = it.phone
            email = it.email
            bankType = it.bankType
            accountNumber = it.accountNumber
            currencyType = it.currencyType
            mobileBank = it.mobileBank
            signatureUri = it.signatureUri
            ownerPhotoUri = it.ownerPhotoUri
            searchQuery = it.country
        }
    }

    val context = LocalContext.current
    val countries = remember { getAllCountries() }
    
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            ownerPhotoUri = ImageUtils.copyUriToPath(context, it, "photo")
            onShowSnackbar("Foto de perfil importada")
        }
    }

    val signaturePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            signatureUri = ImageUtils.copyUriToPath(context, it, "signature")
            onShowSnackbar("Firma digital importada")
        }
    }

    // Launcher for profile photo
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            ownerPhotoUri = ImageUtils.cropAndSaveImage(context, it, "photo")
            onShowSnackbar("Foto de perfil capturada y recortada")
        }
    }

    // Launcher for digital signature capture
    val signatureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            signatureUri = ImageUtils.cropAndSaveImage(context, it, "signature")
            onShowSnackbar("Firma digital capturada y recortada")
        }
    }

    // Filter logic for country dropdown
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isEmpty()) countries
        else countries.filter { it.contains(searchQuery, ignoreCase = true) }
    }
    var expanded by remember { mutableStateOf(false) }

    // Launcher for business logo selection
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = "logo_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(it)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            logoUri = file.absolutePath
        }
    }

    // Scanner Dialog for Bank Cards
    if (showScanner) {
        ScannerDialog(
            scanType = ScanType.BANK_CARD,
            onDismiss = { showScanner = false },
            onResult = { data ->
                if (data.name != null) bankType = data.name
                if (data.ni != null) accountNumber = data.ni
                if (data.country != null) currencyType = data.country
                showScanner = false
                onShowSnackbar("Datos bancarios extraídos con éxito")
            }
        )
    }

    pendingPermission?.let { permission ->
        PermissionGate(
            permission = permission,
            rationale = when(permission) {
                Manifest.permission.CAMERA -> "Para capturar fotos y firmas profesionales, la aplicación necesita acceder a la cámara de su dispositivo."
                Manifest.permission.READ_CONTACTS -> "Para seleccionar datos de contacto rápidamente, necesitamos acceso a su agenda."
                Manifest.permission.READ_EXTERNAL_STORAGE, "android.permission.READ_MEDIA_IMAGES" -> "Para seleccionar un logotipo desde su galería, la aplicación necesita acceso a sus fotos."
                else -> "Esta acción requiere un permiso especial para continuar de forma segura."
            },
            onPermissionGranted = { onPermissionGrantedAction?.invoke() },
            onDismiss = { 
                pendingPermission = null
                onPermissionGrantedAction = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            // Basic Information Section
            Text("Información de Contacto", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it.replace("\n", "").replace("\r", "")
                        expanded = true
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    label = { Text("País") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true,
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded && filteredCountries.isNotEmpty(),
                    onDismissRequest = { expanded = false }
                ) {
                    filteredCountries.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c) },
                            onClick = {
                                country = c
                                searchQuery = c
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            ValidatedTextField(
                value = businessName, 
                onValueChange = { businessName = it }, 
                label = "Nombre de la Empresa o Profesional"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Logo Institucional", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = { 
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pendingPermission = "android.permission.READ_MEDIA_IMAGES"
                        onPermissionGrantedAction = { logoPicker.launch("image/*") }
                    } else {
                        pendingPermission = Manifest.permission.READ_EXTERNAL_STORAGE
                        onPermissionGrantedAction = { logoPicker.launch("image/*") }
                    }
                }) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cargar Logo") 
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (logoUri != null) {
                    TextButton(onClick = { imageToDelete = "Logo Institucional" to { logoUri = null } }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { 
                        Icon(Icons.Default.Delete, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar") 
                    }
                }
            }
            logoUri?.let {
                Card(modifier = Modifier.size(120.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Image(painter = rememberAsyncImagePainter(ImageRequest.Builder(context).data(ImageUtils.resolveUri(context, it)).build()), contentDescription = "Logo", modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ValidatedTextField(
                value = address, 
                onValueChange = { address = it }, 
                label = "Dirección de Oficina"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            ValidatedTextField(
                value = phone,
                onValueChange = { if (it.all { char -> char.isDigit() || char == '+' }) phone = it },
                label = "Teléfono de Contacto",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            ValidatedTextField(
                value = email,
                onValueChange = { email = it },
                label = "Correo Electrónico",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Datos Bancarios (Para Cobros)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Button(
                onClick = { 
                    val permission = Manifest.permission.CAMERA
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        showScanner = true
                    } else {
                        pendingPermission = permission
                        onPermissionGrantedAction = { showScanner = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear Tarjeta Bancaria (AI)")
            }
            
            ValidatedTextField(
                value = bankType, 
                onValueChange = { bankType = it }, 
                label = "Entidad Bancaria"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ValidatedTextField(
                value = accountNumber, 
                onValueChange = { accountNumber = it }, 
                label = "Número de Cuenta / Tarjeta", 
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ValidatedTextField(
                value = currencyType, 
                onValueChange = { currencyType = it }, 
                label = "Tipo de Moneda (CUP, USD, MLC)"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ValidatedTextField(
                value = mobileBank,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '+' }) mobileBank = it },
                label = "ID Móvil (Transfermóvil/Enzona)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Identidad Digital", 
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Foto de Perfil", 
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Card(modifier = Modifier.size(110.dp).padding(vertical = 8.dp)) {
                        if (ownerPhotoUri != null) {
                            Box {
                                Image(painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, ownerPhotoUri)), contentDescription = null, modifier = Modifier.fillMaxSize())
                                IconButton(
                                    onClick = { imageToDelete = "Foto de Perfil" to { ownerPhotoUri = null } },
                                    modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { 
                            val permission = Manifest.permission.CAMERA
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                photoLauncher.launch(null)
                            } else {
                                pendingPermission = permission
                                onPermissionGrantedAction = { photoLauncher.launch(null) }
                            }
                        }) {
                            Icon(Icons.Default.CameraAlt, "Cámara", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { photoPicker.launch("image/*") }) {
                            Icon(Icons.Default.PhotoLibrary, "Galería", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Firma Digital", 
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Card(modifier = Modifier.size(110.dp).padding(vertical = 8.dp)) {
                        if (signatureUri != null) {
                            Box {
                                Image(painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, signatureUri)), contentDescription = null, modifier = Modifier.fillMaxSize())
                                IconButton(
                                    onClick = { imageToDelete = "Firma Digital" to { signatureUri = null } },
                                    modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { 
                            val permission = Manifest.permission.CAMERA
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                signatureLauncher.launch(null)
                            } else {
                                pendingPermission = permission
                                onPermissionGrantedAction = { signatureLauncher.launch(null) }
                            }
                        }) {
                            Icon(Icons.Default.CameraAlt, "Cámara", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { signaturePicker.launch("image/*") }) {
                            Icon(Icons.Default.PhotoLibrary, "Galería", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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
                    viewModel.saveProfile(CompanyProfile(
                        id = 1, // Forzar ID único para el perfil
                        country = country,
                        businessName = businessName,
                        logoUri = logoUri,
                        address = address,
                        phone = phone,
                        email = email,
                        bankType = bankType,
                        accountNumber = accountNumber,
                        currencyType = currencyType,
                        mobileBank = mobileBank,
                        signatureUri = signatureUri,
                        ownerPhotoUri = ownerPhotoUri
                    ))
                    onShowSnackbar("Perfil de empresa guardado correctamente")
                    navController.popBackStack()
                },
                shape = MaterialTheme.shapes.large
            ) { 
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Cambios", fontWeight = FontWeight.Bold) 
            }
        }
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

fun getAllCountries(): List<String> {
    return java.util.Locale.getISOCountries()
        .asSequence()
        .map { java.util.Locale.Builder().setRegion(it).build().displayName }
        .sorted()
        .toList()
}
