# Plan de Implementación - Arreglo Crítico de Respaldo y Restauración

Este plan aborda los fallos reportados en el sistema de recuperación de datos: el aviso de "base de datos ilegible" en nuevos respaldos, los errores visuales persistentes tras el reinicio y el bloqueo de operaciones posteriores ("Job was cancelled").

## Análisis de Causas Raíz

1.  **Omisión de Preferencias Seguras en Restauración**: La versión de flujo (stream) de `importFullBackup` no estaba restaurando el archivo `prefs_state.json`. Esto causaba que, aunque se recuperaran los archivos, las claves de cifrado (`db_passphrase`) no se actualizaran, haciendo que la base de datos fuera ilegible para la aplicación si provenía de una instalación con claves diferentes.
2.  **Falta de Sincronización tras Restauración**: Al no restaurar las preferencias, el estado interno de la aplicación (incluyendo el tema y licencias) quedaba desincronizado con la base de datos restaurada.
3.  **Conflictos de Archivos Auxiliares**: Archivos como `drywall_db-wal` o `drywall_db-shm` pueden interferir si no se limpian adecuadamente antes y después de la restauración.
4.  **Cierre Incompleto de Conexiones**: Aunque se llamaba a `resetInstance()`, es posible que Room o SQLCipher mantuvieran bloqueos sobre el archivo si la operación de restauración ocurría demasiado rápido.

## Cambios Propuestos

### Componente: Common (Utilidades)

#### [MODIFY] [DatabaseBackupUtils.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/common/src/main/java/com/drywall/common/utils/DatabaseBackupUtils.kt)
- **`extractBackupEntriesFromStream`**: Implementar la llamada a `restoreSecurePrefsState(context, secureStateJson)` al final de la función para asegurar que las claves y configuraciones se restauren junto con los datos.
- **Limpieza de Archivos**: Asegurar que todos los archivos auxiliares (`-wal`, `-shm`, `-journal`) se eliminen antes de sobreescribir la base de datos para evitar corrupciones de SQLCipher.

### Componente: App (ViewModel y UI)

#### [MODIFY] [RestoreViewModel.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/settings/RestoreViewModel.kt)
- **`performFullBackup`**: Asegurar que si el descifrado falla, se registre el error detalladamente para diagnóstico.
- **`executeRestore`**: Aumentar la robustez del reinicio y asegurar que no haya tareas pendientes que causen el error "Job was cancelled".
- **Gestión de Errores**: Capturar excepciones específicas de SQLCipher para dar sugerencias más precisas al usuario.

#### [MODIFY] [AppSettingsScreen.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/settings/AppSettingsScreen.kt)
- **Mejora Visual**: Asegurar que el diálogo de restauración se cierre limpiamente y no queden estados "fantasma" que bloqueen la UI.
- **Feedback de Reinicio**: Mostrar un diálogo de "Preparando reinicio" más claro.

---

## Plan de Verificación

### Pruebas Manuales
1.  **Restauración Total**: Recuperar un respaldo y verificar que el tema (oscuro/claro) se actualiza al del respaldo tras el reinicio automático.
2.  **Exportación Post-Restauración**: Crear un nuevo respaldo inmediatamente después de restaurar y verificar que este nuevo respaldo es "legible" (analizable) sin errores.
3.  **Detección de Imágenes**: Confirmar que tras la restauración, las fotos de clientes y obras son visibles (reparación de rutas).

### Notas de Seguridad
> [!CAUTION]
> La restauración de preferencias seguras sobrescribe las claves actuales. Esto es necesario para acceder a la base de datos del respaldo, pero puede invalidar licencias temporales si estas no estaban incluidas en el respaldo seleccionado.
