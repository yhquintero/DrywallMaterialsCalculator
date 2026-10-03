/**
 * Orquestador de exportación: DXF, DWG (vía conversor), SVG y PDF vectorial.
 *
 * Nota importante sobre DWG: el formato .dwg es propietario de Autodesk y
 * ninguna librería libre puede escribirlo de forma fiable. La vía profesional
 * es exportar DXF (intercambio abierto, editable al 100 % en AutoCAD) y
 * convertirlo a DWG con ODA File Converter, instalado en el servidor. Este
 * módulo detecta esa disponibilidad y degrada limpiamente si no está.
 */

import { CadBounds, CadEntity, CadScene, CadUnits, DxfVersion } from './types';
import { DxfDocument } from './dxfWriter';
import { sheetToSvg } from './svgRenderer';

/** Convierte un nombre de proyecto en un nombre de archivo seguro. */
export function slugify(value: string, fallback = 'plano'): string {
  const slug = String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
    .slice(0, 60);
  return slug || fallback;
}

export interface DxfExportOptions {
  version: DxfVersion;
  units: CadUnits;
  /** Índice de lámina concreta o `null` para exportar todas. */
  sheetIndex: number | null;
}

/** Coloca las láminas en fila dentro del espacio modelo. */
function layOutSheets(scene: CadScene, sheetIndex: number | null): { entities: CadEntity[]; bounds: CadBounds } {
  const selected = sheetIndex === null ? scene.sheets : scene.sheets.filter((_, i) => i === sheetIndex);
  const gap = scene.sheets.length ? (scene.sheets[0].paper.width / 1000) * scene.scaleDenominator * 0.15 : 1;
  const entities: CadEntity[] = [];
  let offsetX = 0;
  const bounds: CadBounds = { minX: 0, minY: 0, maxX: 0, maxY: 0 };

  selected.forEach((sheet) => {
    const sheetW = (sheet.paper.width / 1000) * sheet.scaleDenominator;
    const sheetH = (sheet.paper.height / 1000) * sheet.scaleDenominator;
    const push = (e: CadEntity): void => {
      entities.push(translate(e, offsetX, 0));
    };
    sheet.entities.forEach(push);
    sheet.frame.forEach(push);
    bounds.maxX = Math.max(bounds.maxX, offsetX + sheetW);
    bounds.maxY = Math.max(bounds.maxY, sheetH);
    offsetX += sheetW + gap;
  });

  return { entities, bounds };
}

function translate(e: CadEntity, dx: number, dy: number): CadEntity {
  const t = (p: { x: number; y: number }) => ({ x: p.x + dx, y: p.y + dy });
  switch (e.kind) {
    case 'line':
      return { ...e, a: t(e.a), b: t(e.b) };
    case 'polyline':
      return { ...e, points: e.points.map(t) };
    case 'circle':
    case 'arc':
      return { ...e, center: t(e.center) };
    case 'text':
      return { ...e, position: t(e.position) };
    case 'solid':
      return { ...e, corners: [t(e.corners[0]), t(e.corners[1]), t(e.corners[2]), t(e.corners[3])] };
    case 'insert':
      return { ...e, position: t(e.position) };
    default:
      return e;
  }
}

/** Serializa la escena a DXF (todas las láminas o una sola). */
export function sceneToDxf(scene: CadScene, options: DxfExportOptions): string {
  const { entities, bounds } = layOutSheets(scene, options.sheetIndex);
  const doc = new DxfDocument({
    version: options.version,
    units: options.units,
    title: scene.name,
    dimScale: Math.max(1, scene.scaleDenominator / 25),
    extents: bounds
  });
  doc.useStandardLayers();
  doc.addEntities(entities);
  return doc.toDxf();
}

// ── Descargas ────────────────────────────────────────────────────────────────

export function downloadBlob(filename: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export function downloadText(filename: string, content: string, mime = 'text/plain;charset=utf-8'): void {
  downloadBlob(filename, new Blob([content], { type: mime }));
}

export function exportSceneToDxf(scene: CadScene, options: DxfExportOptions): void {
  const dxf = sceneToDxf(scene, options);
  const sheetTag = options.sheetIndex === null ? 'completo' : `lamina-${options.sheetIndex + 1}`;
  downloadText(`${slugify(scene.name)}_${sheetTag}_1-${scene.scaleDenominator}.dxf`, dxf, 'application/dxf');
}

export function exportSceneToSvg(scene: CadScene, sheetIndex = 0): void {
  const sheet = scene.sheets[sheetIndex] ?? scene.sheets[0];
  if (!sheet) return;
  const svg = sheetToSvg(sheet, scene, { width: 2400 });
  downloadText(`${slugify(scene.name)}_${sheet.name}.svg`, svg, 'image/svg+xml');
}

// ── Conversión servidor (DWG / PDF) ──────────────────────────────────────────

export interface ConverterStatus {
  available: boolean;
  provider: string | null;
  formats: string[];
  message: string;
}

export interface ConversionResult {
  ok: boolean;
  filename?: string;
  error?: string;
  hint?: string;
}

/** Consulta al backend si el conversor DXF→DWG está disponible. */
export async function getConverterStatus(): Promise<ConverterStatus> {
  try {
    const res = await fetch('/api/cad/status', { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return (await res.json()) as ConverterStatus;
  } catch (error) {
    return {
      available: false,
      provider: null,
      formats: [],
      message:
        'Servidor de conversión no disponible. Puedes abrir el DXF directamente en AutoCAD y guardarlo como DWG.'
    };
  }
}

/**
 * Solicita al backend la conversión del DXF a DWG (o PDF).
 * El backend usa ODA File Converter si está instalado y configurado.
 */
export async function convertDxf(
  dxf: string,
  filename: string,
  target: 'dwg' | 'pdf' = 'dwg'
): Promise<ConversionResult> {
  try {
    const res = await fetch('/api/cad/convert', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ dxf, filename, target })
    });

    const contentType = res.headers.get('Content-Type') ?? '';
    if (res.ok && contentType.includes('application/octet-stream')) {
      const blob = await res.blob();
      const disposition = res.headers.get('Content-Disposition') ?? '';
      const match = /filename="?([^"]+)"?/.exec(disposition);
      const outName = match ? match[1] : `${filename}.${target}`;
      downloadBlob(outName, blob);
      return { ok: true, filename: outName };
    }

    const payload = (await res.json().catch(() => ({}))) as { error?: string; hint?: string };
    return {
      ok: false,
      error: payload.error ?? `El servidor respondió ${res.status}`,
      hint: payload.hint
    };
  } catch (error) {
    return {
      ok: false,
      error: error instanceof Error ? error.message : 'Error de red al contactar el conversor',
      hint: 'Comprueba que el backend de DrywallPro está en marcha (`npm run dev:api`).'
    };
  }
}

/** Estadísticas de la escena para mostrar en la interfaz. */
export interface SceneStats {
  sheets: number;
  entities: number;
  layers: number;
  estimatedBytes: number;
}

export function sceneStats(scene: CadScene): SceneStats {
  let entities = 0;
  scene.sheets.forEach((s) => {
    entities += s.entities.length + s.frame.length;
  });
  const layers = new Set<string>();
  scene.sheets.forEach((s) => {
    s.entities.forEach((e) => layers.add(e.layer));
  });
  return {
    sheets: scene.sheets.length,
    entities,
    layers: layers.size || 1,
    // Estimación: ~34 bytes por entidad media en DXF ASCII.
    estimatedBytes: Math.round(entities * 34 + 6000)
  };
}
