package com.drywall.calculator.presentation.ui.tax

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.TaxSetting
import com.drywall.calculator.utils.TaxUtils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxScreen(
    navController: NavController,
    onShowSnackbar: (String) -> Unit,
    viewModel: TaxViewModel = hiltViewModel()
) {
    val setting by viewModel.taxSetting.collectAsState()
    val companyProfile by viewModel.companyProfile.collectAsState()
    
    var country by remember { mutableStateOf("") }
    var taxType by remember { mutableStateOf("") }
    var percentage by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(setting, companyProfile) {
        if (setting != null) {
            country = setting!!.country
            taxType = setting!!.taxType
            percentage = setting!!.percentage.toString()
        } else if (companyProfile != null) {
            country = companyProfile!!.country
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = country,
                onValueChange = { country = it },
                label = { Text("País") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true // Country comes from Company settings
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = taxType,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    label = { Text("Tipo de Impuesto") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    TaxUtils.getTaxTypesForCountry(country).forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                taxType = type
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = percentage,
                onValueChange = { 
                    val clean = it.replace("\n", "").replace("\r", "")
                    if (clean.isEmpty() || clean.toDoubleOrNull() != null) percentage = clean 
                },
                label = { Text("Porcentaje (%)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
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
                    val p = percentage.toDoubleOrNull() ?: 0.0
                    viewModel.saveTaxSetting(TaxSetting(
                        country = country,
                        taxType = taxType,
                        percentage = p
                    ))
                    onShowSnackbar("Impuesto guardado")
                    navController.popBackStack()
                },
                shape = MaterialTheme.shapes.large
            ) { 
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Impuesto", fontWeight = FontWeight.Bold)
            }
        }
    }
}
