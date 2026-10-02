# Plan de Implementación - Integración de Reporte Financiero y Branding Profesional

Este plan detalla los pasos para integrar completamente el "Reporte Financiero" en la navegación principal, mover el "Estado de Resultado" a la gestión de datos y asegurar que la información de la empresa sea obligatoria para exportar PDFs.

## Propuestas de Cambio

### [app] - Navegación Principal

#### [MODIFY] [MainActivity.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/MainActivity.kt)
- Reemplazar el ítem de menú "Estado R." por "Reporte F." (Ruta: `financial_reports`).
- Actualizar la lógica de `currentTitle` para que la ruta `financial_reports` muestre "Reporte Financiero".

### [app] - Pantalla de Reportes Financieros

#### [MODIFY] [FinancialReportsScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/financial/FinancialReportsScreen.kt)
- Actualizar el título de la Top Bar a "Reporte Financiero".
- **Gestión de Datos:** Añadir una nueva `NavigationCard` para "Estado de Resultado" que permita capturar los datos financieros del período.
- **Validación de Empresa:**
    - Al intentar exportar cualquier reporte, verificar si el nombre de la empresa está vacío.
    - Si está vacío, mostrar un `AlertDialog` informativo que indique la necesidad de completar los datos de la empresa y proporcione un botón para navegar directamente a la pantalla "Empresa".
- **Integración:** Añadir el parámetro `onNavigateToIncomeStatement` a la función composable.

### [app] - Lógica de Negocio

#### [MODIFY] [AppNavigation.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/navigation/AppNavigation.kt)
- Pasar el callback `onNavigateToIncomeStatement = { navController.navigate("income_statement") }` a `FinancialReportsScreen`.

## Verificación Plan

### Pruebas Manuales
1. **Navegación:** Verificar que en la barra inferior/menú aparezca "Reporte F." y al pulsarlo abra la pantalla de reportes con el título "Reporte Financiero".
2. **Gestión de Datos:** Entrar en "Reporte Financiero" -> "Gestión de Datos" y verificar que aparezca "Estado de Resultado" y funcione la navegación.
3. **Validación de Datos:**
    - Ir a "Empresa" y borrar el nombre de la empresa.
    - Intentar exportar un PDF en "Reporte Financiero".
    - Debería aparecer la alerta. Al pulsar "Completar Datos", debería llevar a la pantalla de configuración de Empresa.
4. **Branding:** Completar los datos de la empresa (incluyendo logo) y verificar que el PDF se genera con el logo a la derecha y el nombre correcto de la empresa (no "Mi Empresa").
