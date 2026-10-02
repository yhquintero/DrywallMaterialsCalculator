# Plan de Mejora: Corrección de Estado de Prueba y Lógica de Expiración

Este plan resuelve el error donde una prueba válida (que vence en el futuro) se muestra como "Expiración Normal" en el historial y permite su uso correcto tras un reseteo.

## User Review Required

> [!IMPORTANT]
> El historial de licencias ahora solo mostrará la prueba como "Expirada" si la fecha actual es realmente posterior a la fecha de vencimiento. Mientras la prueba sea válida (dentro de los 7 días), no aparecerá en el historial de consumidas a menos que haya sido reemplazada por una licencia profesional.

## Proposed Changes

### Lógica de Visualización de Historial

#### [MODIFY] [LicenseViewModel.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/settings/LicenseViewModel.kt)
- Modificar `loadLicense` para que el registro sintético de "Prueba Gratis" solo se añada si `now >= trialEnd`.
- Si `now < trialEnd` y la prueba no está activa pero se detecta que fue usada, se mostrará un estado más descriptivo (ej: "Pendiente de Activación" o "Interrumpida") en lugar de "Expiración Normal".

### Gestión de Licencias

#### [MODIFY] [LicensingManager.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/utils/security/LicensingManager.kt)
- Ajustar `isTrialActive` para que sea más tolerante tras un reseteo manual.
- Asegurar que `debugResetTrial` limpie absolutamente todos los registros, incluyendo los de la bóveda permanente, para que `hasTrialEverBeenUsed` devuelva `false` correctamente y permita un nuevo inicio limpio.

### Interfaz de Usuario

#### [MODIFY] [LicenseManagementScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/settings/LicenseManagementScreen.kt)
- Si la prueba no está activa pero aún no ha vencido, mostrar un botón de "Reactivar Prueba" para que el usuario pueda continuar sus 7 días si hubo un error de guardado previo.

## Verification Plan

### Manual Verification
1.  **Reseteo Limpio**: Usar "Recuperar Prueba" y verificar que el historial queda vacío y la parte superior dice "Active una licencia o prueba...".
2.  **Uso Correcto**: Activar la prueba hoy (15) y verificar que:
    - La pantalla principal tiene las funciones Pro desbloqueadas.
    - El historial **no** muestra "Expiración Normal".
    - El PDF muestra la licencia actual como Válida.
3.  **Prueba de Futuro**: Cambiar la fecha del dispositivo al día 23 y verificar que entonces sí aparece en el historial como "Expiración Normal".
