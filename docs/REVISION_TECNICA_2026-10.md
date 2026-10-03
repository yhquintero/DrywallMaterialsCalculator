# Revisión Técnica — DrywallPro Master (octubre 2026)

> Auditoría de código, datos y experiencia de usuario sobre la rama
> `arena/01a0ffca-drywallmaterialscalculator` (base `3ed3906`).
> Todos los hallazgos incluyen evidencia reproducible y su estado final.

---

## 1. Resumen ejecutivo

El producto está **muy por encima de la media de un MVP**: 10 tipologías
constructivas con consumos normativos, exportación CAD real (DXF R12/R2000/R2007
con capas normalizadas y cajetín), escáner WebXR con homografía, catálogo de
precios con proxy anti-SSRF y una consola de licencias con RBAC de 27 permisos.
La arquitectura de seguridad está bien resuelta (secretos sólo en servidor,
CSP, cabeceras, sanitizadores, rate limiting).

La revisión encontró **un defecto crítico de negocio** (precios comerciales
infravalorados) y varios defectos de fiabilidad, rendimiento y accesibilidad.
Han sido corregidos en esta misma revisión, con pruebas de regresión.

| Severidad | Detectados | Corregidos en esta revisión | Pendientes |
| :--- | :---: | :---: | :---: |
| 🔴 Crítica | 1 | 1 | 0 |
| 🟠 Alta | 2 | 2 | 0 |
| 🟡 Media | 4 | 4 | 0 |
| 🔵 Baja | 3 | 3 | 0 |

**Veredicto de calidad tras los arreglos:** motor de cálculo con precios por
embalaje comercial coherente con el módulo de distribuidores, proyecto cifrado
con SHA-256 y verificación efectiva, arranque web de 150,9 kB gzip (−50 %),
modales conformes con `role="dialog"`/trampa de foco/`Escape`, y CI con
`typecheck` + tests + build + presupuesto de rendimiento.

---

## 2. Metodología

1. Lectura completa de `web/src` (49 archivos TS/TSX), `server/src` (26 módulos),
   workflows de CI y documentación.
2. Ejecución real de la suite (`npm test`), del build de producción (`tsc && vite build`)
   y de un volcado del cómputo del proyecto de ejemplo para inspeccionar cada línea.
3. Contraste de las fórmulas de precios con el módulo de distribuidores
   (`convertToPackage()`) para detectar incoherencias de unidades.
4. Revisión de accesibilidad sobre el DOM renderizado (jsdom) y de los patrones
   de diálogo del panel de administración (que sí eran accesibles) frente a los
   modales de la calculadora (que no lo eran).

---

## 3. Hallazgos

### 🔴 H-01 — CRÍTICO · El precio de catálogo se aplicaba por unidad base a un cómputo por embalaje

**Impacto:** presupuesto de materiales **infravalorado entre un 25 % y un 60 %**
según tipología (todo el material que no se vende por unidad: perfiles, tornillos,
masillas, cintas y aislamiento). Un presupuesto así firmado genera pérdidas reales.

**Evidencia:** en `CONSTRUCTION_TYPES`, `defaultPriceUSD` es el precio por unidad
base (`m lineales`, `piezas`, `kg`) mientras que `commercialPackage.unitSize`
indica el contenido del embalaje (tira de 3 m, caja de 1.000 u., balde de 28 kg,
rollo de 150 m). El motor multiplicaba `commercialUnits × precio_base`:

| Línea (20 m² de techo ST) | Antes | Correcto |
| :--- | ---: | ---: |
| Perfil Primario (7 tiras de 3 m) | 9,45 USD | **28,35 USD** |
| Tornillo T2 (1 caja de 1.000 u.) | 0,02 USD | **18,00 USD** |
| Cinta de papel (1 rollo de 150 m) | 0,08 USD | **12,00 USD** |
| Masilla (1 balde de 28 kg) | 0,85 USD | **23,80 USD** |
| **Total materiales (20 m²)** | **≈ 119,9 USD** | **253,2 USD** |

El módulo de precios de distribuidores ya hacía la conversión correcta
(`convertToPackage()` multiplica por el tamaño del embalaje), por lo que el
presupuesto cambiaba de importe según el origen del precio.

**Corrección:** nueva función `packagePriceFromBase()` en `web/src/lib/calculator.ts`
con la misma semántica que el módulo de precios, más 4 pruebas de regresión
(`web/src/lib/calculator.test.ts`, bloque *«precio de catálogo por embalaje
comercial»*).

---

### 🟠 H-02 — ALTA · Checksum de proyectos calculado pero nunca verificado

**Evidencia:** `saveProjectToStorage()` calculaba un SHA-256 y lo guardaba en
`SavedProject.checksum`, pero ninguna lectura lo comprobaba. Además
`verifyProjectIntegrity()` (en `security/cryptoStorage.ts`) era código muerto y
su implementación **nunca podría validar** nada: firmaba el objeto completo
—incluido el propio campo `checksum`— mientras el guardado firmaba sólo
`{config, rooms, customPrices, customStock}`. La interfaz vendía «anti-tamper»
sin efecto real.

**Corrección:** payload de firma canónico compartido entre guardado y
verificación, `verifyProjectChecksum()`, `loadProjectsWithIntegrity()` (anota
`verified` / `mismatch` / `unverified`) e insignias «Íntegro» / «Modificado» en
el gestor de obras. 6 pruebas nuevas en `web/src/lib/storage.test.ts`, incluida
la manipulación externa del JSON.

---

### 🟠 H-03 — ALTA · 135 kB gzip de jsPDF en la primera carga

**Evidencia:** `dist/index.html` precargaba `pdf-CO5a9k3N.js` (135,5 kB gzip)
porque `BudgetEstimator` importaba estáticamente `lib/pdfGenerator`, y además
DOMPurify (11,3 kB gzip) entraba por `security/xssDefense`, importado a su vez
por `lib/storage`. **Arranque medido: 303,5 kB gzip** para una calculadora que
sólo necesita PDF cuando el usuario pulsa «Exportar».

**Corrección:**
- `BudgetEstimator` importa `pdfGenerator` de forma dinámica (con estado de
  «Generando…» y aviso de error accesible).
- `DOMPurify` se aísla en `security/htmlSanitizer.ts`, consumido únicamente por
  la consola de seguridad, que ahora también se carga en diferido.
- Se elimina el `manualChunks.pdf` que forzaba la precarga.

**Resultado verificado:** **150,9 kB gzip** en el arranque (−50 %), con
`npm run check:size` integrado en CI y tope de 260 kB.

---

### 🟡 H-04 — MEDIA · Los modales de la calculadora no eran accesibles

**Evidencia:** los 7 diálogos de la aplicación (`Configuración`, `Obras
guardadas`, `Catálogo`, `Seguridad`, `Planos CAD`, `Precios`, `Escáner AR`) se
renderizaban como `<div class="fixed inset-0 …">` sin `role="dialog"`, sin
`aria-modal`, sin cierre con `Escape`, sin trampa de foco, sin devolución del
foco y sin bloqueo del scroll de fondo. Los botones de sólo icono usaban `title`
(no es nombre accesible fiable) y las pestañas no tenían semántica `tablist`.

**Corrección:** `web/src/components/ui/Dialog.tsx` (semántica, `Escape`, trampa
de foco, foco inicial y restaurado, scroll bloqueado, cierre por fondo) aplicado
a los 7 modales; `skip-link` al contenido; `aria-label` en todos los botones de
icono; `role="tablist"/"tab"/"tabpanel"` con `aria-selected` y `aria-controls`;
indicadores decorativos marcados `aria-hidden`. 4 pruebas de humo en
`web/src/App.test.tsx` verifican el diálogo, su nombre accesible y su cierre.

---

### 🟡 H-05 — MEDIA · Guardado en cada pulsación de tecla y fallos silenciosos

**Evidencia:** el `useEffect` de autoguardado dependía de `config`, `rooms`,
`customPrices` y `customStock`, de modo que **cada tecla** serializaba el
proyecto completo y recalculaba su SHA-256 de forma asíncrona. Si
`localStorage.setItem` fallaba por cuota, el error sólo iba a `console.error`:
el usuario seguía trabajando creyendo que su obra estaba a salvo.

**Corrección:** guardado con retardo de 800 ms, resultado tipado
(`SaveResult`), recuperación ante `QuotaExceededError` (segundo intento
conservando la obra actual) e indicador visible «Guardando… / Guardado
automático / Sin guardar» con `role="status"`.

---

### 🟡 H-06 — MEDIA · La zona nueva podía no quedar seleccionada

**Evidencia:** `handleNewProject()` creaba la zona con `room_${Date.now()}` y a
continuación asignaba `setSelectedRoomId(\`room_${Date.now()}\`)`, una **segunda**
marca de tiempo distinta. El visor 2D/3D podía mostrar otra zona.

**Corrección:** helper `uid()` y reutilización del identificador de la zona
creada. Se eliminó también el `if` duplicado de `handleAddScannedRooms()`.

---

### 🟡 H-07 — MEDIA · Borrado de obras inconsistente

**Evidencia:** `handleDeleteProject` escribía a mano en la clave
`drywall_calculator_projects_v2` saltándose la capa de almacenamiento: no
reparaba el puntero de proyecto activo (podía quedar apuntando a una obra
inexistente) y el gestor de obras seguía mostrando la fila eliminada hasta
cerrarse y reabrirse, porque leía el almacenamiento en cada render sin estado.

**Corrección:** `deleteProjectFromStorage()` (elimina y repara/limpia el
puntero activo) + el gestor mantiene la lista en estado, la refresca tras cada
operación y sustituye los `alert()` por avisos en línea con `role="status"`.

---

### 🔵 H-08 — BAJA · Exportación CSV con `data:` URI

Un `data:text/csv` con `encodeURI` falla a partir de cierto tamaño (límite de
longitud de URL) y produce saltos de línea que algunas versiones de Excel
interpretan mal. **Corrección:** `Blob` + BOM UTF-8 + `\r\n`, liberando el
`ObjectURL` al terminar.

### 🔵 H-09 — BAJA · Código muerto y ruido en la interfaz

Import duplicado de `lucide-react` en medio de `App.tsx`, 8 iconos importados
sin usar, `if` duplicado, y variables sin uso. **Corrección:** limpieza;
`tsc --noEmit` queda como script (`npm run typecheck`).

### 🔵 H-10 — BAJA · El CI web usaba una versión de Node incompatible

`web-ci.yml` fijaba Node 20 mientras `vitest@5` exige `^22.12 || ^24 || >=26`
(avisos `EBADENGINE` y riesgo real de fallo). Además no había comprobación de
tipos ni de tamaño de bundle. **Corrección:** Node 22, paso de `typecheck`,
paso de `check:size` y `npm run verify` en la raíz.

---

## 4. Riesgos residuales (no corregidos, justificados)

| Riesgo | Por qué no se corrige aquí | Dónde se sigue |
| :--- | :--- | :--- |
| Los datos de obra viven en `localStorage` | Requiere backend de proyectos (#36) y migración; es un proyecto en sí mismo | Fase 6 / 10 del roadmap |
| Precios de catálogo estáticos (2024-2025) | Necesita la biblioteca de precios propia con IPC (#16) o el catálogo de distribuidores (#3, ya operativo) | Fase 2 |
| Sin Service Worker | Es la siguiente gran pieza de producto (#9) | Fase 2 |
| Cobertura de tests del motor limitada a casos representativos | Se propone una batería de proyectos de referencia y pruebas de propiedades (#56) | Fase 9 |
| Sin medición Lighthouse real ni auditoría axe automatizada | Requiere navegador headless en CI; el presupuesto de tamaño ya está cubierto | Fase 9 / 8 |

---

## 5. Verificación

```bash
# Todo en uno (tipos + tests + build + presupuesto de rendimiento)
npm run verify            # raíz: web + API de licencias

# Recuento actual
#   web  : 125 pruebas en 8 archivos (calculadora, CAD, AR, precios, seguridad,
#           permisos, almacenamiento y humo de interfaz)
#   server: 59 pruebas (API, RBAC, servicio de licencias, herramientas)

npm --prefix web run check:size   # 150,90 kB gzip de arranque (tope 260)
```

**Regresión cubierta por pruebas:** `web/src/lib/calculator.test.ts` (precio por
embalaje comercial), `web/src/lib/storage.test.ts` (integridad, cuota y borrado)
y `web/src/App.test.tsx` (diálogos y estructura accesible).
