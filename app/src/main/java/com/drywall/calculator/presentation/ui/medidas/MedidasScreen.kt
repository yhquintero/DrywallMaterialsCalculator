package com.drywall.calculator.presentation.ui.medidas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.domain.measure.*
import com.drywall.calculator.presentation.ui.components.ValidatedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedidasScreen(
    navController: NavController,
    onShowSnackbar: (String) -> Unit,
    viewModel: MedidasViewModel = hiltViewModel()
) {
    val searchQuery = viewModel.searchQuery
    val selectedCategory = viewModel.selectedCategory
    val selectedItem = viewModel.selectedItem
    val selectedMeasure = viewModel.selectedMeasure

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-cabecera interna para navegación dentro del catálogo
        if (selectedMeasure != null || selectedItem != null || selectedCategory != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        when {
                            selectedMeasure != null -> viewModel.onMeasureSelected(null)
                            selectedItem != null -> viewModel.onItemSelected(null)
                            selectedCategory != null -> viewModel.onCategorySelected(null)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                    Text(
                        text = if (selectedMeasure != null) selectedMeasure.name
                        else if (selectedItem != null) selectedItem.name
                        else if (selectedCategory != null) selectedCategory.displayName
                        else "Catálogo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (selectedCategory != null && selectedItem == null && selectedMeasure == null) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filtrar")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                StandardRegion.entries.forEach { std ->
                                    DropdownMenuItem(
                                        text = { Text(std.displayName) },
                                        onClick = {
                                            viewModel.onStandardSelected(std)
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            if (std == viewModel.selectedStandard) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedMeasure != null) {
                MeasureDetailContent(selectedMeasure)
            } else if (selectedItem != null) {
                ItemMeasuresList(
                    measures = viewModel.getMeasuresForSelectedItem(),
                    onMeasureClick = { viewModel.onMeasureSelected(it) }
                )
            } else if (selectedCategory != null) {
                CategoryMeasuresScreen(
                    viewModel = viewModel,
                    onItemClick = { viewModel.onItemSelected(it) }
                )
            } else {
                MainCatalogScreen(
                    viewModel = viewModel,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onCategoryClick = { viewModel.onCategorySelected(it) },
                    onMeasureClick = { viewModel.onMeasureSelected(it) }
                )
            }
        }
    }
}

@Composable
private fun MainCatalogScreen(
    viewModel: MedidasViewModel,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryClick: (MaterialCategory) -> Unit,
    onMeasureClick: (MaterialMeasure) -> Unit
) {
    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else viewModel.searchMeasures(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Buscar materiales, medidas, c\u00f3digos...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (searchQuery.isNotBlank()) {
            SearchResultsList(searchResults = searchResults, onMeasureClick = onMeasureClick)
        } else {
            CategoryGrid(
                categories = viewModel.categories,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    searchResults: List<MaterialMeasure>,
    onMeasureClick: (MaterialMeasure) -> Unit
) {
    if (searchResults.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron resultados", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults) { measure ->
                MeasureSearchCard(measure = measure, onClick = { onMeasureClick(measure) })
            }
        }
    }
}

@Composable
private fun MeasureSearchCard(measure: MaterialMeasure, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(measure.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${measure.category.displayName} • ${measure.standard.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Medidas organizadas verticalmente
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                measure.values.forEach { v ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = v.dimension,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = v.formatted(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<MaterialCategory>,
    onCategoryClick: (MaterialCategory) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories) { category ->
            CategoryCard(category = category, onClick = { onCategoryClick(category) })
        }
    }
}

@Composable
private fun CategoryCard(
    category: MaterialCategory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(100.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                category.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryMeasuresScreen(
    viewModel: MedidasViewModel,
    onItemClick: (MaterialItem) -> Unit
) {
    val group = viewModel.groups.find { it.category == viewModel.selectedCategory }
    val measures = viewModel.getMeasuresByCategory(viewModel.selectedCategory!!)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        group?.let { g ->
            item {
                Text(
                    g.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(g.materials) { item ->
                MaterialItemCard(item = item, onClick = { onItemClick(item) })
            }

            if (g.materials.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Todas las medidas (${measures.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        items(measures) { measure ->
            MeasureSearchCard(measure = measure, onClick = { viewModel.onMeasureSelected(measure) })
        }
    }
}

@Composable
private fun MaterialItemCard(item: MaterialItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                if (item.description.isNotBlank()) {
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.colorDescription.isNotBlank()) {
                    Text(
                        "Color: ${item.colorDescription}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                "${item.measures.size} medidas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ItemMeasuresList(
    measures: List<MaterialMeasure>,
    onMeasureClick: (MaterialMeasure) -> Unit
) {
    if (measures.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay medidas registradas para este material",
                style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(measures) { measure ->
                MeasureSearchCard(measure = measure, onClick = { onMeasureClick(measure) })
            }
        }
    }
}

@Composable
private fun MeasureDetailContent(measure: MaterialMeasure) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(measure.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dimensiones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    measure.values.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(v.dimension, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                v.formatted(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("C\u00f3digo", measure.id)
                    DetailRow("Categor\u00eda", measure.category.displayName)
                    DetailRow("Est\u00e1ndar", measure.standard.displayName)
                    DetailRow("Regi\u00f3n", measure.standard.code)
                    measure.consumptionRate?.let {
                        DetailRow("Consumo", "%.2f pz/m\u00b2".format(it))
                    }
                }
            }
        }

        if (measure.description.isNotBlank()) {
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Descripci\u00f3n", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(measure.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Etiquetas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        measure.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
