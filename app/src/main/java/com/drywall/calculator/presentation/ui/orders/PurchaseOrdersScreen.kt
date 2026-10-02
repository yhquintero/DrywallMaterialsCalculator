package com.drywall.calculator.presentation.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.PurchaseOrder
import com.drywall.calculator.presentation.ui.pdf.PdfViewModel
import com.drywall.calculator.utils.PdfGenerator
import com.drywall.calculator.utils.PdfUtils
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalConfiguration
import java.util.*

private fun parseOrderNotes(notes: String): Map<String, Any> {
    return try {
        if (notes.isBlank()) emptyMap()
        else {
            val json = com.google.gson.Gson().fromJson(notes, Map::class.java) as? Map<String, Any> ?: emptyMap()
            json
        }
    } catch (_: Exception) { emptyMap() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrdersScreen(
    viewModel: PurchaseOrdersViewModel = hiltViewModel(),
    pdfViewModel: PdfViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsState()
    var selectedOrder by remember { mutableStateOf<PurchaseOrder?>(null) }
    var orderToDelete by remember { mutableStateOf<PurchaseOrder?>(null) }
    val context = LocalContext.current
    val generatedPdf by pdfViewModel.pdfFile.collectAsState(initial = null)
    val precision = com.drywall.calculator.presentation.theme.LocalDecimalPrecision.current

    val groupedOrders = remember(orders) {
        orders.groupBy { order ->
            val notes = parseOrderNotes(order.notes)
            notes["constructionType"] as? String ?: "Sin Tipo"
        }
    }

    var showPageSizeDialog by remember { mutableStateOf(false) }
    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var orderForPdf by remember { mutableStateOf<PurchaseOrder?>(null) }
    var itemsForPdf by remember { mutableStateOf<List<com.drywall.calculator.data.local.entity.PurchaseOrderItem>>(emptyList()) }
    var areaForPdf by remember { mutableStateOf(0.0) }

    if (showPageSizeDialog) {
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text("Tamaño de hoja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione el formato de papel para la orden de compra:")
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
                Button(onClick = {
                    showPageSizeDialog = false
                    val pdfItems = itemsForPdf.map { PdfGenerator.InvoiceItem(it.materialName, 0.0, it.quantityRequired, 0.0, 0.0) }
                    pdfViewModel.generatePurchaseOrder(context, orderForPdf?.projectName ?: "", "Proveedor", areaForPdf, pdfItems, 0.0, pageSize = selectedPageSize)
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    if (generatedPdf != null) {
                        IconButton(onClick = { PdfUtils.viewPdf(context, generatedPdf!!) }) {
                            Icon(Icons.Default.Visibility, contentDescription = "Ver PDF", tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (orders.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "No hay órdenes de compra registradas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Las órdenes de compra se generan automáticamente al calcular un proyecto en la Calculadora. Para crear una orden de compra:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Pasos para crear una orden de compra:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("1. Vaya a la sección Calculadora", style = MaterialTheme.typography.bodySmall)
                                Text("2. Seleccione un tipo de construcción y Complete los datos", style = MaterialTheme.typography.bodySmall)
                                Text("3. Pulse Calcular para generar los requerimientos de materiales", style = MaterialTheme.typography.bodySmall)
                                Text("4. La orden de compra se creará automáticamente con los materiales faltantes", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    groupedOrders.forEach { (typeName, typeOrders) ->
                        item {
                            Text(
                                typeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(typeOrders) { order ->
                            val itemsFlow = remember(order.id) { viewModel.getItemsForOrder(order.id) }
                            val items by itemsFlow.collectAsState(initial = emptyList())
                            val notes = remember(order.notes) { parseOrderNotes(order.notes) }
                            val rate = (notes["rate"] as? Number)?.toDouble() ?: 0.0
                            val currency = notes["currency"] as? String ?: ""
                            val totalArea = (notes["totalArea"] as? Number)?.toDouble() ?: 0.0
                            val cotizacionTotal = rate * totalArea

                            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Proyecto: ${order.projectName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", LocalConfiguration.current.locales[0]).format(order.date)}", style = MaterialTheme.typography.bodySmall)
                                            if (rate > 0) {
                                                Text(
                                                    "$currency ${com.drywall.calculator.utils.NumberFormatter.format(cotizacionTotal, precision)} (${com.drywall.calculator.utils.NumberFormatter.format(totalArea, precision)} m² × $currency ${com.drywall.calculator.utils.NumberFormatter.format(rate, precision)})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (order.status == "Pendiente") {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Text(
                                                        " PENDIENTE DE ADQUISICIÓN ",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                orderForPdf = order
                                                itemsForPdf = items
                                                areaForPdf = totalArea
                                                showPageSizeDialog = true
                                            }) {
                                                Icon(Icons.Default.Print, contentDescription = "Exportar PDF", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { selectedOrder = order }) {
                                                Icon(Icons.Default.Info, contentDescription = "Detalles")
                                            }
                                            IconButton(onClick = { orderToDelete = order }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (orderToDelete != null) {
        AlertDialog(
            onDismissRequest = { orderToDelete = null },
            title = { Text("Eliminar Orden de Compra") },
            text = { Text("¿Desea eliminar la orden de compra del proyecto '${orderToDelete!!.projectName}' definitivamente?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOrder(orderToDelete!!)
                        orderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { orderToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (selectedOrder != null) {
        val items by viewModel.getItemsForOrder(selectedOrder!!.id).collectAsState(initial = emptyList())
        val notes = remember(selectedOrder) { parseOrderNotes(selectedOrder!!.notes) }
        val rate = (notes["rate"] as? Number)?.toDouble() ?: 0.0
        val currency = notes["currency"] as? String ?: ""

        AlertDialog(
            onDismissRequest = { selectedOrder = null },
            title = { Text("Materiales Faltantes") },
            text = {
                Column {
                    if (rate > 0) {
                        Text("Tarifa: $currency ${com.drywall.calculator.utils.NumberFormatter.format(rate, precision)}/m²",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items.forEach { item ->
                        ListItem(
                            headlineContent = { Text(item.materialName) },
                            supportingContent = { Text("Requerido: ${com.drywall.calculator.utils.NumberFormatter.format(item.quantityRequired, precision)}") }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedOrder = null }) { Text("Cerrar") } }
        )
    }
}
