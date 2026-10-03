/**
 * Renderizador vectorial SVG de la escena CAD.
 *
 * Se usa para la vista previa en pantalla y para la exportación a SVG
 * (vectorial real, escalable y editable en Inkscape / Illustrator / Corel).
 */

import { CadEntity, CadScene, CadSheet } from './types';

/** Paleta ACI (AutoCAD Color Index) de los índices más usados en el plano. */
const ACI_COLORS: Record<number, string> = {
  0: '#ffffff',
  1: '#ff5555',
  2: '#ffff55',
  3: '#55ff55',
  4: '#55ffff',
  5: '#5599ff',
  6: '#ff55ff',
  7: '#e2e8f0',
  8: '#808080',
  9: '#c0c0c0',
  10: '#ff0000',
  11: '#ff7f7f',
  12: '#a50000',
  20: '#ff3f7f',
  30: '#ff7f00',
  40: '#ffb400',
  41: '#ffe100',
  42: '#d4e100',
  50: '#ffff00',
  60: '#bfd400',
  70: '#00ff00',
  80: '#00d4a5',
  90: '#00ffff',
  140: '#7fd4ff',
  150: '#557fff',
  160: '#7f7fff',
  170: '#a57fff',
  180: '#ff7fff',
  190: '#ff7fbf',
  210: '#7f7f7f',
  240: '#3f3f3f',
  250: '#5a5a5a',
  251: '#6e6e6e',
  252: '#828282',
  253: '#969696',
  254: '#aaaaaa',
  255: '#bfbfbf'
};

const LAYER_COLOR: Record<string, number> = {
  'ARQ-MURO': 7,
  'DW-PERFIL': 4,
  'DW-CANAL': 5,
  'DW-OMEGA': 140,
  'DW-PRIMARIO': 30,
  'DW-PLACA': 3,
  'DW-AISLANTE': 40,
  'DW-ABERTURA': 2,
  'DW-FIJACION': 8,
  'DW-EJE': 1,
  'DW-COTA': 6,
  'DW-TEXTO': 210,
  'DW-SIMBOLO': 7,
  'PLANO-MARCO': 8,
  'PLANO-TABLA': 7
};

function layerColor(layer: string): string {
  const aci = LAYER_COLOR[layer] ?? 7;
  return ACI_COLORS[aci] ?? '#e2e8f0';
}

export interface SvgOptions {
  /** Ancho del SVG en píxeles (la altura se calcula con la relación de aspecto). */
  width?: number;
  background?: string;
  /** Rotula las capas como grupos <g> con `inkscape:groupmode`. */
  groupByLayer?: boolean;
  padding?: number;
  /** ¿Incluir el marco/cajetín? */
  includeFrame?: boolean;
}

interface SvgCtx {
  scale: number;   // metros de modelo → px
  height: number;  // metros de modelo (Y crece hacia arriba)
  out: string[];
}

function esc(value: string): string {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function toSvgPoint(ctx: SvgCtx, x: number, y: number): [number, number] {
  return [x * ctx.scale, (ctx.height - y) * ctx.scale];
}

function fmt(n: number): string {
  return Number.isFinite(n) ? (Math.round(n * 1000) / 1000).toString() : '0';
}

function renderEntity(e: CadEntity, ctx: SvgCtx): string {
  const color = layerColor(e.layer);
  switch (e.kind) {
    case 'line': {
      const [x1, y1] = toSvgPoint(ctx, e.a.x, e.a.y);
      const [x2, y2] = toSvgPoint(ctx, e.b.x, e.b.y);
      return `<line x1="${fmt(x1)}" y1="${fmt(y1)}" x2="${fmt(x2)}" y2="${fmt(y2)}" stroke="${color}" stroke-width="1" />`;
    }
    case 'polyline': {
      const pts = e.points.map((p) => toSvgPoint(ctx, p.x, p.y).map(fmt).join(',')).join(' ');
      return `<${e.closed ? 'polygon' : 'polyline'} points="${pts}" fill="none" stroke="${color}" stroke-width="1" />`;
    }
    case 'circle': {
      const [cx, cy] = toSvgPoint(ctx, e.center.x, e.center.y);
      return `<circle cx="${fmt(cx)}" cy="${fmt(cy)}" r="${fmt(Math.max(0.6, e.radius * ctx.scale))}" fill="none" stroke="${color}" stroke-width="1" />`;
    }
    case 'arc': {
      const [cx, cy] = toSvgPoint(ctx, e.center.x, e.center.y);
      const r = e.radius * ctx.scale;
      const a0 = (e.startAngle * Math.PI) / 180;
      const a1 = (e.endAngle * Math.PI) / 180;
      // En SVG la Y crece hacia abajo: se invierte el signo del seno.
      const p0 = [cx + r * Math.cos(a0), cy - r * Math.sin(a0)];
      const p1 = [cx + r * Math.cos(a1), cy - r * Math.sin(a1)];
      const large = Math.abs(e.endAngle - e.startAngle) > 180 ? 1 : 0;
      const sweep = e.endAngle > e.startAngle ? 0 : 1;
      return `<path d="M ${fmt(p0[0])} ${fmt(p0[1])} A ${fmt(r)} ${fmt(r)} 0 ${large} ${sweep} ${fmt(p1[0])} ${fmt(p1[1])}" fill="none" stroke="${color}" stroke-width="1" />`;
    }
    case 'text': {
      const [x, y] = toSvgPoint(ctx, e.position.x, e.position.y);
      const size = Math.max(1.2, e.height * ctx.scale);
      const anchor = e.align === 'center' || e.align === 'middle' ? 'middle' : e.align === 'right' ? 'end' : 'start';
      const rot = e.rotation ? ` transform="rotate(${fmt(-e.rotation)} ${fmt(x)} ${fmt(y)})"` : '';
      return (
        `<text x="${fmt(x)}" y="${fmt(y)}" fill="${color}" font-family="JetBrains Mono, Consolas, monospace" ` +
        `font-size="${fmt(size)}" text-anchor="${anchor}" dominant-baseline="alphabetic"${rot}>${esc(e.value)}</text>`
      );
    }
    case 'solid': {
      const pts = e.corners.map((p) => toSvgPoint(ctx, p.x, p.y).map(fmt).join(',')).join(' ');
      return `<polygon points="${pts}" fill="${color}" stroke="none" />`;
    }
    case 'insert':
      return '';
    default:
      return '';
  }
}

/** Renderiza una lámina concreta a SVG. */
export function sheetToSvg(sheet: CadSheet, scene: CadScene, options: SvgOptions = {}): string {
  const width = options.width ?? 1600;
  const paperWidthM = sheet.paper.width / 1000 * sheet.scaleDenominator;
  const paperHeightM = sheet.paper.height / 1000 * sheet.scaleDenominator;
  const pad = options.padding ?? 8;
  const scale = width / paperWidthM;
  const heightPx = paperHeightM * scale;

  const ctx: SvgCtx = { scale, height: paperHeightM, out: [] };
  const entities = options.includeFrame === false
    ? sheet.entities
    : [...sheet.entities, ...sheet.frame];

  const body = entities.map((e) => renderEntity(e, ctx)).filter(Boolean).join('\n    ');

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="${width}" height="${Math.round(heightPx)}" viewBox="0 0 ${width} ${Math.round(heightPx)}">
  <metadata>
    <drywallpro:doc xmlns:drywallpro="https://drywallpro.app/cad">
      <drywallpro:project>${esc(scene.meta.projectName)}</drywallpro:project>
      <drywallpro:sheet>${esc(sheet.name)}</drywallpro:sheet>
      <drywallpro:scale>1:${sheet.scaleDenominator}</drywallpro:scale>
      <drywallpro:paper>${esc(sheet.name)}</drywallpro:paper>
    </drywallpro:doc>
  </metadata>
  <rect x="0" y="0" width="${width}" height="${Math.round(heightPx)}" fill="${options.background ?? '#020617'}" />
  <g id="${esc(sheet.name)}" transform="translate(0,${pad})">
    ${body}
  </g>
</svg>`;
}

/** Renderiza todas las láminas de la escena. */
export function sceneToSvgSheets(scene: CadScene, options: SvgOptions = {}): Array<{ name: string; svg: string }> {
  return scene.sheets.map((sheet) => ({ name: sheet.name, svg: sheetToSvg(sheet, scene, options) }));
}
