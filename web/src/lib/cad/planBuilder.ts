/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  Generador de planos técnicos de drywall (modelo 1:1 en metros)
 *  ─────────────────────────────────────────────────────────────────────────
 *  Convierte las estancias del proyecto (Rooms + Segments + Openings) en
 *  geometría vectorial: alzados de tabique con modulación de parantes,
 *  plantas de cielo raso con perfilería primaria/omega, replanteo de placas
 *  con junta alternada, refuerzos de aberturas, trama de aislamiento, patrón
 *  de fijaciones, acotación profesional y cajetín de lámina.
 *
 *  Todo se construye en coordenadas de modelo (metros reales). El trazado a
 *  papel se resuelve escalando el cajetín por el denominador de escala, que
 *  es el procedimiento habitual en espacio modelo de AutoCAD.
 * ═══════════════════════════════════════════════════════════════════════════
 */

import { CalculationSummary, ConstructionType, ProjectConfig, Room, Segment } from '../../types';
import { CONSTRUCTION_TYPES } from '../../data/materials';
import {
  CadBounds,
  CadEntity,
  CadPoint,
  CadScene,
  CadSheet,
  CadUnits,
  PaperSize,
  PAPER_SIZES,
  entitiesBounds,
  expandBounds,
  emptyBounds
} from './types';
import { CAD_LAYERS as L } from './linetypes';
import { DimStyle, horizontalDim, layoutBoards, makeDimStyle, verticalDim } from './dims';

export interface PlanOptions {
  paper: PaperSize;
  scaleDenominator: number;
  units: CadUnits;
  layout: 'per-room' | 'tiled';
  includeFraming: boolean;
  includeBoards: boolean;
  includeFasteners: boolean;
  includeInsulation: boolean;
  includeOpenings: boolean;
  includeDimensions: boolean;
  includeAxis: boolean;
  includeLegend: boolean;
  includeBom: boolean;
  includeTitleBlock: boolean;
  /** Ancho del parante visto en alzado (m). */
  studWidth: number;
  /** Canto del canal/solera (m). */
  trackDepth: number;
  /** Separación de perfiles primarios de cielo raso (m). */
  primarySpacing: number;
  /** Paso de la retícula del plafón registrable (m). */
  gridModule: number;
  /** Máximo de símbolos de fijación por paño (protección de rendimiento). */
  maxFastenersPerSegment: number;
}

export const DEFAULT_PLAN_OPTIONS: PlanOptions = {
  paper: 'A3',
  scaleDenominator: 50,
  units: 'mm',
  layout: 'tiled',
  includeFraming: true,
  includeBoards: true,
  includeFasteners: false,
  includeInsulation: true,
  includeOpenings: true,
  includeDimensions: true,
  includeAxis: true,
  includeLegend: true,
  includeBom: true,
  includeTitleBlock: true,
  studWidth: 0.048,
  trackDepth: 0.03,
  primarySpacing: 1.2,
  gridModule: 0.6,
  maxFastenersPerSegment: 600
};

type FramingMode = 'wall' | 'structural-wall' | 'ceiling' | 'grid-ceiling';

const FRAMING_MODE: Record<ConstructionType, FramingMode> = {
  techo_st: 'ceiling',
  techo_rh: 'ceiling',
  muro_sencillo: 'wall',
  tabique_divisor: 'wall',
  muro_rf: 'wall',
  plafon_reticulado: 'grid-ceiling',
  cajillo_viga: 'wall',
  fachada_eifs: 'wall',
  steel_framing: 'structural-wall',
  multi_partes: 'ceiling'
};

interface DrawCtx {
  opts: PlanOptions;
  /** Convierte milímetros de papel a metros de modelo. */
  mm: (value: number) => number;
  scale: number;
  unitSystem: ProjectConfig['unitSystem'];
  dim: DimStyle;
  dimSmall: DimStyle;
  /** Separación de parantes en metros. */
  studSpacing: number;
  /** Formato de placa (ancho × largo) en metros. */
  sheetWidth: number;
  sheetLength: number;
}

/** Pasa de la unidad activa del proyecto a metros. */
function toMeters(value: number, unitSystem: ProjectConfig['unitSystem']): number {
  return unitSystem === 'imperial' ? value * 0.3048 : value;
}

/** Formatea una longitud para el texto de cota según el sistema de unidades. */
function formatLength(meters: number, unitSystem: ProjectConfig['unitSystem']): string {
  if (unitSystem === 'imperial') {
    const totalInches = meters / 0.0254;
    const feet = Math.floor(totalInches / 12);
    const inches = Math.round(totalInches - feet * 12);
    if (inches === 12) return `${feet + 1}'`;
    return inches === 0 ? `${feet}'` : `${feet}'-${inches}"`;
  }
  return `${meters.toFixed(2)}`;
}

function rect(out: CadEntity[], layer: string, x: number, y: number, w: number, h: number): void {
  out.push({
    kind: 'polyline',
    layer,
    points: [
      { x, y },
      { x: x + w, y },
      { x: x + w, y: y + h },
      { x, y: y + h }
    ],
    closed: true
  });
}

function line(out: CadEntity[], layer: string, x1: number, y1: number, x2: number, y2: number): void {
  out.push({ kind: 'line', layer, a: { x: x1, y: y1 }, b: { x: x2, y: y2 } });
}

function text(
  out: CadEntity[],
  layer: string,
  x: number,
  y: number,
  height: number,
  value: string,
  align: 'left' | 'center' | 'right' | 'middle' = 'left',
  rotation = 0
): void {
  out.push({ kind: 'text', layer, position: { x, y }, height, value, align, rotation });
}

// ─────────────────────────────────────────────────────────────────────────────
//  Alzado de muro / tabique
// ─────────────────────────────────────────────────────────────────────────────

interface OpeningBox {
  x0: number;
  x1: number;
  y0: number;
  y1: number;
  name: string;
  width: number;
  height: number;
  kind: 'door' | 'window';
}

/** Calcula las posiciones de parantes (caras exteriores incluidas). */
function studPositions(width: number, spacing: number, studWidth: number): number[] {
  const positions: number[] = [0];
  const step = Math.max(0.1, spacing);
  const innerLimit = width - studWidth;
  for (let x = step; x < innerLimit - 0.02; x += step) {
    positions.push(Number(x.toFixed(4)));
  }
  if (innerLimit > 0.02 && positions[positions.length - 1] < innerLimit - 0.02) {
    positions.push(Number(innerLimit.toFixed(4)));
  }
  return positions;
}

function drawWallElevation(
  out: CadEntity[],
  segment: Segment,
  mode: FramingMode,
  ctx: DrawCtx,
  opts: { hasInsulation: boolean }
): { width: number; height: number } {
  const o = ctx.opts;
  const W = Math.max(0.2, toMeters(segment.length, ctx.unitSystem));
  const H = Math.max(0.2, toMeters(segment.width, ctx.unitSystem));
  const studW = o.studWidth;
  const track = o.trackDepth;

  // Contorno general
  rect(out, L.ARQ_MURO.name, 0, 0, W, H);

  // Solera inferior y canal superior
  if (o.includeFraming) {
    line(out, L.DW_CANAL.name, 0, track, W, track);
    line(out, L.DW_CANAL.name, 0, H - track, W, H - track);
    if (mode === 'structural-wall') {
      // PGU de 0.90 mm: se dibuja con doble línea para remarcar el canto.
      line(out, L.DW_CANAL.name, 0, track + 0.012, W, track + 0.012);
      line(out, L.DW_CANAL.name, 0, H - track - 0.012, W, H - track - 0.012);
    }
  }

  // Aberturas
  const openings: OpeningBox[] = [];
  if (o.includeOpenings && segment.openings?.length) {
    let cursor = 0.3;
    segment.openings.forEach((op) => {
      const w = Math.max(0.2, toMeters(op.width, ctx.unitSystem));
      const h = Math.max(0.2, toMeters(op.height, ctx.unitSystem));
      if (cursor + w > W - 0.3) return;
      const y0 = op.type === 'window' ? Math.min(H - h - 0.15, 0.9) : 0.02;
      openings.push({
        x0: cursor,
        x1: cursor + w,
        y0,
        y1: y0 + h,
        name: op.name,
        width: w,
        height: h,
        kind: op.type
      });
      cursor += w + 0.45;
    });
  }

  // Parantes: se omiten los que caen dentro del vano (allí van dintel y jambas).
  const realSpacing = ctx.studSpacing;
  const studXs = studPositions(W, realSpacing, studW).filter(
    (x) => !openings.some((op) => x + studW > op.x0 - studW && x < op.x1 + studW)
  );

  if (o.includeFraming) {
    studXs.forEach((x) => rect(out, L.DW_PERFIL.name, x, track, studW, Math.max(0, H - 2 * track)));

    // Refuerzos de abertura: jambas + dintel + antepecho
    openings.forEach((op) => {
      rect(out, L.DW_PERFIL.name, op.x0 - studW, track, studW, Math.max(0, H - 2 * track));
      rect(out, L.DW_PERFIL.name, op.x1, track, studW, Math.max(0, H - 2 * track));
      rect(out, L.DW_PERFIL.name, op.x0 - studW, op.y1, op.x1 - op.x0 + 2 * studW, 0.09);
      if (op.kind === 'window') {
        rect(out, L.DW_PERFIL.name, op.x0 - studW, Math.max(track, op.y0 - 0.09), op.x1 - op.x0 + 2 * studW, 0.09);
      }
      rect(out, L.DW_ABERTURAS.name, op.x0, op.y0, op.x1 - op.x0, op.y1 - op.y0);
      text(
        out,
        L.DW_TEXTO.name,
        op.x0,
        op.y1 + 0.14,
        ctx.mm(2.2),
        `${op.name} ${formatLength(op.width, ctx.unitSystem)} x ${formatLength(op.height, ctx.unitSystem)}`
      );
    });

    // Cruces de San Andrés (steel framing estructural)
    if (mode === 'structural-wall') {
      for (let i = 0; i < studXs.length - 1; i += 1) {
        const a = studXs[i] + studW;
        const b = studXs[i + 1];
        if (b - a < 0.3) continue;
        line(out, L.DW_PERFIL.name, a, track, b, H - track);
        line(out, L.DW_PERFIL.name, a, H - track, b, track);
      }
    }
  }

  // Aislamiento (trama en zig-zag entre parantes)
  if (o.includeInsulation && opts.hasInsulation && o.includeFraming) {
    const cavities: Array<[number, number]> = [];
    const boundaries = [0, ...studXs.map((x) => x), W];
    for (let i = 0; i < boundaries.length - 1; i += 1) {
      const a = boundaries[i];
      const b = boundaries[i + 1];
      if (b - a < 0.08) continue;
      if (openings.some((op) => a < op.x1 && b > op.x0 && track < op.y1 && H - track > op.y0)) continue;
      cavities.push([a, b]);
    }
    // Limita la densidad para no disparar el tamaño del DXF.
    const maxCavities = 80;
    cavities.slice(0, maxCavities).forEach(([a, b]) => {
      const steps = 6;
      const pitch = (b - a) / steps;
      const points: CadPoint[] = [];
      for (let s = 0; s <= steps; s += 1) {
        points.push({ x: a + pitch * s, y: track + (s % 2 === 0 ? 0.015 : 0.055) });
      }
      out.push({ kind: 'polyline', layer: L.DW_AISLAMIENTO.name, points, closed: false });
    });
  }

  // Modulación de placas con junta alternada
  let boardPieces: Array<{ x: number; y: number; w: number; h: number }> = [];
  if (o.includeBoards) {
    const bw = Math.max(0.3, ctx.sheetWidth);
    const bh = Math.max(0.3, ctx.sheetLength);
    boardPieces = layoutBoards(W, H, bw, bh, true).slice(0, 400);
    boardPieces.forEach((p) => rect(out, L.DW_PLACA.name, p.x, p.y, p.w, p.h));
  }

  // Patrón de fijaciones
  if (o.includeFasteners && boardPieces.length) {
    const r = ctx.mm(0.45);
    let count = 0;
    const cap = o.maxFastenersPerSegment;
    boardPieces.forEach((p) => {
      if (count > cap) return;
      [[p.x, p.y], [p.x + p.w, p.y], [p.x, p.y + p.h], [p.x + p.w, p.y + p.h]].forEach(([cx, cy]) => {
        if (count > cap) return;
        out.push({ kind: 'circle', layer: L.DW_FIJACION.name, center: { x: cx, y: cy }, radius: r });
        count += 1;
      });
    });
  }

  // Ejes de replanteo
  if (o.includeAxis) {
    line(out, L.DW_EJE.name, -ctx.mm(12), H + ctx.mm(8), W + ctx.mm(12), H + ctx.mm(8));
    out.push({ kind: 'circle', layer: L.DW_EJE.name, center: { x: 0, y: H + ctx.mm(8) }, radius: ctx.mm(3.5) });
    out.push({ kind: 'circle', layer: L.DW_EJE.name, center: { x: W, y: H + ctx.mm(8) }, radius: ctx.mm(3.5) });
  }

  // Acotación
  if (o.includeDimensions) {
    const bottomA = -ctx.mm(14);
    const bottomB = -ctx.mm(26);
    horizontalDim(out, L.DW_COTA.name, 0, W, -0.01, bottomA, formatLength(W, ctx.unitSystem), ctx.dim);
    if (o.includeFraming && studXs.length > 1 && studXs.length < 60) {
      const xs = [0, ...studXs.map((x) => x + studW / 2), W];
      for (let i = 0; i < xs.length - 1; i += 1) {
        horizontalDim(
          out,
          L.DW_COTA.name,
          xs[i],
          xs[i + 1],
          bottomA,
          bottomB,
          formatLength(xs[i + 1] - xs[i], ctx.unitSystem),
          ctx.dimSmall
        );
      }
    }
    verticalDim(out, L.DW_COTA.name, 0, H, 0, -ctx.mm(20), formatLength(H, ctx.unitSystem), ctx.dim);
    text(
      out,
      L.DW_TEXTO.name,
      0,
      H + ctx.mm(5),
      ctx.mm(2.6),
      `MODULACION DE PARANTES @ ${formatLength(realSpacing, ctx.unitSystem)}${ctx.unitSystem === 'imperial' ? '' : ' m'}`,
      'left'
    );
  }

  return { width: W, height: H };
}

// ─────────────────────────────────────────────────────────────────────────────
//  Planta de cielo raso (perfil primario + omega)
// ─────────────────────────────────────────────────────────────────────────────

function drawCeilingPlan(
  out: CadEntity[],
  segment: Segment,
  ctx: DrawCtx,
  opts: { hasInsulation: boolean }
): { width: number; height: number } {
  const o = ctx.opts;
  const W = Math.max(0.2, toMeters(segment.length, ctx.unitSystem));
  const H = Math.max(0.2, toMeters(segment.width, ctx.unitSystem));
  const spacing = ctx.studSpacing;

  rect(out, L.ARQ_MURO.name, 0, 0, W, H);

  if (o.includeFraming) {
    // Ángulo perimetral
    rect(out, L.DW_CANAL.name, 0.025, 0.025, Math.max(0, W - 0.05), Math.max(0, H - 0.05));

    // Perfiles primarios: paralelos al lado corto, cada `primarySpacing`
    const primaries: number[] = [];
    const step = Math.max(0.3, o.primarySpacing);
    for (let x = step / 2; x < W - 0.05; x += step) primaries.push(Number(x.toFixed(3)));
    primaries.forEach((x) => {
      line(out, L.DW_PRIMARIO.name, x - 0.012, 0, x - 0.012, H);
      line(out, L.DW_PRIMARIO.name, x + 0.012, 0, x + 0.012, H);
    });

    // Omegas / furring transversales
    const omegas: number[] = [];
    for (let y = spacing; y < H - 0.05; y += spacing) omegas.push(Number(y.toFixed(3)));
    omegas.slice(0, 200).forEach((y) => {
      line(out, L.DW_OMEGA.name, 0, y, W, y);
    });

    // Suspensiones (varilla + anclaje)
    const r = ctx.mm(0.6);
    primaries.slice(0, 60).forEach((x) => {
      for (let y = step / 2; y < H; y += step) {
        out.push({ kind: 'circle', layer: L.DW_SIMBOLO.name, center: { x, y }, radius: r });
        line(out, L.DW_SIMBOLO.name, x - r * 1.6, y, x + r * 1.6, y);
        line(out, L.DW_SIMBOLO.name, x, y - r * 1.6, x, y + r * 1.6);
      }
    });
  }

  // Modulación de placas
  let boardPieces: Array<{ x: number; y: number; w: number; h: number }> = [];
  if (o.includeBoards) {
    boardPieces = layoutBoards(W, H, ctx.sheetWidth, ctx.sheetLength, true).slice(0, 400);
    boardPieces.forEach((p) => rect(out, L.DW_PLACA.name, p.x, p.y, p.w, p.h));
  }

  // Fijaciones
  if (o.includeFasteners && boardPieces.length) {
    const r = ctx.mm(0.4);
    let count = 0;
    boardPieces.forEach((p) => {
      if (count > o.maxFastenersPerSegment) return;
      for (let x = p.x + 0.3; x < p.x + p.w - 0.05; x += 0.3) {
        if (count > o.maxFastenersPerSegment) return;
        out.push({ kind: 'circle', layer: L.DW_FIJACION.name, center: { x, y: p.y }, radius: r });
        out.push({ kind: 'circle', layer: L.DW_FIJACION.name, center: { x, y: p.y + p.h }, radius: r });
        count += 2;
      }
    });
  }

  if (o.includeInsulation && opts.hasInsulation) {
    const pitchX = 0.4;
    for (let x = pitchX; x < W; x += pitchX) {
      const points: CadPoint[] = [];
      for (let y = 0; y <= H; y += 0.1) {
        points.push({ x: x + (Math.round(y / 0.1) % 2 === 0 ? 0 : 0.02), y });
      }
      out.push({ kind: 'polyline', layer: L.DW_AISLAMIENTO.name, points, closed: false });
    }
  }

  if (o.includeAxis) {
    line(out, L.DW_EJE.name, -ctx.mm(10), H / 2, W + ctx.mm(10), H / 2);
    line(out, L.DW_EJE.name, W / 2, -ctx.mm(10), W / 2, H + ctx.mm(10));
  }

  if (o.includeDimensions) {
    horizontalDim(out, L.DW_COTA.name, 0, W, -0.01, -ctx.mm(14), formatLength(W, ctx.unitSystem), ctx.dim);
    verticalDim(out, L.DW_COTA.name, 0, H, 0, -ctx.mm(20), formatLength(H, ctx.unitSystem), ctx.dim);
    if (o.includeFraming) {
      const xs: number[] = [];
      for (let x = 0; x < W - 0.05; x += Math.max(0.3, o.primarySpacing)) xs.push(Number(x.toFixed(3)));
      xs.push(W);
      for (let i = 0; i < xs.length - 1 && i < 40; i += 1) {
        horizontalDim(
          out,
          L.DW_COTA.name,
          xs[i],
          xs[i + 1],
          H + 0.01,
          H + ctx.mm(14),
          formatLength(xs[i + 1] - xs[i], ctx.unitSystem),
          ctx.dimSmall
        );
      }
      text(
        out,
        L.DW_TEXTO.name,
        0,
        H + ctx.mm(22),
        ctx.mm(2.6),
        `OMEGAS @ ${formatLength(spacing, ctx.unitSystem)} / PRIMARIOS @ ${formatLength(o.primarySpacing, ctx.unitSystem)}`,
        'left'
      );
    } else {
      text(out, L.DW_TEXTO.name, 0, H + ctx.mm(14), ctx.mm(2.6), 'PLANTA DE CIELO RASO', 'left');
    }
  }

  return { width: W, height: H };
}

// ─────────────────────────────────────────────────────────────────────────────
//  Planta de plafón registrable (retícula 60×60 / 2×2 ft)
// ─────────────────────────────────────────────────────────────────────────────

function drawGridCeilingPlan(out: CadEntity[], segment: Segment, ctx: DrawCtx): { width: number; height: number } {
  const o = ctx.opts;
  const W = Math.max(0.2, toMeters(segment.length, ctx.unitSystem));
  const H = Math.max(0.2, toMeters(segment.width, ctx.unitSystem));
  const mod = Math.max(0.3, o.gridModule);

  rect(out, L.ARQ_MURO.name, 0, 0, W, H);

  // Perfil perimetral (angular en L)
  rect(out, L.DW_CANAL.name, 0.024, 0.024, Math.max(0, W - 0.048), Math.max(0, H - 0.048));

  // Perfiles principales (cada 1.20 m) y secundarios (cada módulo)
  const mainStep = 1.2;
  const mains: number[] = [];
  for (let x = mainStep / 2; x < W; x += mainStep) mains.push(Number(x.toFixed(3)));
  mains.forEach((x) => {
    line(out, L.DW_PRIMARIO.name, x - 0.012, 0, x - 0.012, H);
    line(out, L.DW_PRIMARIO.name, x + 0.012, 0, x + 0.012, H);
  });

  // Retícula: líneas transversales cada módulo + terciarios
  const crossX: number[] = [];
  for (let x = mod; x < W; x += mod) crossX.push(Number(x.toFixed(3)));
  const crossY: number[] = [];
  for (let y = mod; y < H; y += mod) crossY.push(Number(y.toFixed(3)));

  crossX.slice(0, 200).forEach((x) => {
    if (mains.some((m) => Math.abs(m - x) < 0.05)) return;
    line(out, L.DW_OMEGA.name, x, 0, x, H);
  });
  crossY.slice(0, 200).forEach((y) => line(out, L.DW_OMEGA.name, 0, y, W, y));

  // Baldosas (se marcan las que quedan cortadas en el perímetro)
  if (o.includeBoards) {
    const colsX = [0, ...crossX.filter((x) => !mains.some((m) => Math.abs(m - x) < 0.05)), W];
    const colsY = [0, ...crossY, H];
    for (let i = 0; i < colsX.length - 1 && i < 60; i += 1) {
      for (let j = 0; j < colsY.length - 1 && j < 60; j += 1) {
        const w = colsX[i + 1] - colsX[i];
        const h = colsY[j + 1] - colsY[j];
        if (w < 0.05 || h < 0.05) continue;
        const cut = w < mod - 0.01 || h < mod - 0.01;
        rect(out, cut ? L.DW_ABERTURAS.name : L.DW_PLACA.name, colsX[i], colsY[j], w, h);
      }
    }
  }

  // Suspensión
  const r = ctx.mm(0.6);
  mains.slice(0, 40).forEach((x) => {
    for (let y = mainStep / 2; y < H; y += mainStep) {
      out.push({ kind: 'circle', layer: L.DW_SIMBOLO.name, center: { x, y }, radius: r });
    }
  });

  if (o.includeDimensions) {
    horizontalDim(out, L.DW_COTA.name, 0, W, -0.01, -ctx.mm(14), formatLength(W, ctx.unitSystem), ctx.dim);
    verticalDim(out, L.DW_COTA.name, 0, H, 0, -ctx.mm(20), formatLength(H, ctx.unitSystem), ctx.dim);
    text(
      out,
      L.DW_TEXTO.name,
      0,
      H + ctx.mm(14),
      ctx.mm(2.6),
      `RETICULA ${Math.round(mod * 100)} x ${Math.round(mod * 100)} cm - PRINCIPALES @ 1.20 m`,
      'left'
    );
  }

  return { width: W, height: H };
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dibujo completo de una estancia
// ─────────────────────────────────────────────────────────────────────────────

interface RoomDrawing {
  name: string;
  subtitle: string;
  entities: CadEntity[];
  bbox: CadBounds;
}

function buildRoomDrawing(room: Room, ctx: DrawCtx): RoomDrawing {
  const typeDef = CONSTRUCTION_TYPES[room.type] ?? CONSTRUCTION_TYPES.techo_st;
  const mode = FRAMING_MODE[room.type] ?? 'wall';
  const hasInsulation = Object.keys(typeDef.consumptionRates).some((k) => /lana|aislan|insul/i.test(k));
  const entities: CadEntity[] = [];

  const gap = ctx.mm(25);
  const drawings: Array<{ entities: CadEntity[]; width: number; height: number }> = [];

  room.segments.forEach((segment) => {
    const local: CadEntity[] = [];
    let size: { width: number; height: number };
    if (mode === 'ceiling') size = drawCeilingPlan(local, segment, ctx, { hasInsulation });
    else if (mode === 'grid-ceiling') size = drawGridCeilingPlan(local, segment, ctx);
    else size = drawWallElevation(local, segment, mode, ctx, { hasInsulation });
    drawings.push({ entities: local, width: size.width, height: size.height });
  });

  // Coloca los paños de la estancia en fila
  let cursor = 0;
  let maxHeight = 0;
  drawings.forEach((d, i) => {
    const dx = i === 0 ? 0 : cursor + gap;
    d.entities.forEach((e) => entities.push(translateEntity(e, dx, 0)));
    cursor = dx + d.width;
    maxHeight = Math.max(maxHeight, d.height);
  });

  // Rótulo de la estancia
  text(entities, L.DW_TEXTO.name, 0, maxHeight + ctx.mm(12), ctx.mm(4.2), room.name.toUpperCase(), 'left');
  text(
    entities,
    L.DW_TEXTO.name,
    0,
    maxHeight + ctx.mm(5),
    ctx.mm(2.6),
    `${typeDef.name} - ${mode === 'ceiling' ? 'PLANTA DE CIELO RASO' : mode === 'grid-ceiling' ? 'PLANTA DE PLAFON REGISTRABLE' : 'ALZADO DE TABIQUE'} (${room.segments.length} pano${room.segments.length === 1 ? '' : 's'})`,
    'left'
  );
  if (room.notes) {
    text(entities, L.DW_TEXTO.name, 0, maxHeight + ctx.mm(1), ctx.mm(2.2), `NOTA: ${room.notes}`, 'left');
  }

  return {
    name: room.name,
    subtitle: typeDef.name,
    entities,
    bbox: entitiesBounds(entities)
  };
}

function translateEntity(e: CadEntity, dx: number, dy: number): CadEntity {
  const t = (p: CadPoint): CadPoint => ({ x: p.x + dx, y: p.y + dy });
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

// ─────────────────────────────────────────────────────────────────────────────
//  Cajetín, marco, leyenda y cuadro de materiales
// ─────────────────────────────────────────────────────────────────────────────

interface FrameGeometry {
  sheetW: number;
  sheetH: number;
  area: { x: number; y: number; w: number; h: number };
  frameEntities: CadEntity[];
}

function buildFrame(ctx: DrawCtx, paper: PaperSize, includeTitleBlock: boolean): FrameGeometry {
  const size = PAPER_SIZES[paper];
  const sheetW = ctx.mm(size.width);
  const sheetH = ctx.mm(size.height);
  const margin = ctx.mm(8);
  const titleH = includeTitleBlock ? ctx.mm(40) : 0;
  const out: CadEntity[] = [];

  rect(out, L.PLANO_MARCO.name, margin, margin, sheetW - 2 * margin, sheetH - 2 * margin);
  rect(
    out,
    L.PLANO_MARCO.name,
    margin + ctx.mm(3),
    margin + ctx.mm(3),
    sheetW - 2 * margin - ctx.mm(6),
    sheetH - 2 * margin - ctx.mm(6)
  );

  if (includeTitleBlock) {
    const y0 = margin + ctx.mm(3);
    const x0 = margin + ctx.mm(3);
    const w = sheetW - 2 * margin - ctx.mm(6);
    const h = titleH;
    line(out, L.PLANO_MARCO.name, x0, y0 + h, x0 + w, y0 + h);
    const cols = [0, 0.24, 0.46, 0.63, 0.76, 0.88, 1];
    for (let i = 1; i < cols.length - 1; i += 1) {
      line(out, L.PLANO_MARCO.name, x0 + w * cols[i], y0, x0 + w * cols[i], y0 + h);
    }
    line(out, L.PLANO_MARCO.name, x0, y0 + h * 0.42, x0 + w, y0 + h * 0.42);
  }

  return {
    sheetW,
    sheetH,
    area: {
      x: margin + ctx.mm(12),
      y: margin + titleH + ctx.mm(12),
      w: sheetW - 2 * margin - ctx.mm(24),
      h: sheetH - 2 * margin - titleH - ctx.mm(24)
    },
    frameEntities: out
  };
}

interface TitleBlockMeta {
  company: string;
  project: string;
  client: string;
  scale: string;
  units: string;
  sheetIndex: number;
  sheetTotal: number;
  date: string;
  revision: string;
  area: string;
  drawingName: string;
}

function fitText(ctx: DrawCtx, value: string, widthMeters: number, textSizeMm: number): string {
  const maxChars = Math.max(4, Math.floor(widthMeters / (ctx.mm(1) * 1.55 * (textSizeMm / 2.8))));
  return value.length > maxChars ? `${value.slice(0, Math.max(3, maxChars - 1))}.` : value;
}

function buildTitleBlock(ctx: DrawCtx, meta: TitleBlockMeta): CadEntity[] {
  const out: CadEntity[] = [];
  const size = PAPER_SIZES[ctx.opts.paper];
  const margin = ctx.mm(8);
  const x0 = margin + ctx.mm(3);
  const y0 = margin + ctx.mm(3);
  const w = ctx.mm(size.width) - 2 * margin - ctx.mm(6);
  const h = ctx.mm(40);
  const colX = [0, 0.24, 0.46, 0.63, 0.76, 0.88, 1];

  const cell = (i: number, label: string, value: string, valueSize = 2.8): void => {
    const cx = x0 + w * colX[i];
    const cw = w * (colX[i + 1] - colX[i]);
    const pad = ctx.mm(2.5);
    text(out, L.DW_TEXTO.name, cx + pad, y0 + h * 0.26, ctx.mm(2.0), label.toUpperCase(), 'left');
    text(out, L.DW_TEXTO.name, cx + pad, y0 + h * 0.08, ctx.mm(valueSize), fitText(ctx, value, cw - pad * 2, valueSize), 'left');
  };

  cell(0, 'Empresa / Contratista', meta.company, 2.6);
  cell(1, 'Proyecto', meta.project, 2.6);
  cell(2, 'Cliente', meta.client, 2.6);
  cell(3, 'Escala / Unidades', `${meta.scale} (${meta.units})`, 2.6);
  cell(4, 'Lam.', `${meta.sheetIndex}/${meta.sheetTotal}`, 3.4);
  cell(5, 'Fecha / Rev.', `${meta.date} / ${meta.revision}`, 2.6);

  text(out, L.DW_TEXTO.name, x0 + ctx.mm(2), y0 + h + ctx.mm(4), ctx.mm(3.2), meta.drawingName.toUpperCase(), 'left');
  text(out, L.DW_TEXTO.name, x0 + w - ctx.mm(2), y0 + h + ctx.mm(4), ctx.mm(2.6), `SUP. NETA: ${meta.area}`, 'right');
  return out;
}

const LEGEND_ITEMS: Array<{ label: string; layer: string }> = [
  { label: 'Parante / montante', layer: L.DW_PERFIL.name },
  { label: 'Canal, solera y angular', layer: L.DW_CANAL.name },
  { label: 'Omega / furring', layer: L.DW_OMEGA.name },
  { label: 'Perfil primario suspendido', layer: L.DW_PRIMARIO.name },
  { label: 'Modulacion de placas', layer: L.DW_PLACA.name },
  { label: 'Aislamiento', layer: L.DW_AISLAMIENTO.name },
  { label: 'Aberturas y refuerzos', layer: L.DW_ABERTURAS.name },
  { label: 'Fijaciones', layer: L.DW_FIJACION.name }
];

function buildLegend(ctx: DrawCtx, x: number, topY: number, width: number): CadEntity[] {
  const out: CadEntity[] = [];
  const height = ctx.mm(10) + LEGEND_ITEMS.length * ctx.mm(5.2);
  rect(out, L.PLANO_MARCO.name, x, topY - height, width, height);
  text(out, L.DW_TEXTO.name, x + ctx.mm(3), topY - ctx.mm(4), ctx.mm(3.0), 'LEYENDA DE CAPAS', 'left');
  LEGEND_ITEMS.forEach((item, i) => {
    const yy = topY - ctx.mm(11) - i * ctx.mm(5.2);
    line(out, item.layer, x + ctx.mm(3), yy, x + ctx.mm(15), yy);
    text(out, L.DW_TEXTO.name, x + ctx.mm(18), yy - ctx.mm(0.8), ctx.mm(2.3), item.label, 'left');
  });
  return out;
}

/** Parte un texto en líneas de longitud máxima aproximada. */
function wrapText(value: string, maxChars: number, maxLines = 40): string[] {
  const words = String(value || '').split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let current = '';
  words.forEach((word) => {
    if ((current + ' ' + word).trim().length > maxChars) {
      if (current) lines.push(current);
      current = word;
    } else {
      current = `${current} ${word}`.trim();
    }
  });
  if (current) lines.push(current);
  return lines.slice(0, maxLines);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Lámina de portada: índice, leyenda y notas generales
// ─────────────────────────────────────────────────────────────────────────────

interface IndexEntry {
  code: string;
  title: string;
}

function buildIndexSheet(
  ctx: DrawCtx,
  frame: FrameGeometry,
  config: ProjectConfig,
  summary: CalculationSummary,
  entries: IndexEntry[]
): CadEntity[] {
  const out: CadEntity[] = [...frame.frameEntities];
  const x = frame.area.x;
  const w = frame.area.w;
  let y = frame.area.y + frame.area.h;

  text(out, L.DW_TEXTO.name, x, y - ctx.mm(6), ctx.mm(9), 'MEMORIA DE SISTEMAS CONSTRUCTIVOS EN SECO', 'left');
  y -= ctx.mm(20);
  text(out, L.DW_TEXTO.name, x, y, ctx.mm(6), fitText(ctx, config.projectName.toUpperCase(), w, 6), 'left');
  y -= ctx.mm(10);
  text(out, L.DW_TEXTO.name, x, y, ctx.mm(3.0), `CLIENTE: ${config.clientName}`, 'left');
  y -= ctx.mm(6);
  text(out, L.DW_TEXTO.name, x, y, ctx.mm(3.0), `DIRECCION: ${config.projectAddress}`, 'left');
  y -= ctx.mm(6);
  text(
    out,
    L.DW_TEXTO.name,
    x,
    y,
    ctx.mm(3.0),
    `CONTRATISTA: ${config.contractorCompany || config.contractorName} - ${config.contractorPhone}`,
    'left'
  );
  y -= ctx.mm(6);
  text(
    out,
    L.DW_TEXTO.name,
    x,
    y,
    ctx.mm(3.0),
    `SUPERFICIE NETA: ${summary.totalNetArea} ${config.unitSystem === 'metric' ? 'm2' : 'sq ft'} - ESCALA 1:${ctx.opts.scaleDenominator}`,
    'left'
  );
  y -= ctx.mm(14);

  // Índice de láminas
  text(out, L.DW_TEXTO.name, x, y, ctx.mm(4.0), 'INDICE DE LAMINAS', 'left');
  y -= ctx.mm(7);
  const indexLimit = Math.max(4, Math.floor((y - frame.area.y - ctx.mm(60)) / ctx.mm(5.4)));
  entries.slice(0, indexLimit).forEach((entry) => {
    text(out, L.DW_TEXTO.name, x + ctx.mm(2), y, ctx.mm(2.6), entry.code, 'left');
    text(out, L.DW_TEXTO.name, x + ctx.mm(22), y, ctx.mm(2.6), fitText(ctx, entry.title, w - ctx.mm(26), 2.6), 'left');
    y -= ctx.mm(5.4);
  });

  // Notas generales
  y = Math.max(frame.area.y + ctx.mm(4), y - ctx.mm(8));
  const notesX = x;
  const notesW = Math.max(ctx.mm(60), w * 0.62);
  text(out, L.DW_TEXTO.name, notesX, y, ctx.mm(4.0), 'NOTAS GENERALES', 'left');
  let ny = y - ctx.mm(7);
  const notes = [
    ...wrapText(config.notes || 'Sin notas adicionales.', 95, 8),
    '',
    'RECOMENDACIONES TECNICAS:',
    ...summary.advisoryTips.slice(0, 10).flatMap((tip) => wrapText(`- ${tip}`, 95, 3))
  ];
  notes
    .slice(0, Math.max(2, Math.floor((ny - frame.area.y) / ctx.mm(4.6)) + 1))
    .forEach((lineText) => {
      text(out, L.DW_TEXTO.name, notesX, ny, ctx.mm(2.3), lineText, 'left');
      ny -= ctx.mm(4.6);
    });

  // Leyenda
  if (ctx.opts.includeLegend) {
    const legendW = ctx.mm(72);
    out.push(...buildLegend(ctx, frame.area.x + frame.area.w - legendW, frame.area.y + frame.area.h, legendW));
  }
  return out;
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cuadro de cómputo de materiales
// ─────────────────────────────────────────────────────────────────────────────

interface BomColumn {
  title: string;
  widthMm: number;
  align: 'left' | 'right';
  value: (r: CalculationSummary['requirements'][number]) => string;
}

const BOM_COLUMNS: BomColumn[] = [
  { title: 'MATERIAL', widthMm: 92, align: 'left', value: (r) => r.name },
  { title: 'UNIDAD', widthMm: 24, align: 'left', value: (r) => r.unit },
  { title: 'FORMATO COMERCIAL', widthMm: 52, align: 'left', value: (r) => r.commercialFormat },
  { title: 'CANT.', widthMm: 20, align: 'right', value: (r) => `${r.commercialUnits}` },
  { title: 'P. UNIT.', widthMm: 26, align: 'right', value: (r) => r.unitPrice.toFixed(2) },
  { title: 'TOTAL', widthMm: 30, align: 'right', value: (r) => r.totalPrice.toFixed(2) }
];

function buildBomSheets(ctx: DrawCtx, summary: CalculationSummary, currency: string): CadEntity[][] {
  if (!summary.requirements.length) return [];
  const frame = buildFrame(ctx, ctx.opts.paper, ctx.opts.includeTitleBlock);
  const rowH = ctx.mm(6.5);
  const headerH = ctx.mm(12);
  const usableH = frame.area.h - headerH - ctx.mm(55);
  const rowsPerSheet = Math.max(4, Math.floor(usableH / rowH));
  const totalWidth = BOM_COLUMNS.reduce((sum, c) => sum + ctx.mm(c.widthMm), 0);
  const startX = frame.area.x + Math.max(0, (frame.area.w - totalWidth) / 2);

  const pages: CadEntity[][] = [];
  for (let i = 0; i < summary.requirements.length; i += rowsPerSheet) {
    const rows = summary.requirements.slice(i, i + rowsPerSheet);
    const out: CadEntity[] = [...frame.frameEntities];
    let y = frame.area.y + frame.area.h;
    text(out, L.DW_TEXTO.name, frame.area.x, y - ctx.mm(2), ctx.mm(4.6), 'COMPUTO DE MATERIALES', 'left');
    text(out, L.DW_TEXTO.name, frame.area.x + frame.area.w, y - ctx.mm(2), ctx.mm(2.6), `MONEDA: ${currency}`, 'right');
    y -= headerH;

    let cx = startX;
    BOM_COLUMNS.forEach((col) => {
      const cw = ctx.mm(col.widthMm);
      rect(out, L.PLANO_TABLA.name, cx, y, cw, rowH);
      text(
        out,
        L.DW_TEXTO.name,
        col.align === 'left' ? cx + ctx.mm(1.5) : cx + cw - ctx.mm(1.5),
        y + ctx.mm(1.2),
        ctx.mm(2.4),
        col.title,
        col.align === 'left' ? 'left' : 'right'
      );
      cx += cw;
    });
    y -= rowH;

    rows.forEach((r) => {
      let cxx = startX;
      BOM_COLUMNS.forEach((col) => {
        const cw = ctx.mm(col.widthMm);
        rect(out, L.PLANO_TABLA.name, cxx, y, cw, rowH);
        const raw = col.value(r);
        const maxChars = Math.floor((col.widthMm - 3) / 1.55);
        const value = raw.length > maxChars ? `${raw.slice(0, Math.max(4, maxChars - 1))}.` : raw;
        text(
          out,
          L.DW_TEXTO.name,
          col.align === 'left' ? cxx + ctx.mm(1.5) : cxx + cw - ctx.mm(1.5),
          y + ctx.mm(1.2),
          ctx.mm(2.3),
          value,
          col.align === 'left' ? 'left' : 'right'
        );
        cxx += cw;
      });
      y -= rowH;
    });

    // Resumen económico
    y -= ctx.mm(10);
    const totalRows: Array<[string, string]> = [
      ['COSTE DE MATERIALES', summary.materialsCost.toFixed(2)],
      ['MANO DE OBRA', summary.laborCost.toFixed(2)],
      ['SUBTOTAL', summary.subtotal.toFixed(2)],
      ['BENEFICIO / MARGEN', summary.profitAmount.toFixed(2)],
      ['IMPUESTOS', summary.taxAmount.toFixed(2)],
      ['TOTAL PRESUPUESTO', summary.grandTotal.toFixed(2)]
    ];
    totalRows.forEach(([label, value], i) => {
      const yy = y - i * ctx.mm(6);
      text(out, L.DW_TEXTO.name, startX + totalWidth - ctx.mm(70), yy, ctx.mm(2.6), label, 'left');
      text(out, L.DW_TEXTO.name, startX + totalWidth, yy, ctx.mm(2.8), value, 'right');
    });
    pages.push(out);
  }
  return pages;
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ensamblado de la escena
// ─────────────────────────────────────────────────────────────────────────────

function normalizeBounds(b: CadBounds): CadBounds {
  if (!Number.isFinite(b.minX) || !Number.isFinite(b.minY)) return { minX: 0, minY: 0, maxX: 1, maxY: 1 };
  return b;
}

function scaleEntities(entities: CadEntity[], factor: number): CadEntity[] {
  if (factor >= 0.999) return entities;
  return entities.map((e) => {
    switch (e.kind) {
      case 'line':
        return { ...e, a: { x: e.a.x * factor, y: e.a.y * factor }, b: { x: e.b.x * factor, y: e.b.y * factor } };
      case 'polyline':
        return { ...e, points: e.points.map((p) => ({ x: p.x * factor, y: p.y * factor })) };
      case 'circle':
      case 'arc':
        return { ...e, center: { x: e.center.x * factor, y: e.center.y * factor }, radius: e.radius * factor };
      case 'text':
        return { ...e, position: { x: e.position.x * factor, y: e.position.y * factor }, height: e.height * factor };
      case 'solid':
        return {
          ...e,
          corners: e.corners.map((p) => ({ x: p.x * factor, y: p.y * factor })) as [CadPoint, CadPoint, CadPoint, CadPoint]
        };
      case 'insert':
        return { ...e, position: { x: e.position.x * factor, y: e.position.y * factor }, scale: (e.scale ?? 1) * factor };
      default:
        return e;
    }
  });
}

/** Coloca un bloque de entidades con su esquina inferior izquierda en (ox, oy). */
function placeBlock(entities: CadEntity[], bbox: CadBounds, ox: number, oy: number, factor = 1): CadEntity[] {
  const moved = entities.map((e) => translateEntity(e, -bbox.minX, -bbox.minY));
  return scaleEntities(moved, factor).map((e) => translateEntity(e, ox, oy));
}

function blockSize(bbox: CadBounds): { w: number; h: number } {
  return { w: bbox.maxX - bbox.minX, h: bbox.maxY - bbox.minY };
}

/** Construye la escena completa del proyecto. */
export function buildCadScene(
  rooms: Room[],
  config: ProjectConfig,
  summary: CalculationSummary,
  options: PlanOptions
): CadScene {
  const scale = options.scaleDenominator;
  const mm = (v: number) => (v / 1000) * scale;
  const ctx: DrawCtx = {
    opts: options,
    mm,
    scale,
    unitSystem: config.unitSystem,
    dim: makeDimStyle(mm(2.8)),
    dimSmall: makeDimStyle(mm(2.0)),
    studSpacing: Math.max(0.1, config.studSpacing),
    sheetWidth: Math.max(0.3, config.sheetWidth),
    sheetLength: Math.max(0.3, config.sheetLength)
  };

  const drawings = rooms.filter((r) => r.segments.length > 0).map((room) => buildRoomDrawing(room, ctx));

  const metaBase = {
    projectName: config.projectName,
    clientName: config.clientName,
    contractorCompany: config.contractorCompany || config.contractorName,
    drawnBy: config.contractorName,
    date: config.quoteDate,
    revision: 'A',
    unitSystem: config.unitSystem,
    totalNetArea: summary.totalNetArea,
    areaUnit: config.unitSystem === 'metric' ? 'm2' : 'sq ft',
    notes: config.notes
  };

  if (!drawings.length) {
    return {
      name: config.projectName || 'Proyecto Drywall',
      unitsOut: options.units,
      paper: options.paper,
      scaleDenominator: scale,
      blocks: [],
      sheets: [],
      bounds: { minX: 0, minY: 0, maxX: 0, maxY: 0 },
      meta: metaBase
    };
  }

  const frame = buildFrame(ctx, options.paper, options.includeTitleBlock);
  const gap = ctx.mm(22);
  const sheets: CadSheet[] = [];
  const roomSheetNames: IndexEntry[] = [];

  if (options.layout === 'per-room') {
    drawings.forEach((d) => {
      const { w, h } = blockSize(d.bbox);
      const fit = Math.min(1, frame.area.w / Math.max(w, 1e-6), frame.area.h / Math.max(h, 1e-6));
      const ox = frame.area.x + Math.max(0, (frame.area.w - w * fit) / 2);
      const oy = frame.area.y + Math.max(0, (frame.area.h - h * fit) / 2);
      sheets.push({
        name: `LAMINA-${String(sheets.length + 1).padStart(2, '0')}`,
        title: d.name,
        paper: PAPER_SIZES[options.paper],
        scaleDenominator: scale,
        entities: placeBlock(d.entities, d.bbox, ox, oy, fit),
        frame: frame.frameEntities
      });
      roomSheetNames.push({ code: `LAMINA-${String(sheets.length).padStart(2, '0')}`, title: d.name });
    });
  } else {
    let current: CadEntity[] = [];
    let cursorX = frame.area.x;
    let cursorY = frame.area.y + frame.area.h;
    let rowHeight = 0;
    const flush = (): void => {
      if (!current.length) return;
      sheets.push({
        name: `LAMINA-${String(sheets.length + 1).padStart(2, '0')}`,
        title: 'PLANTA GENERAL DE SISTEMAS DRYWALL',
        paper: PAPER_SIZES[options.paper],
        scaleDenominator: scale,
        entities: current,
        frame: frame.frameEntities
      });
      roomSheetNames.push({ code: `LAMINA-${String(sheets.length).padStart(2, '0')}`, title: 'Planta general de sistemas drywall' });
      current = [];
    };

    drawings.forEach((d) => {
      const { w, h } = blockSize(d.bbox);
      const fitsArea = w <= frame.area.w && h <= frame.area.h;
      if (!fitsArea) {
        flush();
        const fit = Math.min(frame.area.w / Math.max(w, 1e-6), frame.area.h / Math.max(h, 1e-6));
        const ox = frame.area.x + Math.max(0, (frame.area.w - w * fit) / 2);
        const oy = frame.area.y + Math.max(0, (frame.area.h - h * fit) / 2);
        sheets.push({
          name: `LAMINA-${String(sheets.length + 1).padStart(2, '0')}`,
          title: d.name,
          paper: PAPER_SIZES[options.paper],
          scaleDenominator: scale,
          entities: placeBlock(d.entities, d.bbox, ox, oy, fit),
          frame: frame.frameEntities
        });
        roomSheetNames.push({ code: `LAMINA-${String(sheets.length).padStart(2, '0')}`, title: d.name });
        cursorX = frame.area.x;
        cursorY = frame.area.y + frame.area.h;
        rowHeight = 0;
        return;
      }
      if (cursorX + w > frame.area.x + frame.area.w + 1e-9) {
        cursorX = frame.area.x;
        cursorY -= rowHeight + gap;
        rowHeight = 0;
      }
      if (cursorY - h < frame.area.y) {
        flush();
        cursorX = frame.area.x;
        cursorY = frame.area.y + frame.area.h;
        rowHeight = 0;
      }
      current.push(...placeBlock(d.entities, d.bbox, cursorX, cursorY - h));
      cursorX += w + gap;
      rowHeight = Math.max(rowHeight, h);
    });
    flush();
  }

  // Cómputo de materiales
  if (options.includeBom) {
    buildBomSheets(ctx, summary, config.currency).forEach((entities, i) => {
      sheets.push({
        name: `COMPUTO-${String(i + 1).padStart(2, '0')}`,
        title: `Computo de materiales (${i + 1})`,
        paper: PAPER_SIZES[options.paper],
        scaleDenominator: scale,
        entities,
        frame: frame.frameEntities
      });
    });
  }

  // Portada (índice, leyenda y notas) al inicio
  const indexEntries: IndexEntry[] = [
    ...roomSheetNames,
    ...sheets
      .filter((s) => s.name.startsWith('COMPUTO'))
      .map((s) => ({ code: s.name, title: s.title }))
  ];
  const indexEntities = buildIndexSheet(ctx, frame, config, summary, indexEntries);
  sheets.unshift({
    name: 'INDICE-00',
    title: 'Indice, leyenda y notas generales',
    paper: PAPER_SIZES[options.paper],
    scaleDenominator: scale,
    entities: indexEntities,
    frame: frame.frameEntities
  });

  // Cajetín en todas las láminas + recálculo de encuadre
  const total = sheets.length;
  const bounds = emptyBounds();
  sheets.forEach((sheet, i) => {
    const meta: TitleBlockMeta = {
      company: config.contractorCompany || config.contractorName,
      project: config.projectName,
      client: config.clientName,
      scale: `1:${scale}`,
      units: options.units === 'in' ? 'PULGADAS' : options.units === 'ft' ? 'PIES' : options.units.toUpperCase(),
      sheetIndex: i + 1,
      sheetTotal: total,
      date: config.quoteDate,
      revision: 'A',
      area: `${summary.totalNetArea} ${config.unitSystem === 'metric' ? 'm2' : 'sq ft'}`,
      drawingName: sheet.title
    };
    if (options.includeTitleBlock) sheet.entities.push(...buildTitleBlock(ctx, meta));
    sheet.entities.forEach((e) => {
      const eb = entitiesBounds([e]);
      expandBounds(bounds, eb.minX, eb.minY);
      expandBounds(bounds, eb.maxX, eb.maxY);
    });
    expandBounds(bounds, frame.sheetW, frame.sheetH);
  });

  return {
    name: config.projectName || 'Proyecto Drywall',
    unitsOut: options.units,
    paper: options.paper,
    scaleDenominator: scale,
    blocks: [],
    sheets,
    bounds: normalizeBounds(bounds),
    meta: metaBase
  };
}
