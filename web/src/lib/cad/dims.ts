/**
 * Utilidades de acotación para planos de drywall.
 *
 * Se generan cotas como geometría explícita (líneas de cota, líneas de
 * referencia, marcas de arquitecto y texto) en lugar de entidades DIMENSION.
 * Motivo: una cota construida con geometría primitiva se abre y se edita sin
 * dependencias en AutoCAD, BricsCAD, LibreCAD, QCAD, nanoCAD, DraftSight y en
 * cualquier visor DXF, y además permite controlar al milímetro su apariencia.
 */

import { CadEntity, CadPoint } from './types';

export interface DimStyle {
  /** Altura de texto en unidades de modelo (metros). */
  textHeight: number;
  /** Tamaño de la marca de arquitecto (slash) en metros. */
  tickSize: number;
  /** Hueco entre el elemento medido y el inicio de la línea de referencia. */
  extGap: number;
  /** Cuánto sobrepasa la línea de referencia la línea de cota. */
  extOver: number;
}

export function makeDimStyle(textHeight: number): DimStyle {
  return {
    textHeight,
    tickSize: textHeight * 0.7,
    extGap: textHeight * 0.5,
    extOver: textHeight * 0.35
  };
}

/** Inserta una marca de arquitecto (slash a 45°) en el extremo de una cota. */
function archTick(out: CadEntity[], layer: string, p: CadPoint, dir: 1 | -1, style: DimStyle): void {
  const d = style.tickSize * 0.5;
  out.push({ kind: 'line', layer, a: { x: p.x - d, y: p.y - d * dir }, b: { x: p.x + d, y: p.y + d * dir } });
}

/**
 * Cota horizontal.
 * @param yMeasured  Y de los puntos medidos (elemento).
 * @param yDim       Y de la línea de cota.
 */
export function horizontalDim(
  out: CadEntity[],
  layer: string,
  x1: number,
  x2: number,
  yMeasured: number,
  yDim: number,
  text: string,
  style: DimStyle
): void {
  if (Math.abs(x2 - x1) < 1e-6) return;
  const dirDown = yDim < yMeasured; // la cota cuelga por debajo del elemento
  const extStart = yMeasured + (dirDown ? -style.extGap : style.extGap);
  const extEnd = yDim + (dirDown ? -style.extOver : style.extOver);

  out.push({ kind: 'line', layer, a: { x: x1, y: extStart }, b: { x: x1, y: extEnd } });
  out.push({ kind: 'line', layer, a: { x: x2, y: extStart }, b: { x: x2, y: extEnd } });
  out.push({ kind: 'line', layer, a: { x: x1, y: yDim }, b: { x: x2, y: yDim } });
  archTick(out, layer, { x: x1, y: yDim }, 1, style);
  archTick(out, layer, { x: x2, y: yDim }, 1, style);
  out.push({
    kind: 'text',
    layer,
    position: { x: (x1 + x2) / 2, y: yDim + style.textHeight * 0.35 },
    height: style.textHeight,
    value: text,
    align: 'middle',
    rotation: 0
  });
}

/** Cota vertical (texto rotado 90°). */
export function verticalDim(
  out: CadEntity[],
  layer: string,
  y1: number,
  y2: number,
  xMeasured: number,
  xDim: number,
  text: string,
  style: DimStyle
): void {
  if (Math.abs(y2 - y1) < 1e-6) return;
  const dirLeft = xDim < xMeasured;
  const extStart = xMeasured + (dirLeft ? -style.extGap : style.extGap);
  const extEnd = xDim + (dirLeft ? -style.extOver : style.extOver);

  out.push({ kind: 'line', layer, a: { x: extStart, y: y1 }, b: { x: extEnd, y: y1 } });
  out.push({ kind: 'line', layer, a: { x: extStart, y: y2 }, b: { x: extEnd, y: y2 } });
  out.push({ kind: 'line', layer, a: { x: xDim, y: y1 }, b: { x: xDim, y: y2 } });
  archTick(out, layer, { x: xDim, y: y1 }, 1, style);
  archTick(out, layer, { x: xDim, y: y2 }, 1, style);
  out.push({
    kind: 'text',
    layer,
    position: { x: xDim - style.textHeight * 0.4, y: (y1 + y2) / 2 },
    height: style.textHeight,
    value: text,
    align: 'middle',
    rotation: 90
  });
}

/** Cadena de cotas horizontales consecutivas (modulación de parantes). */
export function horizontalDimChain(
  out: CadEntity[],
  layer: string,
  positions: number[],
  yMeasured: number,
  yDim: number,
  format: (a: number, b: number) => string,
  style: DimStyle
): void {
  for (let i = 0; i < positions.length - 1; i += 1) {
    horizontalDim(out, layer, positions[i], positions[i + 1], yMeasured, yDim, format(positions[i], positions[i + 1]), style);
  }
}

/**
 * Distribuye piezas de placa sobre un rectángulo con junta alternada
 * (stagger), devolviendo los rectángulos recortados al área útil.
 */
export function layoutBoards(
  width: number,
  height: number,
  boardWidth: number,
  boardHeight: number,
  stagger = true
): Array<{ x: number; y: number; w: number; h: number }> {
  const pieces: Array<{ x: number; y: number; w: number; h: number }> = [];
  if (width <= 0 || height <= 0 || boardWidth <= 0 || boardHeight <= 0) return pieces;

  // Orientación: la placa trabaja a lo largo de la dimensión dominante.
  const vertical = height >= boardHeight * 0.55;
  const bw = vertical ? boardWidth : boardHeight;
  const bh = vertical ? boardHeight : boardWidth;

  const rows = Math.max(1, Math.ceil(height / bh));
  for (let r = 0; r < rows; r += 1) {
    const y0 = r * bh;
    const y1 = Math.min(height, y0 + bh);
    const rowOffset = stagger && r % 2 === 1 ? -bw / 2 : 0;
    const cols = Math.max(1, Math.ceil((width - rowOffset) / bw));
    for (let c = 0; c < cols; c += 1) {
      const x0 = rowOffset + c * bw;
      const x1 = Math.min(width, x0 + bw);
      if (x1 - x0 < 0.02 || y1 - y0 < 0.02) continue;
      pieces.push({ x: Math.max(0, x0), y: y0, w: x1 - Math.max(0, x0), h: y1 - y0 });
    }
  }
  return pieces;
}
