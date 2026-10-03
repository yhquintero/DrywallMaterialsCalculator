/**
 * Cliente de alto nivel del módulo de precios: gestiona la configuración de
 * proveedores (persistida en localStorage), lanza sincronizaciones y expone
 * un canal de eventos para que la interfaz se suscriba.
 */

import { CalculationSummary, ProjectConfig } from '../../types';
import { MaterialQuery, RawQuote, SupplierConfig, SyncReport } from './types';
import { createDirectTransport, createProxyTransport, reportFromRows, syncPrices } from './engine';
import { cacheStats, clearCache } from './cache';
import { csvToObjects } from './csv';
import { parsePrice } from './normalize';
import { createMockProvider } from './providers/mock';
import { validateEndpoint } from './engine';

const SUPPLIERS_KEY = 'drywall_suppliers_v1';
const SETTINGS_KEY = 'drywall_pricing_settings_v1';

export interface PricingSettings {
  /** Usar el proxy del backend (recomendado: oculta claves y evita CORS). */
  useServerProxy: boolean;
  /** Buscar automáticamente al abrir el módulo. */
  autoSync: boolean;
  /** Aplicar precios automáticamente cuando la diferencia supera el umbral. */
  autoApply: boolean;
  autoApplyThresholdPct: number;
  /** Política de stock: descartar ofertas sin existencias. */
  ignoreOutOfStock: boolean;
  retries: number;
  /** Proveedor preferido (empate de precio). */
  preferredSupplierId?: string;
}

export const DEFAULT_PRICING_SETTINGS: PricingSettings = {
  useServerProxy: true,
  autoSync: false,
  autoApply: false,
  autoApplyThresholdPct: 2,
  ignoreOutOfStock: false,
  retries: 1
};

export const DEFAULT_SUPPLIERS: SupplierConfig[] = [
  {
    id: 'demo-nacional',
    name: 'Distribuidor Demo Nacional',
    kind: 'mock',
    enabled: true,
    currency: 'USD',
    country: 'ES',
    method: 'GET',
    timeoutMs: 8000,
    ttlMinutes: 60,
    priceAdjustmentPct: 0,
    matchThreshold: 0.35,
    notes: 'Catálogo de demostración. Sustitúyelo por tu distribuidor real desde «Añadir proveedor».',
    createdAt: new Date().toISOString()
  }
];

/** Plantillas de proveedor listas para rellenar con el endpoint real. */
export const SUPPLIER_TEMPLATES: Array<{
  id: string;
  title: string;
  description: string;
  config: Partial<SupplierConfig>;
}> = [
  {
    id: 'tpl-rest-search',
    title: 'API REST con búsqueda',
    description: 'Portal B2B con endpoint de búsqueda tipo /api/products?q={query} y respuesta JSON.',
    config: {
      kind: 'rest-json',
      method: 'GET',
      urlTemplate: '/api/products?q={query}&limit={limit}',
      responsePath: 'data.items',
      fieldMap: { sku: 'sku', name: 'name', price: 'price', currency: 'currency', unit: 'unit', stock: 'stock' },
      auth: { mode: 'server-proxy', headerName: 'X-API-Key', secretRef: 'SUPPLIER_API_KEY' },
      matchThreshold: 0.4
    }
  },
  {
    id: 'tpl-rest-post',
    title: 'API REST con POST (GraphQL/RPC)',
    description: 'Proveedor que exige enviar un cuerpo JSON con la consulta.',
    config: {
      kind: 'rest-json',
      method: 'POST',
      endpoint: 'https://api.distribuidor.com/v2/catalog/search',
      bodyTemplate: '{"q":"{query}","page":1,"perPage":50}',
      responsePath: 'result.products',
      fieldMap: { sku: 'reference', name: 'description', price: 'unitPrice', currency: 'currencyCode', stock: 'availability' },
      auth: { mode: 'server-proxy', headerName: 'Authorization', secretRef: 'SUPPLIER_TOKEN' },
      matchThreshold: 0.4
    }
  },
  {
    id: 'tpl-csv',
    title: 'Lista de precios CSV/TSV',
    description: 'Tarifa publicada como fichero CSV (también sirve para intranet o Drive corporativo).',
    config: {
      kind: 'csv',
      method: 'GET',
      endpoint: 'https://distribuidor.com/tarifas/pladur-2026.csv',
      csvOptions: { delimiter: ';', headerRow: 1, priceColumn: 'Precio', nameColumn: 'Descripcion', skuColumn: 'Referencia' },
      matchThreshold: 0.38
    }
  },
  {
    id: 'tpl-a3',
    title: 'Plataforma de compras A3/ERP',
    description: 'Endpoint interno del ERP del cliente expuesto por HTTPS con token de servicio.',
    config: {
      kind: 'rest-json',
      method: 'GET',
      urlTemplate: '/api/articulos?filtro={query}',
      responsePath: 'data',
      fieldMap: { sku: 'codigo', name: 'denominacion', price: 'precioVenta', currency: 'moneda', unit: 'unidad', stock: 'stockActual' },
      auth: { mode: 'server-proxy', headerName: 'Authorization', secretRef: 'ERP_SERVICE_TOKEN' },
      matchThreshold: 0.45
    }
  }
];

function readJson<T>(key: string, fallback: T): T {
  if (typeof localStorage === 'undefined') return fallback;
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as T;
    return parsed ?? fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key: string, value: unknown): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* cuota agotada */
  }
}

// ── Configuración ────────────────────────────────────────────────────────────

export function loadSuppliers(): SupplierConfig[] {
  const stored = readJson<SupplierConfig[]>(SUPPLIERS_KEY, DEFAULT_SUPPLIERS);
  return Array.isArray(stored) && stored.length ? stored : DEFAULT_SUPPLIERS;
}

export function saveSuppliers(suppliers: SupplierConfig[]): void {
  writeJson(SUPPLIERS_KEY, suppliers);
}

export function loadSettings(): PricingSettings {
  return { ...DEFAULT_PRICING_SETTINGS, ...readJson<Partial<PricingSettings>>(SETTINGS_KEY, {}) };
}

export function saveSettings(settings: PricingSettings): void {
  writeJson(SETTINGS_KEY, settings);
}

/** Valida un proveedor antes de guardarlo. */
export function validateSupplier(supplier: SupplierConfig): string[] {
  const errors: string[] = [];
  if (!supplier.name?.trim()) errors.push('El proveedor necesita un nombre.');
  if (!supplier.id?.trim()) errors.push('Falta el identificador interno.');
  if (supplier.kind === 'rest-json' || supplier.kind === 'csv') {
    if (!supplier.endpoint && !supplier.urlTemplate) errors.push('Indica el endpoint del catálogo.');
    const target = supplier.endpoint || supplier.urlTemplate || '';
    if (/^https?:/i.test(target)) {
      const check = validateEndpoint(target);
      if (!check.ok) errors.push(check.error ?? 'Endpoint no válido.');
    }
  }
  if (supplier.kind === 'csv' && !supplier.csvOptions?.priceColumn && !supplier.csvOptions?.nameColumn) {
    // No es bloqueante: el detector automático de columnas puede resolverlo.
  }
  if (supplier.timeoutMs < 1000 || supplier.timeoutMs > 60_000) {
    errors.push('El tiempo de espera debe estar entre 1 y 60 segundos.');
  }
  if (supplier.ttlMinutes < 0 || supplier.ttlMinutes > 7 * 24 * 60) {
    errors.push('La caducidad de la caché debe estar entre 0 y 10.080 minutos (7 días).');
  }
  if (supplier.matchThreshold < 0 || supplier.matchThreshold > 1) {
    errors.push('El umbral de coincidencia debe estar entre 0 y 1.');
  }
  return errors;
}

// ── Métricas del proyecto → consultas ────────────────────────────────────────

/**
 * Extrae el tamaño del embalaje comercial a partir del texto del formato
 * ("Caja 1,000 u.", "Balde 28 kg", "Rollo 150 m", "Tira de 3.00 m").
 * Los valores se usan para convertir precios cotizados por unidad base.
 */
export function packageSizeFromFormat(format: string, category: string, fallback: number): number {
  const text = String(format || '');
  const numbers = (text.match(/\d+(?:[.,]\d+)*/g) ?? [])
    .map((raw) => {
      // "1,000" y "1.000" son miles; "3.00" y "2,88" son decimales.
      if (/^\d{1,3}(?:[.,]\d{3})+$/.test(raw)) return Number(raw.replace(/[.,]/g, ''));
      return Number(raw.replace(',', '.'));
    })
    .filter((n) => Number.isFinite(n) && n > 0);
  if (!numbers.length) return fallback;

  switch (category) {
    case 'fasteners':
    case 'compounds':
    case 'tapes':
      // En estos formatos el tamaño del paquete es el número mayor
      // (1.000 tornillos, 28 kg, 150 m).
      return Math.max(...numbers);
    case 'profiles': {
      // "Tira de 3.00 m" → longitud de la tira.
      const meters = /(\d+(?:[.,]\d+)?)\s*m\b/i.exec(text);
      if (meters) return Number(meters[1].replace(',', '.'));
      return numbers[0];
    }
    case 'boards': {
      // Si el formato declara la superficie de la placa ("(2.88 m²)") se usa
      // esa; si no, la del proyecto (ancho × largo configurados).
      const area = /(\d+(?:[.,]\d+)?)\s*m(?:²|2)\b/i.exec(text);
      if (area) return Number(area[1].replace(',', '.'));
      return fallback;
    }
    default:
      return numbers[0];
  }
}

/** Convierte el cómputo del proyecto en consultas de precio para los catálogos. */
export function buildMaterialQueries(
  summary: CalculationSummary,
  config: ProjectConfig
): MaterialQuery[] {
  const sheetArea = Math.max(0.01, config.sheetWidth * config.sheetLength);
  return summary.requirements.map((req) => {
    const fallback =
      req.category === 'boards'
        ? sheetArea
        : req.category === 'profiles'
          ? Math.max(1, config.profileLength)
          : 1;
    const packageSize =
      req.category === 'boards'
        ? sheetArea
        : packageSizeFromFormat(req.commercialFormat, req.category, fallback);
    return {
      name: req.name,
      category: req.category,
      unit: req.unit,
      currentPrice: req.unitPrice,
      packageSize,
      packageName: req.commercialFormat
    };
  });
}

// ── Cliente ──────────────────────────────────────────────────────────────────

export type PricingEvent =
  | { type: 'progress'; supplier: string; step: number; total: number; message: string }
  | { type: 'report'; report: SyncReport }
  | { type: 'error'; message: string }
  | { type: 'suppliers'; suppliers: SupplierConfig[] };

export class PricingClient {
  private settings: PricingSettings;
  private suppliers: SupplierConfig[];
  private listeners = new Set<(event: PricingEvent) => void>();
  private running = false;

  constructor() {
    this.settings = loadSettings();
    this.suppliers = loadSuppliers();
  }

  getSettings(): PricingSettings {
    return { ...this.settings };
  }

  getSuppliers(): SupplierConfig[] {
    return this.suppliers.map((s) => ({ ...s }));
  }

  updateSettings(patch: Partial<PricingSettings>): PricingSettings {
    this.settings = { ...this.settings, ...patch };
    saveSettings(this.settings);
    return this.getSettings();
  }

  upsertSupplier(supplier: SupplierConfig): SupplierConfig {
    const now = new Date().toISOString();
    const next: SupplierConfig = {
      ...supplier,
      createdAt: supplier.createdAt ?? now,
      updatedAt: now
    };
    const index = this.suppliers.findIndex((s) => s.id === supplier.id);
    if (index >= 0) this.suppliers[index] = next;
    else this.suppliers.push(next);
    saveSuppliers(this.suppliers);
    this.emit({ type: 'suppliers', suppliers: this.getSuppliers() });
    return next;
  }

  removeSupplier(id: string): void {
    this.suppliers = this.suppliers.filter((s) => s.id !== id);
    saveSuppliers(this.suppliers);
    clearCache(id);
    this.emit({ type: 'suppliers', suppliers: this.getSuppliers() });
  }

  subscribe(listener: (event: PricingEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(event: PricingEvent): void {
    this.listeners.forEach((listener) => listener(event));
  }

  /** Sincroniza los precios del proyecto con los proveedores habilitados. */
  async sync(
    materials: MaterialQuery[],
    currency: string,
    options: { force?: boolean } = {}
  ): Promise<SyncReport> {
    if (this.running) throw new Error('Ya hay una sincronización en curso.');
    this.running = true;
    try {
      const report = await syncPrices({
        suppliers: this.suppliers,
        materials,
        currency,
        force: options.force ?? false,
        retries: this.settings.retries,
        transport: this.settings.useServerProxy ? createProxyTransport() : createDirectTransport(),
        onProgress: (progress) => this.emit({ type: 'progress', ...progress })
      });
      this.emit({ type: 'report', report });
      return report;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Error de sincronización';
      this.emit({ type: 'error', message });
      throw error;
    } finally {
      this.running = false;
    }
  }

  /** Prueba la conexión con un proveedor descargando las primeras filas. */
  async testSupplier(
    supplier: SupplierConfig,
    materials: MaterialQuery[],
    currency: string
  ): Promise<{ ok: boolean; quotes: number; message: string }> {
    if (supplier.kind === 'mock') {
      const provider = createMockProvider({ latencyMs: 120 });
      const raw = await provider.fetchQuotes({
        supplier,
        transport: createProxyTransport(),
        queries: materials.slice(0, 5)
      });
      return { ok: true, quotes: raw.length, message: `Proveedor simulado OK: ${raw.length} artículos de prueba.` };
    }

    const check = validateEndpoint(supplier.endpoint ?? supplier.urlTemplate ?? '');
    if (!check.ok) return { ok: false, quotes: 0, message: check.error ?? 'Endpoint no válido.' };

    try {
      const report = await syncPrices({
        suppliers: [{ ...supplier, ttlMinutes: 0 }],
        materials: materials.slice(0, 5),
        currency,
        force: true,
        retries: 0,
        transport: this.settings.useServerProxy ? createProxyTransport() : createDirectTransport()
      });
      const quotes = report.suppliers[0]?.quotes ?? 0;
      const ok = report.totals.errors === 0;
      return {
        ok,
        quotes,
        message: ok
          ? `Conexión correcta: ${quotes} artículos emparejados de ${materials.slice(0, 5).length} consultados.`
          : report.errors[0]?.message ?? 'El proveedor no respondió.'
      };
    } catch (error) {
      return { ok: false, quotes: 0, message: error instanceof Error ? error.message : 'Error desconocido' };
    }
  }

  /** Importa una lista de precios pegada o subida como CSV. */
  importCsv(
    supplier: SupplierConfig,
    content: string,
    materials: MaterialQuery[],
    currency: string
  ): SyncReport {
    const delimiter = supplier.csvOptions?.delimiter || detectDelimiter(content);
    const rows = csvToObjects(content, delimiter);
    const nameKey = rows.length
      ? Object.keys(rows[0]).find((k) => /nombre|descripcion|producto|articulo|name/i.test(k)) ?? Object.keys(rows[0])[0]
      : '';
    const priceKey = rows.length
      ? Object.keys(rows[0]).find((k) => /precio|price|pvp|importe/i.test(k)) ?? Object.keys(rows[0])[1]
      : '';
    const skuKey = rows.length ? Object.keys(rows[0]).find((k) => /sku|referencia|codigo|ref/i.test(k)) : undefined;

    const parsed: RawQuote[] = rows
      .map((row) => ({
        name: String(row[nameKey] ?? '').trim(),
        price: parsePrice(row[priceKey]),
        sku: skuKey ? row[skuKey] : undefined,
        currency: supplier.currency
      }))
      .filter((r) => r.name && r.price > 0);

    return reportFromRows(parsed, supplier, materials, currency);
  }

  stats(): { cache: ReturnType<typeof cacheStats>; suppliers: number; enabled: number } {
    return {
      cache: cacheStats(),
      suppliers: this.suppliers.length,
      enabled: this.suppliers.filter((s) => s.enabled).length
    };
  }
}

function detectDelimiter(text: string): string {
  const sample = text.split(/\r?\n/).slice(0, 5).join('\n');
  let best = ',';
  let bestCount = 0;
  [';', ',', '\t', '|'].forEach((c) => {
    const count = sample.split(c).length - 1;
    if (count > bestCount) {
      bestCount = count;
      best = c;
    }
  });
  return best;
}
