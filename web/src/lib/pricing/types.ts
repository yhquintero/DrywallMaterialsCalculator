/**
 * Modelo de datos del módulo de precios en tiempo real.
 *
 * Diseño: el navegador NUNCA guarda credenciales de distribuidor. Los secretos
 * viven en el backend (`server/src/routes/pricing.js`) y se inyectan en el
 * proxy mediante `secretRef`. Además, el proxy valida el host contra una lista
 * blanca y bloquea rangos privados (protección SSRF).
 */

export type SupplierKind = 'rest-json' | 'csv' | 'mock' | 'manual';

export type AuthMode = 'none' | 'header' | 'query' | 'server-proxy';

export interface SupplierAuth {
  mode: AuthMode;
  /** Nombre de la cabecera (modo `header`). */
  headerName?: string;
  /** Parámetro de consulta (modo `query`). */
  queryParam?: string;
  /** Identificador del secreto guardado en el servidor (modo `server-proxy`). */
  secretRef?: string;
  /** Valor literal: sólo para APIs públicas sin secreto. */
  value?: string;
}

export interface FieldMap {
  sku?: string;
  name?: string;
  price?: string;
  currency?: string;
  unit?: string;
  stock?: string;
  brand?: string;
  updatedAt?: string;
  url?: string;
}

export interface CsvOptions {
  delimiter: string;
  /** Fila (1-based) donde empiezan los datos. */
  headerRow: number;
  skuColumn?: string;
  nameColumn?: string;
  priceColumn: string;
  currencyColumn?: string;
  unitColumn?: string;
  stockColumn?: string;
}

export interface SupplierConfig {
  id: string;
  name: string;
  kind: SupplierKind;
  enabled: boolean;
  /** Moneda en la que publica precios el proveedor. */
  currency: string;
  country?: string;
  city?: string;
  /** Endpoint https del catálogo. */
  endpoint?: string;
  method: 'GET' | 'POST';
  /** Plantilla de URL con marcadores {query}, {sku}, {page}. */
  urlTemplate?: string;
  /** Plantilla de cuerpo (JSON) con los mismos marcadores. */
  bodyTemplate?: string;
  headers?: Record<string, string>;
  auth?: SupplierAuth;
  fieldMap?: FieldMap;
  /** Ruta al array de resultados, p. ej. `data.items` o `result[0].rows`. */
  responsePath?: string;
  csvOptions?: CsvOptions;
  timeoutMs: number;
  ttlMinutes: number;
  /** Descuento/recargo negociado (%). -8 ⇒ 8 % de descuento. */
  priceAdjustmentPct: number;
  /** Umbral mínimo de similitud (0–1) para aceptar una coincidencia. */
  matchThreshold: number;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** Fila cruda devuelta por un proveedor, antes de normalizar. */
export interface RawQuote {
  sku?: string;
  name: string;
  price: number;
  currency?: string;
  unit?: string;
  stock?: number | null;
  brand?: string;
  updatedAt?: string;
  url?: string;
}

export interface PriceQuote {
  materialName: string;
  supplierId: string;
  supplierName: string;
  sku?: string;
  matchedName: string;
  /** Precio ya convertido a la moneda del proyecto y al embalaje comercial. */
  price: number;
  /** Precio original publicado por el proveedor. */
  rawPrice: number;
  currency: string;
  unit?: string;
  stock?: number | null;
  fetchedAt: string;
  /** Calidad de la coincidencia por nombre (0 – 1). */
  confidence: number;
  source: 'live' | 'cache' | 'mock' | 'manual';
  brand?: string;
  url?: string;
}

export interface MaterialQuery {
  name: string;
  category: string;
  unit: string;
  /** Precio actual en el presupuesto (moneda del proyecto). */
  currentPrice: number;
  /** Unidades por embalaje comercial (1 plancha, 1000 tornillos, 28 kg…). */
  packageSize: number;
  packageName?: string;
}

export type SyncItemStatus = 'updated' | 'unchanged' | 'not-found' | 'error' | 'cached';

export interface SyncItem {
  materialName: string;
  category: string;
  currentPrice: number;
  quotes: PriceQuote[];
  best: PriceQuote | null;
  previousPrice: number;
  delta: number;
  deltaPct: number;
  status: SyncItemStatus;
  error?: string;
}

export interface SyncError {
  supplierId: string;
  supplierName: string;
  message: string;
  retriable: boolean;
}

export interface SyncReport {
  startedAt: string;
  finishedAt: string;
  durationMs: number;
  currency: string;
  suppliers: Array<{ id: string; name: string; kind: SupplierKind; quotes: number; status: 'ok' | 'error' | 'cached' | 'disabled' }>;
  items: SyncItem[];
  totals: {
    materials: number;
    matched: number;
    updated: number;
    notFound: number;
    errors: number;
    /** Ahorro potencial si se aplican los mejores precios (puede ser negativo). */
    potentialSavings: number;
  };
  errors: SyncError[];
}

/** Transporte que ejecuta la petición HTTP del proveedor. */
export interface FetchTransport {
  (request: {
    url: string;
    method: string;
    headers: Record<string, string>;
    body?: string;
    supplier: SupplierConfig;
    timeoutMs: number;
  }): Promise<{ status: number; text: string; fromCache?: boolean }>;
}

export interface ProviderContext {
  supplier: SupplierConfig;
  transport: FetchTransport;
  /** Consultas que se quieren resolver. */
  queries: MaterialQuery[];
  signal?: AbortSignal;
}

export interface SupplierProvider {
  kind: SupplierKind;
  /** Descarga las filas crudas del catálogo del proveedor. */
  fetchQuotes(ctx: ProviderContext): Promise<RawQuote[]>;
}
