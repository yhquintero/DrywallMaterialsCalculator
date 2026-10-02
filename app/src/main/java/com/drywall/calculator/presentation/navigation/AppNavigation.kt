package com.drywall.calculator.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.drywall.calculator.presentation.ui.bank.BankAccountsScreen
import com.drywall.calculator.presentation.ui.calculator.CalculatorScreen
import com.drywall.calculator.presentation.ui.clients.ClientsScreen
import com.drywall.calculator.presentation.ui.company.CompanyScreen
import com.drywall.calculator.presentation.ui.converter.UnitConverterScreen
import com.drywall.calculator.presentation.ui.currency.CurrencyConverterScreen
import com.drywall.calculator.presentation.ui.dashboard.DashboardScreen
import com.drywall.calculator.presentation.ui.diary.DiaryScreen
import com.drywall.calculator.presentation.ui.gallery.GalleryScreen
import com.drywall.calculator.presentation.ui.idcard.IdCardScreen
import com.drywall.calculator.presentation.ui.incomestatement.IncomeStatementScreen
import com.drywall.calculator.presentation.ui.inventory.InventoryScreen
import com.drywall.calculator.presentation.ui.labor.LaborPriceScreen
import com.drywall.calculator.presentation.ui.maintenance.MaintenanceScreen
import com.drywall.calculator.presentation.ui.materials.MaterialsListScreen
import com.drywall.calculator.presentation.ui.measurements.MaterialMeasurementsScreen
import com.drywall.calculator.presentation.ui.medidas.MedidasScreen
import com.drywall.calculator.presentation.ui.money.MoneyCalculatorScreen
import com.drywall.calculator.presentation.ui.orders.PurchaseOrdersScreen
import com.drywall.calculator.presentation.ui.pdf.PdfExportScreen
import com.drywall.calculator.presentation.ui.projects.ProjectsScreen
import com.drywall.calculator.presentation.ui.providers.ProvidersScreen
import com.drywall.calculator.presentation.ui.settings.AppSettingsScreen
import com.drywall.calculator.presentation.ui.settings.ErrorLogScreen
import com.drywall.calculator.presentation.ui.settings.LicenseManagementScreen
import com.drywall.calculator.presentation.ui.settings.ReadmeScreen
import com.drywall.calculator.presentation.ui.sketchup.SketchUpImportScreen
import com.drywall.calculator.presentation.ui.stats.StatsScreen
import com.drywall.calculator.presentation.ui.tax.TaxScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onLicenseInvalidated: () -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier
    ) {
        composable("dashboard") { DashboardScreen(navController = navController) }
        composable("company") { CompanyScreen(navController = navController, onShowSnackbar = onShowSnackbar) }
        composable("bank") { BankAccountsScreen(onShowSnackbar = onShowSnackbar) }
        composable("clients") { ClientsScreen() }
        composable("tax") { TaxScreen(navController = navController, onShowSnackbar = onShowSnackbar) }
        composable("projects") { ProjectsScreen() }
        composable("labor") { LaborPriceScreen(navController = navController, onShowSnackbar = onShowSnackbar) }
        composable("measurements") { MaterialMeasurementsScreen(navController = navController, onShowSnackbar = onShowSnackbar) }
        composable("materials") { MaterialsListScreen(navController) }
        composable("calculator") { CalculatorScreen(navController) }
        composable("inventory") { InventoryScreen(navController) }
        composable("orders") { PurchaseOrdersScreen() }
        composable("converter") { UnitConverterScreen(navController = navController) }
        composable("currency") { CurrencyConverterScreen(navController) }
        composable("sketchup") { SketchUpImportScreen(navController) }
        composable("pdf") { PdfExportScreen(navController) }
        composable("money_calculator") { MoneyCalculatorScreen() }
        composable("settings") {
            AppSettingsScreen(
                navController = navController,
                onNavigateToLicense = { navController.navigate("license_management") },
                onNavigateToErrorLog = { navController.navigate("error_log") },
                onShowSnackbar = onShowSnackbar
            )
        }
        composable("license_management") {
            LicenseManagementScreen(
                onLicenseRemoved = {
                    onLicenseInvalidated()
                    navController.popBackStack("dashboard", inclusive = false)
                },
                onNavigateBack = {
                    if (!navController.popBackStack("settings", inclusive = false)) {
                        navController.navigate("settings") {
                            launchSingleTop = true
                            popUpTo("dashboard") { inclusive = false }
                        }
                    }
                }
            )
        }
        composable("stats") { StatsScreen() }
        composable("readme") { ReadmeScreen() }
        composable("providers") { ProvidersScreen() }
        composable("diary") { DiaryScreen() }
        composable("gallery") { GalleryScreen() }
        composable("id_card") { IdCardScreen() }
        composable("income_statement") { IncomeStatementScreen() }
        composable("financial_reports") {
            com.drywall.calculator.presentation.ui.financial.FinancialReportsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCuentas = { navController.navigate("cuentas_contables") },
                onNavigateToMovimientos = { navController.navigate("movimientos_contables") },
                onNavigateToFondos = { navController.navigate("fondos_inversion") },
                onNavigateToIncomeStatement = { navController.navigate("income_statement") },
                onNavigateToCompany = { navController.navigate("company") }
            )
        }
        composable("cuentas_contables") {
            com.drywall.calculator.presentation.ui.financial.CuentasContablesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("movimientos_contables") {
            com.drywall.calculator.presentation.ui.financial.MovimientosContablesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("fondos_inversion") {
            com.drywall.calculator.presentation.ui.financial.FondosInversionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("error_log") { ErrorLogScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("medidas") { MedidasScreen(navController = navController, onShowSnackbar = onShowSnackbar) }
        composable("maintenance") { MaintenanceScreen(navController = navController) }
    }
}
