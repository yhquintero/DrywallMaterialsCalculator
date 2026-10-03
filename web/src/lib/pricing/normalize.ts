/**
 * Normalización de catálogos de proveedor: nombres, unidades y monedas.
 *
 * El reto real de conectar con distribuidores es que cada uno llama al mismo
 * producto de una forma distinta ("Placa Yeso ST 12,5", "Gypsum Board 1/2",
 * "Panel Yeso 12mm Standard"). Aquí se resuelven tres capas:
 *   1. Normalización léxica (acentos, puntuación, sinónimos de medida).
 *   2. Coincidencia por tokens + ratio de subcadenas (Jaccard + LCS).
 *   3. Conversión de unidades y moneda al embalaje comercial del presupuesto.
 */

import { CURRENCY_SYMBOLS } from '../../data/materials';
import { MaterialQuery } from './types';

// ── Normalización léxica ─────────────────────────────────────────────────────

const SYNONYMS: Array<[RegExp, string]> = [
  [/\bplacas?\b/g, 'placa'],
  [/\bpaineles?\b/g, 'panel'],
  [/\bpainel\b/g, 'panel'],
  [/\bpainéis\b/g, 'panel'],
  [/\bpaneles\b/g, 'panel'],
  [/\bpanel\b/g, 'placa'],
  [/\bpladur\b/g, 'placa'],
  [/\bdrywall\b/g, 'placa'],
  [/\bgypsum\b/g, 'placa'],
  [/\bcarton[\s-]?yeso\b/g, 'placa'],
  [/\bboard\b/g, 'placa'],
  [/\bperfiles?\b/g, 'perfil'],
  [/\bperfil\b/g, 'perfil'],
  [/\bmontantes?\b/g, 'parante'],
  [/\bparantes?\b/g, 'parante'],
  [/\bpastantes?\b/g, 'parante'],
  [/\bstuds?\b/g, 'parante'],
  [/\bcanales?\b/g, 'canal'],
  [/\bsoleras?\b/g, 'canal'],
  [/\btracks?\b/g, 'canal'],
  [/\bomegas?\b/g, 'omega'],
  [/\bfurring\b/g, 'omega'],
  [/\btornillos?\b/g, 'tornillo'],
  [/\bscrews?\b/g, 'tornillo'],
  [/\bmasillas?\b/g, 'masilla'],
  [/\bcompuestos?\b/g, 'masilla'],
  [/\bjoint[\s-]?compound\b/g, 'masilla'],
  [/\bcintas?\b/g, 'cinta'],
  [/\btapes?\b/g, 'cinta'],
  [/\blana(s)?\b/g, 'lana'],
  [/\baislantes?\b/g, 'lana'],
  [/\binsulation\b/g, 'lana'],
  [/\bfijaciones\b/g, 'fijacion'],
  [/\banclajes?\b/g, 'fijacion'],
  [/\bmilimetros?\b/g, 'mm'],
  [/\bcentimetros?\b/g, 'cm'],
  [/\bmetros?\b/g, 'm'],
  [/\bunidades?\b/g, 'u'],
  [/\bpiezas?\b/g, 'u'],
  [/\bpzas?\b/g, 'u'],
  [/\bcajas?\b/g, 'caja'],
  [/\brollos?\b/g, 'rollo'],
  [/\bbaldes?\b/g, 'balde'],
  [/\bcubos?\b/g, 'balde'],
  [/\btiras?\b/g, 'tira']
];

/** Pasa a minúsculas, quita acentos, puntuación y aplica sinónimos del sector. */
export function normalizeText(value: string): string {
  let out = String(value ?? '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/([0-9]+)[,]([0-9]+)/g, '$1.$2')
    .replace(/[^a-z0-9.\s/]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  for (const [pattern, replacement] of SYNONYMS) out = out.replace(pattern, replacement);
  return out.replace(/\s+/g, ' ').trim();
}

export function tokenize(value: string): string[] {
  return normalizeText(value)
    .split(/\s+/)
    .filter((t) => t.length > 1);
}

/** Longitud de la subsecuencia común más larga (base del ratio de similitud). */
function lcsLength(a: string, b: string): number {
  const m = a.length;
  const n = b.length;
  if (!m || !n) return 0;
  let prev = new Array<number>(n + 1).fill(0);
  for (let i = 1; i <= m; i += 1) {
    const curr = new Array<number>(n + 1).fill(0);
    for (let j = 1; j <= n; j += 1) {
      curr[j] = a[i - 1] === b[j - 1] ? prev[j - 1] + 1 : Math.max(prev[j], curr[j - 1]);
    }
    prev = curr;
  }
  return prev[n];
}

/** Similitud combinada [0,1]: 60 % tokens (Jaccard) + 40 % subsecuencia. */
export function similarity(a: string, b: string): number {
  const na = normalizeText(a);
  const nb = normalizeText(b);
  if (!na || !nb) return 0;
  if (na === nb) return 1;

  const ta = new Set(tokenize(a));
  const tb = new Set(tokenize(b));
  let intersection = 0;
  ta.forEach((t) => {
    if (tb.has(t)) intersection += 1;
  });
  const union = new Set<string>([...ta, ...tb]).size || 1;
  const jaccard = intersection / union;

  const lcsRatio = (2 * lcsLength(na, nb)) / (na.length + nb.length);
  return Math.min(1, jaccard * 0.6 + lcsRatio * 0.4);
}

/**
 * Empareja el nombre publicado por el proveedor con los materiales del
 * presupuesto. Devuelve `null` si ninguno supera el umbral.
 */
export function matchMaterial(
  supplierName: string,
  materials: MaterialQuery[],
  threshold = 0.42
): { material: MaterialQuery; score: number } | null {
  if (!supplierName) return null;
  let bestMaterial: MaterialQuery | null = null;
  let bestScore = 0;
  materials.forEach((material) => {
    const score = similarity(supplierName, material.name);
    if (score > bestScore) {
      bestMaterial = material;
      bestScore = score;
    }
  });
  if (!bestMaterial) return null;
  // Umbral dinámico: exige más cuando el nombre es muy corto (ambiguo).
  const dynamicThreshold = Math.min(0.8, threshold + Math.max(0, (18 - supplierName.length) * 0.01));
  return bestScore >= Math.max(threshold, dynamicThreshold)
    ? { material: bestMaterial, score: bestScore }
    : null;
}

// ── Unidades ─────────────────────────────────────────────────────────────────

export type CanonicalUnit = 'm2' | 'm' | 'u' | 'kg' | 'caja' | 'rollo' | 'balde' | 'tira' | 'placa' | 'l' | 'desconocido';

const UNIT_SYNONYMS: Record<string, CanonicalUnit> = {
  m2: 'm2',
  'm\u00b2': 'm2',
  sqm: 'm2',
  'metro cuadrado': 'm2',
  'metros cuadrados': 'm2',
  sqft: 'm2',
  m: 'm',
  metro: 'm',
  'metro lineal': 'm',
  ml: 'm',
  ml_: 'm',
  u: 'u',
  ud: 'u',
  und: 'u',
  unidad: 'u',
  unidades: 'u',
  pza: 'u',
  pieza: 'u',
  piezas: 'u',
  kg: 'kg',
  kilo: 'kg',
  kilos: 'kg',
  kilogramo: 'kg',
  caja: 'caja',
  cajas: 'caja',
  box: 'caja',
  paquete: 'caja',
  pack: 'caja',
  rollo: 'rollo',
  rollos: 'rollo',
  roll: 'rollo',
  balde: 'balde',
  cubo: 'balde',
  bucket: 'balde',
  tira: 'tira',
  barra: 'tira',
  perfil: 'tira',
  plancha: 'placa',
  placa: 'placa',
  lamina: 'placa',
  sheet: 'placa',
  l: 'l',
  litro: 'l',
  litros: 'l'
};

export function parseUnit(value?: string): CanonicalUnit {
  if (!value) return 'desconocido';
  const key = String(value)
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    // Superíndices de uso habitual en tarifas: m², m³, m2.
    .replace(/\u00b2/g, '2')
    .replace(/\u00b3/g, '3')
    .replace(/[^a-z0-9]+/g, '')
    .trim();
  if (!key) return 'desconocido';
  if (UNIT_SYNONYMS[key]) return UNIT_SYNONYMS[key];

  // "metroslineales", "precioporm2", "unidadcaja"…: se busca la coincidencia
  // más larga entre las claves conocidas.
  let best: CanonicalUnit = 'desconocido';
  let bestLength = 0;
  Object.keys(UNIT_SYNONYMS)
    .filter((k) => k.length > 1)
    .forEach((candidate) => {
      if (key.includes(candidate) && candidate.length > bestLength) {
        best = UNIT_SYNONYMS[candidate];
        bestLength = candidate.length;
      }
    });
  return best;
}

/**
 * Convierte el precio publicado por el proveedor al precio del embalaje
 * comercial que usa el presupuesto.
 *
 * Ejemplos:
 *  · Placa cotizada por m² a 4.20 ⇒ 4.20 × 2.88 m² = 12.10 por plancha.
 *  · Perfil cotizado por metro a 1.35 ⇒ 1.35 × 3 m = 4.05 por tira.
 *  · Tornillo cotizado por unidad a 0.018 ⇒ 0.018 × 1000 = 18.00 por caja.
 *
 * @param packageSize unidades de la unidad canónica que caben en el embalaje
 *                    (2.88 m² por plancha, 3 m por tira, 1000 u por caja…).
 * @param packageUnit unidad canónica del embalaje (m2, m, u, kg…).
 */
export function convertToPackage(
  price: number,
  supplierUnit: CanonicalUnit | string,
  packageSize: number,
  packageContentUnit: CanonicalUnit | string = 'u'
): { price: number; converted: boolean } {
  const from = typeof supplierUnit === 'string' ? parseUnit(supplierUnit) : supplierUnit;
  const content = typeof packageContentUnit === 'string' ? parseUnit(packageContentUnit) : packageContentUnit;
  if (!Number.isFinite(price) || price < 0) return { price: 0, converted: false };
  // Sin unidad reconocida se asume que el proveedor ya publica el precio del
  // embalaje comercial (plancha, caja, rollo, tira…).
  if (from === 'desconocido') return { price, converted: false };
  // El proveedor cotiza por la unidad base del embalaje (m², m, kg, unidad):
  // se multiplica por el contenido del embalaje.
  if (from === content && Number.isFinite(packageSize) && packageSize > 0 && packageSize !== 1) {
    return { price: price * packageSize, converted: true };
  }
  return { price, converted: false };
}

/** Deduce el embalaje comercial a partir de la categoría y el nombre. */
export interface PackageSpec {
  /** Cuántas unidades de `content` contiene un embalaje comercial. */
  size: number;
  /** Unidad base que se usa para cotizar dentro del embalaje. */
  content: CanonicalUnit;
  /** Unidad comercial de compra (plancha, caja, tira, rollo, balde…). */
  unit: CanonicalUnit;
}

/** Deduce el embalaje comercial a partir de la categoría y el nombre. */
export function inferPackage(material: MaterialQuery): PackageSpec {
  const category = material.category;
  const name = normalizeText(material.name);
  const packSize = material.packageSize > 0 ? material.packageSize : 0;

  // La categoría manda: un "Tornillo T2 para Placa" es ferretería, no placa.
  switch (category) {
    case 'boards':
      return { size: packSize || 2.88, content: 'm2', unit: 'placa' };
    case 'profiles':
      return { size: packSize || 3, content: 'm', unit: 'tira' };
    case 'fasteners':
      return { size: packSize || 1000, content: 'u', unit: 'caja' };
    case 'compounds':
      return { size: packSize || 28, content: 'kg', unit: 'balde' };
    case 'tapes':
      return { size: packSize || 150, content: 'm', unit: 'rollo' };
    case 'insulation':
      return { size: packSize || 1, content: 'm2', unit: 'm2' };
    default:
      break;
  }

  // Sin categoría fiable: se deduce por el nombre.
  if (/placa/.test(name)) return { size: packSize || 2.88, content: 'm2', unit: 'placa' };
  if (/perfil|omega|canal/.test(name)) return { size: packSize || 3, content: 'm', unit: 'tira' };
  if (/tornillo|fijacion/.test(name)) return { size: packSize || 1000, content: 'u', unit: 'caja' };
  if (/masilla/.test(name)) return { size: packSize || 28, content: 'kg', unit: 'balde' };
  if (/cinta/.test(name)) return { size: packSize || 150, content: 'm', unit: 'rollo' };
  return { size: packSize || 1, content: 'u', unit: 'u' };
}

// ── Moneda ───────────────────────────────────────────────────────────────────

/** Convierte entre divisas usando las tasas de referencia del proyecto. */
export function convertCurrency(amount: number, from: string, to: string): number {
  if (!Number.isFinite(amount)) return 0;
  if (from === to) return amount;
  const fromRate = CURRENCY_SYMBOLS[from]?.rateToUSD;
  const toRate = CURRENCY_SYMBOLS[to]?.rateToUSD;
  if (!fromRate || !toRate) return amount;
  const usd = amount / fromRate;
  return usd * toRate;
}

/** Parsea precios con formatos locales: "1.234,56", "$ 1,234.56", "12,5". */
export function parsePrice(value: unknown): number {
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0;
  if (typeof value !== 'string') return 0;
  let txt = value.trim().replace(/[^\d.,\-]/g, '');
  if (!txt) return 0;
  const lastComma = txt.lastIndexOf(',');
  const lastDot = txt.lastIndexOf('.');
  if (lastComma > lastDot) {
    // Formato europeo: 1.234,56
    txt = txt.replace(/\./g, '').replace(',', '.');
  } else {
    // Formato anglosajón: 1,234.56
    txt = txt.replace(/,/g, '');
  }
  const n = Number.parseFloat(txt);
  return Number.isFinite(n) ? n : 0;
}

/** Extrae un valor anidado mediante una ruta tipo `data.items[0].price`. */
export function getByPath(source: unknown, path: string): unknown {
  if (!path) return source;
  const segments = path
    .replace(/\[(\d+)\]/g, '.$1')
    .split('.')
    .map((s) => s.trim())
    .filter(Boolean);
  let current: unknown = source;
  for (const segment of segments) {
    if (current === null || current === undefined) return undefined;
    if (Array.isArray(current)) {
      const index = Number.parseInt(segment, 10);
      if (Number.isNaN(index)) return undefined;
      current = current[index];
    } else if (typeof current === 'object') {
      current = (current as Record<string, unknown>)[segment];
    } else {
      return undefined;
    }
  }
  return current;
}

/** Localiza el primer array dentro de una respuesta JSON desconocida. */
export function findFirstArray(source: unknown, maxDepth = 5): unknown[] | null {
  if (Array.isArray(source)) return source;
  if (!source || typeof source !== 'object' || maxDepth <= 0) return null;
  const record = source as Record<string, unknown>;
  for (const key of Object.keys(record)) {
    const value = record[key];
    if (Array.isArray(value) && value.length) return value;
  }
  for (const key of Object.keys(record)) {
    const found = findFirstArray(record[key], maxDepth - 1);
    if (found) return found;
  }
  return null;
}
