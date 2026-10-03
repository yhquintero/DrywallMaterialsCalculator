/**
 * Proveedor simulado (demo / entorno sin credenciales).
 *
 * Genera precios deterministas a partir del nombre del material y del día en
 * curso: sirve para demostrar y probar el flujo de sincronización (incluidos
 * reintentos, latencia y fallos) sin depender de terceros. Es el que se
 * activa por defecto la primera vez que se abre el módulo.
 */

import { RawQuote, SupplierProvider, SupplierConfig } from '../types';

/** Hash estable (FNV-1a de 32 bits) para generar variación determinista. */
function hash(text: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i += 1) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

/** Precio base aproximado del mercado (USD) por familia de material. */
function basePrice(name: string): number {
  const n = name.toLowerCase();
  if (/placa/.test(n)) return /rh|verde|humedad/.test(n) ? 16.5 : /rf|rosa|fuego/.test(n) ? 22.0 : 12.5;
  if (/perfil|canal|omega|montante|parante|solera|pgc|pgu/.test(n)) return 4.1;
  if (/tornillo/.test(n)) return 18.0;
  if (/masilla|compuesto/.test(n)) return 24.0;
  if (/cinta/.test(n)) return 12.0;
  if (/lana|aislan/.test(n)) return 32.0;
  if (/fijacion|anclaje|clavo/.test(n)) return 25.0;
  if (/angular|esquinero/.test(n)) return 2.4;
  return 15.0;
}

export interface MockProviderOptions {
  /** Probabilidad de fallo simulado (0 – 1) para probar la tolerancia a errores. */
  failureRate?: number;
  /** Latencia simulada en ms. */
  latencyMs?: number;
  /** Amplitud de la variación de precio (%). */
  volatilityPct?: number;
}

export function createMockProvider(options: MockProviderOptions = {}): SupplierProvider {
  const failureRate = options.failureRate ?? 0;
  const latencyMs = options.latencyMs ?? 0;
  const volatilityPct = options.volatilityPct ?? 12;

  return {
    kind: 'mock',
    async fetchQuotes({ supplier, queries }): Promise<RawQuote[]> {
      if (latencyMs) await new Promise((resolve) => setTimeout(resolve, latencyMs));
      if (failureRate > 0) {
        const roll = (hash(`${supplier.id}:${new Date().toISOString().slice(0, 13)}`) % 1000) / 1000;
        if (roll < failureRate) {
          throw new Error('El catálogo del proveedor no responde (simulado). Reintenta en unos minutos.');
        }
      }

      const daySeed = Math.floor(Date.now() / 86_400_000);
      return queries.map((query) => {
        const seed = hash(`${supplier.id}|${query.name}|${daySeed}`);
        // Variación pseudoaleatoria determinista en ±volatilidad/2.
        const jitter = ((seed % 1000) / 1000 - 0.5) * volatilityPct;
        const base = basePrice(query.name) * (1 + jitter / 100);
        const price = Math.max(0.01, Math.round(base * 100) / 100);
        return {
          sku: `SKU-${(seed % 900000 + 100000).toString()}`,
          name: query.name,
          price,
          currency: supplier.currency || 'USD',
          unit: /placa/.test(query.name.toLowerCase()) ? 'placa' : 'u',
          stock: seed % 7 === 0 ? 0 : (seed % 400) + 12,
          brand: 'Proveedor demo',
          updatedAt: new Date().toISOString()
        } as RawQuote;
      });
    }
  };
}

export const mockProvider: SupplierProvider = createMockProvider();
