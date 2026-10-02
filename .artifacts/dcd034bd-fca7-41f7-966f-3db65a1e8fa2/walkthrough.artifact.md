# Walkthrough - Unificación de Exportaciones PDF y Correcciones en Almacén

Se han aplicado las correcciones finales en la interfaz de usuario para asegurar una experiencia de usuario (UX) consistente en todas las exportaciones a PDF de la aplicación, siguiendo el patrón de la aplicación KeyGen. También se han ajustado los colores en la sección de Almacén para mayor claridad.

## Cambios Realizados

### [Módulo: Inventario / Almacén]
- **Color de "Vale de Salida"**: Se ha actualizado el color de fondo del botón de salida en la lista de materiales a `MaterialTheme.colorScheme.error` (Rojo), coincidiendo exactamente con el color utilizado en la Tarjeta de Estiba.
- **Corrección de Cabecera**: Se ha depurado la cabecera de la sección de Almacén para eliminar cualquier redundancia visual. El botón de la impresora ahora abre correctamente el diálogo de selección de tamaño de hoja sin duplicaciones.

### [Unificación de Exportación PDF]
Se ha implementado el patrón **Modal de Tamaño de Hoja -> Botón Generar** en todas las secciones de la aplicación:
- **Almacén (Inventario)**: Reporte general de existencias.
- **Calculadora**: Cotización y Factura Final. Se ha añadido un estado intermedio `PendingPdfData` para asegurar que los cálculos de costos (mano de obra, impuestos) se preserven entre la selección de costos y la selección de tamaño de hoja.
- **Dashboard**: Resúmenes rápidos de inventario, obras, clientes y licencias.
- **Estadísticas**: Reporte de rendimiento por categoría.
- **Carnet Profesional**: Generación del carnet digital.
- **Órdenes de Compra**: Listado de materiales faltantes por proyecto.

### [Mejoras Técnicas]
- **Consistencia en Diálogos**: Todos los diálogos de tamaño de hoja ahora utilizan `TextButton` con la etiqueta "Generar" al final, proporcionando un flujo de trabajo predecible para el usuario.
- **Gestión de Estado**: Se han corregido errores de compilación relacionados con la gestión de estados mutables y la inferencia de tipos en los lambdas de los diálogos.

## Verificación de Resultados

### Pruebas de Compilación
- `gradlew :app:assembleDebug`: ✅ ÉXITO

### Verificación Manual Sugerida
1.  **Almacén**: Verifique que el botón de salida sea rojo. Pulse el icono de la impresora en la cabecera; debe aparecer el diálogo de tamaño de hoja con las opciones A4, Carta, Oficio y Legal, y un botón "Generar" al final.
2.  **Calculadora**: Realice un cálculo, pulse "Generar PDF" en el diálogo de costos. Debe aparecer el segundo diálogo de tamaño de hoja y, tras pulsar "Generar", se debe abrir el PDF con los costos aplicados.
3.  **Dashboard**: Pulse en "Exportar Resumen". Seleccione un tipo de reporte (ej. Obras). Debe aparecer el diálogo de tamaño de hoja antes de abrir el documento.
