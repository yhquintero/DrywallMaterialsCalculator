# Correcciones en Modal de Costos y Tarjeta de Estiba

Este plan detalla los ajustes para corregir la visualización del modal de costos y las funcionalidades pendientes en la Tarjeta de Estiba.

## User Review Required

> [!IMPORTANT]
> Se cambiará el diseño de las tarjetas de tipo de construcción en el modal de costos para que los datos se presenten de forma horizontal, mejorando la legibilidad en pantallas estrechas.

## Proposed Changes

### [Componente: Calculadora (App)]

#### [MODIFY] [CalculatorScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/calculator/CalculatorScreen.kt)
- **Modal "Aplicar Costos"**:
    - Envolver el contenido principal del diálogo en un `Column` con `verticalScroll` para asegurar que todos los datos sean accesibles.
    - Rediseñar `M2TypeCard` para mostrar la información de forma horizontal (Uso de `Row` y `FlowRow` o mejor distribución de espacio).
    - Asegurar que los textos no se corten y se vean profesionales.

---

### [Componente: Almacén / Inventario (App)]

#### [MODIFY] [InventoryScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/inventory/InventoryScreen.kt)
- **Conectar Acciones**: Pasar el callback `onAddEntry` a `StockCardScreen` para que los botones de (+) Entrada funcionen correctamente invocando `viewModel.addStock`.
- **Nuevo Vale de Salida**: Implementar la lógica para manejar vales de salida directamente desde la tarjeta de estiba.

#### [MODIFY] [StockCardScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/inventory/StockCardScreen.kt)
- **Botón Nuevo Vale de Salida**: Añadir un botón rojo (-) junto al de entrada (+) para registrar salidas rápidamente.
- **Visualización de Motivo**: Centrar el texto del motivo en la tabla y organizarlo en una columna para mejor presentación.
- **Actualización de Stock**: Asegurar que la cabecera de "Stock actual" refleje los cambios inmediatamente al guardar una edición o agregar un nuevo vale.
- **Corrección de Diálogo**: Asegurar que al hacer clic en "Confirmar" en los diálogos de nuevos vales, el flujo se complete, se guarde el valor y se cierre el modal.

---

## Verification Plan

### Manual Verification
1. Abrir el modal "Aplicar Costos" en la calculadora y verificar que el texto sea horizontal y el contenido tenga scroll vertical.
2. Abrir la Tarjeta de Estiba de un material.
3. Probar el botón (+) para agregar una entrada y verificar que se guarde y actualice el stock.
4. Probar el nuevo botón (-) para agregar una salida y verificar lo mismo.
5. Editar una transacción existente y comprobar que el "Stock actual" en la cabecera cambie en tiempo real según el valor editado.
6. Verificar que la columna "Motivo" en la tabla esté centrada y bien presentada.
