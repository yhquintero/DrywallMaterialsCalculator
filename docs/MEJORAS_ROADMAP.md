# Roadmap de mejoras — DrywallPro Master

Lista priorizada de mejoras para implementar **de una en una**. Cada entrada está
dimensionada para resolverse en una iteración y viene con su criterio de
aceptación, de modo que se pueda cerrar y pasar a la siguiente sin arrastrar
deuda.

Leyenda de esfuerzo: 🟢 ≤ 1 sesión · 🟡 2–3 sesiones · 🔴 proyecto de varias semanas.

---

## Fase 0 — Ya implementado (base actual)

- [x] **M1 · Exportación de planos vectoriales DXF/DWG** con capas normalizadas,
      cotas, cajetín, índice, cómputo de materiales, SVG y conversión a DWG en
      servidor (ODA File Converter) + cola de impresión.
- [x] **M2 · Escáner de habitaciones con cámara y Realidad Aumentada (WebXR)**
      con hit-test, medición en metros, rectificación de ángulos, modo foto con
      homografía y asistencia remota WebRTC.
- [x] **M3 · Catálogo de precios en tiempo real de distribuidores** con proxy
      seguro, emparejamiento inteligente, conversión de embalaje y divisa,
      caché con TTL y editor de proveedores.

---

## Fase 1 — Consolidar lo construido (alto impacto, bajo riesgo)

1. 🟢 **Firma digital y sello de tiempo de los planos.** Firmar el DXF/PDF con
   el certificado del contratista (CMS/PKCS#7) y estampar hash SHA-256 en el
   cajetín. *Aceptación:* el plano incluye bloque de firma verificable y el
   servidor devuelve el certificado usado.
2. 🟢 **Histórico de precios por material.** Guardar cada sincronización en
   `price_history` y dibujar la evolución (sparkline) junto al precio.
   *Aceptación:* gráfico de 30 días y alerta cuando el precio sube más del X %.
3. 🟢 **Presupuesto en dos monedas simultáneas.** Mostrar total en la divisa del
   proyecto y en USD/EUR con la tasa aplicada y su fecha. *Aceptación:* el PDF
   incluye ambas columnas y la referencia de la tasa.
4. 🟢 **Plantillas de cómputo guardables.** Guardar una combinación
   tipología + medidas + precios como plantilla reutilizable de 1 clic.
   *Aceptación:* crear/aplicar/eliminar plantillas desde el proyecto.
5. 🟢 **Validación de datos en la captura manual.** Avisos en vivo (medidas
   absurdas, aberturas mayores que el paño, solapes, perímetro incoherente).
   *Aceptación:* los errores aparecen inline y bloquean el cálculo incoherente.
6. 🟢 **Accesibilidad y teclado.** Navegación completa por teclado, `aria-label`
   en todos los controles, foco visible y contraste AA. *Aceptación:* auditoría
   axe sin errores críticos y prueba manual sólo con teclado.
7. 🟢 **Panel de diagnóstico.** Página `/estado` con versión, backend, licencia,
   TLS, capacidades AR, proveedores configurados y tamaño de caché.
   *Aceptación:* permite resolver el 80 % de los soportes sin preguntar.
8. 🟢 **Code-splitting y presupuesto de rendimiento.** Lazy-load por pestaña,
   límite de tamaño en CI y medición con Lighthouse. *Aceptación:* primera
   carga < 250 KB gzip y Lighthouse ≥ 90.

## Fase 2 — Productividad en obra

9. 🟢 **Modo offline-first con cola de sincronización.** Service Worker que
   cachea el shell y encola sincronizaciones/consultas para cuando vuelva la
   red. *Aceptación:* se puede medir y calcular sin cobertura; al recuperar
   red, los datos se sincronizan solos.
10. 🟢 **Firma a mano alzada en el móvil.** Captura de la firma del cliente en
    el PDF del presupuesto con validez probatoria (hash + sello temporal).
    *Aceptación:* firma embebida en el PDF y verificable.
11. 🟢 **Órdenes de compra por proveedor.** Repartir la lista de compra entre
    proveedores según el mejor precio y generar una OC por cada uno.
    *Aceptación:* un PDF de OC por proveedor con subtotales y plazos.
12. 🟢 **Exportación a Excel nativo (XLSX).** Además del CSV, generar libro con
    formato, fórmulas y varias hojas (materiales, mano de obra, resumen).
    *Aceptación:* el archivo abre en Excel y Google Sheets sin avisos.
13. 🟡 **Integración con almacén/ERP.** API `POST /api/integrations/erp` con
    mapeo configurable (Odoo, SAP B1, A3, Dynamics) y reintentos idempotentes.
    *Aceptación:* alta de pedido de compra desde el presupuesto con traza.
14. 🟡 **Facturación electrónica.** Plantillas de factura según normativa local
    (Verifactu/TicketBAI/Facturae/CFDI/DTE) y envío por email.
    *Aceptación:* factura válida emitida y archivada con su serie.
15. 🟡 **Multi-proyecto y comparativa de versiones.** Comparar dos
    presupuestos (delta de materiales, precios y superficie) con informe.
    *Aceptación:* diff visual y exportable entre dos revisiones.
16. 🟡 **Biblioteca de precios propia.** Base de datos interna de precios
    históricos del contratista con ajuste automático por IPC/índice.
    *Aceptación:* sugerencia de precio con justificación estadística.

## Fase 3 — Ingeniería y validación técnica

17. 🟡 **Cálculo estructural de steel framing.** Verificación de perfiles
    (PGC/PGU), cargas de viento/nieve, esbeltez, pandeo y arriostramiento
    según EN 1993/AISI S100. *Aceptación:* memoria de cálculo exportable con
    comprobaciones y coeficientes.
18. 🟡 **Acústica y fuego.** Cálculo de índice Rw/R'w por composición de
    trasdosado y clasificación EI según EN 13501/EN 1365. *Aceptación:* ficha
    técnica por sistema con valores justificados.
19. 🟡 **Verificación térmica y puentes térmicos.** Transmitancia U del cerramiento
    con EIFS, condensaciones (Glaser) y control de humedad. *Aceptación:*
    informe con cumplimiento del CTE/ASHRAE 90.1.
20. 🟡 **Desperdicio inteligente (nesting de placas).** Optimizar el corte de
    placas por paño para minimizar recortes, con plano de despiece.
    *Aceptación:* reducción medible de m² comprados respecto al modo actual.
21. 🔴 **Modelo BIM/IFC export (IFC4).** Exportar los sistemas como `IfcWall`,
    `IfcSlab`, `IfcCovering` con propiedades acústicas y de fuego, para
    Revit/ArchiCAD/BIMserver. *Aceptación:* el IFC abre sin errores de validación.
22. 🔴 **Clash detection básico.** Detección de interferencias con MEP
    (conductos, tuberías) importados como IFC. *Aceptación:* listado de choques
    con capturas.

## Fase 4 — Realidad aumentada y captura avanzada

23. 🟡 **Persistencia de anclajes AR (`anchors`).** Guardar las esquinas en
    anclas persistentes para volver días después al mismo replanteo.
    *Aceptación:* reapertura en la misma posición ±5 cm.
24. 🟡 **Replanteo en obra sobre el terreno (AR proyectado).** Dibujar en el
    suelo físico la modulación real (parantes, placas) a escala 1:1 para
    replantear antes de montar. *Aceptación:* verificación con cinta métrica.
25. 🟡 **Escaneo de techo y altura automática.** Captura de techo + suelo y
    altura medida, con aviso de falso techo registrable vs. continuo.
    *Aceptación:* altura con error < 2 cm y adjuntada al paño.
26. 🔴 **Fotogrametría multi-foto (SfM).** Reconstrucción 3D a partir de varias
    imágenes para estancias irregulares. *Aceptación:* malla + medidas con
    error < 3 %.
27. 🟡 **Detección automática de aberturas.** Visión artificial para reconocer
    puertas y ventanas en la foto y pre-rellenar los huecos. *Aceptación:*
    detección correcta en ≥ 80 % de fotos de prueba.
28. 🟡 **Medición sobre vídeo en directo.** Trazar el contorno viendo la pantalla,
    sin capturar foto, con apuntado AR del trazo.

## Fase 5 — Web, diseño y colaboración

29. 🟢 **Editor 2D de planta editable.** Mover vértices y paredes sobre un
    lienzo de planta en vez de sólo por formularios. *Aceptación:* edición
    directa con rejilla y cotas dinámicas.
30. 🟡 **Exportar el visualizador a imagen/vídeo.** Captura del corte 3D y GIF
    del giro para presentaciones al cliente.
31. 🟡 **Enlaces de solo lectura para el cliente.** URL firmada con caducidad
    que muestra superficie, presupuesto y planos sin poder editar.
32. 🟡 **Comentarios y anotaciones sobre el plano.** Pin con comentario en una
    coordenada del plano, visibles en equipo.
33. 🟡 **Historial de cambios (auditoría de proyecto).** Quién cambió qué y
    cuándo, con posibilidad de restaurar una versión.
34. 🟡 **Temas claro/oscuro y modo obra.** Interfaz de alto contraste para
    exteriores, tipografía grande y botones para usar con guantes.
35. 🟢 **Internacionalización completa ES/EN/PT.** Todos los textos extraídos,
    unidades y formatos locales por región.

## Fase 6 — Plataforma, datos e IA

36. 🟡 **Backend propio de proyectos (sustituir localStorage).** API REST con
    usuarios, proyectos, versiones y control de acceso; migración desde el
    almacenamiento local. *Aceptación:* varios dispositivos ven el mismo proyecto.
37. 🟡 **Trabajo sin conexión con CRDT.** Edición concurrente y sincronización
    sin conflictos (Yjs/Automerge).
38. 🟡 **Asistente IA de presupuestos.** Sugerir tipologías, detectar olvidos y
    explicar desviaciones usando el histórico de obras cerradas.
39. 🟡 **Detección de anomalías en precios.** Alertas cuando un proveedor
    cotiza fuera de rango respecto al histórico.
40. 🟡 **Reconocimiento de planos (OCR/vectorización).** Subir un PDF de plano
    del arquitecto y extraer estancias y medidas automáticamente.
41. 🔴 **Gemelo digital de obra.** Seguimiento del avance real (AR/cámara)
    frente al planificado, con curva de producción y desviaciones.
42. 🟡 **Panel analítico del negocio.** Margen por tipo de obra, coste por m²
    histórico, productividad por cuadrilla y estacionalidad.

## Fase 7 — Android nativo (app `:app`)

43. 🟡 **ARCore en la app móvil.** Llevar el escáner WebXR a ARCore nativo con
    profundidad (Depth API) para mejor precisión.
44. 🟡 **Sincronización web ↔ móvil.** Mismo modelo de datos, con cola de
    cambios y resolución de conflictos.
45. 🟡 **Impresión desde el móvil.** Enviar el plano a la cola del plotter
    mediante el mismo endpoint `POST /api/cad/plot`.
46. 🟢 **Widget y accesos rápidos.** Acceso directo a «nueva medición» y
    «último presupuesto» desde la pantalla de inicio.
47. 🟢 **Modo campo (batería y datos).** Reducir refresco de sensores, cacheo
    agresivo y desactivar animaciones cuando la batería baja.

## Fase 8 — Operación, calidad y negocio

48. 🟢 **CI/CD completo.** Matriz de tests (web, servidor, Android) + despliegue
    automático a staging con verificación de humo.
    *Nota:* la matriz Android (`Compile`, `Static Analysis`, `Unit Tests`) ya
    fallaba en `main` antes de estos módulos; conviene arreglarla primero, ya
    que la web y el servidor sí pasan limpios.
49. 🟢 **Pruebas end-to-end (Playwright).** Flujos críticos: crear obra, medir,
    presupuestar, exportar PDF/DXF.
50. 🟢 **Cobertura y calidad de código.** Umbral mínimo de cobertura, reglas
    ESLint/Detekt estrictas y análisis de dependencias vulnerables.
51. 🟢 **Telemetría anónima opt-in y crash reporting.** Métricas de uso que
    alimenten el roadmap sin invadir la privacidad.
52. 🟢 **Documentación de usuario.** Manual ilustrado por tarea, vídeos cortos
    y FAQ de obra; guía de resolución de problemas de cámara/AR.
53. 🟡 **Multi-empresa y equipos.** Varias empresas/usuarios con roles,
    plantillas compartidas y límites por plan.
54. 🟡 **Pasarela de pago y planes.** Suscripción con facturación automática
    conectada a la Consola de Licencias.
55. 🟡 **API pública para clientes.** Endpoints documentados (OpenAPI) para que
    distribuidores y ERPs integren presupuestos y pedidos.

---

## Orden sugerido para las próximas 6 iteraciones

| Iteración | Mejora | Por qué primero |
| --- | --- | --- |
| 1 | **#9 Modo offline-first** | La obra casi nunca tiene cobertura; es la queja nº 1 de los usuarios de campo. |
| 2 | **#11 Órdenes de compra por proveedor** | Cierra el ciclo que abrimos con el módulo de precios. |
| 3 | **#2 Histórico de precios** | Aprovecha los datos que ya empieza a generar el módulo de precios. |
| 4 | **#1 Firma digital de planos** | Requisito para presentar el plano a dirección facultativa. |
| 5 | **#20 Nesting de placas** | Ahorro directo y medible en el coste de materiales. |
| 6 | **#17 Cálculo estructural steel framing** | Habilita el segmento profesional (estructuras portantes). |

**Convención para trabajar de una en una:** cada mejora se cierra con sus
pruebas automatizadas, documentación en `docs/` y una nota en el historial de
versiones; no se abre la siguiente hasta que la anterior esté verificada en la
rama principal.
