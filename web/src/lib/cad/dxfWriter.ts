/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  Escritor DXF ASCII (sin dependencias externas)
 *  ─────────────────────────────────────────────────────────────────────────
 *  Genera archivos DXF compatibles con AutoCAD, BricsCAD, ZWCAD, GstarCAD,
 *  DraftSight, LibreCAD, QCAD, nanoCAD y con el conversor ODA File Converter
 *  (para obtener .dwg binario nativo).
 *
 *  Compatibilidad por versión:
 *   • R12   (AC1009) → máxima interoperabilidad. Sin handles, sin diccionarios.
 *   • R2000 (AC1015) → handles + BLOCK_RECORD + OBJECTS mínimo.
 *   • R2007 (AC1021) → mismo contenido que R2000 con cabecera AC1021.
 *
 *  Nota profesional: el formato .dwg es propietario de Autodesk. Este módulo
 *  exporta DXF (formato de intercambio abierto y 100 % editable en AutoCAD) y
 *  delega la conversión a .dwg en ODA File Converter cuando el servidor lo
 *  tiene configurado (ver server/src/routes/cad.js).
 * ═══════════════════════════════════════════════════════════════════════════
 */

import {
  CadEntity,
  CadPoint,
  CadUnits,
  DxfVersion,
  INSUNITS_CODE,
  TextAlign,
  UNIT_FACTORS
} from './types';
import { CAD_LAYERS, CAD_LINETYPES, CAD_TEXT_STYLES, CadLayerDef, safeLayerName } from './linetypes';

const ACADVER: Record<DxfVersion, string> = {
  R12: 'AC1009',
  R2000: 'AC1015',
  R2007: 'AC1021'
};

/** Caracteres que rompen el parser DXF o que no existen en la página de códigos. */
const TEXT_REPLACEMENTS: Array<[RegExp, string]> = [
  [/\u20ac/g, 'EUR'],
  [/\u00d7/g, 'x'],
  [/\u00b2/g, '2'],
  [/\u00b3/g, '3'],
  [/\u00b0/g, 'd'],
  [/\u2026/g, '...'],
  [/\u2265/g, '>='],
  [/\u2264/g, '<='],
  [/[\u2018\u2019\u201b]/g, "\u0027"],
  [/[\u201c\u201d]/g, "\u0022"],
  [/[\u2013\u2014]/g, '-'],
  [/[\u00a0\u2007\u2009\u200a]/g, ' ']
];

/** Limpia un texto para que sea seguro dentro de un DXF ASCII. */
export function sanitizeDxfText(value: string): string {
  let out = String(value ?? '');
  for (const [pattern, replacement] of TEXT_REPLACEMENTS) out = out.replace(pattern, replacement);
  out = out
    .replace(/[\r\n\t\v\f]+/g, ' ')
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .replace(/[^\u0020-\u007e\u00a0-\u00ff]/gu, '?')
    .replace(/\?{2,}/g, '?')
    .trim();
  return out;
}

/** Formatea un número para DXF: siempre con punto decimal y sin notación científica. */
export function fmtNumber(value: number, decimals = 6): string {
  if (!Number.isFinite(value)) return '0.0';
  // Evita notación científica incluso para valores muy pequeños/grandes.
  let s = value.toFixed(decimals);
  if (s.includes('.')) s = s.replace(/0+$/, '').replace(/\.$/, '');
  if (s === '' || s === '-') s = '0.0';
  if (!s.includes('.')) s += '.0';
  return s;
}

/** Asignador de handles hexadecimales (solo versiones > R12). */
class HandleAllocator {
  private next: number;
  constructor(seed = 0x200) {
    this.next = seed;
  }
  nextHandle(): string {
    const h = this.next.toString(16).toUpperCase();
    this.next += 1;
    return h;
  }
  get seedValue(): string {
    return (this.next + 0x1000).toString(16).toUpperCase();
  }
}

class DxfBuilder {
  private chunks: string[] = [];
  pair(code: number | string, value: string | number): void {
    this.chunks.push(`${code}\n${value}\n`);
  }
  raw(text: string): void {
    this.chunks.push(text);
  }
  toString(): string {
    return this.chunks.join('');
  }
  get length(): number {
    return this.chunks.length;
  }
}

export interface DxfOptions {
  version?: DxfVersion;
  units?: CadUnits;
  /** Nombre del dibujo (cabecera). */
  title?: string;
  /** Escala global de acotación ($DIMSCALE). */
  dimScale?: number;
  /** Envolvente en unidades de dibujo (para $EXTMIN / $EXTMAX / $LIMMIN). */
  extents?: { minX: number; minY: number; maxX: number; maxY: number };
}

/** Documento DXF: acumula entidades y las serializa a ASCII DXF. */
export class DxfDocument {
  readonly version: DxfVersion;
  readonly units: CadUnits;
  private readonly scale: number;
  private readonly layers = new Map<string, CadLayerDef>();
  private readonly entities: CadEntity[] = [];
  private readonly blocks = new Map<string, CadEntity[]>();
  private readonly handles = new HandleAllocator();
  private readonly blockRecordHandles = new Map<string, string>();
  private readonly options: DxfOptions;

  constructor(options: DxfOptions = {}) {
    this.options = options;
    this.version = options.version ?? 'R12';
    this.units = options.units ?? 'mm';
    this.scale = UNIT_FACTORS[this.units];
    // La capa 0 siempre existe.
    this.layers.set('0', { name: '0', color: 7, linetype: 'CONTINUOUS', description: 'Capa por defecto' });
  }

  /** Registra una capa (idempotente). */
  addLayer(name: string, color = 7, linetype = 'CONTINUOUS'): string {
    const layer = safeLayerName(name);
    if (!this.layers.has(layer)) {
      this.layers.set(layer, { name: layer, color, linetype, description: '' });
    }
    return layer;
  }

  /** Precarga todas las capas del catálogo normalizado. */
  useStandardLayers(): void {
    Object.values(CAD_LAYERS).forEach((l) => this.addLayer(l.name, l.color, l.linetype));
  }

  addLine(layer: string, a: CadPoint, b: CadPoint): void {
    this.entities.push({ kind: 'line', layer: safeLayerName(layer), a, b });
  }

  addPolyline(layer: string, points: CadPoint[], closed = false): void {
    if (points.length < 2) return;
    this.entities.push({ kind: 'polyline', layer: safeLayerName(layer), points, closed });
  }

  addRect(layer: string, x: number, y: number, w: number, h: number): void {
    this.addPolyline(
      layer,
      [
        { x, y },
        { x: x + w, y },
        { x: x + w, y: y + h },
        { x, y: y + h }
      ],
      true
    );
  }

  addCircle(layer: string, center: CadPoint, radius: number): void {
    this.entities.push({ kind: 'circle', layer: safeLayerName(layer), center, radius });
  }

  addArc(layer: string, center: CadPoint, radius: number, startAngle: number, endAngle: number): void {
    this.entities.push({ kind: 'arc', layer: safeLayerName(layer), center, radius, startAngle, endAngle });
  }

  addText(
    layer: string,
    position: CadPoint,
    height: number,
    value: string,
    opts: { rotation?: number; align?: TextAlign; style?: string } = {}
  ): void {
    const text = sanitizeDxfText(value);
    if (!text) return;
    this.entities.push({
      kind: 'text',
      layer: safeLayerName(layer),
      position,
      height,
      value: text,
      rotation: opts.rotation ?? 0,
      align: opts.align ?? 'left',
      style: opts.style ?? 'ROMANS'
    });
  }

  addSolid(layer: string, corners: [CadPoint, CadPoint, CadPoint, CadPoint]): void {
    this.entities.push({ kind: 'solid', layer: safeLayerName(layer), corners });
  }

  /** Define un bloque reutilizable (símbolo). Entidades en metros. */
  defineBlock(name: string, entities: CadEntity[]): void {
    this.blocks.set(name.toUpperCase().slice(0, 31), entities);
  }

  addInsert(layer: string, block: string, position: CadPoint, scale = 1, rotation = 0): void {
    this.entities.push({ kind: 'insert', layer: safeLayerName(layer), block: block.toUpperCase(), position, scale, rotation });
  }

  /** Añade una entidad ya construida (convierte unidades al serializar). */
  addEntity(entity: CadEntity): void {
    this.entities.push({ ...entity, layer: safeLayerName(entity.layer) } as CadEntity);
  }

  addEntities(entities: CadEntity[]): void {
    entities.forEach((e) => this.addEntity(e));
  }

  get entityCount(): number {
    return this.entities.length;
  }

  // ── Serialización ────────────────────────────────────────────────────────

  toDxf(): string {
    const b = new DxfBuilder();
    this.writeHeader(b);
    this.writeTables(b);
    this.writeBlocks(b);
    this.writeEntities(b);
    if (this.usesHandles()) this.writeObjects(b);
    b.pair(0, 'EOF');
    return b.toString();
  }

  private usesHandles(): boolean {
    return this.version !== 'R12';
  }

  private sx(v: number): string {
    return fmtNumber(v * this.scale);
  }

  private writeHeader(b: DxfBuilder): void {
    const ext = this.options.extents;
    b.pair(0, 'SECTION');
    b.pair(2, 'HEADER');
    b.pair(9, '$ACADVER');
    b.pair(1, ACADVER[this.version]);
    if (this.usesHandles()) {
      b.pair(9, '$HANDSEED');
      b.pair(5, this.handles.seedValue);
    }
    b.pair(9, '$DWGCODEPAGE');
    b.pair(3, 'ANSI_1252');
    b.pair(9, '$INSBASE');
    b.pair(10, '0.0');
    b.pair(20, '0.0');
    b.pair(30, '0.0');
    b.pair(9, '$INSUNITS');
    b.pair(70, INSUNITS_CODE[this.units]);
    b.pair(9, '$MEASUREMENT');
    b.pair(70, this.units === 'in' || this.units === 'ft' ? 0 : 1);
    b.pair(9, '$LUNITS');
    b.pair(70, 2);
    b.pair(9, '$LUPREC');
    b.pair(70, this.units === 'in' || this.units === 'ft' ? 4 : 3);
    b.pair(9, '$AUNITS');
    b.pair(70, 0);
    b.pair(9, '$AUPREC');
    b.pair(70, 2);
    b.pair(9, '$FILLETRAD');
    b.pair(40, '0.0');
    b.pair(9, '$TEXTSIZE');
    b.pair(40, fmtNumber(0.0025 * this.scale));
    b.pair(9, '$DIMSCALE');
    b.pair(40, fmtNumber(this.options.dimScale ?? 1));
    b.pair(9, '$DIMTXT');
    b.pair(40, fmtNumber(0.0025 * this.scale));
    b.pair(9, '$DIMASZ');
    b.pair(40, fmtNumber(0.0018 * this.scale));
    b.pair(9, '$DIMEXE');
    b.pair(40, fmtNumber(0.0012 * this.scale));
    b.pair(9, '$DIMEXO');
    b.pair(40, fmtNumber(0.0006 * this.scale));
    b.pair(9, '$DIMDLI');
    b.pair(40, fmtNumber(0.006 * this.scale));
    b.pair(9, '$DIMTIH');
    b.pair(70, 0);
    b.pair(9, '$DIMTOH');
    b.pair(70, 0);
    b.pair(9, '$DIMTAD');
    b.pair(70, 1);
    b.pair(9, '$LTSCALE');
    b.pair(40, fmtNumber(this.options.dimScale && this.options.dimScale > 1 ? this.options.dimScale * 0.5 : 1));
    b.pair(9, '$PDMODE');
    b.pair(70, 35);
    b.pair(9, '$PDSIZE');
    b.pair(40, '0.0');
    b.pair(9, '$CLAYER');
    b.pair(8, '0');

    const minX = ext ? this.sx(ext.minX) : '0.0';
    const minY = ext ? this.sx(ext.minY) : '0.0';
    const maxX = ext ? this.sx(ext.maxX) : fmtNumber(1 * this.scale);
    const maxY = ext ? this.sx(ext.maxY) : fmtNumber(1 * this.scale);
    b.pair(9, '$EXTMIN');
    b.pair(10, minX);
    b.pair(20, minY);
    b.pair(30, '0.0');
    b.pair(9, '$EXTMAX');
    b.pair(10, maxX);
    b.pair(20, maxY);
    b.pair(30, '0.0');
    b.pair(9, '$LIMMIN');
    b.pair(10, minX);
    b.pair(20, minY);
    b.pair(9, '$LIMMAX');
    b.pair(10, maxX);
    b.pair(20, maxY);
    b.pair(0, 'ENDSEC');
  }

  private writeTables(b: DxfBuilder): void {
    const withHandles = this.usesHandles();
    b.pair(0, 'SECTION');
    b.pair(2, 'TABLES');

    // ── VPORT ──────────────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'VPORT');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, 1);
    if (withHandles) {
      b.pair(0, 'VPORT');
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTableRecord');
      b.pair(100, 'AcDbViewportTableRecord');
      b.pair(2, '*ACTIVE');
      b.pair(70, 0);
      b.pair(10, '0.0');
      b.pair(20, '0.0');
      b.pair(11, '1.0');
      b.pair(21, '1.0');
      b.pair(12, fmtNumber(0.5 * this.scale));
      b.pair(22, fmtNumber(0.5 * this.scale));
      b.pair(13, '0.0');
      b.pair(23, '0.0');
      b.pair(14, fmtNumber(0.1 * this.scale));
      b.pair(24, fmtNumber(0.1 * this.scale));
      b.pair(15, '0.0');
      b.pair(25, '0.0');
      b.pair(16, '0.0');
      b.pair(26, '0.0');
      b.pair(36, '1.0');
      b.pair(17, '0.0');
      b.pair(27, '0.0');
      b.pair(37, '0.0');
      b.pair(40, fmtNumber(1 * this.scale));
      b.pair(41, '1.0');
      b.pair(42, '50.0');
      b.pair(43, '0.0');
      b.pair(44, '0.0');
      b.pair(50, '0.0');
      b.pair(51, '0.0');
      b.pair(71, 0);
      b.pair(72, 100);
      b.pair(73, 1);
      b.pair(74, 3);
      b.pair(75, 0);
      b.pair(76, 1);
      b.pair(77, 0);
      b.pair(78, 0);
    }
    b.pair(0, 'ENDTAB');

    // ── LTYPE ──────────────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'LTYPE');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, CAD_LINETYPES.length);
    for (const lt of CAD_LINETYPES) {
      b.pair(0, 'LTYPE');
      if (withHandles) {
        b.pair(5, this.handles.nextHandle());
        b.pair(330, '0');
        b.pair(100, 'AcDbSymbolTableRecord');
        b.pair(100, 'AcDbLinetypeTableRecord');
      }
      b.pair(2, lt.name);
      b.pair(70, 0);
      b.pair(3, lt.description);
      b.pair(72, 65);
      b.pair(73, lt.pattern.length);
      b.pair(40, fmtNumber(lt.totalLength, 6));
      lt.pattern.forEach((p) => b.pair(49, fmtNumber(p, 6)));
    }
    b.pair(0, 'ENDTAB');

    // ── LAYER ──────────────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'LAYER');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, this.layers.size);
    for (const layer of this.layers.values()) {
      b.pair(0, 'LAYER');
      if (withHandles) {
        b.pair(5, this.handles.nextHandle());
        b.pair(330, '0');
        b.pair(100, 'AcDbSymbolTableRecord');
        b.pair(100, 'AcDbLayerTableRecord');
      }
      b.pair(2, layer.name);
      b.pair(70, 0);
      b.pair(62, layer.color);
      b.pair(6, layer.linetype);
      if (withHandles) b.pair(370, -3); // Lineweight por defecto
    }
    b.pair(0, 'ENDTAB');

    // ── STYLE ──────────────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'STYLE');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, CAD_TEXT_STYLES.length);
    for (const st of CAD_TEXT_STYLES) {
      b.pair(0, 'STYLE');
      if (withHandles) {
        b.pair(5, this.handles.nextHandle());
        b.pair(330, '0');
        b.pair(100, 'AcDbSymbolTableRecord');
        b.pair(100, 'AcDbTextStyleTableRecord');
      }
      b.pair(2, st.name);
      b.pair(70, 0);
      b.pair(40, '0.0');
      b.pair(41, fmtNumber(st.widthFactor));
      b.pair(50, fmtNumber(st.oblique));
      b.pair(71, 0);
      b.pair(42, fmtNumber(0.0025 * this.scale));
      b.pair(3, st.font);
      b.pair(4, '');
    }
    b.pair(0, 'ENDTAB');

    // ── VIEW (vacía) ───────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'VIEW');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, 0);
    b.pair(0, 'ENDTAB');

    // ── UCS (vacía) ────────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'UCS');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, 0);
    b.pair(0, 'ENDTAB');

    // ── DIMSTYLE ───────────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'DIMSTYLE');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, 1);
    b.pair(0, 'DIMSTYLE');
    if (withHandles) {
      b.pair(105, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTableRecord');
      b.pair(100, 'AcDbDimStyleTableRecord');
    }
    b.pair(2, 'DRYWALL-ISO');
    b.pair(70, 0);
    b.pair(3, '');
    b.pair(40, fmtNumber(this.options.dimScale ?? 1));
    b.pair(41, fmtNumber(0.0018 * this.scale));
    b.pair(42, fmtNumber(0.0012 * this.scale));
    b.pair(43, fmtNumber(0.0006 * this.scale));
    b.pair(44, fmtNumber(0.006 * this.scale));
    b.pair(140, fmtNumber(0.0025 * this.scale));
    b.pair(147, fmtNumber(0.001 * this.scale));
    b.pair(77, 0);
    b.pair(78, 0);
    b.pair(0, 'ENDTAB');

    // ── BLOCK_RECORD (solo >R12) ───────────────────────────────────────────
    if (withHandles) {
      b.pair(0, 'TABLE');
      b.pair(2, 'BLOCK_RECORD');
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
      b.pair(70, this.blocks.size + 2);

      const modelSpace = this.handles.nextHandle();
      this.blockRecordHandles.set('*MODEL_SPACE', modelSpace);
      b.pair(0, 'BLOCK_RECORD');
      b.pair(5, modelSpace);
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTableRecord');
      b.pair(100, 'AcDbBlockTableRecord');
      b.pair(2, '*MODEL_SPACE');

      const paperSpace = this.handles.nextHandle();
      this.blockRecordHandles.set('*PAPER_SPACE', paperSpace);
      b.pair(0, 'BLOCK_RECORD');
      b.pair(5, paperSpace);
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTableRecord');
      b.pair(100, 'AcDbBlockTableRecord');
      b.pair(2, '*PAPER_SPACE');

      for (const name of this.blocks.keys()) {
        const h = this.handles.nextHandle();
        this.blockRecordHandles.set(name, h);
        b.pair(0, 'BLOCK_RECORD');
        b.pair(5, h);
        b.pair(330, '0');
        b.pair(100, 'AcDbSymbolTableRecord');
        b.pair(100, 'AcDbBlockTableRecord');
        b.pair(2, name);
      }
      b.pair(0, 'ENDTAB');
    }

    // ── APPID ──────────────────────────────────────────────────────────────
    b.pair(0, 'TABLE');
    b.pair(2, 'APPID');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTable');
    }
    b.pair(70, 2);
    b.pair(0, 'APPID');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTableRecord');
      b.pair(100, 'AcDbRegAppTableRecord');
    }
    b.pair(2, 'ACAD');
    b.pair(70, 0);
    b.pair(0, 'APPID');
    if (withHandles) {
      b.pair(5, this.handles.nextHandle());
      b.pair(330, '0');
      b.pair(100, 'AcDbSymbolTableRecord');
      b.pair(100, 'AcDbRegAppTableRecord');
    }
    b.pair(2, 'DRYWALLPRO');
    b.pair(70, 0);
    b.pair(0, 'ENDTAB');

    b.pair(0, 'ENDSEC');
  }

  private writeBlocks(b: DxfBuilder): void {
    if (this.blocks.size === 0) return;
    const withHandles = this.usesHandles();
    b.pair(0, 'SECTION');
    b.pair(2, 'BLOCKS');
    for (const [name, entities] of this.blocks) {
      const recordHandle = this.blockRecordHandles.get(name) ?? '0';
      const beginHandle = withHandles ? this.handles.nextHandle() : null;
      b.pair(0, 'BLOCK');
      if (withHandles) {
        b.pair(5, beginHandle as string);
        b.pair(330, recordHandle);
        b.pair(100, 'AcDbEntity');
      }
      b.pair(8, '0');
      if (withHandles) b.pair(100, 'AcDbBlockBegin');
      b.pair(2, name);
      b.pair(70, 0);
      b.pair(10, '0.0');
      b.pair(20, '0.0');
      b.pair(30, '0.0');
      b.pair(3, name);
      b.pair(1, '');
      this.writeEntityList(b, entities);
      b.pair(0, 'ENDBLK');
      if (withHandles) {
        b.pair(5, this.handles.nextHandle());
        b.pair(330, recordHandle);
        b.pair(100, 'AcDbEntity');
      }
      b.pair(8, '0');
      if (withHandles) b.pair(100, 'AcDbBlockEnd');
    }
    b.pair(0, 'ENDSEC');
  }

  private writeEntities(b: DxfBuilder): void {
    b.pair(0, 'SECTION');
    b.pair(2, 'ENTITIES');
    this.writeEntityList(b, this.entities);
    b.pair(0, 'ENDSEC');
  }

  /** Escribe una entidad en el builder (coordenadas en metros → unidad de dibujo). */
  private writeEntity(b: DxfBuilder, entity: CadEntity): void {
    const withHandles = this.usesHandles();
    const owner = this.blockRecordHandles.get('*MODEL_SPACE') ?? '0';
    const begin = () => {
      if (withHandles) {
        b.pair(5, this.handles.nextHandle());
        b.pair(330, owner);
        b.pair(100, 'AcDbEntity');
      }
    };

    switch (entity.kind) {
      case 'line': {
        b.pair(0, 'LINE');
        begin();
        b.pair(8, entity.layer);
        b.pair(10, this.sx(entity.a.x));
        b.pair(20, this.sx(entity.a.y));
        b.pair(30, '0.0');
        b.pair(11, this.sx(entity.b.x));
        b.pair(21, this.sx(entity.b.y));
        b.pair(31, '0.0');
        break;
      }
      case 'polyline': {
        const pts = entity.points;
        if (pts.length < 2) return;
        if (this.version === 'R12') {
          // R12 no soporta LWPOLYLINE: se emite POLYLINE/VERTEX/SEQEND.
          b.pair(0, 'POLYLINE');
          b.pair(8, entity.layer);
          b.pair(66, 1);
          b.pair(10, '0.0');
          b.pair(20, '0.0');
          b.pair(30, '0.0');
          b.pair(70, entity.closed ? 1 : 0);
          b.pair(40, '0.0');
          b.pair(41, '0.0');
          for (const p of pts) {
            b.pair(0, 'VERTEX');
            b.pair(8, entity.layer);
            b.pair(10, this.sx(p.x));
            b.pair(20, this.sx(p.y));
            b.pair(30, '0.0');
            b.pair(42, '0.0');
          }
          b.pair(0, 'SEQEND');
          b.pair(8, entity.layer);
        } else {
          b.pair(0, 'LWPOLYLINE');
          begin();
          b.pair(100, 'AcDbPolyline');
          b.pair(8, entity.layer);
          b.pair(90, pts.length);
          b.pair(70, entity.closed ? 1 : 0);
          b.pair(43, '0.0');
          for (const p of pts) {
            b.pair(10, this.sx(p.x));
            b.pair(20, this.sx(p.y));
          }
        }
        break;
      }
      case 'circle': {
        b.pair(0, 'CIRCLE');
        begin();
        b.pair(100, 'AcDbCircle');
        b.pair(8, entity.layer);
        b.pair(10, this.sx(entity.center.x));
        b.pair(20, this.sx(entity.center.y));
        b.pair(30, '0.0');
        b.pair(40, this.sx(entity.radius));
        break;
      }
      case 'arc': {
        b.pair(0, 'ARC');
        begin();
        b.pair(100, 'AcDbCircle');
        b.pair(8, entity.layer);
        b.pair(10, this.sx(entity.center.x));
        b.pair(20, this.sx(entity.center.y));
        b.pair(30, '0.0');
        b.pair(40, this.sx(entity.radius));
        b.pair(100, 'AcDbArc');
        b.pair(50, fmtNumber(entity.startAngle));
        b.pair(51, fmtNumber(entity.endAngle));
        break;
      }
      case 'text': {
        let code72 = 0;
        let code73 = 0;
        switch (entity.align) {
          case 'center':
            code72 = 1;
            break;
          case 'right':
            code72 = 2;
            break;
          case 'middle':
            code72 = 4;
            code73 = 2;
            break;
          default:
            code72 = 0;
        }
        b.pair(0, 'TEXT');
        begin();
        b.pair(100, 'AcDbText');
        b.pair(8, entity.layer);
        b.pair(10, this.sx(entity.position.x));
        b.pair(20, this.sx(entity.position.y));
        b.pair(30, '0.0');
        b.pair(40, this.sx(entity.height));
        b.pair(1, entity.value);
        b.pair(50, fmtNumber(entity.rotation ?? 0));
        if (entity.style) b.pair(7, entity.style);
        b.pair(72, code72);
        b.pair(73, code73);
        if (code72 !== 0 || code73 !== 0) {
          b.pair(11, this.sx(entity.position.x));
          b.pair(21, this.sx(entity.position.y));
          b.pair(31, '0.0');
        }
        b.pair(100, 'AcDbText');
        break;
      }
      case 'solid': {
        const [c1, c2, c3, c4] = entity.corners;
        b.pair(0, 'SOLID');
        begin();
        b.pair(100, 'AcDbTrace');
        b.pair(8, entity.layer);
        b.pair(10, this.sx(c1.x));
        b.pair(20, this.sx(c1.y));
        b.pair(30, '0.0');
        b.pair(11, this.sx(c2.x));
        b.pair(21, this.sx(c2.y));
        b.pair(31, '0.0');
        b.pair(12, this.sx(c4.x));
        b.pair(22, this.sx(c4.y));
        b.pair(32, '0.0');
        b.pair(13, this.sx(c3.x));
        b.pair(23, this.sx(c3.y));
        b.pair(33, '0.0');
        break;
      }
      case 'insert': {
        b.pair(0, 'INSERT');
        begin();
        b.pair(100, 'AcDbBlockReference');
        b.pair(8, entity.layer);
        b.pair(2, entity.block);
        b.pair(10, this.sx(entity.position.x));
        b.pair(20, this.sx(entity.position.y));
        b.pair(30, '0.0');
        const s = entity.scale ?? 1;
        b.pair(41, fmtNumber(s));
        b.pair(42, fmtNumber(s));
        b.pair(43, fmtNumber(s));
        b.pair(50, fmtNumber(entity.rotation ?? 0));
        break;
      }
    }
  }

  private writeEntityList(b: DxfBuilder, entities: CadEntity[]): void {
    for (const e of entities) this.writeEntity(b, e);
  }

  /** Sección OBJECTS mínima (diccionario raíz) requerida por versiones > R12. */
  private writeObjects(b: DxfBuilder): void {
    b.pair(0, 'SECTION');
    b.pair(2, 'OBJECTS');
    const root = this.handles.nextHandle();
    const groupDict = this.handles.nextHandle();
    b.pair(0, 'DICTIONARY');
    b.pair(5, root);
    b.pair(330, '0');
    b.pair(100, 'AcDbDictionary');
    b.pair(281, 1);
    b.pair(3, 'ACAD_GROUP');
    b.pair(350, groupDict);
    b.pair(0, 'DICTIONARY');
    b.pair(5, groupDict);
    b.pair(330, root);
    b.pair(100, 'AcDbDictionary');
    b.pair(281, 1);
    b.pair(0, 'ENDSEC');
  }
}

/** Crea y serializa un DXF a partir de entidades de escena (helper directo). */
export function entitiesToDxf(
  entities: CadEntity[],
  options: DxfOptions & { blocks?: Array<{ name: string; entities: CadEntity[] }> } = {}
): string {
  const doc = new DxfDocument(options);
  doc.useStandardLayers();
  (options.blocks ?? []).forEach((bl) => doc.defineBlock(bl.name, bl.entities));
  doc.addEntities(entities);
  return doc.toDxf();
}
