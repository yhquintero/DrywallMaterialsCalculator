/**
 * Homografía 2D para corregir la perspectiva en el modo de medición por foto.
 *
 * Permite mapear píxeles de la imagen al plano real del suelo: el usuario
 * marca 4 puntos que forman un rectángulo de dimensiones conocidas (por
 * ejemplo, dos baldosas de 60 × 60 cm) y el sistema calcula la matriz que
 * convierte cualquier punto de la foto en coordenadas métricas.
 *
 * Resolución: DLT normalizada con eliminación de Gauss sobre el sistema 8×8.
 */

export type Matrix3 = [number, number, number, number, number, number, number, number, number];

export interface Point2 {
  x: number;
  y: number;
}

const IDENTITY: Matrix3 = [1, 0, 0, 0, 1, 0, 0, 0, 1];

/** Resuelve A·x = b por eliminación de Gauss con pivote parcial. */
function solveLinearSystem(A: number[][], b: number[]): number[] {
  const n = b.length;
  const m = A.map((row, i) => [...row, b[i]]);

  for (let col = 0; col < n; col += 1) {
    let pivot = col;
    for (let r = col + 1; r < n; r += 1) {
      if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
    }
    if (Math.abs(m[pivot][col]) < 1e-12) {
      throw new Error('El sistema es singular: los puntos marcados son degenerados (colineales o repetidos).');
    }
    if (pivot !== col) {
      const tmp = m[col];
      m[col] = m[pivot];
      m[pivot] = tmp;
    }
    const pivotValue = m[col][col];
    for (let c = col; c <= n; c += 1) m[col][c] /= pivotValue;
    for (let r = 0; r < n; r += 1) {
      if (r === col) continue;
      const f = m[r][col];
      if (f === 0) continue;
      for (let c = col; c <= n; c += 1) m[r][c] -= f * m[col][c];
    }
  }
  return m.map((row) => row[n]);
}

/**
 * Calcula la homografía que lleva `src` (píxeles) a `dst` (metros).
 * Ambos arrays deben tener exactamente 4 puntos en el mismo orden.
 */
export function computeHomography(src: Point2[], dst: Point2[]): Matrix3 {
  if (src.length !== 4 || dst.length !== 4) {
    throw new Error('Se necesitan exactamente 4 pares de puntos.');
  }

  // Normalización (Hartley) para mejorar el condicionamiento numérico.
  const normalizer = (points: Point2[]): { T: Matrix3; pts: Point2[] } => {
    const cx = points.reduce((s, p) => s + p.x, 0) / points.length;
    const cy = points.reduce((s, p) => s + p.y, 0) / points.length;
    const dist = points.reduce((s, p) => s + Math.hypot(p.x - cx, p.y - cy), 0) / points.length || 1;
    const s = Math.SQRT2 / dist;
    return {
      T: [s, 0, -s * cx, 0, s, -s * cy, 0, 0, 1],
      pts: points.map((p) => ({ x: (p.x - cx) * s, y: (p.y - cy) * s }))
    };
  };

  const ns = normalizer(src);
  const nd = normalizer(dst);

  const A: number[][] = [];
  const b: number[] = [];
  for (let i = 0; i < 4; i += 1) {
    const { x: u, y: v } = ns.pts[i];
    const { x, y } = nd.pts[i];
    A.push([u, v, 1, 0, 0, 0, -u * x, -v * x]);
    b.push(x);
    A.push([0, 0, 0, u, v, 1, -u * y, -v * y]);
    b.push(y);
  }

  const h = solveLinearSystem(A, b);
  const Hn: Matrix3 = [h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1];

  // Des-normalización: H = Tdst⁻¹ · Hn · Tsrc
  const H = multiply(multiply(invert3(nd.T), Hn), ns.T);
  return H;
}

export function multiply(a: Matrix3, b: Matrix3): Matrix3 {
  const out = new Array(9).fill(0) as number[];
  for (let r = 0; r < 3; r += 1) {
    for (let c = 0; c < 3; c += 1) {
      let sum = 0;
      for (let k = 0; k < 3; k += 1) sum += a[r * 3 + k] * b[k * 3 + c];
      out[r * 3 + c] = sum;
    }
  }
  return out as Matrix3;
}

export function invert3(m: Matrix3): Matrix3 {
  const det =
    m[0] * (m[4] * m[8] - m[5] * m[7]) -
    m[1] * (m[3] * m[8] - m[5] * m[6]) +
    m[2] * (m[3] * m[7] - m[4] * m[6]);
  if (Math.abs(det) < 1e-14) return IDENTITY;
  const d = 1 / det;
  return [
    (m[4] * m[8] - m[5] * m[7]) * d,
    (m[2] * m[7] - m[1] * m[8]) * d,
    (m[1] * m[5] - m[2] * m[4]) * d,
    (m[5] * m[6] - m[3] * m[8]) * d,
    (m[0] * m[8] - m[2] * m[6]) * d,
    (m[2] * m[3] - m[0] * m[5]) * d,
    (m[3] * m[7] - m[4] * m[6]) * d,
    (m[1] * m[6] - m[0] * m[7]) * d,
    (m[0] * m[4] - m[1] * m[3]) * d
  ];
}

/** Aplica la homografía a un punto (en píxeles) y devuelve coordenadas reales. */
export function applyHomography(H: Matrix3, p: Point2): Point2 {
  const w = H[6] * p.x + H[7] * p.y + H[8];
  if (Math.abs(w) < 1e-12) return { x: 0, y: 0 };
  return {
    x: (H[0] * p.x + H[1] * p.y + H[2]) / w,
    y: (H[3] * p.x + H[4] * p.y + H[5]) / w
  };
}

/**
 * Construye la homografía de suelo a partir de un rectángulo de referencia:
 * el usuario marca 4 esquinas en la foto y indica ancho y alto reales.
 */
export function homographyFromReference(
  corners: Point2[],
  widthMeters: number,
  heightMeters: number
): Matrix3 {
  const dst: Point2[] = [
    { x: 0, y: 0 },
    { x: widthMeters, y: 0 },
    { x: widthMeters, y: heightMeters },
    { x: 0, y: heightMeters }
  ];
  return computeHomography(corners, dst);
}

/** Error de reproyección medio (útil para estimar la calidad de la captura). */
export function homographyError(H: Matrix3, src: Point2[], dst: Point2[]): number {
  if (!src.length) return 0;
  const total = src.reduce((acc, p, i) => {
    const q = applyHomography(H, p);
    return acc + Math.hypot(q.x - dst[i].x, q.y - dst[i].y);
  }, 0);
  return total / src.length;
}
