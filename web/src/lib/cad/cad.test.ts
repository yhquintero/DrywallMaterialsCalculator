import { describe, it, expect } from 'vitest';
import { DxfDocument, entitiesToDxf, fmtNumber, sanitizeDxfText } from './dxfWriter';
import { layoutBoards } from './dims';
import { buildCadScene, DEFAULT_PLAN_OPTIONS, PlanOptions } from './planBuilder';
import { sceneToDxf, slugify } from './exporter';
import { sheetToSvg } from './svgRenderer';
import { calculateProjectMaterials } from '../calculator';
import { DEFAULT_CONFIG, DEFAULT_ROOMS } from '../storage';
import { ProjectConfig, Room } from '../../types';

/** Analiza un DXF en pares código/valor para validar su estructura. */
function parseDxf(dxf: string): Array<{ code: string; value: string }> {
  const lines = dxf.split(/\r?\n/);
  const pairs: Array<{ code: string; value: string }> = [];
  for (let i = 0; i < lines.length - 1; i += 2) {
    const code = lines[i].trim();
    const value = lines[i + 1];
    if (code === '') continue;
    pairs.push({ code, value });
  }
  return pairs;
}

function countEntitiesInSection(dxf: string): number {
  const start = dxf.indexOf('2\nENTITIES\n');
  const end = dxf.indexOf('ENDSEC', start);
  const body = dxf.slice(start + '2\nENTITIES\n'.length, end);
  // Cada entidad empieza por un grupo 0 con un nombre de entidad.
  const lines = body.split(/\r?\n/);
  let count = 0;
  for (let i = 0; i < lines.length - 1; i += 2) {
    if (lines[i].trim() !== '0') continue;
    const name = lines[i + 1].trim();
    if (/^[A-Z_*][A-Z0-9_*-]*$/.test(name) && name !== 'ENDSEC') count += 1;
  }
  return count;
}

describe('DXF writer', () => {
  it('emite las secciones obligatorias y termina en EOF', () => {
    const doc = new DxfDocument({ version: 'R12', units: 'mm' });
    doc.useStandardLayers();
    doc.addLine('ARQ-MURO', { x: 0, y: 0 }, { x: 1, y: 1 });
    const dxf = doc.toDxf();
    expect(dxf.trimEnd().endsWith('EOF')).toBe(true);
    expect(dxf).toContain('SECTION');
    expect(dxf).toContain('HEADER');
    expect(dxf).toContain('TABLES');
    expect(dxf).toContain('ENTITIES');
    expect(dxf).toContain('AC1009');
  });

  it('usa POLYLINE/VERTEX en R12 y LWPOLYLINE en R2000', () => {
    const r12 = new DxfDocument({ version: 'R12' });
    r12.addPolyline('DW-PLACA', [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }], true);
    const r12Out = r12.toDxf();
    expect(r12Out).toContain('POLYLINE');
    expect(r12Out).toContain('VERTEX');
    expect(r12Out).toContain('SEQEND');
    expect(r12Out).not.toContain('LWPOLYLINE');

    const r2000 = new DxfDocument({ version: 'R2000' });
    r2000.addPolyline('DW-PLACA', [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }], true);
    const r2000Out = r2000.toDxf();
    expect(r2000Out).toContain('LWPOLYLINE');
    expect(r2000Out).toContain('AC1015');
    expect(r2000Out).toContain('BLOCK_RECORD');
    expect(r2000Out).toContain('OBJECTS');
  });

  it('escala las coordenadas a la unidad de dibujo indicada', () => {
    const mm = new DxfDocument({ units: 'mm' });
    mm.addLine('0', { x: 0, y: 0 }, { x: 1, y: 0 });
    expect(mm.toDxf()).toContain('1000.0'); // 1 m = 1000 mm

    const m = new DxfDocument({ units: 'm' });
    m.addLine('0', { x: 0, y: 0 }, { x: 1, y: 0 });
    expect(m.toDxf()).toContain('1.0');
  });

  it('define las capas del catálogo y los tipos de línea', () => {
    const doc = new DxfDocument();
    doc.useStandardLayers();
    const dxf = doc.toDxf();
    expect(dxf).toContain('DW-PERFIL');
    expect(dxf).toContain('DW-COTA');
    expect(dxf).toContain('CENTER');
    expect(dxf).toContain('DASHED');
  });

  it('sanitiza textos incompatibles con DXF ASCII', () => {
    expect(sanitizeDxfText('Muro 12,5 mm — Placa × 2')).toBe('Muro 12,5 mm - Placa x 2');
    expect(sanitizeDxfText('Línea\ncon salto\ty tab')).toBe('Línea con salto y tab');
    expect(sanitizeDxfText('Precio 45€/m²')).toBe('Precio 45EUR/m2');
    expect(sanitizeDxfText('emoji 🙂 fuera')).toBe('emoji ? fuera');
  });

  it('nunca escribe NaN ni notación científica', () => {
    const doc = new DxfDocument();
    doc.addLine('0', { x: 0, y: 0 }, { x: 1e-9, y: Number.NaN });
    const dxf = doc.toDxf();
    expect(dxf).not.toContain('NaN');
    expect(dxf).not.toMatch(/\de[-+]\d/);
    expect(fmtNumber(0.0000001)).toBe('0.0');
  });

  it('genera bloques reutilizables e inserciones', () => {
    const doc = new DxfDocument({ version: 'R2000' });
    doc.defineBlock('TORNILLO', [{ kind: 'circle', layer: 'DW-SIMBOLO', center: { x: 0, y: 0 }, radius: 0.005 }]);
    doc.addInsert('DW-SIMBOLO', 'TORNILLO', { x: 2, y: 3 }, 1, 0);
    const dxf = doc.toDxf();
    expect(dxf).toContain('BLOCKS');
    expect(dxf).toContain('TORNILLO');
    expect(dxf).toContain('INSERT');
    expect(countEntitiesInSection(dxf)).toBe(1);
  });
});

describe('Modulación de placas', () => {
  it('cubre toda la superficie sin solapamientos', () => {
    const pieces = layoutBoards(4.8, 2.4, 1.2, 2.4, true);
    const area = pieces.reduce((s, p) => s + p.w * p.h, 0);
    expect(area).toBeCloseTo(4.8 * 2.4, 6);
    pieces.forEach((p) => {
      expect(p.x).toBeGreaterThanOrEqual(0);
      expect(p.y).toBeGreaterThanOrEqual(0);
      expect(p.x + p.w).toBeLessThanOrEqual(4.8 + 1e-9);
      expect(p.y + p.h).toBeLessThanOrEqual(2.4 + 1e-9);
    });
  });

  it('aplica junta alternada entre filas', () => {
    const pieces = layoutBoards(3.6, 4.8, 1.2, 2.4, true);
    const row0 = pieces.filter((p) => Math.abs(p.y) < 1e-9).map((p) => p.x);
    const row1 = pieces.filter((p) => Math.abs(p.y - 2.4) < 1e-9).map((p) => p.x);
    expect(row0[0]).toBe(0);
    // La segunda fila arranca desplazada media placa: aparecen cortes a 0.6 m.
    expect(row1.some((x) => Math.abs(x % 1.2) > 0.01)).toBe(true);
  });

  it('no genera piezas degeneradas', () => {
    const pieces = layoutBoards(1.25, 2.5, 1.2, 2.4, true);
    pieces.forEach((p) => {
      expect(p.w).toBeGreaterThan(0.02);
      expect(p.h).toBeGreaterThan(0.02);
    });
  });
});

describe('Generación de planos', () => {
  const config: ProjectConfig = { ...DEFAULT_CONFIG };
  const summary = calculateProjectMaterials(DEFAULT_ROOMS, config);

  const buildScene = (overrides: Partial<PlanOptions> = {}) =>
    buildCadScene(DEFAULT_ROOMS, config, summary, { ...DEFAULT_PLAN_OPTIONS, ...overrides });

  it('crea al menos una lámina con geometría', () => {
    const scene = buildScene();
    expect(scene.sheets.length).toBeGreaterThan(0);
    const totalEntities = scene.sheets.reduce((s, sh) => s + sh.entities.length, 0);
    expect(totalEntities).toBeGreaterThan(20);
  });

  it('incluye lámina de cómputo de materiales cuando se pide', () => {
    const withBom = buildScene({ includeBom: true });
    const withoutBom = buildScene({ includeBom: false });
    expect(withBom.sheets.some((s) => s.name.startsWith('COMPUTO'))).toBe(true);
    expect(withoutBom.sheets.some((s) => s.name.startsWith('COMPUTO'))).toBe(false);
  });

  it('respeta los conmutadores de capas (perfiles / placas / cotas)', () => {
    const minimal = buildScene({
      includeFraming: false,
      includeBoards: false,
      includeDimensions: false,
      includeInsulation: false,
      includeLegend: false,
      includeBom: false
    });
    const full = buildScene();
    const countLayers = (layers: string[]) => (scene: ReturnType<typeof buildScene>) =>
      scene.sheets.reduce(
        (sum, sh) => sum + sh.entities.filter((e) => layers.includes(e.layer)).length,
        0
      );
    const counter = countLayers(['DW-PERFIL', 'DW-PLACA', 'DW-COTA']);
    expect(counter(minimal)).toBeLessThan(counter(full));
    expect(counter(minimal)).toBe(0);
  });

  it('dibuja una lámina por estancia (+ portada) en modo per-room', () => {
    const scene = buildScene({ layout: 'per-room', includeBom: false });
    expect(scene.sheets.length).toBe(DEFAULT_ROOMS.length + 1);
    expect(scene.sheets[0].name).toBe('INDICE-00');
    expect(scene.sheets[0].title).toMatch(/indice/i);
    // El índice lista todas las láminas del juego.
    const indexText = scene.sheets[0].entities
      .filter((e) => e.kind === 'text')
      .map((e) => (e as { value: string }).value)
      .join(' ');
    expect(indexText).toContain('INDICE DE LAMINAS');
    expect(indexText).toContain('NOTAS GENERALES');
    DEFAULT_ROOMS.forEach((room) => {
      expect(indexText.toUpperCase()).toContain(room.name.toUpperCase());
    });
  });

  it('el cajetín aparece en todas las láminas con la numeración correcta', () => {
    const scene = buildScene();
    scene.sheets.forEach((sheet, i) => {
      const texts = sheet.entities.filter((e) => e.kind === 'text').map((e) => (e as { value: string }).value);
      expect(texts.some((t) => t.includes(`${i + 1}/${scene.sheets.length}`))).toBe(true);
      expect(texts.some((t) => t.includes('ESCALA / UNIDADES'))).toBe(true);
    });
  });

  it('serializa un DXF válido con todas las capas del proyecto', () => {
    const scene = buildScene();
    const dxf = sceneToDxf(scene, { version: 'R12', units: 'mm', sheetIndex: null });
    const pairs = parseDxf(dxf);
    expect(pairs.length).toBeGreaterThan(100);
    expect(dxf.trimEnd().endsWith('EOF')).toBe(true);
    expect(dxf).toContain('ARQ-MURO');
    expect(dxf).toContain('DW-COTA');
    expect(countEntitiesInSection(dxf)).toBeGreaterThan(20);
    expect(dxf).not.toContain('NaN');
    // Todas las coordenadas numéricas deben ser parseables
    const numericCodes = new Set(['10', '20', '11', '21', '40', '50', '51']);
    pairs
      .filter((p) => numericCodes.has(p.code))
      .forEach((p) => expect(Number.isFinite(Number(p.value))).toBe(true));
  });

  it('exporta una sola lámina cuando se selecciona', () => {
    const scene = buildScene();
    const one = sceneToDxf(scene, { version: 'R12', units: 'mm', sheetIndex: 0 });
    const all = sceneToDxf(scene, { version: 'R12', units: 'mm', sheetIndex: null });
    expect(one.length).toBeLessThan(all.length);
  });

  it('renderiza la lámina a SVG vectorial', () => {
    const scene = buildScene();
    const svg = sheetToSvg(scene.sheets[0], scene, { width: 800 });
    expect(svg.startsWith('<?xml')).toBe(true);
    expect(svg).toContain('<svg');
    expect(svg).toContain('</svg>');
    expect(svg).not.toContain('NaN');
  });

  it('soporta sistema imperial sin romper el dibujo', () => {
    const imperialConfig: ProjectConfig = { ...DEFAULT_CONFIG, unitSystem: 'imperial' };
    const imperialRooms: Room[] = [
      {
        id: 'r_imp',
        name: 'Living Room',
        type: 'tabique_divisor',
        segments: [
          {
            id: 's_imp',
            name: 'Wall',
            length: 16,
            width: 9,
            repetitions: 1,
            openings: [{ id: 'o1', type: 'door', name: 'Door', width: 3, height: 7, count: 1 }]
          }
        ]
      }
    ];
    const impSummary = calculateProjectMaterials(imperialRooms, imperialConfig);
    const scene = buildCadScene(imperialRooms, imperialConfig, impSummary, DEFAULT_PLAN_OPTIONS);
    expect(scene.sheets.length).toBeGreaterThan(0);
    const dxf = sceneToDxf(scene, { version: 'R12', units: 'in', sheetIndex: null });
    expect(dxf).toContain('EOF');
    expect(dxf).not.toContain('NaN');
  });

  it('genera nombres de archivo seguros', () => {
    expect(slugify('Residencial Los Álamos — Piso 3')).toBe('residencial-los-alamos-piso-3');
    expect(slugify('')).toBe('plano');
    expect(slugify('////')).toBe('plano');
  });

  it('produce DXF con entidades desde el helper directo', () => {
    const dxf = entitiesToDxf([{ kind: 'line', layer: 'ARQ-MURO', a: { x: 0, y: 0 }, b: { x: 2, y: 0 } }]);
    expect(dxf).toContain('LINE');
    expect(dxf).toContain('ENDSEC');
  });
});
