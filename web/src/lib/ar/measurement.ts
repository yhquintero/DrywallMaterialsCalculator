/**
 * Geometría del escaneo: ajuste de plano, áreas, rectificación de ángulos y
 * estimación de confianza.
 *
 * Todo se trabaja en metros y en coordenadas de mundo. Las funciones son puras
 * y perfectamente testeables sin navegador.
 */

import { RoomMeasurement, Vec3, WallSegmentMeasure } from './types';

export function vec(x: number, y: number, z: number): Vec3 {
  return { x, y, z };
}

export function sub(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}

export function add(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z };
}

export function scale(a: Vec3, f: number): Vec3 {
  return { x: a.x * f, y: a.y * f, z: a.z * f };
}

export function dot(a: Vec3, b: Vec3): number {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

export function cross(a: Vec3, b: Vec3): Vec3 {
  return {
    x: a.y * b.z - a.z * b.y,
    y: a.z * b.x - a.x * b.z,
    z: a.x * b.y - a.y * b.x
  };
}

export function length(a: Vec3): number {
  return Math.sqrt(dot(a, a));
}

export function normalize(a: Vec3): Vec3 {
  const l = length(a);
  return l < 1e-9 ? vec(0, 0, 0) : scale(a, 1 / l);
}

export function distance(a: Vec3, b: Vec3): number {
  return length(sub(a, b));
}

/** Distancia en planta (ignora la componente vertical). */
export function distance2D(a: Vec3, b: Vec3): number {
  return Math.hypot(a.x - b.x, a.z - b.z);
}

/**
 * Ajusta un plano a una nube de puntos por mínimos cuadrados (PCA).
 * Devuelve el centroide y la normal unitaria.
 */
export function fitPlane(points: Vec3[]): { centroid: Vec3; normal: Vec3; residual: number } {
  if (points.length < 3) {
    const centroid = points.length
      ? scale(points.reduce((acc, p) => add(acc, p), vec(0, 0, 0)), 1 / points.length)
      : vec(0, 0, 0);
    return { centroid, normal: vec(0, 1, 0), residual: 0 };
  }

  const centroid = scale(points.reduce((acc, p) => add(acc, p), vec(0, 0, 0)), 1 / points.length);
  let xx = 0;
  let xy = 0;
  let xz = 0;
  let yy = 0;
  let yz = 0;
  let zz = 0;

  points.forEach((p) => {
    const d = sub(p, centroid);
    xx += d.x * d.x;
    xy += d.x * d.y;
    xz += d.x * d.z;
    yy += d.y * d.y;
    yz += d.y * d.z;
    zz += d.z * d.z;
  });

  // Matriz de covarianza simétrica 3×3
  const cov = [
    [xx, xy, xz],
    [xy, yy, yz],
    [xz, yz, zz]
  ];

  // Diagonalización por el método de Jacobi (matriz simétrica 3×3): el vector
  // propio asociado al menor valor propio es la normal del plano de ajuste.
  const jacobi = jacobiEigen(cov);
  let minIndex = 0;
  jacobi.values.forEach((v, i) => {
    if (v < jacobi.values[minIndex]) minIndex = i;
  });
  let normal = vec(
    jacobi.vectors[0][minIndex],
    jacobi.vectors[1][minIndex],
    jacobi.vectors[2][minIndex]
  );
  if (!Number.isFinite(normal.x) || !Number.isFinite(normal.y) || !Number.isFinite(normal.z) || length(normal) < 1e-9) {
    normal = vec(0, 1, 0);
  }
  normal = normalize(normal);
  // Convención: la normal apunta hacia arriba.
  if (normal.y < 0) normal = scale(normal, -1);

  const residual =
    points.reduce((acc, p) => acc + Math.abs(dot(sub(p, centroid), normal)), 0) / points.length;

  return { centroid, normal, residual };
}

/**
 * Diagonaliza una matriz simétrica 3×3 por rotaciones de Jacobi.
 * Devuelve valores propios y una matriz de vectores propios por columnas.
 */
export function jacobiEigen(input: number[][]): { values: number[]; vectors: number[][] } {
  const a = input.map((row) => row.slice());
  // v[:, i] es el vector propio del valor propio i.
  const v = [
    [1, 0, 0],
    [0, 1, 0],
    [0, 0, 1]
  ];
  const n = 3;

  for (let sweep = 0; sweep < 24; sweep += 1) {
    let off = 0;
    for (let p = 0; p < n; p += 1) {
      for (let q = p + 1; q < n; q += 1) off += a[p][q] * a[p][q];
    }
    if (off < 1e-20) break;

    for (let p = 0; p < n; p += 1) {
      for (let q = p + 1; q < n; q += 1) {
        if (Math.abs(a[p][q]) < 1e-18) continue;
        const theta = (a[q][q] - a[p][p]) / (2 * a[p][q]);
        const t = Math.sign(theta || 1) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
        const c = 1 / Math.sqrt(t * t + 1);
        const s = t * c;
        for (let k = 0; k < n; k += 1) {
          const akp = a[k][p];
          const akq = a[k][q];
          a[k][p] = c * akp - s * akq;
          a[k][q] = s * akp + c * akq;
        }
        for (let k = 0; k < n; k += 1) {
          const apk = a[p][k];
          const aqk = a[q][k];
          a[p][k] = c * apk - s * aqk;
          a[q][k] = s * apk + c * aqk;
        }
        for (let k = 0; k < n; k += 1) {
          const vkp = v[k][p];
          const vkq = v[k][q];
          v[k][p] = c * vkp - s * vkq;
          v[k][q] = s * vkp + c * vkq;
        }
      }
    }
  }

  return { values: [a[0][0], a[1][1], a[2][2]], vectors: v };
}

/** Proyecta un punto sobre el plano definido por centroide + normal. */
export function projectOntoPlane(p: Vec3, centroid: Vec3, normal: Vec3): Vec3 {
  const d = dot(sub(p, centroid), normal);
  return sub(p, scale(normal, d));
}

/** Área de un polígono 3D (fórmula del área proyectada de Newell). */
export function polygonArea3D(points: Vec3[]): number {
  if (points.length < 3) return 0;
  let nx = 0;
  let ny = 0;
  let nz = 0;
  for (let i = 0; i < points.length; i += 1) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    nx += (a.y - b.y) * (a.z + b.z);
    ny += (a.z - b.z) * (a.x + b.x);
    nz += (a.x - b.x) * (a.y + b.y);
  }
  return 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz);
}

/** Área de un polígono en planta (X–Z), usando la fórmula del shoelace. */
export function polygonArea2D(points: Vec3[]): number {
  if (points.length < 3) return 0;
  let area = 0;
  for (let i = 0; i < points.length; i += 1) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    area += a.x * b.z - b.x * a.z;
  }
  return Math.abs(area) / 2;
}

/** Perímetro de un polígono cerrado. */
export function polygonPerimeter(points: Vec3[], closed: boolean): number {
  if (points.length < 2) return 0;
  let total = 0;
  for (let i = 0; i < points.length - 1; i += 1) total += distance(points[i], points[i + 1]);
  if (closed && points.length > 2) total += distance(points[points.length - 1], points[0]);
  return total;
}

/**
 * Rectifica el polígono forzando ángulos rectos cuando el ángulo medido está
 * dentro de la tolerancia. Es la corrección que aplican las apps profesionales
 * de medición, porque las habitaciones reales son ortogonales salvo errores de
 * captura.
 */
export function rectifyPolygon(points: Vec3[], toleranceDeg = 12): Vec3[] {
  if (points.length < 4) return points.map((p) => ({ ...p }));
  const out: Vec3[] = [{ ...points[0] }];
  for (let i = 1; i < points.length; i += 1) {
    const prev = out[i - 1];
    const current = points[i];
    const edge = sub(current, prev);
    const len = length(edge);
    if (len < 1e-6) continue;
    let dir = scale(edge, 1 / len);

    if (i >= 2) {
      const before = sub(out[i - 1], out[i - 2]);
      const bl = length(before);
      if (bl > 1e-6) {
        const bdir = scale(before, 1 / bl);
        const angle = (Math.acos(Math.max(-1, Math.min(1, dot(bdir, dir)))) * 180) / Math.PI;
        const target = 90;
        if (Math.abs(angle - target) <= toleranceDeg) {
          // Gira `bdir` 90° en el plano horizontal y conserva la longitud.
          const rotated = normalize(vec(-bdir.z, 0, bdir.x));
          const sign = dot(rotated, dir) >= 0 ? 1 : -1;
          dir = scale(rotated, sign);
        }
      }
    }
    out.push(add(prev, scale(dir, len)));
  }
  return out;
}

/**
 * Rectificación cíclica de un polígono cerrado: relaja todos los vértices
 * (incluido el del cierre) hasta que cada ángulo interior queda a 90° dentro
 * de la tolerancia. Es una relajación de Gauss-Seidel sobre el bucle, por lo
 * que reparte el error de cierre entre todos los lados en vez de acumularlo
 * en el último.
 */
export function rectifyLoop(points: Vec3[], toleranceDeg = 12, iterations = 8): Vec3[] {
  const n = points.length;
  if (n < 4) return points.map((p) => ({ ...p }));
  const pts = points.map((p) => ({ ...p }));
  const toleranceRad = (toleranceDeg * Math.PI) / 180;

  for (let iter = 0; iter < iterations; iter += 1) {
    let maxCorrection = 0;
    for (let i = 0; i < n; i += 1) {
      const prev = pts[(i - 1 + n) % n];
      const prevPrev = pts[(i - 2 + n) % n];
      const curr = pts[i];

      const inVec = sub(prev, prevPrev);
      const inLen = length(inVec);
      if (inLen < 1e-6) continue;
      const inDir = scale(inVec, 1 / inLen);

      const edge = sub(curr, prev);
      const edgeLen = length(edge);
      if (edgeLen < 1e-6) continue;
      const edgeDir = scale(edge, 1 / edgeLen);

      const cos = Math.max(-1, Math.min(1, dot(inDir, edgeDir)));
      const angle = Math.acos(cos);
      if (Math.abs(angle - Math.PI / 2) > toleranceRad) continue;

      // Dirección ortogonal a la anterior, en el sentido del lado medido.
      const rotated = normalize(vec(-inDir.z, 0, inDir.x));
      const sign = dot(rotated, edgeDir) >= 0 ? 1 : -1;
      const target = scale(rotated, sign);
      const corrected = add(prev, scale(target, edgeLen));
      maxCorrection = Math.max(maxCorrection, distance(corrected, curr));
      pts[i] = corrected;
    }
    if (maxCorrection < 1e-4) break;
  }
  return pts;
}

/** Elimina vértices duplicados o casi coincidentes. */
export function dedupePolygon(points: Vec3[], epsilon = 0.02): Vec3[] {
  const out: Vec3[] = [];
  points.forEach((p) => {
    if (!out.some((q) => distance(q, p) < epsilon)) out.push({ ...p });
  });
  return out;
}

/** Devuelve el rectángulo envolvente alineado con la dirección dominante. */
export function orientedBoundingBox(points: Vec3[]): { length: number; width: number } {
  if (points.length < 3) return { length: 0, width: 0 };
  let best = { angle: 0, minA: Infinity, maxA: -Infinity, minB: Infinity, maxB: -Infinity, area: Infinity };
  const candidates = new Set<number>();
  for (let i = 0; i < points.length; i += 1) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    candidates.add(Math.atan2(b.z - a.z, b.x - a.x));
  }
  for (let k = 0; k < 24; k += 1) candidates.add((k * Math.PI) / 24);

  candidates.forEach((angle) => {
    const ca = Math.cos(angle);
    const sa = Math.sin(angle);
    let minA = Infinity;
    let maxA = -Infinity;
    let minB = Infinity;
    let maxB = -Infinity;
    points.forEach((p) => {
      const u = p.x * ca + p.z * sa;
      const v = -p.x * sa + p.z * ca;
      minA = Math.min(minA, u);
      maxA = Math.max(maxA, u);
      minB = Math.min(minB, v);
      maxB = Math.max(maxB, v);
    });
    const area = (maxA - minA) * (maxB - minB);
    if (area < best.area) best = { angle, minA, maxA, minB, maxB, area };
  });

  const l = best.maxA - best.minA;
  const w = best.maxB - best.minB;
  return { length: Math.max(l, w), width: Math.min(l, w) };
}

/** Ángulo interior (grados) en el vértice i de un polígono cerrado. */
export function interiorAngle(points: Vec3[], i: number): number {
  const n = points.length;
  if (n < 3) return 0;
  const prev = points[(i - 1 + n) % n];
  const curr = points[i];
  const next = points[(i + 1) % n];
  const a = sub(prev, curr);
  const b = sub(next, curr);
  const la = length(a);
  const lb = length(b);
  if (la < 1e-9 || lb < 1e-9) return 0;
  const cos = Math.max(-1, Math.min(1, dot(a, b) / (la * lb)));
  return (Math.acos(cos) * 180) / Math.PI;
}

/**
 * Calcula todas las magnitudes de la estancia a partir del polígono de suelo.
 * @param height altura de muro: la medida en AR o la estimada por el usuario.
 */
export function measureRoom(
  polygon: Vec3[],
  height: number,
  options: { closed?: boolean; toleranceDeg?: number } = {}
): RoomMeasurement {
  // Por defecto el polígono se interpreta como un anillo cerrado: el último
  // punto se une con el primero de forma implícita.
  const closed = options.closed ?? true;
  const warnings: string[] = [];
  const cleaned = dedupePolygon(polygon);
  if (cleaned.length < 3) {
    return {
      perimeter: 0,
      floorArea: 0,
      wallArea: 0,
      height,
      segments: [],
      bounding: { length: 0, width: 0 },
      closureGap: 0,
      closureRatio: 0,
      confidence: 0,
      warnings: ['Se necesitan al menos 3 puntos para medir la estancia.']
    };
  }

  const tolerance = options.toleranceDeg ?? 12;
  // La rectificación aplana el anillo sobre la horizontal, por lo que la
  // planaridad se evalúa sobre los puntos crudos capturados por el usuario.
  const plane = fitPlane(cleaned);
  const ring = closed
    ? rectifyLoop(cleaned, tolerance)
    : rectifyPolygon(cleaned, tolerance);
  const perimeter = polygonPerimeter(ring, closed);
  const rawArea = polygonArea2D(ring);
  const projected = cleaned.map((p) => projectOntoPlane(p, plane.centroid, plane.normal));
  const floorArea = Math.max(rawArea, polygonArea3D(projected));
  const bounding = orientedBoundingBox(ring);
  // En un anillo el cierre es implícito (no hay hueco que medir). El hueco
  // sólo tiene sentido en un recorrido abierto, donde el usuario intentó
  // volver al punto de partida sin conseguirlo.
  const closureGap = closed ? 0 : distance(ring[ring.length - 1], ring[0]);
  const closureRatio = perimeter > 0 ? Math.min(1, closureGap / perimeter) : 0;

  const segments: WallSegmentMeasure[] = ring.map((p, i) => {
    const next = ring[(i + 1) % ring.length];
    const isLast = i === ring.length - 1;
    const segLength = closed || !isLast ? distance(p, next) : 0;
    return {
      index: i,
      length: Number(segLength.toFixed(3)),
      height: Number(height.toFixed(3)),
      cornerAngle: Number(interiorAngle(ring, i).toFixed(1)),
      closureError: Number(closureRatio.toFixed(3))
    };
  });
  if (!closed) segments.pop();

  if (closureRatio > 0.03) {
    warnings.push(
      `El recorrido no cierra bien (${(closureGap * 100).toFixed(0)} cm de desfase). Revisa el último punto.`
    );
  }
  if (plane.residual > 0.08) {
    warnings.push('Los puntos no están en el mismo plano: es posible que hayas mezclado suelo y mobiliario.');
  }
  if (perimeter < 1) warnings.push('Perímetro muy pequeño: comprueba la escala de la captura.');
  if (height < 1.2) warnings.push('Altura inferior a 1.20 m: revísala antes de generar los muros.');

  // Confianza: penaliza desfase de cierre, ruido de plano y ángulos extraños.
  const anglePenalty = ring.reduce((acc, _p, i) => {
    const a = interiorAngle(ring, i);
    const deviation = Math.min(Math.abs(a - 90), Math.abs(a - 180)) / 90;
    return acc + deviation / ring.length;
  }, 0);
  const confidence = Math.max(
    0,
    Math.min(1, 1 - closureRatio * 4 - Math.min(0.5, plane.residual * 2) - anglePenalty * 0.5)
  );

  return {
    perimeter: Number(perimeter.toFixed(3)),
    floorArea: Number(floorArea.toFixed(3)),
    wallArea: Number((perimeter * height).toFixed(3)),
    height: Number(height.toFixed(3)),
    segments,
    bounding: { length: Number(bounding.length.toFixed(3)), width: Number(bounding.width.toFixed(3)) },
    closureGap: Number(closureGap.toFixed(3)),
    closureRatio: Number(closureRatio.toFixed(4)),
    confidence: Number(confidence.toFixed(3)),
    warnings
  };
}

/** Altura media a partir de puntos de suelo y de techo. */
export function estimateHeight(floorPoints: Vec3[], ceilingPoints: Vec3[]): number {
  if (!floorPoints.length || !ceilingPoints.length) return 0;
  const floorY = floorPoints.reduce((s, p) => s + p.y, 0) / floorPoints.length;
  const ceilY = ceilingPoints.reduce((s, p) => s + p.y, 0) / ceilingPoints.length;
  return Math.max(0, ceilY - floorY);
}

/** Redondea al paso indicado (0.05 m ⇒ múltiplos de 5 cm). */
export function roundToStep(value: number, step = 0.05): number {
  if (!Number.isFinite(value) || step <= 0) return value;
  return Math.round(value / step) * step;
}
