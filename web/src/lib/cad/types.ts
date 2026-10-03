/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  DrywallPro Master — Módulo CAD (DXF / DWG)
 *  Modelo de escena vectorial intermedio.
 *
 *  Flujo:  Rooms + ProjectConfig  ──▶ CadScene ──▶ DxfDocument ──▶ archivo .dxf
 *                                        │
 *                                        └──▶ render SVG (preview / export)
 *
 *  Todas las coordenadas de la escena se expresan en METROS reales (modelo
 *  1:1). La conversión a la unidad de dibujo (mm, cm, m, in, ft) se hace en
 *  el momento de serializar, de modo que la misma escena sirve para el DXF y
 *  para la vista previa SVG.
 * ═══════════════════════════════════════════════════════════════════════════
 */

/** Unidad de dibujo del archivo DXF de salida. */
export type CadUnits = 'mm' | 'cm' | 'm' | 'in' | 'ft';

/** Versión de formato DXF solicitada. */
export type DxfVersion = 'R12' | 'R2000' | 'R2007';

/** Formatos de papel soportados (en milímetros, orientación horizontal). */
export type PaperSize = 'A4' | 'A3' | 'A2' | 'A1' | 'A0' | 'ANSI_A' | 'ANSI_B' | 'ANSI_C' | 'ANSI_D' | 'ANSI_E';

export interface CadPoint {
  x: number;
  y: number;
}

/** Justificación horizontal del texto (código 72 de DXF). */
export type TextAlign = 'left' | 'center' | 'right' | 'middle';

export type CadEntity =
  | { kind: 'line'; layer: string; a: CadPoint; b: CadPoint }
  | { kind: 'polyline'; layer: string; points: CadPoint[]; closed?: boolean }
  | { kind: 'circle'; layer: string; center: CadPoint; radius: number }
  | { kind: 'arc'; layer: string; center: CadPoint; radius: number; startAngle: number; endAngle: number }
  | {
      kind: 'text';
      layer: string;
      position: CadPoint;
      height: number;   // altura en metros reales (se escala al serializar)
      value: string;
      rotation?: number; // grados
      align?: TextAlign;
      style?: string;
    }
  | { kind: 'solid'; layer: string; corners: [CadPoint, CadPoint, CadPoint, CadPoint] }
  | { kind: 'insert'; layer: string; block: string; position: CadPoint; scale?: number; rotation?: number };

export interface CadBlock {
  name: string;
  /** Entidades definidas en coordenadas locales del bloque (metros). */
  entities: CadEntity[];
}

export interface CadBounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

/** Contenido de una lámina (hoja) del plano. */
export interface CadSheet {
  name: string;
  title: string;
  /** Tamaño de papel en mm (ancho × alto, orientación horizontal). */
  paper: { width: number; height: number };
  /** Denominador de la escala de trazado (50 ⇒ 1:50). */
  scaleDenominator: number;
  /** Entidades en coordenadas de modelo (metros). */
  entities: CadEntity[];
  /** Marco / cajetín ya escalado a coordenadas de modelo. */
  frame: CadEntity[];
}

export interface CadScene {
  name: string;
  unitsOut: CadUnits;
  paper: PaperSize;
  scaleDenominator: number;
  blocks: CadBlock[];
  sheets: CadSheet[];
  bounds: CadBounds;
  meta: {
    projectName: string;
    clientName: string;
    contractorCompany: string;
    drawnBy: string;
    date: string;
    revision: string;
    unitSystem: 'metric' | 'imperial';
    totalNetArea: number;
    areaUnit: string;
    notes: string;
  };
}

/** Factor de conversión metros → unidad de dibujo. */
export const UNIT_FACTORS: Record<CadUnits, number> = {
  mm: 1000,
  cm: 100,
  m: 1,
  in: 39.3700787,
  ft: 3.2808399
};

/** Código $INSUNITS de DXF. */
export const INSUNITS_CODE: Record<CadUnits, number> = {
  in: 1,
  ft: 2,
  mm: 4,
  cm: 5,
  m: 6
};

/** Tamaños de papel en milímetros (ancho × alto, apaisado). */
export const PAPER_SIZES: Record<PaperSize, { width: number; height: number; label: string }> = {
  A4: { width: 297, height: 210, label: 'A4 (297 × 210 mm)' },
  A3: { width: 420, height: 297, label: 'A3 (420 × 297 mm)' },
  A2: { width: 594, height: 420, label: 'A2 (594 × 420 mm)' },
  A1: { width: 841, height: 594, label: 'A1 (841 × 594 mm)' },
  A0: { width: 1189, height: 841, label: 'A0 (1189 × 841 mm)' },
  ANSI_A: { width: 279.4, height: 215.9, label: 'ANSI A / Carta (11" × 8.5")' },
  ANSI_B: { width: 431.8, height: 279.4, label: 'ANSI B / Tabloide (17" × 11")' },
  ANSI_C: { width: 558.8, height: 431.8, label: 'ANSI C (22" × 17")' },
  ANSI_D: { width: 863.6, height: 558.8, label: 'ANSI D (34" × 22")' },
  ANSI_E: { width: 1117.6, height: 863.6, label: 'ANSI E (44" × 34")' }
};

/** Escalas de trazado habituales en obra. */
export const PLOT_SCALES = [10, 20, 25, 50, 75, 100, 200];

export function emptyBounds(): CadBounds {
  return { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
}

export function expandBounds(b: CadBounds, x: number, y: number): void {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return;
  if (x < b.minX) b.minX = x;
  if (y < b.minY) b.minY = y;
  if (x > b.maxX) b.maxX = x;
  if (y > b.maxY) b.maxY = y;
}

/** Calcula el recuadro envolvente de una lista de entidades (en metros). */
export function entitiesBounds(entities: CadEntity[]): CadBounds {
  const b = emptyBounds();
  for (const e of entities) {
    switch (e.kind) {
      case 'line':
        expandBounds(b, e.a.x, e.a.y);
        expandBounds(b, e.b.x, e.b.y);
        break;
      case 'polyline':
        e.points.forEach((p) => expandBounds(b, p.x, p.y));
        break;
      case 'circle':
        expandBounds(b, e.center.x - e.radius, e.center.y - e.radius);
        expandBounds(b, e.center.x + e.radius, e.center.y + e.radius);
        break;
      case 'arc':
        expandBounds(b, e.center.x - e.radius, e.center.y - e.radius);
        expandBounds(b, e.center.x + e.radius, e.center.y + e.radius);
        break;
      case 'text':
        expandBounds(b, e.position.x, e.position.y);
        expandBounds(b, e.position.x + e.value.length * e.height * 0.55, e.position.y + e.height);
        break;
      case 'solid':
        e.corners.forEach((p) => expandBounds(b, p.x, p.y));
        break;
      case 'insert':
        expandBounds(b, e.position.x, e.position.y);
        break;
    }
  }
  if (!Number.isFinite(b.minX)) return { minX: 0, minY: 0, maxX: 0, maxY: 0 };
  return b;
}
