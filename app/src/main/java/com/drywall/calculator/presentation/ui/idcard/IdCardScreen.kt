package com.drywall.calculator.presentation.ui.idcard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.drywall.calculator.utils.ImageUtils
import com.drywall.calculator.utils.PdfGenerator
import com.drywall.calculator.presentation.ui.company.CompanyViewModel
import com.drywall.calculator.presentation.ui.pdf.PdfViewModel
import com.drywall.calculator.utils.PdfUtils

/**
 * Pantalla que muestra y genera el Carnet Digital Profesional.
 * Utiliza los datos del Perfil de Empresa para personalizar el carnet.
 */
@Composable
fun IdCardScreen(
    companyViewModel: CompanyViewModel = hiltViewModel(),
    pdfViewModel: PdfViewModel = hiltViewModel()
) {
    val profile by companyViewModel.profile.collectAsState()
    val context = LocalContext.current
    val generatedPdf by pdfViewModel.pdfFile.collectAsState(initial = null)

    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var showPageSizeDialog by remember { mutableStateOf(false) }

    if (showPageSizeDialog) {
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text("Tamaño de hoja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione el formato de papel para el carnet:")
                    PdfGenerator.PageSize.entries.forEach { ps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPageSize = ps }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ps.label)
                            if (selectedPageSize == ps) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPageSizeDialog = false
                    profile?.let { p ->
                        pdfViewModel.generateIdCard(context, p.businessName, p.logoUri, p.ownerPhotoUri, p.signatureUri, p.businessName, pageSize = selectedPageSize)
                    }
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Tu Carnet Digital Profesional", 
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                "Identificación oficial para obras y clientes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Previsualización del Carnet (Diseño Profesional)
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Fondo con gradiente sutil
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(Color(0xFF2196F3).copy(alpha = 0.05f), Color.White))
                    ))
                    
                    // Franja lateral de color
                    Box(modifier = Modifier.fillMaxHeight().width(12.dp).background(MaterialTheme.colorScheme.primary))
                    
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            // Foto del Titular con Marco
                            Box(
                                modifier = Modifier
                                    .size(85.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            ) {
                                if (profile?.ownerPhotoUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, profile?.ownerPhotoUri)), 
                                        contentDescription = null, 
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Badge, 
                                        contentDescription = null, 
                                        modifier = Modifier.align(Alignment.Center).size(35.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    profile?.businessName?.uppercase() ?: "NOMBRE DEL TITULAR", 
                                    style = MaterialTheme.typography.titleMedium, 
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    maxLines = 2,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    "ESPECIALISTA EN DRYWALL", 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    profile?.country ?: "País de residencia", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Info de contacto pequeña
                                if (!profile?.phone.isNullOrBlank()) {
                                    Text("📞 ${profile?.phone}", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.SpaceBetween, 
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Sección de Firma
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    "FIRMA DIGITAL", 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .width(100.dp)
                                        .background(Color(0xFFF0F0F0), RoundedCornerShape(4.dp))
                                        .padding(4.dp)
                                ) {
                                    if (profile?.signatureUri != null) {
                                        Image(
                                            painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, profile?.signatureUri)), 
                                            contentDescription = "Firma",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text(
                                            "Sin Firma", 
                                            modifier = Modifier.align(Alignment.Center),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.LightGray,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                            }
                            
                            // Logo de la Empresa
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(4.dp)
                            ) {
                                if (profile?.logoUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(ImageUtils.resolveUri(context, profile?.logoUri)), 
                                        contentDescription = "Logo",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Botones de Acción Agrupados para evitar scroll
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { showPageSizeDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Badge, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generar Carnet en PDF", fontWeight = FontWeight.Bold)
            }

            if (generatedPdf != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { PdfUtils.viewPdf(context, generatedPdf!!) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Print, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir")
                    }
                    Button(
                        onClick = { PdfUtils.sharePdf(context, generatedPdf!!) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compartir")
                    }
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Completar Perfil de Empresa para ver tus datos.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }


        }
    }
}
