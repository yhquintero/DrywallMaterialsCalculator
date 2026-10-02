# Plan de Implementación: Exportación PDF y Mejoras en Licencia

Este plan detalla la implementación de la exportación a PDF del Estado de Resultados, ajustes en la interfaz de usuario para los botones de acción y la corrección del sistema de licencias para que persista correctamente tras la reinstalación.

## User Review Required

> [!IMPORTANT]
> El sistema de licencias utiliza `MediaStore` para persistir un marcador en el dispositivo que sobrevive a la desinstalación. La corrección asegurará que si un usuario reinstala la app durante su período de prueba de 7 días, este pueda continuar usándola hasta que expire, en lugar de quedar bloqueado o resetear el tiempo.

## Proposed Changes

### [app]

#### [MODIFY] [LicensingManager.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/utils/security/LicensingManager.kt)
- Corregir `syncTrialVault` para restaurar el estado `trialActive` si el período de prueba no ha expirado y no hay registro de consumo previo, permitiendo la continuidad tras reinstalación.

#### [MODIFY] [IncomeStatementViewModel.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/incomestatement/IncomeStatementViewModel.kt)
- Agregar función `exportToPdf(context: Context)` que utilice `PdfGenerator` para generar el reporte y notificar al usuario (vía snackbar o abriendo el archivo).

#### [MODIFY] [IncomeStatementScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/incomestatement/IncomeStatementScreen.kt)
- Ajustar el `floatingActionButton` del `Scaffold` para mostrar una fila con dos botones: "Guardar" y "PDF".
- Usar `Modifier.weight(1f)` en ambos botones para maximizar su ancho.
- Asegurar que la ubicación sea en la parte inferior y no interfiera con el scroll (ajustando el padding de `LazyColumn`).

## Verification Plan

### Automated Tests
- Verificar compilación tras los cambios.
- `analyze_file` en los archivos modificados.

### Manual Verification
1. **PDF**: Abrir Estado de Resultados, pulsar el botón PDF y verificar que se genera el archivo correctamente.
2. **UI**: Verificar que los botones en la parte inferior se ven bien y el scroll permite ver todo el contenido.
3. **Licencia**:
   - Activar prueba.
   - Desinstalar la app.
   - Reinstalar y verificar que el tiempo restante es correcto y la prueba sigue activa.
   - Verificar que al pasar los 7 días (simulando tiempo o esperando), el acceso se bloquea.
