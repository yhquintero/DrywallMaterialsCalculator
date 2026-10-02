package com.drywall.calculator.presentation.ui.pdf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.repository.CompanyRepository
import com.drywall.calculator.data.repository.ProjectRepository
import com.drywall.calculator.data.repository.TaxRepository
import com.drywall.calculator.data.repository.MaterialRepository
import com.drywall.calculator.data.repository.CalculationRepository
import com.drywall.calculator.data.repository.MaterialMeasurementRepository
import com.drywall.calculator.data.repository.LaborPriceRepository
import com.drywall.calculator.presentation.ui.calculator.ConstructionBlock
import com.drywall.calculator.utils.PdfGenerator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfGenerator: PdfGenerator,
    private val projectRepository: ProjectRepository,
    private val companyRepository: CompanyRepository,
    private val taxRepository: TaxRepository,
    private val materialRepository: MaterialRepository,
    private val calculationRepository: CalculationRepository,
    private val measurementRepository: MaterialMeasurementRepository,
    private val laborPriceRepository: LaborPriceRepository,
    private val appConfigRepository: com.drywall.calculator.data.repository.AppConfigRepository
) : ViewModel() {

    private val _pdfFile = MutableSharedFlow<File>()
    val pdfFile: SharedFlow<File> = _pdfFile.asSharedFlow()

    val projects = projectRepository.getAllProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val companyProfile = companyRepository.getCompanyProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val appConfig = appConfigRepository.getConfig().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val taxSetting = taxRepository.getTaxSetting().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val measurements = measurementRepository.getMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun generateQuotation(
        context: Context,
        projectName: String,
        clientName: String,
        totalM2: Double,
        items: List<PdfGenerator.InvoiceItem>,
        totalCost: Double,
        constructionType: String = "",
        specialData: String? = null,
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        generate(context, PdfGenerator.DocType.QUOTATION, projectName, clientName, totalM2, items, totalCost, constructionType = constructionType, specialData = specialData, pageSize = pageSize)
    }
    
    fun generateDocumentForProject(
        context: Context,
        project: com.drywall.calculator.data.local.entity.Project,
        docType: PdfGenerator.DocType,
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        viewModelScope.launch {
            val catalog = materialRepository.getAllMaterials().first()
            val currentMeasurements = measurements.value
            val laborPrices = laborPriceRepository.getLaborPrices().first()
            
            val items = mutableListOf<PdfGenerator.InvoiceItem>()
            
            if (project.constructionType == "Especial (Multi-Partes)" && project.specialPartsJson.isNotBlank()) {
                try {
                    val type = object : TypeToken<List<ConstructionBlock>>() {}.type
                    val blocks: List<ConstructionBlock> = Gson().fromJson(project.specialPartsJson, type)
                    
                    val rate = project.specialRate
                    
                    blocks.forEachIndexed { bIdx, block ->
                        val bName = if (block.name.isNotBlank()) block.name else "Bloque #${bIdx + 1}"
                        block.segments.forEach { seg ->
                            items.add(PdfGenerator.InvoiceItem(
                                name = seg.name,
                                qtyPerM2 = seg.length * seg.width,
                                totalQty = seg.subtotal,
                                unitPrice = rate,
                                totalPrice = seg.subtotal * rate,
                                unitType = if (currentMeasurements?.firstOrNull()?.unitSystem == "imperial") "ft²" else "m²",
                                groupName = bName,
                                segLength = seg.length,
                                segWidth = seg.width,
                                segRepetitions = seg.repetitions
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to regular calculation if JSON fails
                }
            }
            
            if (items.isEmpty()) {
                val reqs = calculationRepository.calculateRequirements(
                    project.constructionType, 
                    project.totalAreaM2, 
                    catalog,
                    currentMeasurements?.firstOrNull()
                )
                items.addAll(reqs.map { PdfGenerator.InvoiceItem(it.materialName, it.quantityPerM2, it.totalQuantity, it.unitPrice, it.totalPrice) })
            }
            
            // Add Labor Price as an item if available
            laborPrices?.let { lp ->
                val unitPrice = when (project.constructionType) {
                    "Tabique Interior" -> lp.salePriceInterior()
                    "Tabique Exterior" -> lp.salePriceExterior()
                    "Cielo Raso Descolgado" -> lp.salePriceDropCeiling()
                    "Cielo Raso de Baldosa" -> lp.salePriceTileCeiling()
                    else -> 0.0
                }
                if (unitPrice > 0) {
                    items.add(PdfGenerator.InvoiceItem(
                        name = "Mano de Obra (${project.constructionType})",
                        qtyPerM2 = 1.0,
                        totalQty = project.totalAreaM2,
                        unitPrice = unitPrice,
                        totalPrice = project.totalAreaM2 * unitPrice,
                        unitType = "m²"
                    ))
                }
            }
            
            val totalCost = items.sumOf { it.totalPrice }
            
            if (docType == PdfGenerator.DocType.MEASUREMENTS_REPORT) {
                val measurements = mapOf(
                    "Altura" to project.height,
                    "Largo" to project.length,
                    "Ancho" to project.width,
                    "Área Total" to project.totalAreaM2
                )
                val specialData = if (project.constructionType == "Especial (Multi-Partes)") project.specialPartsJson else null
                generate(context, docType, project.name, project.clientName, project.totalAreaM2, items, 0.0, measurementsInfo = measurements, constructionType = project.constructionType, specialData = specialData, pageSize = pageSize)
            } else {
                val specialData = if (project.constructionType == "Especial (Multi-Partes)") project.specialPartsJson else null
                generate(context, docType, project.name, project.clientName, project.totalAreaM2, items, totalCost, constructionType = project.constructionType, specialData = specialData, pageSize = pageSize)
            }
        }
    }

    fun generatePurchaseOrder(
        context: Context,
        projectName: String,
        clientName: String,
        totalM2: Double,
        items: List<PdfGenerator.InvoiceItem>,
        totalCost: Double,
        constructionType: String = "",
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        generate(context, PdfGenerator.DocType.PURCHASE_ORDER, projectName, clientName, totalM2, items, totalCost, constructionType = constructionType, pageSize = pageSize)
    }

    fun generateFinalInvoice(
        context: Context,
        projectName: String,
        clientName: String,
        totalM2: Double,
        items: List<PdfGenerator.InvoiceItem>,
        totalCost: Double,
        constructionType: String = "",
        specialData: String? = null,
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        generate(context, PdfGenerator.DocType.FINAL_INVOICE, projectName, clientName, totalM2, items, totalCost, constructionType = constructionType, specialData = specialData, pageSize = pageSize)
    }

    fun generateMeasurementsReport(
        context: Context,
        projectName: String,
        clientName: String,
        totalM2: Double,
        measurements: Map<String, Double>,
        constructionType: String = "",
        specialData: String? = null,
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        generate(context, PdfGenerator.DocType.MEASUREMENTS_REPORT, projectName, clientName, totalM2, emptyList(), 0.0, measurementsInfo = measurements, constructionType = constructionType, specialData = specialData, pageSize = pageSize)
    }

    fun generateIdCard(
        context: Context,
        companyName: String,
        logoPath: String?,
        ownerPhotoPath: String?,
        signaturePath: String?,
        ownerName: String,
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        viewModelScope.launch {
            val file = pdfGenerator.generateIdCard(context, companyName, logoPath, ownerPhotoPath, signaturePath, ownerName, pageSize = pageSize)
            _pdfFile.emit(file)
        }
    }

    fun generateTechnicalReport(
        context: Context,
        projectName: String,
        clientName: String,
        totalM2: Double,
        blocks: List<ConstructionBlock>,
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        viewModelScope.launch {
            val profile = companyProfile.value
            val config = appConfig.value
            val precision = config?.decimalPrecision ?: 4
            
            val items = blocks.flatMapIndexed { bIdx, block ->
                val bName = if (block.name.isNotBlank()) block.name else "BLOQUE #${bIdx + 1}"
                block.segments.map { seg ->
                    PdfGenerator.InvoiceItem(
                        name = seg.name,
                        qtyPerM2 = seg.length * seg.width,
                        totalQty = seg.subtotal,
                        unitPrice = seg.length,
                        totalPrice = seg.width,
                        unitType = "m²",
                        groupName = bName,
                        segLength = seg.length,
                        segWidth = seg.width,
                        segRepetitions = seg.repetitions
                    )
                }
            }

            val simplifiedBlocks = blocks.map { b -> 
                b.copy(id = "", segments = b.segments.map { s -> s.copy(id = "") }) 
            }
            val jsonToEmbed = Gson().toJson(simplifiedBlocks)

            val measurements = mapOf<String, Double>("Área Total" to totalM2)

            val file = pdfGenerator.generateDocument(
                context = context,
                docType = PdfGenerator.DocType.MEASUREMENTS_REPORT,
                companyName = profile?.businessName ?: "Mi Empresa Drywall",
                logoPath = profile?.logoUri,
                projectName = projectName,
                clientName = clientName,
                totalM2 = totalM2,
                items = items,
                totalCost = 0.0,
                taxInfo = "REPORTE TÉCNICO DE IMPORTACIÓN",
                measurementsInfo = measurements,
                bankInfo = emptyMap<String, String>(),
                grandTotal = 0.0,
                constructionType = "Especial (Multi-Partes)",
                decimalPrecision = precision,
                specialData = jsonToEmbed,
                pageSize = pageSize
            )
            _pdfFile.emit(file)
        }
    }

    private fun generate(
        context: Context,
        docType: PdfGenerator.DocType,
        projectName: String,
        clientName: String,
        totalM2: Double,
        items: List<PdfGenerator.InvoiceItem>,
        totalCost: Double,
        measurementsInfo: Map<String, Double> = emptyMap(),
        constructionType: String = "",
        specialData: String? = null,
        pageSize: PdfGenerator.PageSize = PdfGenerator.PageSize.A4
    ) {
        viewModelScope.launch {
            val profile = companyProfile.value
            val config = appConfig.value
            val company = profile?.businessName ?: "Mi Empresa Drywall"
            val logo = profile?.logoUri
            val precision = config?.decimalPrecision ?: 4
            val taxSettingValue = taxRepository.getTaxSetting().first()
            
            val taxAmount = if (taxSettingValue != null) totalCost * (taxSettingValue.percentage / 100.0) else 0.0
            val totalWithTax = totalCost + taxAmount
            
            val taxLabel = if (taxSettingValue != null) {
                "${taxSettingValue.taxType} (${taxSettingValue.percentage}%): $${String.format(Locale.getDefault(), "%,.${precision}f", taxAmount)}"
            } else ""
            
            val bankData = if (profile != null && profile.accountNumber.isNotBlank()) {
                mapOf(
                    "type" to profile.bankType,
                    "account" to profile.accountNumber,
                    "currency" to profile.currencyType,
                    "mobile" to profile.mobileBank
                )
            } else emptyMap<String, String>()

            val file = pdfGenerator.generateDocument(
                context = context,
                docType = docType,
                companyName = company,
                logoPath = logo,
                projectName = projectName,
                clientName = clientName,
                totalM2 = totalM2,
                items = items,
                totalCost = totalCost,
                taxInfo = taxLabel,
                measurementsInfo = measurementsInfo,
                bankInfo = bankData,
                grandTotal = totalWithTax,
                constructionType = constructionType,
                decimalPrecision = precision,
                specialData = specialData,
                pageSize = pageSize
            )
            _pdfFile.emit(file)
        }
    }
}
