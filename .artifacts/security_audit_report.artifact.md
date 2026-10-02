# Informe de Auditoría de Seguridad y Mejoras de Licenciamiento

Se ha realizado una revisión exhaustiva del sistema de licencias tras detectarse una vulnerabilidad que permitía la reutilización infinita del período de prueba. Se han aplicado parches críticos en las tres aplicaciones del ecosistema.

## 1. Vulnerabilidades Detectadas y Corregidas

### [ALTA] Exploit de Re-activación de Prueba
- **Problema**: El botón "Eliminar Licencia" en la App principal realizaba una limpieza total de los metadatos de seguridad, incluyendo el marcador de "Prueba Usada".
- **Corrección**: Se implementó `removeProfessionalLicense()`, un método de borrado selectivo que elimina la licencia de pago pero **mantiene bloqueado** el modo prueba.
- **Resultado**: El usuario puede cambiar de licencia profesional, pero no puede volver a usar los 7 días gratuitos una vez consumidos.

### [CRÍTICA] Autoridad de Reseteo de Marcadores
- **Problema**: La App principal tenía la capacidad de borrar el `DeviceTrialMarker` (archivo oculto en el sistema), facilitando el fraude mediante ingeniería inversa simple del UI.
- **Corrección**: Se degradó la función de reseteo en la App principal a un "Soft Reset" (solo limpia caché local). La autoridad para borrar el marcador físico del dispositivo se movió exclusivamente al proyecto `:cleaner`.
- **Resultado**: Solo alguien con el APK específico de limpieza puede resetear el estado de fábrica de la prueba.

## 2. Hardening (Fortalecimiento) de Lógica

### Bloqueo Proactivo
Al activar una licencia profesional, el sistema ahora marca **instantáneamente** la prueba como "Utilizada" en la bóveda permanente. Esto evita que, si la licencia profesional es eliminada, el sistema "olvide" que ya hubo un uso previo del dispositivo.

### Sincronización Blindada
El método `syncTrialVault` ahora prioriza la existencia del `DeviceTrialMarker`. Si el archivo existe, la App se bloqueará para la prueba incluso si el usuario borra los datos de la aplicación desde los ajustes de Android.

## 3. Estado del Ecosistema de Apps

### `:app` (Calculadora Drywall)
- **Seguridad**: Nivel Producción.
- **Cambios**: Lógica de borrado quirúrgico y reseteo suave.

### `:keygen` (Generador de Licencias)
- **Seguridad**: Nivel Administrativo.
- **Estado**: Verificado. Genera firmas RSA-4096 válidas compatibles con el nuevo sistema de bloqueo.

### `:cleaner` (Limpiador Profesional)
- **Seguridad**: Herramienta de Soporte.
- **Estado**: Funcional. Es el **único** medio para rehabilitar una prueba de 7 días en un dispositivo bloqueado.

## 4. Mejoras de Historial
- Se corrigió la discrepancia de fechas. La "Fecha de Uso" ahora siempre refleja el inicio real de la prueba, no la fecha de expiración o de borrado.

---
> [!IMPORTANT]
> Se recomienda no distribuir el APK `:cleaner` al público general. Debe ser utilizado únicamente por el equipo de soporte técnico para resolver incidencias legítimas de clientes.
