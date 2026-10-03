# Historial de versiones — DrywallPro Master

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
el versionado semántico. Cada entrega cierra con las pruebas en verde y el
presupuesto de rendimiento dentro del tope.

---

## [3.1.0] — 2026-10-03 · «Revisión técnica y perfeccionamiento»

Revisión completa del producto (10 hallazgos documentados en
[`docs/REVISION_TECNICA_2026-10.md`](docs/REVISION_TECNICA_2026-10.md)) y
actualización total de la hoja de ruta a la
[versión 4.0](docs/MEJORAS_ROADMAP.md).

### Corregido (crítico y alto)

- **Precio de catálogo por embalaje comercial.** `defaultPriceUSD` se publica por
  unidad base (m, kg, pieza) pero se aplicaba al número de embalajes, dejando el
  presupuesto de materiales infravalorado (≈ 119,9 USD → **253,2 USD** en el caso
  de referencia de 20 m² de cielo raso). Ahora `packagePriceFromBase()` aplica la
  misma conversión que el módulo de precios de distribuidores.
- **Integridad SHA-256 realmente verificada.** El checksum se calculaba pero
  nunca se comprobaba; además `verifyProjectIntegrity()` firmaba un payload
  distinto al guardado, por lo que jamás habría validado nada. Se unifica el
  payload de firma, se verifica al leer (`loadProjectsWithIntegrity()`) y el
  gestor de obras muestra las insignias «Íntegro» / «Modificado».
- **Borrado de obras consistente.** `deleteProjectFromStorage()` elimina la obra
  y repara el puntero de proyecto activo; la lista se refresca al instante y los
  `alert()` se sustituyen por avisos accesibles en línea.
- **Identificadores únicos.** Al crear una obra se usaban dos marcas de tiempo
  distintas para la zona y su selección, lo que podía dejar el visor en otra
  zona. Nuevo helper `uid()`.
- **Entorno `npm run dev:https` multiplataforma (Windows PowerShell / CMD, macOS y Linux).**
  `scripts/dev-https.mjs` invocaba `spawn('bash', ['scripts/gen-certs.sh'])` (fallando en
  Windows con `Error: spawn bash ENOENT`) y pasaba `--https` al CLI de Vite 6 (opción
  eliminada en Vite 6 que lanzaba `CACError: Unknown option '--https'`). Se reemplaza por
  `scripts/gen-certs.mjs` — generador X.509 v3 nativo con `node:crypto` (CA local + hojas
  con SAN DNS/IPv4/IPv6 sin `bash` ni `openssl`) — y lanzamiento directo con `process.execPath`
  (`shell: false`) compatible con rutas con espacios en Windows y Node 22/24.

### Rendimiento

- **Arranque un 50 % más ligero: 303,5 kB → 150,9 kB gzip.**
  El generador de PDF (jsPDF + autotable, 135 kB gzip) se importa de forma
  dinámica al pulsar «Exportar»; DOMPurify se aísla en
  `security/htmlSanitizer.ts` y se carga sólo con la consola de seguridad; el
  catálogo de materiales también pasa a carga diferida; se elimina el
  `manualChunks` que forzaba la precarga de jsPDF.
- **Nuevo presupuesto de rendimiento bloqueante en CI**
  (`web/scripts/check-bundle.mjs`, tope 260 kB gzip).

### Añadido

- **Componente `Dialog` accesible** (`web/src/components/ui/Dialog.tsx`):
  `role="dialog"`, `aria-modal`, nombre accesible, cierre con `Escape`, trampa
  de foco, foco inicial y restaurado, bloqueo del scroll de fondo. Aplicado a
  los 7 diálogos de la calculadora.
- **Navegación accesible:** enlace «Saltar al contenido principal», semántica
  `tablist`/`tab`/`tabpanel` con `aria-selected` y `aria-controls`, nombre
  accesible en todos los botones de icono, indicadores decorativos ocultos a
  lectores de pantalla.
- **Guardado automático con retardo (800 ms)** y resultado tipado: recuperación
  ante `QuotaExceededError` e indicador visible «Guardando… / Guardado
  automático / Sin guardar».
- **Exportación CSV reforzada:** `Blob` + BOM UTF-8 + CRLF (sin el límite de
  longitud de las URLs `data:`), liberando el `ObjectURL`.
- **Scripts nuevos:** `npm run typecheck`, `npm run check:size`, `npm run verify`.

### Pruebas

- 125 pruebas web en 8 archivos (antes 111 en 6) y 59 de la API.
- Nuevas: precio por embalaje comercial y divisas (4), integridad, cuota y
  borrado de proyectos (6), humo de interfaz con jsdom sobre diálogos y ARIA (4).

### Integración continua

- `web-ci.yml` pasa a Node 22 (Vitest 5 exige `^22.12 || ^24 || >=26`) y añade
  los pasos de `typecheck` y de presupuesto de rendimiento.

### Documentación

- Nueva **hoja de ruta v4.0**: 74 mejoras con estado verificado, esfuerzo,
  criterio de aceptación, tablero de indicadores, definición de terminado,
  fases 9–11 nuevas y backlog de ideas.
- Nuevo informe de **revisión técnica** con los 10 hallazgos y su evidencia.
- README actualizado: métricas reales, scripts, estado del roadmap y enlaces.

---

## [3.0.0] — 2026 · «Módulos profesionales»

- Exportación de planos vectoriales DXF/DWG/SVG con capas normalizadas, cotas,
  cajetín, índice y cuadro de cómputo; conversión a DWG y cola de impresión.
- Escáner de estancias con cámara y Realidad Aumentada (WebXR), rectificación
  geométrica, modo foto con homografía y asistencia remota por WebRTC.
- Catálogo de precios en tiempo real de distribuidores con proxy seguro
  (anti-SSRF), emparejamiento difuso, conversión de embalaje y divisa, caché TTL.
- Consola web de licencias con RBAC (5 roles, 27 permisos), firma RSA en
  servidor, auditoría, tasas de cambio y endpoints públicos `/.well-known/*`.
- Blindaje de aplicación (CSP, cabeceras, sanitizadores) y despliegue HTTPS por
  diseño (Docker, Caddy, nginx).

## [2.0.0] — 2026

- Visualizador 2D/3D interactivo, gestor multi-estancias con deducción de
  huecos, cotizador con mano de obra/margen/impuestos, PDF de presupuesto y
  orden de compra, y persistencia local.

## [1.0.0] — 2025

- Calculadora de materiales para drywall, tabiques, cielos rasos y plafones con
  10 tipologías constructivas y sistema dual métrico/imperial.
