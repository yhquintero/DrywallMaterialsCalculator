# 🗺️ Hoja de Ruta — DrywallPro Master · «Roadmap para la Perfección Continua»

**Versión 4.0 · 3 de octubre de 2026 · 74 mejoras priorizadas**

Este documento sustituye a la lista plana de mejoras por una hoja de ruta
**auditada contra el código real**: cada elemento indica su estado verificado,
su esfuerzo, su criterio de aceptación y la evidencia que lo respalda. Se
trabaja **de una en una**, cerrando con pruebas y documentación.

- Revisión técnica que origina la v4.0: [`REVISION_TECNICA_2026-10.md`](REVISION_TECNICA_2026-10.md)
- Historial de versiones del producto: [`../CHANGELOG.md`](../CHANGELOG.md)
- Arquitectura de los módulos profesionales: [`MODULOS_PROFESIONALES.md`](MODULOS_PROFESIONALES.md)

---

## 📊 Tablero de control (verificado el 2026-10-03)

| Indicador | Valor actual | Objetivo | Estado |
| :--- | :--- | :--- | :---: |
| Pruebas web (Vitest) | **125** en 8 archivos | ≥ 180 con cobertura ≥ 80 % | 🟡 |
| Pruebas API de licencias (Node test) | **59** | ≥ 90 | 🟡 |
| Pruebas Android | 4 archivos | Cobertura del dominio | 🟡 |
| Peso de arranque (gzip) | **150,9 kB** | < 250 kB | ✅ |
| Presupuesto de rendimiento en CI | Activo (tope 260 kB) | Bloqueante | ✅ |
| Tipos estrictos (`tsc --noEmit`) | En CI | Bloqueante | ✅ |
| Exactitud del precio comercial | Corregida (H-01) | 0 defectos críticos | ✅ |
| Integridad de proyectos (SHA-256) | Verificada en lectura | 100 % de proyectos | ✅ |
| Accesibilidad de diálogos | 7/7 conformes | Auditoría axe sin críticos | 🟡 |
| Módulos con carga diferida | CAD, AR, precios, PDF, seguridad, catálogo | Todo lo no esencial | ✅ |

**Leyenda**

| Símbolo | Estado | | Símbolo | Esfuerzo |
| :---: | :--- | :--- | :---: | :--- |
| ✅ | Terminado y verificado | | 🟢 | ≤ 1 sesión |
| 🔶 | Parcial (hay base, falta el alcance completo) | | 🟡 | 2–3 sesiones |
| ⬜ | Pendiente | | 🔴 | Proyecto de varias semanas |

**Definición de Terminado (DoD)** — un elemento sólo pasa a ✅ cuando:
1. tiene pruebas automatizadas que fallan sin el cambio;
2. `npm run verify` (tipos + tests + build + presupuesto) pasa en CI;
3. está documentado en `docs/` o en el README;
4. aparece reseñado en `CHANGELOG.md`;
5. se ha probado en móvil real si toca cámara, AR o PDF.

---

## Fase 0 — Base ya construida y verificada

| # | Capacidad | Evidencia en el repositorio |
| :---: | :--- | :--- |
| M1 | Exportación de planos vectoriales DXF/DWG/SVG con capas normalizadas, cotas, cajetín, índice y cuadro de cómputo; conversión a DWG (ODA File Converter) y cola de impresión | `web/src/lib/cad/*`, `server/src/routes/cad.js` (`/status`, `/convert`, `/plot`) |
| M2 | Escáner de estancias con cámara y Realidad Aumentada WebXR (hit-test, rectificación, homografía de foto, asistencia remota WebRTC) | `web/src/lib/ar/*`, `web/src/components/ArScannerModal.tsx`, `server/src/routes/ar.js` |
| M3 | Catálogo de precios de distribuidores con proxy anti-SSRF, emparejamiento difuso, conversión de embalaje y divisa, caché TTL | `web/src/lib/pricing/*`, `server/src/routes/pricing.js` |
| M4 | Consola de licencias con RBAC (5 roles, 27 permisos), firma RSA en servidor, auditoría y endpoints públicos `/.well-known/*` | `web/src/admin/*`, `server/src/**` |
| M5 | Blindaje de aplicación: CSP, cabeceras de seguridad, sanitizadores SQLi/XSS/CSV, anti-prototype-pollution, HTTPS por diseño | `web/index.html`, `web/vite.config.ts`, `web/src/security/*`, `deploy/` |
| M6 | **Motor de precios por embalaje comercial corregido** (revisión 2026-10) | `packagePriceFromBase()` en `web/src/lib/calculator.ts` + 4 pruebas |
| M7 | **Integridad SHA-256 de proyectos realmente verificada** | `web/src/lib/storage.ts` + 6 pruebas |
| M8 | **Arranque optimizado y con presupuesto en CI** (150,9 kB gzip, −50 %) | `web/scripts/check-bundle.mjs`, `.github/workflows/web-ci.yml` |
| M9 | **Accesibilidad de diálogos y navegación** (`Dialog` accesible, skip-link, ARIA, foco) | `web/src/components/ui/Dialog.tsx` + `web/src/App.test.tsx` |

---

## Fase 1 — Consolidar lo construido (alto impacto, bajo riesgo)

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 1 | Firma digital y sello de tiempo de los planos (CMS/PKCS#7 + SHA-256 en cajetín) | ⬜ | 🟢 | El plano incluye bloque de firma verificable y el servidor devuelve el certificado usado. Reutiliza `cryptoService` (RSA ya existente). |
| 2 | Histórico de precios por material + sparkline y alerta de subida | 🔶 | 🟢 | Existe `PriceHistory` en Android y `fetchedAt` en la caché web; falta persistir la serie y dibujarla (30 días, alerta > X %). |
| 3 | Presupuesto en dos monedas simultáneas (divisa del proyecto + USD/EUR con tasa y fecha) | ⬜ | 🟢 | El PDF incluye ambas columnas y la referencia de la tasa. `CURRENCY_SYMBOLS.rateToUSD` ya existe; el servidor publica `/api/public/rates`. |
| 4 | Plantillas de cómputo guardables (tipología + medidas + precios) | 🔶 | 🟢 | Hay presets de tipología (`QUICK_PRESETS`); falta guardar/aplicar/eliminar plantillas de usuario desde el proyecto. |
| 5 | Validación en vivo de la captura manual (medidas absurdas, hueco > paño, perímetro incoherente) | ⬜ | 🟢 | Errores inline que bloquean el cálculo incoherente. Semilla: la auditoría numérica de la Fase 9. |
| 6 | Accesibilidad y teclado de extremo a extremo | 🔶 | 🟢 | Diálogos, ARIA y skip-link ya resueltos (M9); faltan auditoría `axe` sin críticos, contraste AA verificado y teclado en el visor 2D/3D. |
| 7 | Panel de diagnóstico `/estado` (versión, backend, licencia, TLS, capacidades AR, proveedores, caché) | ⬜ | 🟢 | Resuelve el 80 % de los soportes sin preguntar. `server/src/routes/index.js → /api/system` ya expone parte de los datos. |
| 8 | Code-splitting y presupuesto de rendimiento | ✅ | 🟢 | Cumplido: 150,9 kB gzip de arranque con tope bloqueante en CI. Queda pendiente la medición Lighthouse real (ver #66). |

## Fase 2 — Productividad en obra

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 9 | Modo offline-first con cola de sincronización (Service Worker) | ⬜ | 🟢 | Medir y calcular sin cobertura; al volver la red, sincroniza solo. `web/public/` sólo contiene `_headers`. |
| 10 | Firma a mano alzada del cliente en el PDF (hash + sello temporal) | ⬜ | 🟢 | Firma embebida y verificable. |
| 11 | Órdenes de compra **por proveedor** según mejor precio | 🔶 | 🟢 | Ya existe la OC en PDF con lista de compra; falta repartir por proveedor y emitir una OC por cada uno con subtotales y plazos. |
| 12 | Exportación XLSX nativa (varias hojas, formato y fórmulas) | 🔶 | 🟢 | El CSV actual es robusto (Blob + BOM + CRLF); falta el libro `.xlsx` con hojas de materiales, mano de obra y resumen. |
| 13 | Integración con almacén/ERP (Odoo, SAP B1, A3, Dynamics) con reintentos idempotentes | ⬜ | 🟡 | `POST /api/integrations/erp` con mapeo configurable y traza de pedido. |
| 14 | Facturación electrónica según normativa (Verifactu/TicketBAI/Facturae/CFDI/DTE) | ⬜ | 🟡 | Factura válida emitida y archivada con su serie. |
| 15 | Multi-proyecto y comparativa de versiones | 🔶 | 🟡 | El multi-proyecto está resuelto (con integridad SHA-256); falta el diff exportable entre dos revisiones (delta de materiales, precios y superficie). |
| 16 | Biblioteca de precios propia con ajuste por IPC | 🔶 | 🟡 | Existen precios personalizados por proyecto; falta la base histórica del contratista con sugerencia justificada. |

## Fase 3 — Ingeniería y validación técnica

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 17 | Cálculo estructural de steel framing (PGC/PGU, viento/nieve, esbeltez, pandeo) | 🔶 | 🟡 | Ya hay tablas de altura máxima por deflexión (L/240, L/360) y refuerzos de dintel/jamba; falta la memoria de cálculo exportable según EN 1993/AISI S100. |
| 18 | Acústica y fuego (Rw/R'w y clasificación EI) | 🔶 | 🟡 | Existen tipologías RF y lana acústica con consejos; falta la ficha técnica con valores justificados (EN 13501/EN 1365). |
| 19 | Verificación térmica, puentes térmicos y Glaser | ⬜ | 🟡 | Informe con cumplimiento CTE/ASHRAE 90.1. |
| 20 | Desperdicio inteligente (nesting de placas) con plano de despiece | ⬜ | 🟡 | Reducción medible de m² comprados. Relacionado con #63 (formato de placa óptimo). |
| 21 | Exportación BIM/IFC4 (`IfcWall`, `IfcSlab`, `IfcCovering`) | ⬜ | 🔴 | El IFC abre sin errores de validación en Revit/ArchiCAD/BIMserver. |
| 22 | Detección de interferencias (clash detection) con MEP importado | ⬜ | 🔴 | Listado de choques con capturas. |

## Fase 4 — Realidad aumentada y captura avanzada

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 23 | Persistencia de anclajes AR (`anchors`) | ⬜ | 🟡 | Reapertura del replanteo en la misma posición ±5 cm. |
| 24 | Replanteo en obra 1:1 sobre el terreno (AR proyectado) | ⬜ | 🟡 | Verificación con cinta métrica (parantes y placas a escala real). |
| 25 | Escaneo de techo y altura automática con aviso de falso techo | 🔶 | 🟡 | El escáner ya vuelca techos y muros al cómputo; falta la medición de altura específica con error < 2 cm. |
| 26 | Fotogrametría multi-foto (SfM) | ⬜ | 🔴 | Malla + medidas con error < 3 %. |
| 27 | Detección automática de aberturas por visión artificial | ⬜ | 🟡 | ≥ 80 % de acierto en fotos de prueba. |
| 28 | Medición sobre vídeo en directo con apuntado AR | ⬜ | 🟡 | Existe el modo foto con homografía; falta el trazo en vivo sin capturar. |

## Fase 5 — Web, diseño y colaboración

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 29 | Editor 2D de planta editable (vértices y paredes sobre lienzo) | ⬜ | 🟢 | Edición directa con rejilla y cotas dinámicas (el visualizador actual es de sólo lectura). |
| 30 | Exportar el visualizador a imagen/vídeo/GIF | ⬜ | 🟡 | `html2canvas` ya se usa en el módulo CAD y está en un chunk diferido: base reaprovechable. |
| 31 | Enlaces de solo lectura para el cliente (URL firmada y caducable) | ⬜ | 🟡 | Muestra superficie, presupuesto y planos sin edición. |
| 32 | Comentarios y anotaciones sobre el plano (pins por coordenada) | ⬜ | 🟡 | Visibles para el equipo. |
| 33 | Historial de cambios y restauración de versiones del proyecto | 🔶 | 🟡 | El backend audita licencias (actor/IP/UA) y los proyectos ya llevan checksum; falta el registro append-only por proyecto (ver #61). |
| 34 | Temas claro/oscuro y modo obra (alto contraste, tipografía grande, guantes) | ⬜ | 🟡 | Hoy sólo existe el tema oscuro. |
| 35 | Internacionalización completa ES/EN/PT con formatos locales | 🔶 | 🟢 | Los datos ya son bilingües (`nameEn`, `technicalTipsEn`) y existe el tipo `Language`; falta el framework i18n y la extracción de textos. |

## Fase 6 — Plataforma, datos e IA

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 36 | Backend propio de proyectos (sustituir `localStorage`), con usuarios y versiones | ⬜ | 🟡 | Varios dispositivos ven el mismo proyecto. Es la dependencia principal de #31, #33, #37 y #44. |
| 37 | Edición concurrente con CRDT (Yjs/Automerge) | ⬜ | 🟡 | Sincronización sin conflictos. |
| 38 | Asistente IA de presupuestos (sugerir tipologías, detectar olvidos, explicar desviaciones) | ⬜ | 🟡 | Basado en el histórico de obras cerradas. |
| 39 | Detección de anomalías en precios de proveedor | ⬜ | 🟡 | Alerta cuando una cotización se sale del rango histórico. |
| 40 | Reconocimiento de planos (OCR/vectorización de PDF del arquitecto) | ⬜ | 🟡 | Extracción automática de estancias y medidas. |
| 41 | Gemelo digital de obra (avance real vs. planificado, curva de producción) | ⬜ | 🔴 | Seguimiento con AR/cámara. |
| 42 | Panel analítico del negocio (margen por tipo de obra, coste/m², productividad) | 🔶 | 🟡 | La consola ya ofrece estadísticas de licencias e ingresos; falta la analítica de obra. |

## Fase 7 — Android nativo (módulos `:app`, `:keygen`, `:cleaner`)

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 43 | ARCore nativo con Depth API | ⬜ | 🟡 | No hay dependencia `arcore` en `app/build.gradle.kts`; el escáner vive en la web (WebXR). |
| 44 | Sincronización web ↔ móvil con cola de cambios | 🔶 | 🟡 | `ConsoleSync` sincroniza licencias; el modelo de obra es independiente. Depende de #36. |
| 45 | Impresión desde el móvil contra la cola del plotter | ⬜ | 🟡 | El endpoint `POST /api/cad/plot` ya existe; falta el cliente Android. |
| 46 | Widget y accesos rápidos («nueva medición», «último presupuesto») | ⬜ | 🟢 | No hay `appwidget` en el proyecto. |
| 47 | Modo campo (batería y datos: menos refresco, caché agresiva, sin animaciones) | ⬜ | 🟢 | Se activa con batería baja o red móvil lenta. |

## Fase 8 — Operación, calidad y negocio

| # | Mejora | Estado | Esf. | Criterio de aceptación / notas |
| :---: | :--- | :---: | :---: | :--- |
| 48 | CI/CD completo con despliegue automático a staging y prueba de humo | 🔶 | 🟢 | Hay 3 workflows (web, servidor, Android) con tipos, tests, build y presupuesto; falta el despliegue y el smoke test posterior. |
| 49 | Pruebas end-to-end con Playwright (crear obra → medir → presupuestar → exportar) | ⬜ | 🟢 | Ya existen pruebas de componente con jsdom (`App.test.tsx`) como paso intermedio. |
| 50 | Cobertura y calidad de código (umbral mínimo, ESLint/Detekt estrictos, dependencias vulnerables) | 🔶 | 🟢 | Hay `tsc` estricto, `.detekt/config.yml` y Dependabot; falta el umbral de cobertura bloqueante. |
| 51 | Telemetría anónima opt-in y crash reporting | ⬜ | 🟢 | Métricas que alimenten el roadmap sin invadir la privacidad. |
| 52 | Documentación de usuario ilustrada y FAQ de obra | 🔶 | 🟢 | Hay README, 5 documentos técnicos y esta hoja de ruta; falta el manual por tareas y los vídeos cortos. |
| 53 | Multi-empresa y equipos con roles y planes | 🔶 | 🟡 | El RBAC (5 roles, 27 permisos) ya es multi-usuario; falta el aislamiento por empresa y los límites por plan. |
| 54 | Pasarela de pago y planes con facturación automática | ⬜ | 🟡 | Hoy los cobros se registran manualmente en la consola. |
| 55 | API pública documentada (OpenAPI) para distribuidores y ERPs | ⬜ | 🟡 | Los endpoints públicos existen y están descritos en `server/README.md`, pero no hay especificación OpenAPI. |

---

## Fase 9 — Fiabilidad numérica y trazabilidad *(nueva, origen: revisión 2026-10)*

La revisión demostró que un defecto de unidades puede pasar desapercibido sin
una red de seguridad numérica. Esta fase la construye.

**56 · 🟢 Batería de proyectos de referencia («golden tests»)**
Cinco obras tipo (vivienda, oficina, baño RH, plafón registrable, fachada EIFS)
con resultado congelado de materiales, embalajes y totales. *Aceptación:* un
cambio en consumos o precios rompe el test y muestra el delta en el mensaje.

**57 · 🟢 Auditoría numérica del motor de cálculo**
Casos límite: área neta menor que 1 m², hueco mayor que el paño, `repetitions`
= 0, división por cero en `costPerUnitArea`, mezcla de unidades métricas e
imperiales y redondeos acumulados. *Aceptación:* ninguna entrada produce `NaN`,
`Infinity` o totales negativos; se documenta el comportamiento esperado.

**58 · 🟡 Trazabilidad del precio por partida**
Cada línea del presupuesto indica el origen del precio (catálogo, distribuidor
con nombre y fecha, o manual) y su hash. *Aceptación:* el PDF y el CSV muestran
la columna «Origen del precio» y el JSON exportado lo conserva.

**59 · 🟢 Cuadre contable y redondeo profesional**
IVA sobre base imponible correcta, redondeo a 2 decimales por línea con ajuste
en la última, y verificación `suma(líneas) == total`. *Aceptación:* prueba que
detecta descuadres de un céntimo.

**60 · 🟡 Versionado del formato de proyecto (`schemaVersion` + migradores)**
Migración automática de proyectos antiguos y rechazo explícito de versiones
futuras desconocidas. *Aceptación:* un proyecto v1 guardado abre en v3 sin
pérdida de datos.

**61 · 🟢 Registro append-only de cambios del proyecto (rellena #33)**
Historial con checksum encadenado por operación y restauración de versiones.
*Aceptación:* se puede auditar quién cambió qué y volver a cualquier estado.

**62 · 🟡 Merma por material y por tipología**
Hoy la merma es un único porcentaje global. *Aceptación:* configuración por
categoría (placas, perfilería, pastas) con repercusión verificable en el cómputo.

## Fase 10 — Experiencia de campo y ahorro real *(nueva)*

**63 · 🟢 Optimizador de formato de placa (2,40 m vs 2,60 m vs 3,00 m)**
Sugerencia del formato que minimiza recortes según la altura real del paño.
*Aceptación:* el ahorro estimado de m² se muestra antes de comprar y queda
registrado en el presupuesto.

**64 · 🟡 Cálculo inverso «presupuesto objetivo»**
Dado un importe máximo por m², proponer la tipología y el nivel de acabado que
caben. *Aceptación:* propuesta con desglose y aviso de compromisos técnicos.

**65 · 🟢 Cuadro de mando de obra en una pantalla**
Superficie, materiales, mano de obra, margen, plazo y avance en una vista
imprimible para la caseta de obra. *Aceptación:* se genera en PDF/PNG con los
datos del proyecto activo.

**66 · 🟢 Medición Lighthouse real y auditoría axe en CI**
Complementa el presupuesto de tamaño con métricas de campo (LCP/CLS/INP) y
accesibilidad automatizada. *Aceptación:* informe publicado como artefacto y
umbral Lighthouse ≥ 90 en móvil.

**67 · 🟡 Modo colaboración en tiempo real sobre el presupuesto**
Dos usuarios viendo y ajustando el mismo cómputo con presencia (requiere #36 y #37).
*Aceptación:* los cambios se propagan en < 2 s sin pisarse.

**68 · 🟢 Compartir por WhatsApp/email con resumen ejecutivo**
Enlace o PDF ligero con superficie, importe y plazo, sin datos internos de coste.
*Aceptación:* un clic desde el presupuesto, con vista previa de lo que se comparte.

## Fase 11 — Ingeniería avanzada y negocio *(nueva)*

**69 · 🟡 Verificación cruzada contra tablas normativas (ASTM C840 / USG / EN 520)**
Comparar los consumos calculados con las tablas de referencia y avisar de
desviaciones > 10 %. *Aceptación:* informe de verificación adjunto al cómputo.

**70 · 🟡 Biblioteca de detalles constructivos (nudos CAD reutilizables)**
Bloques DXF paramétricos (encuentros, dinteles, cajillos, juntas de dilatación)
insertables en las láminas. *Aceptación:* catálogo buscable y bloque insertado
con las cotas del proyecto.

**71 · 🟡 Presupuestos por fases y certificaciones de obra**
Repartir el importe por hitos y emitir certificaciones parciales con retención.
*Aceptación:* certificación nº X con acumulado y pendiente.

**72 · 🟡 Comparador de ofertas de proveedor lado a lado con ahorro anual**
Extiende la comparativa actual con proyección de consumo anual y ahorro
estimado por proveedor. *Aceptación:* informe con ahorro y confianza de la
coincidencia.

**73 · 🟡 Multi-idioma/multi-divisa del mismo presupuesto con tasas históricas**
Reemisión de un presupuesto antiguo con la tasa del día de la oferta y la del
día de la consulta. *Aceptación:* ambas columnas y trazabilidad de la tasa.

**74 · 🟢 Exportación de la orden de compra a `punch list` de almacén**
Lista de picking en PDF con ubicaciones y cantidades verificables.
*Aceptación:* el encargado de almacén puede preparar el pedido sin el técnico.

---

## ⏭️ Orden sugerido para las próximas 8 iteraciones

| Iteración | Mejora | Por qué ahora |
| :---: | :--- | :--- |
| 1 | **#9 Modo offline-first (Service Worker)** | La obra no tiene cobertura; es la queja nº 1 del usuario de campo y no depende de nada pendiente. |
| 2 | **#56/#57 Red de seguridad numérica** | Protege el corazón del producto antes de añadir más cálculos (la revisión demostró su necesidad). |
| 3 | **#5 Validación en vivo + #7 Panel `/estado`** | Eliminan errores de captura y reducen el soporte; ambas son de coste bajo. |
| 4 | **#11 Órdenes de compra por proveedor** | Cierra el ciclo del módulo de precios y se apoya en #58 (trazabilidad). |
| 5 | **#2 Histórico de precios + #39 anomalías** | Aprovecha los datos que ya generan las sincronizaciones. |
| 6 | **#1 Firma digital de planos** | Requisito para presentar el plano a dirección facultativa. |
| 7 | **#20 Nesting + #63 formato de placa** | Ahorro directo y medible en materiales: la palanca más vendible. |
| 8 | **#12 XLSX + #11 (cierre) y #59 cuadre contable** | Deja la cadena económica impecable para el salto a la Fase 6 (#36). |

**Convención para trabajar de una en una:** cada mejora se cierra con sus
pruebas, su documentación en `docs/`, una nota en `CHANGELOG.md` y la
verificación verde en la rama principal; no se abre la siguiente hasta que la
anterior esté verificada.

---

## 🌱 Backlog de ideas en estudio (no priorizadas)

- **Dictado por voz a cómputo:** «salón de seis por cuatro con dos ventanas».
- **Marketplace de plantillas de cómputo** entre contratistas verificados.
- **Optimizador de corte de perfilería** (nesting de tiras de 3 m) con etiquetas de taller.
- **AR multiusuario** sobre la misma escena (WebXR + WebRTC) para revisión en obra.
- **Modo entrenamiento:** explicar cada paso del cálculo a un instalador novel.
- **Generador de propuestas visuales** con render 3D del resultado final.
- **Integración con marketplaces de materiales** (Amazon Business, Leroy Merlin) para reponer stock.
- **Modo subcontrata** con reparto de trabajo por cuadrilla y control de jornales.
- **Certificado energético simplificado** a partir del sistema de trasdosado elegido.
- **APK ligera offline** para tablets de taller, sin dependencias de red.

---

## 📝 Registro de cambios de esta hoja de ruta

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
| 4.0 | 2026-10-03 | Auditoría contra el código: estados verificados de los 55 elementos previos, 19 mejoras nuevas (fases 9–11) con criterios de aceptación, tablero de indicadores, DoD, backlog de ideas y nuevo orden de iteraciones. |
| 3.0 | (anterior) | Lista priorizada de 55 mejoras con criterios de aceptación y fases 0–8. |
