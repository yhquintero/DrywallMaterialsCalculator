# Plan de Corrección de UI, Almacén y Exportaciones PDF

Este plan detalla los ajustes necesarios para unificar el comportamiento de exportación a PDF en toda la aplicación, siguiendo el patrón de Keygen (Modal de Tamaño de Hoja -> Botón Generar), y corregir errores visuales en la sección de Almacén.

## User Review Required

> [!IMPORTANT]
> Se cambiará el color del botón "Vale de Salida" en el Almacén a Rojo para consistencia con la Tarjeta de Estiba.
> Todas las acciones de exportación a PDF ahora mostrarán primero un diálogo de selección de tamaño de hoja con un botón de "Generar" al final, eliminando menús desplegables directos o comportamientos inconsistentes.

## Proposed Changes

### [Módulo: Inventario / Almacén]

#### [MODIFY] [InventoryScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/inventory/InventoryScreen.kt)
- **Color de Botones**: Actualizar el color de fondo del botón "Vale de Salida" a `MaterialTheme.colorScheme.error` (Rojo).
- **Corrección de Cabecera**: Eliminar cualquier icono redundante y asegurar que el botón de impresora (`Print`) abra el diálogo de selección de hoja.
- **Unificación de Diálogo**: Asegurar que el diálogo de tamaño de hoja tenga el botón "Generar" abajo, similar a Keygen.

### [Módulo: Calculadora]

#### [MODIFY] [CalculatorScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/calculator/CalculatorScreen.kt)
- **Flujo de Exportación**: Al pulsar "Generar PDF" en el diálogo de costos, mostrar el diálogo de tamaño de hoja antes de realizar la generación real.
- **DocumentButton**: Actualizar para que el icono de impresora principal abra la selección de hoja si es necesario, o que la lógica de llamada lo maneje.

### [Módulo: Dashboard / Estadísticas / Carnet]

#### [MODIFY] [DashboardScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/dashboard/DashboardScreen.kt)
- **Exportar Resumen**: Cambiar el comportamiento para que muestre el modal de tamaño de hoja antes de abrir el PDF.

#### [MODIFY] [StatsScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/stats/StatsScreen.kt)
- **Exportar a PDF**: Implementar el modal de tamaño de hoja.

#### [MODIFY] [IdCardScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/idcard/IdCardScreen.kt)
- **Generar PDF**: Implementar el modal de tamaño de hoja.

### [Módulo: Órdenes de Compra]

#### [MODIFY] [PurchaseOrdersScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/orders/PurchaseOrdersScreen.kt)
- **Consistencia**: Verificar que el diálogo existente coincida con el nuevo estándar de la aplicación.

## Verification Plan

### Automated Tests
- Ejecutar `./gradlew :app:assembleDebug` para asegurar que no hay errores de compilación.

### Manual Verification
- **Almacén**: Verificar que el botón de "Vale de Salida" es rojo. Verificar que el botón de la cabecera no se repite y abre el modal de tamaño de hoja.
- **Calculadora**: Probar la generación de Cotización y Factura, verificando que aparece el modal de tamaño de hoja y el botón "Generar".
- **Dashboard/Stats**: Probar las exportaciones y verificar la consistencia del modal.
