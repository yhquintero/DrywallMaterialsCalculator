/**
 * Caché de precios con TTL en `localStorage`.
 *
 * Aplica la política *stale-while-revalidate*: si el proveedor falla, se
 * devuelven los últimos precios conocidos marcados como `cache` para que el
 * presupuesto siga siendo válido sin conexión.
 */

import { PriceQuote } from './types';

const CACHE_KEY = 'drywall_price_cache_v1';
const CACHE_VERSION = 1;

interface CacheEnvelope {
  version: number;
  updatedAt: string;
  entries: Record<string, PriceQuote[]>;
}

function emptyEnvelope(): CacheEnvelope {
  return { version: CACHE_VERSION, updatedAt: new Date(0).toISOString(), entries: {} };
}

/** Almacén en memoria: usado en tests, SSR o con `localStorage` bloqueado. */
const memoryStore = new Map<string, string>();

function storageAvailable(): boolean {
  return typeof localStorage !== 'undefined' && localStorage !== null;
}

function readRaw(): string | null {
  if (!storageAvailable()) return memoryStore.get(CACHE_KEY) ?? null;
  try {
    return localStorage.getItem(CACHE_KEY);
  } catch {
    return memoryStore.get(CACHE_KEY) ?? null;
  }
}

function writeRaw(value: string): void {
  memoryStore.set(CACHE_KEY, value);
  if (!storageAvailable()) return;
  try {
    localStorage.setItem(CACHE_KEY, value);
  } catch {
    /* cuota agotada: queda en memoria */
  }
}

function removeRaw(): void {
  memoryStore.delete(CACHE_KEY);
  if (!storageAvailable()) return;
  try {
    localStorage.removeItem(CACHE_KEY);
  } catch {
    /* ignore */
  }
}

function read(): CacheEnvelope {
  try {
    const raw = readRaw();
    if (!raw) return emptyEnvelope();
    const parsed = JSON.parse(raw) as CacheEnvelope;
    if (!parsed || parsed.version !== CACHE_VERSION || typeof parsed.entries !== 'object') return emptyEnvelope();
    return parsed;
  } catch {
    return emptyEnvelope();
  }
}

function write(envelope: CacheEnvelope): void {
  // Cuota agotada: se conserva sólo la mitad más reciente de las entradas.
  const trimmed: CacheEnvelope = { ...envelope, entries: {} };
  const keys = Object.keys(envelope.entries);
  keys.slice(0, Math.ceil(keys.length / 2)).forEach((k) => {
    trimmed.entries[k] = envelope.entries[k];
  });
  try {
    writeRaw(JSON.stringify(envelope));
  } catch {
    try {
      writeRaw(JSON.stringify(trimmed));
    } catch {
      /* se renuncia a cachear en disco */
    }
  }
}

export interface CacheReadResult {
  quotes: PriceQuote[];
  ageMinutes: number;
  fresh: boolean;
}

/** Lee las cotizaciones cacheadas de un proveedor. */
export function readCache(supplierId: string, ttlMinutes: number): CacheReadResult {
  const envelope = read();
  const stored = envelope.entries[supplierId] ?? [];
  const ageMinutes = (Date.now() - new Date(envelope.updatedAt).getTime()) / 60000;
  return {
    quotes: stored.map((q) => ({ ...q, source: 'cache' as const })),
    ageMinutes: Math.max(0, ageMinutes),
    fresh: stored.length > 0 && ageMinutes < ttlMinutes
  };
}

export function writeCache(supplierId: string, quotes: PriceQuote[]): void {
  if (!quotes.length) return;
  const envelope = read();
  envelope.entries[supplierId] = quotes;
  envelope.updatedAt = new Date().toISOString();
  write(envelope);
}

export function clearCache(supplierId?: string): void {
  if (!supplierId) {
    removeRaw();
    return;
  }
  const envelope = read();
  delete envelope.entries[supplierId];
  envelope.updatedAt = new Date().toISOString();
  write(envelope);
}

export interface CacheStats {
  suppliers: number;
  quotes: number;
  updatedAt: string | null;
  bytes: number;
}

export function cacheStats(): CacheStats {
  const envelope = read();
  const quotes = Object.values(envelope.entries).reduce((sum, list) => sum + list.length, 0);
  let bytes = 0;
  try {
    bytes = (readRaw() ?? '').length;
  } catch {
    bytes = 0;
  }
  return {
    suppliers: Object.keys(envelope.entries).length,
    quotes,
    updatedAt: envelope.updatedAt === new Date(0).toISOString() ? null : envelope.updatedAt,
    bytes
  };
}
