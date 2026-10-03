import { describe, it, expect } from 'vitest';
import {
  distance,
  estimateHeight,
  rectifyLoop,
  fitPlane,
  interiorAngle,
  measureRoom,
  orientedBoundingBox,
  polygonArea2D,
  polygonArea3D,
  polygonPerimeter,
  rectifyPolygon,
  roundToStep,
  vec
} from './measurement';
import {
  applyHomography,
  computeHomography,
  homographyError,
  homographyFromReference,
  invert3,
  multiply
} from './homography';
import { scanToRooms, isRectangular } from './scanToRooms';
import { RoomScan, ScanToRoomsOptions } from './types';

/** Dibuja un rectángulo de 5 × 4 m en el plano del suelo (y = 0). */
function rectangle(width: number, depth: number, jitter = 0) {
  const j = (i: number) => (jitter ? (i % 2 === 0 ? jitter : -jitter) : 0);
  return [
    vec(0 + j(0), 0, 0 + j(1)),
    vec(width + j(2), 0, 0 + j(3)),
    vec(width + j(4), 0, depth + j(5)),
    vec(0 + j(6), 0, depth + j(7))
  ];
}

describe('Geometría de escaneo', () => {
  it('calcula el área de un polígono en planta', () => {
    expect(polygonArea2D(rectangle(5, 4))).toBeCloseTo(20, 6);
    expect(polygonArea3D(rectangle(5, 4))).toBeCloseTo(20, 6);
  });

  it('calcula el perímetro cerrado y abierto', () => {
    const poly = rectangle(5, 4);
    expect(polygonPerimeter(poly, true)).toBeCloseTo(18, 6);
    expect(polygonPerimeter(poly, false)).toBeCloseTo(14, 6);
  });

  it('ajusta un plano horizontal y detecta la normal', () => {
    const pts = rectangle(5, 4).map((p) => vec(p.x, 0.02, p.z));
    const { normal, residual, centroid } = fitPlane(pts);
    expect(Math.abs(normal.y)).toBeCloseTo(1, 3);
    expect(residual).toBeLessThan(0.001);
    expect(centroid.x).toBeCloseTo(2.5, 4);
  });

  it('ajusta un plano inclinado', () => {
    const pts = rectangle(4, 4).map((p) => vec(p.x, p.z * 0.1, p.z));
    const { normal } = fitPlane(pts);
    // Plano y = 0.1z ⇒ normal ∝ (0, 1, -0.1) normalizada
    expect(normal.y).toBeGreaterThan(0.9);
    expect(Math.abs(normal.z)).toBeGreaterThan(0.05);
  });

  it('rectifica ángulos casi rectos', () => {
    const noisy = rectangle(5, 4, 0.08);
    const fixed = rectifyPolygon(noisy, 12);
    for (let i = 0; i < fixed.length; i += 1) {
      const a = interiorAngle(fixed, i);
      expect(Math.abs(a - 90)).toBeLessThan(0.5);
    }
    // Las longitudes se conservan (error < 1 cm por lado)
    expect(distance(fixed[0], fixed[1])).toBeCloseTo(distance(noisy[0], noisy[1]), 2);
  });

  it('rectifica también el lado de cierre del anillo', () => {
    const noisy = [
      vec(0.05, 0, -0.04),
      vec(5.1, 0.02, 0.06),
      vec(4.92, 0, 4.1),
      vec(-0.07, -0.02, 3.95)
    ];
    const ring = rectifyLoop(noisy, 12);
    for (let i = 0; i < ring.length; i += 1) {
      expect(Math.abs(interiorAngle(ring, i) - 90)).toBeLessThan(1.5);
    }
  });

  it('mantiene el polígono si los ángulos no son rectos', () => {
    const triangle = [vec(0, 0, 0), vec(4, 0, 0), vec(2, 0, 3)];
    const fixed = rectifyPolygon(triangle, 12);
    expect(fixed.length).toBe(3);
    expect(distance(fixed[0], fixed[1])).toBeCloseTo(4, 5);
  });

  it('obtiene el rectángulo envolvente orientado', () => {
    const box = orientedBoundingBox(rectangle(6, 3));
    expect(box.length).toBeCloseTo(6, 2);
    expect(box.width).toBeCloseTo(3, 2);
  });

  it('estima la altura a partir de puntos de suelo y techo', () => {
    const floor = [vec(0, 0, 0), vec(3, 0, 0)];
    const ceiling = [vec(0, 2.55, 0), vec(3, 2.6, 0)];
    expect(estimateHeight(floor, ceiling)).toBeCloseTo(2.575, 3);
  });

  it('mide una habitación completa y detecta el cierre correcto', () => {
    const poly = rectangle(5, 4);
    const m = measureRoom(poly, 2.6);
    expect(m.perimeter).toBeCloseTo(18, 2);
    expect(m.floorArea).toBeCloseTo(20, 2);
    expect(m.wallArea).toBeCloseTo(18 * 2.6, 2);
    expect(m.segments).toHaveLength(4);
    expect(m.closureGap).toBeLessThan(0.01);
    expect(m.confidence).toBeGreaterThan(0.85);
    expect(m.warnings).toHaveLength(0);
  });

  it('avisa cuando el recorrido no cierra', () => {
    const open = [vec(0, 0, 0), vec(5, 0, 0), vec(5, 0, 4), vec(4.2, 0, 4.3)];
    const m = measureRoom(open, 2.6, { closed: false });
    expect(m.warnings.length).toBeGreaterThan(0);
    expect(m.confidence).toBeLessThan(0.95);
  });

  it('penaliza los puntos fuera de plano', () => {
    const withNoise = [
      vec(0, 0, 0),
      vec(5, 0.4, 0),
      vec(5, 0, 4),
      vec(0, 0, 4)
    ];
    const m = measureRoom(withNoise, 2.6);
    expect(m.warnings.some((w) => w.includes('plano'))).toBe(true);
  });

  it('redondea al paso indicado', () => {
    expect(roundToStep(2.37, 0.05)).toBeCloseTo(2.35, 6);
    expect(roundToStep(2.37, 0.01)).toBeCloseTo(2.37, 6);
    expect(roundToStep(Number.NaN, 0.05)).toBeNaN();
  });
});

describe('Homografía para medición por foto', () => {
  const src = [
    { x: 100, y: 500 },
    { x: 600, y: 480 },
    { x: 700, y: 200 },
    { x: 220, y: 260 }
  ];

  it('mapea el rectángulo de referencia a las coordenadas reales', () => {
    const H = homographyFromReference(src, 1.2, 1.2);
    const p0 = applyHomography(H, src[0]);
    const p1 = applyHomography(H, src[1]);
    const p2 = applyHomography(H, src[2]);
    const p3 = applyHomography(H, src[3]);
    expect(p0.x).toBeCloseTo(0, 4);
    expect(p0.y).toBeCloseTo(0, 4);
    expect(p1.x).toBeCloseTo(1.2, 4);
    expect(p1.y).toBeCloseTo(0, 4);
    expect(p2.x).toBeCloseTo(1.2, 4);
    expect(p2.y).toBeCloseTo(1.2, 4);
    expect(p3.x).toBeCloseTo(0, 4);
    expect(p3.y).toBeCloseTo(1.2, 4);
    expect(homographyError(H, src, [p0, p1, p2, p3])).toBeLessThan(1e-6);
  });

  it('es exacta en transformaciones afines (sin fuga de perspectiva)', () => {
    // Un paralelogramo en la imagen ⇒ homografía afín: el punto medio se
    // conserva. Con perspectiva real el punto medio NO se conserva (es el
    // efecto que corrige precisamente la homografía).
    const affine = [
      { x: 100, y: 400 },
      { x: 500, y: 400 },
      { x: 600, y: 200 },
      { x: 200, y: 200 }
    ];
    const H = homographyFromReference(affine, 2, 2);
    const mid = applyHomography(H, { x: (affine[0].x + affine[1].x) / 2, y: (affine[0].y + affine[1].y) / 2 });
    expect(mid.x).toBeCloseTo(1, 4);
    expect(mid.y).toBeCloseTo(0, 4);
  });

  it('corrige el escorzo en una foto con perspectiva', () => {
    const H = homographyFromReference(src, 2, 2);
    const near = applyHomography(H, { x: (src[0].x + src[1].x) / 2, y: (src[0].y + src[1].y) / 2 });
    // Bajo perspectiva el punto medio en píxeles no coincide con el real: el
    // resultado debe seguir siendo monótono y acotado dentro del rectángulo.
    expect(near.x).toBeGreaterThan(0.5);
    expect(near.x).toBeLessThan(1.6);
  });

  it('inversa y producto respetan la identidad', () => {
    const H = computeHomography(src, [
      { x: 0, y: 0 },
      { x: 2, y: 0 },
      { x: 2, y: 3 },
      { x: 0, y: 3 }
    ]);
    const I = multiply(H, invert3(H));
    expect(I[0]).toBeCloseTo(1, 6);
    expect(I[4]).toBeCloseTo(1, 6);
    expect(I[8]).toBeCloseTo(1, 6);
    expect(I[1]).toBeCloseTo(0, 6);
    expect(I[3]).toBeCloseTo(0, 6);
  });

  it('lanza un error con puntos degenerados', () => {
    expect(() =>
      computeHomography(
        [
          { x: 0, y: 0 },
          { x: 1, y: 1 },
          { x: 2, y: 2 },
          { x: 3, y: 3 }
        ],
        [
          { x: 0, y: 0 },
          { x: 1, y: 0 },
          { x: 1, y: 1 },
          { x: 0, y: 1 }
        ]
      )
    ).toThrow();
  });

  it('exige exactamente 4 puntos', () => {
    expect(() => computeHomography([{ x: 0, y: 0 }], [{ x: 0, y: 0 }])).toThrow('exactamente 4');
  });
});

describe('Conversión de escaneo a estancias', () => {
  const scan: RoomScan = {
    id: 'scan_1',
    name: 'Salón',
    createdAt: new Date().toISOString(),
    source: 'webxr',
    points: [],
    polygon: rectangle(5, 4),
    measurement: measureRoom(rectangle(5, 4), 2.6),
    measuredHeight: 2.6
  };

  const options: ScanToRoomsOptions = {
    target: 'both',
    ceilingType: 'techo_st',
    wallType: 'tabique_divisor',
    defaultHeight: 2.6,
    unitSystem: 'metric',
    roomName: 'Salón escaneado',
    roundTo: 0.05
  };

  it('detecta polígonos rectangulares', () => {
    expect(isRectangular(scan)).toBe(true);
  });

  it('genera techo y muros en modo both', () => {
    const rooms = scanToRooms(scan, options);
    expect(rooms).toHaveLength(2);
    const ceiling = rooms.find((r) => r.type === 'techo_st');
    const walls = rooms.find((r) => r.type === 'tabique_divisor');
    expect(ceiling?.segments[0].length).toBeCloseTo(5, 2);
    expect(ceiling?.segments[0].width).toBeCloseTo(4, 2);
    expect(walls?.segments).toHaveLength(4);
    expect(walls?.segments.reduce((s, seg) => s + seg.length, 0)).toBeCloseTo(18, 1);
  });

  it('sólo genera techo cuando se pide', () => {
    const rooms = scanToRooms(scan, { ...options, target: 'ceiling' });
    expect(rooms).toHaveLength(1);
    expect(rooms[0].type).toBe('techo_st');
  });

  it('convierte a pies en sistema imperial', () => {
    const rooms = scanToRooms(scan, { ...options, target: 'ceiling', unitSystem: 'imperial', roundTo: 0.25 });
    const seg = rooms[0].segments[0];
    // 5 m = 16.4042 ft redondeado al cuarto de pie más cercano.
    expect(seg.length).toBeCloseTo(16.5, 5);
    expect(seg.width).toBeCloseTo(13, 5);
  });

  it('genera un paño por muro con la altura medida', () => {
    const rooms = scanToRooms(scan, { ...options, target: 'walls' });
    rooms[0].segments.forEach((s) => {
      expect(s.width).toBeCloseTo(2.6, 2);
      expect(s.repetitions).toBe(1);
    });
  });

  it('no genera nada con un escaneo vacío', () => {
    const empty: RoomScan = { ...scan, polygon: [], measurement: measureRoom([], 2.6) };
    expect(scanToRooms(empty, options)).toHaveLength(0);
  });
});
