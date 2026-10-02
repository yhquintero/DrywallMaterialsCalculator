package com.drywall.calculator.presentation.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.drywall.calculator.utils.ScannerUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class ScanType {
    ID_FRONT, ID_BACK, BANK_CARD
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerDialog(
    scanType: ScanType,
    onDismiss: () -> Unit,
    onResult: (ScannerUtils.IdCardData) -> Unit,
) {
    var showPermissionGate by remember { mutableStateOf(true) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            showPermissionGate = false
        }
    }

    if (showPermissionGate && !cameraPermissionState.status.isGranted) {
        PermissionGate(
            permission = Manifest.permission.CAMERA,
            rationale = "Para poder utilizar la tecnología de escaneo inteligente (IA) y extraer los datos automáticamente, la aplicación necesita acceder a su cámara.",
            onPermissionGranted = { showPermissionGate = false },
            onDismiss = onDismiss
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize(),
            title = null,
            text = {
                ScannerView(scanType, onResult, onDismiss)
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
        )
    }
}

@Composable
fun ScannerView(
    scanType: ScanType,
    onResult: (ScannerUtils.IdCardData) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isHighRes by remember { mutableStateOf(true) }
    var flashEnabled by remember { mutableStateOf(false) }
    var showResMenu by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    val result = processCapturedBitmap(bitmap, scanType, context)
                    onResult(result)
                }
            }
        }
    }

    // Color dinámico según el tipo de escaneo
    val accentColor = when(scanType) {
        ScanType.ID_FRONT -> Color.Cyan
        ScanType.ID_BACK -> Color.Green
        ScanType.BANK_CARD -> Color.Yellow
    }

    LaunchedEffect(isHighRes) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val resolution = if (isHighRes) Size(1080, 1920) else Size(720, 1280)
            
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(resolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(resolutionSelector)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraInstance = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                android.util.Log.e("ScannerComponent", "Error binding camera lifecycle", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        
        DocumentOverlay(
            label = when(scanType) {
                ScanType.ID_FRONT -> "FRONTAL DEL CARNET"
                ScanType.ID_BACK -> "REVERSO DEL CARNET"
                ScanType.BANK_CARD -> "TARJETA BANCARIA"
            },
            accentColor = accentColor
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(0.4f))) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
            
            Row {
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(0.4f)),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Galería", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { 
                        flashEnabled = !flashEnabled
                        cameraInstance?.cameraControl?.enableTorch(flashEnabled)
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = if(flashEnabled) Color.Yellow.copy(0.4f) else Color.Black.copy(0.4f)),
                ) {
                    Icon(if(flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "Flash", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showResMenu = true },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(0.4f)),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Calidad", tint = Color.White)
                }
            }
        }

        DropdownMenu(expanded = showResMenu, onDismissRequest = { showResMenu = false }) {
            DropdownMenuItem(text = { Text("Alta Definición (HD)") }, onClick = { isHighRes = true; showResMenu = false })
            DropdownMenuItem(text = { Text("Estándar (SD)") }, onClick = { isHighRes = false; showResMenu = false })
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            color = Color.Black.copy(0.5f),
            shape = CircleShape,
        ) {
            Text(if(isHighRes) "MODO HD ACTIVO" else "MODO SD", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
            IconButton(
                onClick = {
                    imageCapture?.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap().rotate(image.imageInfo.rotationDegrees.toFloat())
                            image.close()
                            
                            scope.launch {
                                val result = processCapturedBitmap(bitmap, scanType, context)
                                onResult(result)
                            }
                        }
                    })
                },
                modifier = Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).padding(4.dp).background(Color.White.copy(0.2f), CircleShape),
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capturar", modifier = Modifier.size(48.dp), tint = Color.White)
            }
        }
    }
}

/**
 * Procesa un bitmap capturado o importado, realizando el recorte y el procesamiento de datos.
 */
private suspend fun processCapturedBitmap(
    bitmap: Bitmap,
    scanType: ScanType,
    context: android.content.Context
): ScannerUtils.IdCardData {
    // Recortar la imagen según el marco de alineación (lo que está dentro del visor)
    val croppedBitmap = cropToOverlay(bitmap)
    
    val result = when (scanType) {
        ScanType.ID_FRONT -> {
            val data = ScannerUtils.processFront(croppedBitmap)
            // Extraer firma y perfil del área recortada
            data.copy(
                signatureImageUri = saveSubImage(croppedBitmap, Rect(20, 350, 300, 500), "firma", data.ni ?: "temp", context),
                profileImageUri = saveSubImage(croppedBitmap, Rect(20, 50, 250, 350), "perfil", data.ni ?: "temp", context)
            )
        }
        ScanType.ID_BACK -> {
            val data = ScannerUtils.processBack(croppedBitmap)
            data.copy(
                barcodeImageUri = saveSubImage(croppedBitmap, Rect(300, 300, 600, 500), "barra", data.ni ?: "temp", context),
                qrCodeImageUri = saveSubImage(croppedBitmap, Rect(450, 50, 600, 200), "qr", data.ni ?: "temp", context)
            )
        }
        ScanType.BANK_CARD -> ScannerUtils.processBankCard(croppedBitmap)
    }

    val prefix = if(scanType == ScanType.ID_FRONT) "frontal" else if(scanType == ScanType.ID_BACK) "reverso" else "banco"
    return result.copy(imageUri = saveClientImage(croppedBitmap, prefix, result.ni ?: "temp", context))
}

/**
 * Recorta el bitmap original para que solo contenga lo que estaba dentro del marco de alineación.
 */
private fun cropToOverlay(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    
    // Las proporciones deben coincidir con DocumentOverlay
    val cardWidth = width * 0.9f
    val cardHeight = cardWidth * 0.63f
    val left = (width - cardWidth) / 2
    val top = (height - cardHeight) / 2
    
    return try {
        Bitmap.createBitmap(
            bitmap, 
            left.toInt().coerceIn(0, width - 1), 
            top.toInt().coerceIn(0, height - 1), 
            cardWidth.toInt().coerceAtMost(width - left.toInt()), 
            cardHeight.toInt().coerceAtMost(height - top.toInt())
        )
    } catch (e: Exception) {
        bitmap // Retornar original si falla
    }
}

@Composable
fun DocumentOverlay(label: String, accentColor: Color = Color.White) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cardWidth = width * 0.9f
        val cardHeight = cardWidth * 0.63f
        val left = (width - cardWidth) / 2
        val top = (height - cardHeight) / 2
        
        val fullRectPath = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, width, height))
        }
        val cardRectPath = Path().apply { 
            addRoundRect(RoundRect(
                left = left, top = top, right = left + cardWidth, bottom = top + cardHeight,
                cornerRadius = CornerRadius(20.dp.toPx()),
            ))
        }
        
        val overlayPath = Path.combine(PathOperation.Difference, fullRectPath, cardRectPath)
        drawPath(overlayPath, color = Color.Black.copy(0.6f))
        
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(20.dp.toPx()),
            style = Stroke(width = 4.dp.toPx()),
        )
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(260.dp))
            Surface(color = accentColor.copy(alpha = 0.8f), shape = MaterialTheme.shapes.small) {
                Text(
                    text = label, 
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.Black, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Alinee el documento con el marco", color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun saveClientImage(bitmap: Bitmap, prefix: String, ni: String, context: android.content.Context): String {
    val root = File(context.filesDir, "clientes/$ni")
    if (!root.exists()) root.mkdirs()
    val file = File(root, "${prefix}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    return "clientes/$ni/${file.name}"
}

private fun saveSubImage(fullBitmap: Bitmap, rect: Rect, prefix: String, ni: String, context: android.content.Context): String? {
    return try {
        val left = rect.left.coerceIn(0, fullBitmap.width - 1)
        val top = rect.top.coerceIn(0, fullBitmap.height - 1)
        val width = rect.width().coerceAtMost(fullBitmap.width - left)
        val height = rect.height().coerceAtMost(fullBitmap.height - top)
        val subBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height)
        
        val root = File(context.filesDir, "clientes/$ni/extra")
        if (!root.exists()) root.mkdirs()
        val file = File(root, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { subBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        "clientes/$ni/extra/${file.name}"
    } catch (exc: Exception) {
        android.util.Log.e("ScannerComponent", "Error saving sub image", exc)
        null
    }
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
