# Walkthrough - Integración Total de Reporte Financiero

Se han completado los cambios para integrar el módulo de **Reporte Financiero** como una parte central de la aplicación, asegurando un flujo de trabajo profesional y validaciones de marca.

## Cambios Principales

### [app] - Interfaz y Navegación Centralizada

#### [MainActivity.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/MainActivity.kt)
- **Barra de Menú:** Se reemplazó "Estado R." por **"Reporte F."** en el menú lateral y la barra flotante.
- **Títulos:** La aplicación ahora muestra "Reporte Financiero" en la barra superior al estar en este módulo.

#### [FinancialReportsScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/financial/FinancialReportsScreen.kt)
- **Gestión de Datos:** Se integró el **"Estado de Resultado"** (captura) dentro de la sección de Gestión de Datos, unificando todas las entradas financieras en un solo lugar.
- **Validación Profesional:** Al intentar exportar cualquier PDF, la App verifica si los datos de la empresa están configurados.
- **Alerta de Seguridad:** Si no hay nombre de empresa, se muestra un diálogo informativo con acceso directo a la configuración de Empresa.

### [app] - Lógica de Negocio y Branding

#### [FinancialPdfGenerator.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/utils/FinancialPdfGenerator.kt)
- Se garantizó que los reportes utilicen el nombre real de la empresa y el logotipo cargado en lugar de valores genéricos.

#### [AppNavigation.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/navigation/AppNavigation.kt)
- Se actualizaron las rutas para soportar la navegación bidireccional entre Reportes, Captura y Configuración de Empresa.

## Verificación de Flujo de Usuario

1.  **Entrada:** Pulsa "Reporte F." en la barra inferior.
2.  **Captura:** Ve a "Gestión de Datos" para llenar tu Catálogo, Movimientos o el Estado de Resultado.
3.  **Exportación:** Pulsa cualquier botón de exportación arriba.
    - *Si no has configurado tu empresa:* Recibirás una alerta invitándote a hacerlo.
    - *Si ya la configuraste:* El PDF se generará con tu logo a la derecha y tu nombre comercial de forma automática.

> [!IMPORTANT]
> Recuerda que para que el PDF luzca profesional con el logo a la derecha, debes subir una imagen en la sección **Empresa**.
