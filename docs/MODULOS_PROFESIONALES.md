# Módulos profesionales — Planos CAD, Escáner AR y Precios de distribuidores

Documentación técnica de los tres módulos incorporados a DrywallPro Master.
Todo el código está cubierto por pruebas automatizadas (111 del front-end y 19
del servidor sólo para estos módulos).

---

## 1. Planos vectoriales DXF / DWG para AutoCAD

### Qué genera

| Lámina | Contenido |
| --- | --- |
| `INDICE-00` | Portada: memoria del proyecto, índice de láminas, leyenda de capas y notas técnicas. |
| `LAMINA-xx` | Alzado de tabique o planta de cielo raso/plafón, con cotas, ejes y cajetín. |
| `COMPUTO-xx` | Cuadro de materiales con formato comercial, precio unitario y resumen económico. |

El contenido del alzado se adapta al tipo constructivo:

* **Muros** (`muro_sencillo`, `tabique_divisor`, `muro_rf`, `cajillo_viga`, `fachada_eifs`): solera y canal, parantes a la modulación configurada (0,407 / 0,488 / 0,61 m), dinteles y jambas de las aberturas, cruces de San Andrés en `steel_framing`, trama de aislamiento y replanteo de placas con junta alternada.
* **Cielos** (`techo_st`, `techo_rh`, `multi_partes`): ángulo perimetral, perfiles primarios cada 1,20 m, omegas a la modulación y puntos de suspensión.
* **Plafón registrable** (`plafon_reticulado`): retícula modular, baldosas cortadas marcadas en otra capa y suspensiones.

### Capas normalizadas

`ARQ-MURO`, `DW-PERFIL`, `DW-CANAL`, `DW-OMEGA`, `DW-PRIMARIO`, `DW-PLACA`,
`DW-AISLANTE`, `DW-ABERTURA`, `DW-FIJACION`, `DW-EJE`, `DW-COTA`, `DW-TEXTO`,
`DW-SIMBOLO`, `PLANO-MARCO`, `PLANO-TABLA`.

Con tipos de línea `CONTINUOUS`, `DASHED`, `CENTER`, `HIDDEN`, `PHANTOM`,
`DOTTED`, estilos de texto SHX (`ROMANS`, `ISOCP`, `MONOTXT`) y un estilo de
cota `DRYWALL-ISO` que usa los mismos factores que la escala de trazado.

### Versiones y unidades

* **R12 (AC1009)** → `POLYLINE`/`VERTEX`/`SEQEND`, sin handles. Máxima compatibilidad (LibreCAD, QCAD, nanoCAD, CNC/láser).
* **R2000 (AC1015)** → `LWPOLYLINE`, handles, `BLOCK_RECORD` y sección `OBJECTS`. Recomendada.
* **R2007 (AC1021)** → igual que R2000 con otra cabecera.

Unidades de dibujo: mm, cm, m, pulgadas o pies, con `$INSUNITS`,
`$MEASUREMENT` y `$LUPREC` coherentes. La escena se construye siempre en metros
reales (modelo 1:1) y se escala al serializar.

### Cómo obtener DWG

El formato `.dwg` es propietario de Autodesk. El camino profesional es:

1. Exportar el DXF (formato abierto, 100 % editable en AutoCAD).
2. Convertirlo con **ODA File Converter** (gratuito, Open Design Alliance):

```bash
# server/.env
CAD_CONVERTER_CMD=/usr/bin/ODAFileConverter
```

Con eso, el botón «Generar DWG» de la interfaz llama a
`POST /api/cad/convert` y devuelve el binario listo para descargar. Sin
conversor, el endpoint responde `501` con instrucciones y la web ofrece la
descarga del DXF.

### Impresión / plotter

Si `CAD_PLOT_DIR` apunta a la carpeta vigilada por el plotter o la reprografía,
`POST /api/cad/plot` deposita el DXF con marca temporal más un JSON con los
metadatos (escala, papel, proyecto, cliente) para que el operario ajuste el
trazado sin preguntar.

### Archivos

```
web/src/lib/cad/
├── types.ts        modelo de escena, unidades, papeles y escalas
├── linetypes.ts    capas ACI, tipos de línea y estilos SHX
├── dxfWriter.ts    serializador DXF R12/R2000/R2007
├── dims.ts         cotas, marcas de arquitecto y modulación de placas
├── planBuilder.ts  generación de alzados, plantas, cajetín, índice y cómputo
├── svgRenderer.ts  render SVG (vista previa y exportación vectorial)
├── exporter.ts     orquestación, descargas y conversión en servidor
├── download.ts     utilidades de descarga y cola de impresión
└── cad.test.ts     20 pruebas
server/src/routes/cad.js   status / convert / plot
```

### Límites conocidos

* Se usa geometría explícita para las cotas (líneas + textos) en lugar de
  entidades `DIMENSION`: se abren igual en todos los CAD y no dependen de
  estilos externos, pero no son paramétricas.
* El alzado reparte las aberturas desde el extremo izquierdo con separación
  constante; el usuario puede moverlas después en AutoCAD (están en
  `DW-ABERTURA`).

---

## 2. Escáner de estancias: WebXR (AR) y cámara / WebRTC

### Modos disponibles

| Modo | Requisito | Precisión típica | Uso |
| --- | --- | --- | --- |
| **WebXR `immersive-ar`** | Chrome Android con ARCore, Safari iOS 18+, HTTPS | ±1–3 cm | Medición 3D real con hit-test |
| **Cámara + homografía** | Cualquier móvil con navegador moderno, HTTPS | ±2–5 cm | Foto con rectángulo de referencia |
| **Cámara + escala simple** | Igual | ±5–10 % | Cuando no hay referencia rectangular |

La app detecta las capacidades reales del dispositivo (incluidas
características opcionales de la sesión AR, probando `hit-test`, `dom-overlay`,
`anchors` y `light-estimation`) y propone el modo recomendado.

### Flujo WebXR

1. `navigator.xr.requestSession('immersive-ar', { requiredFeatures: ['hit-test'] })`.
2. `XRWebGLLayer` + `local-floor` como espacio de referencia.
3. En cada fotograma, `getHitTestResults()` devuelve la pose 3D real del punto
   de impacto; se dibuja un retículo y las cintas de medición.
4. El evento `select` (toque en pantalla o gatillo del mando) fija cada
   esquina. También se pueden marcar puntos de techo para medir la altura.
5. Al finalizar: rectificación de ángulos a 90°, cálculo de perímetro, área,
   rectángulo envolvente y confianza.

El HUD muestra en vivo puntos, tramo en curso, perímetro y área estimada.

### Flujo por foto

1. `getUserMedia` con cámara trasera.
2. Captura del fotograma a canvas.
3. Se marcan las 4 esquinas de un rectángulo de medida conocida (con presets:
   baldosa 60, folio A3, ancho de puerta, metro plegado…).
4. Se marca el contorno de la estancia.
5. La homografía (DLT normalizada con eliminación gaussiana) corrige la
   perspectiva y devuelve las medidas; se informa del error de reproyección.

### Conversión al proyecto

`scanToRooms()` genera estancias reales del cómputo:

* **Techo**: un paño con el rectángulo envolvente orientado.
* **Muros**: un paño por lado del polígono, con la altura medida o estimada.
* **Ambos**: las dos anteriores.

Los tipos constructivos son seleccionables y las medidas se redondean al paso
elegido (5 cm en métrico, ¼ de pie en imperial).

### Asistencia remota

`/asistencia` permite compartir la cámara del móvil con la oficina técnica:

* Señalización por SSE + POST (`/api/ar/signal/:room`) → no hace falta abrir
  WebSocket ni puertos extra.
* Salas efímeras (`POST /api/ar/rooms`) con TTL, aforo y caducidad automática.
* El vídeo va P2P con DTLS-SRTP: el servidor nunca lo ve.

### Permisos del navegador

`Permissions-Policy` se ha ajustado en todos los despliegues (nginx, Caddy,
Vercel, Netlify y el servidor de desarrollo) a:

```
camera=(self), microphone=(self), xr-spatial-tracking=(self), fullscreen=(self), geolocation=(), payment=()
```

Sin esta corrección, `camera=()` impedía abrir la cámara por completo.

### Archivos

```
web/src/lib/ar/
├── types.ts          puntos, medidas y opciones de conversión
├── measurement.ts    ajuste de plano (Jacobi), áreas, rectificación, confianza
├── homography.ts     DLT + inversión + error de reproyección
├── capabilities.ts   detección de WebXR / cámara / WebGL
├── xrRenderer.ts     renderizador WebGL mínimo + geometría AR
├── xrScanner.ts      sesión WebXR, hit-test, captura de puntos
├── photoMeasure.ts   cámara, captura y medición por foto
├── webrtc.ts         asistencia remota P2P y señalización HTTP
├── scanToRooms.ts    conversión a estancias del proyecto
└── ar.test.ts        25 pruebas
web/src/components/ArScannerModal.tsx     interfaz del escáner
web/src/components/RemoteAssistPage.tsx   sala de asistencia (/asistencia)
server/src/routes/ar.js                   salas + señalización
```

---

## 3. Precios en tiempo real de distribuidores

### Arquitectura

```
Presupuesto ──▶ buildMaterialQueries() ──▶ PricingClient.sync()
                                              │
              ┌───────────────────────────────┴────────────────────────┐
              ▼                                                        ▼
      Proxy del servidor (/api/pricing/proxy)                 Transporte directo
      · lista blanca de hosts                                 (proveedores con CORS)
      · inyecta secretos (SUPPLIER_*)
      · anti-SSRF (bloquea rangos privados y redirects)
              │
              ▼
      Proveedor (rest-json | csv | mock | manual)
              │
              ▼
      normalizeQuote()  ·  matchMaterial()  ·  convertToPackage()  ·  convertCurrency()
              │
              ▼
      SyncReport ──▶ tabla comparativa ──▶ applyReportToPrices()
```

### Emparejamiento de artículos

1. **Normalización léxica**: minúsculas, sin acentos, sinónimos del sector
   (`pladur`, `drywall`, `gypsum`, `cartón-yeso` → `placa`; `montante` →
   `parante`; `solera` → `canal`; `furring` → `omega`…).
2. **Similitud**: 60 % Jaccard de tokens + 40 % subsecuencia común más larga.
3. **Umbral dinámico**: se endurece con nombres cortos o ambiguos.

### Conversión de precio

El precio del proveedor se lleva al **embalaje comercial del presupuesto**:

| Proveedor cotiza | Embalaje del proyecto | Resultado |
| --- | --- | --- |
| 4,20 €/m² | plancha de 2,88 m² | 12,10 € por plancha |
| 1,35 €/m | tira de 3 m | 4,05 € por tira |
| 0,018 €/u | caja de 1.000 u | 18,00 € por caja |
| 12,10 €/plancha | plancha | sin cambios |

Y después se aplica el **ajuste negociado** (`priceAdjustmentPct`, p. ej. −8 %
por volumen) y la **conversión de divisa** con las tasas del proyecto.

### Caché y resiliencia

* `localStorage` con TTL por proveedor y política *stale-while-revalidate*.
* Si un proveedor falla: reintentos con espera exponencial y, si sigue sin
  responder, se usan los últimos precios conocidos marcados como `cache`.
* Cada fila de la tabla indica proveedor, confianza de la coincidencia, stock y
  origen del dato.

### Seguridad

* Los secretos viven sólo en el servidor; el navegador envía el **nombre** de
  la variable (`secretRef`) y el proxy la inyecta. Prefijos admitidos:
  `SUPPLIER_`, `PRICING_`, `ERP_`, `DISTRIBUTOR_`, `CATALOG_`.
* Bloqueo de `localhost`, rangos privados, link-local, metadatos de nube y
  redirecciones (`redirect: 'error'`).
* Sólo HTTPS, sin credenciales en la URL, límite de tamaño y de tiempo,
  cabeceras hop-by-hop descartadas y límite de tasa por IP.
* La lista blanca (`PRICING_ALLOWED_HOSTS`) es la opción recomendada en
  producción.

### Archivos

```
web/src/lib/pricing/
├── types.ts             modelo de proveedor, cotización e informe
├── normalize.ts         sinónimos, similitud, unidades, embalaje, divisas
├── csv.ts               parseador CSV/TSV con detección de columnas
├── cache.ts             caché con TTL (localStorage + memoria)
├── engine.ts            transporte, validación SSRF y motor de sincronización
├── client.ts            configuración, plantillas y API de alto nivel
├── providers/
│   ├── rest.ts          REST/JSON genérico con plantillas y mapeo de campos
│   ├── csv.ts           tarifas CSV/TSV
│   └── mock.ts          proveedor simulado (demo y pruebas)
└── pricing.test.ts      35 pruebas
web/src/components/PricingModal.tsx   interfaz + editor de proveedores
server/src/routes/pricing.js          proxy con lista blanca de hosts
```

---

## Ejemplos generados

En [`docs/ejemplos/`](ejemplos/) hay láminas reales generadas por el módulo
(proyecto de demostración, formato A3 a escala 1:50):

| Archivo | Contenido |
| --- | --- |
| `indice.png` | Portada: memoria, índice de láminas, leyenda de capas y notas técnicas. |
| `lamina-01.png` | Planta de cielo raso y alzado de tabique acústico con cotas de modulación. |
| `lamina-02.png` | Lámina de baño (placa RH) con su cajetín. |
| `computo.png` | Cuadro de cómputo de materiales con resumen económico. |
| `plano-ejemplo-R2000.dxf` | **DXF R2000 listo para abrir en AutoCAD** (5 láminas en espacio modelo). |

### Corrección de precios por embalaje comercial

Al generar el cuadro de cómputo se detectó que el precio de los materiales con
embalaje múltiple se tomaba por unidad base y no por paquete: una caja de 1.000
tornillos se cotizaba a 0,015 $ (el precio de un tornillo) en lugar de 15 $.
Ahora `unitPrice` es siempre **por unidad comercial** (plancha, tira, caja,
rollo, balde):

```
precio por defecto = precio de referencia × contenido del embalaje × tasa de cambio
precio manual      = tal cual (ya se introduce por unidad comercial)
```

Afecta a tornillos (×1.000), perfiles (×3 m), masilla (×28 kg) y cintas
(×150 m). Las placas, cuyo embalaje es una unidad, no cambian.

## Puesta en marcha rápida

```bash
# 1. Front-end
npm install
npm run dev            # http://localhost:3000

# 2. Backend (opcional pero recomendado: habilita proxy, DWG y asistencia)
npm --prefix server install
npm --prefix server run bootstrap
npm run dev:api        # http://127.0.0.1:8443
```

Sin backend, la calculadora, los planos CAD y el escáner AR funcionan igual;
sólo se desactivan la conversión a DWG, el proxy de precios (se usa el
transporte directo) y la asistencia remota.
