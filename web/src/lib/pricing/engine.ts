/**
 * Motor de sincronización de precios.
 *
 * Orquesta: transporte HTTP (proxy del backend o directo), proveedores,
 * normalización/emparejamiento, conversión de moneda y de embalaje, caché y
 * generación del informe de sincronización que consume la interfaz.
 */

import {
  FetchTransport,
  MaterialQuery,
  PriceQuote,
  ProviderContext,
  RawQuote,
  SupplierConfig,
  SupplierProvider,
  SyncError,
  SyncItem,
  SyncReport
} from './types';
import { convertCurrency, convertToPackage, inferPackage, matchMaterial, normalizeText, parseUnit } from './normalize';
import { restJsonProvider } from './providers/rest';
import { csvProvider } from './providers/csv';
import { createMockProvider, mockProvider } from './providers/mock';
import { readCache, writeCache } from './cache';

// ── Seguridad de red ─────────────────────────────────────────────────────────

/** Rangos privados/no enrutables que un catálogo público nunca debería usar. */
const BLOCKED_HOST_PATTERNS = [
  /^localhost$/i,
  /^127\./,
  /^10\./,
  /^192\.168\./,
  /^172\.(1[6-9]|2\d|3[01])\./,
  /^169\.254\./,
  /^0\./,
  /^\[?::1\]?$/,
  /^\[?fc00:/i,
  /^\[?fe80:/i,
  /\.local$/i,
  /^metadata\./i,
  /^169\.254\.169\.254$/
];

export interface UrlValidation {
  ok: boolean;
  error?: string;
}

/** Valida que un endpoint sea HTTPS público (protección SSRF básica). */
export function validateEndpoint(url: string): UrlValidation {
  if (!url) return { ok: false, error: 'Falta la URL del catálogo.' };
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return { ok: false, error: 'La URL del catálogo no es válida.' };
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    return { ok: false, error: 'Sólo se admiten URLs http(s).' };
  }
  if (parsed.username || parsed.password) {
    return { ok: false, error: 'No incluyas credenciales en la URL: usa la configuración de autenticación.' };
  }
  if (BLOCKED_HOST_PATTERNS.some((p) => p.test(parsed.hostname))) {
    return { ok: false, error: 'La URL apunta a una dirección interna o local (bloqueado por seguridad).' };
  }
  if (parsed.protocol === 'http:') {
    return { ok: false, error: 'El catálogo debe publicarse por HTTPS.' };
  }
  return { ok: true };
}

// ── Transporte ───────────────────────────────────────────────────────────────

/**
 * Transporte que pasa por el backend: /api/pricing/proxy
 * Ventajas: inyecta los secretos del proveedor sin exponerlos, aplica la lista
 * blanca de hosts, evita CORS y deja traza de auditoría.
 */
export function createProxyTransport(options: { token?: string } = {}): FetchTransport {
  return async ({ url, method, headers, body, timeoutMs, supplier }) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), Math.max(1000, timeoutMs));
    try {
      // El secreto del proveedor NUNCA viaja: sólo se envía el nombre de la
      // variable de entorno (`secretRef`) para que el servidor lo inyecte.
      const auth = supplier?.auth;
      const authPayload =
        auth && auth.mode !== 'none'
          ? {
              secretRef: auth.secretRef,
              authMode:
                auth.mode === 'query' ? 'query' : auth.mode === 'server-proxy' ? 'bearer' : 'header',
              authHeader: auth.headerName,
              authQueryParam: auth.queryParam
            }
          : {};
      const res = await fetch('/api/pricing/proxy', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
        },
        body: JSON.stringify({ url, method, headers, body, ...authPayload }),
        signal: controller.signal
      });
      const text = await res.text();
      return { status: res.status, text };
    } finally {
      clearTimeout(timer);
    }
  };
}

/**
 * Transporte directo (sin backend). Sólo funciona si el distribuidor publica
 * cabeceras CORS; en la práctica se usa para proveedores públicos abiertos.
 */
export function createDirectTransport(): FetchTransport {
  return async ({ url, method, headers, body, timeoutMs }) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), Math.max(1000, timeoutMs));
    try {
      const res = await fetch(url, { method, headers, body, signal: controller.signal, mode: 'cors' });
      const text = await res.text();
      return { status: res.status, text };
    } finally {
      clearTimeout(timer);
    }
  };
}

const PROVIDERS: Record<string, SupplierProvider> = {
  'rest-json': restJsonProvider,
  csv: csvProvider,
  mock: mockProvider,
  manual: mockProvider
};

export function getProvider(kind: SupplierConfig['kind']): SupplierProvider {
  return PROVIDERS[kind] ?? mockProvider;
}

// ── Sincronización ───────────────────────────────────────────────────────────

export interface SyncOptions {
  suppliers: SupplierConfig[];
  materials: MaterialQuery[];
  /** Moneda del proyecto: todos los precios se devuelven convertidos. */
  currency: string;
  transport?: FetchTransport;
  /** Fuerza la descarga ignorando la caché fresca. */
  force?: boolean;
  /** Reintentos por proveedor ante errores transitorios. */
  retries?: number;
  onProgress?: (progress: { supplier: string; step: number; total: number; message: string }) => void;
  now?: () => number;
}

interface SupplierOutcome {
  supplier: SupplierConfig;
  quotes: PriceQuote[];
  status: 'ok' | 'error' | 'cached' | 'disabled';
  error?: string;
}

/** Convierte una fila cruda del proveedor en una cotización normalizada. */
export function normalizeQuote(
  raw: RawQuote,
  supplier: SupplierConfig,
  material: MaterialQuery,
  projectCurrency: string,
  confidence: number,
  source: PriceQuote['source']
): PriceQuote | null {
  const packageInfo = inferPackage(material);
  const supplierUnit = parseUnit(raw.unit);
  const { price: packagePrice, converted } = convertToPackage(
    raw.price,
    supplierUnit,
    packageInfo.size,
    packageInfo.content
  );
  const adjusted = packagePrice * (1 + (supplier.priceAdjustmentPct || 0) / 100);
  const price = convertCurrency(adjusted, raw.currency || supplier.currency || 'USD', projectCurrency);
  if (!Number.isFinite(price) || price <= 0) return null;

  return {
    materialName: material.name,
    supplierId: supplier.id,
    supplierName: supplier.name,
    sku: raw.sku,
    matchedName: raw.name,
    price: Number(price.toFixed(4)),
    rawPrice: Number(raw.price.toFixed(4)),
    currency: raw.currency || supplier.currency,
    unit: converted ? `${raw.unit ?? packageInfo.content} x${packageInfo.size} → ${packageInfo.unit}` : raw.unit,
    stock: raw.stock ?? null,
    fetchedAt: new Date().toISOString(),
    confidence: Number(confidence.toFixed(3)),
    source,
    brand: raw.brand,
    url: raw.url
  };
}

async function syncSupplier(
  supplier: SupplierConfig,
  options: Required<Pick<SyncOptions, 'materials' | 'currency'>> & {
    transport: FetchTransport;
    force: boolean;
    retries: number;
    now: () => number;
    onProgress?: SyncOptions['onProgress'];
    step: number;
    total: number;
  }
): Promise<SupplierOutcome> {
  const { materials, currency, transport, force, retries, now, onProgress, step, total } = options;

  if (!supplier.enabled) {
    return { supplier, quotes: [], status: 'disabled' };
  }

  // 1) ¿Sirve la caché?
  const cached = readCache(supplier.id, supplier.ttlMinutes);
  if (!force && cached.fresh && cached.quotes.length) {
    onProgress?.({ supplier: supplier.name, step, total, message: `Usando caché (${Math.round(cached.ageMinutes)} min)` });
    return { supplier, quotes: cached.quotes, status: 'cached' };
  }

  // 2) Validación de endpoint (excepto proveedores simulados/manuales).
  if (supplier.kind === 'rest-json' || supplier.kind === 'csv') {
    const check = validateEndpoint(supplier.endpoint ?? '');
    if (!check.ok) {
      return { supplier, quotes: [], status: 'error', error: check.error };
    }
  }

  const provider = getProvider(supplier.kind);
  let lastError: Error | null = null;

  for (let attempt = 0; attempt <= retries; attempt += 1) {
    try {
      onProgress?.({
        supplier: supplier.name,
        step,
        total,
        message: attempt > 0 ? `Reintento ${attempt}/${retries}…` : 'Consultando catálogo…'
      });

      const context: ProviderContext = { supplier, transport, queries: materials };
      const raw = await provider.fetchQuotes(context);
      if (!raw.length) throw new Error('El catálogo no devolvió resultados.');

      // 3) Emparejamiento nombre proveedor ↔ material del presupuesto
      const quotes: PriceQuote[] = [];
      raw.forEach((row) => {
        const match = matchMaterial(row.name, materials, supplier.matchThreshold);
        if (!match) return;
        const quote = normalizeQuote(row, supplier, match.material, currency, match.score, supplier.kind === 'mock' ? 'mock' : 'live');
        if (quote) quotes.push(quote);
      });

      if (!quotes.length) {
        throw new Error('Ningún artículo del catálogo coincide con los materiales del presupuesto.');
      }

      writeCache(supplier.id, quotes);
      onProgress?.({ supplier: supplier.name, step, total, message: `${quotes.length} precios sincronizados` });
      return { supplier, quotes, status: 'ok' };
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      if (attempt < retries) {
        // Espera exponencial corta antes de reintentar.
        await new Promise((resolve) => setTimeout(resolve, 250 * (attempt + 1)));
      }
    }
  }

  // 4) Fallback a caché obsoleta
  if (cached.quotes.length) {
    onProgress?.({
      supplier: supplier.name,
      step,
      total,
      message: `Sin conexión: se usan precios cacheados de hace ${Math.round(cached.ageMinutes)} min`
    });
    return {
      supplier,
      quotes: cached.quotes,
      status: 'cached',
      error: lastError?.message
    };
  }

  return { supplier, quotes: [], status: 'error', error: lastError?.message ?? 'Error desconocido' };
}

/** Ejecuta la sincronización completa y devuelve el informe. */
export async function syncPrices(options: SyncOptions): Promise<SyncReport> {
  const startedAt = new Date();
  const clock = options.now ?? (() => Date.now());
  const transport = options.transport ?? createProxyTransport();
  const retries = Math.max(0, Math.min(3, options.retries ?? 1));
  const suppliers = options.suppliers.filter((s) => s.kind !== 'manual');

  const outcomes: SupplierOutcome[] = [];
  for (let i = 0; i < suppliers.length; i += 1) {
    // Secuencial a propósito: evita saturar portales de distribuidores y
    // permite informar del progreso real al usuario.
    // eslint-disable-next-line no-await-in-loop
    const outcome = await syncSupplier(suppliers[i], {
      materials: options.materials,
      currency: options.currency,
      transport,
      force: options.force ?? false,
      retries,
      now: clock,
      onProgress: options.onProgress,
      step: i + 1,
      total: suppliers.length
    });
    outcomes.push(outcome);
  }

  // Consolidado por material
  const byMaterial = new Map<string, PriceQuote[]>();
  const quotesByMaterialAndSupplier = new Map<string, Map<string, PriceQuote>>();

  outcomes.forEach((outcome) => {
    outcome.quotes.forEach((quote) => {
      const list = byMaterial.get(quote.materialName) ?? [];
      list.push(quote);
      byMaterial.set(quote.materialName, list);

      const perSupplier = quotesByMaterialAndSupplier.get(quote.materialName) ?? new Map<string, PriceQuote>();
      const existing = perSupplier.get(quote.supplierId);
      if (!existing || quote.price < existing.price) perSupplier.set(quote.supplierId, quote);
      quotesByMaterialAndSupplier.set(quote.materialName, perSupplier);
    });
  });

  const items: SyncItem[] = options.materials.map((material) => {
    const quotes = (byMaterial.get(material.name) ?? []).slice().sort((a, b) => a.price - b.price);
    const best = quotes[0] ?? null;
    const current = material.currentPrice;
    const delta = best ? Number((best.price - current).toFixed(4)) : 0;
    const deltaPct = current > 0 ? Number(((delta / current) * 100).toFixed(2)) : 0;

    let status: SyncItem['status'] = 'not-found';
    if (best) {
      if (Math.abs(delta) < 0.005) status = 'unchanged';
      else if (best.source === 'cache') status = 'cached';
      else status = 'updated';
    }
    const supplierError = outcomes.find((o) => o.status === 'error');
    if (!best && supplierError) status = 'error';

    return {
      materialName: material.name,
      category: material.category,
      currentPrice: current,
      quotes,
      best,
      previousPrice: current,
      delta,
      deltaPct,
      status,
      error: !best && supplierError ? supplierError.error : undefined
    };
  });

  const matched = items.filter((i) => i.best).length;
  const updated = items.filter((i) => i.status === 'updated').length;
  const potentialSavings = items.reduce((sum, item) => {
    if (!item.best || item.delta >= 0) return sum;
    return sum + Math.abs(item.delta) * (item.best ? 1 : 0);
  }, 0);

  const errors: SyncError[] = outcomes
    .filter((o) => o.status === 'error')
    .map((o) => ({
      supplierId: o.supplier.id,
      supplierName: o.supplier.name,
      message: o.error ?? 'Error desconocido',
      retriable: !/no coincide|inválid/i.test(o.error ?? '')
    }));

  const finishedAt = new Date();
  return {
    startedAt: startedAt.toISOString(),
    finishedAt: finishedAt.toISOString(),
    durationMs: Math.max(0, clock() - startedAt.getTime()),
    currency: options.currency,
    suppliers: outcomes.map((o) => ({
      id: o.supplier.id,
      name: o.supplier.name,
      kind: o.supplier.kind,
      quotes: o.quotes.length,
      status: o.status
    })),
    items,
    totals: {
      materials: options.materials.length,
      matched,
      updated,
      notFound: items.filter((i) => i.status === 'not-found').length,
      errors: errors.length,
      potentialSavings: Number(potentialSavings.toFixed(2))
    },
    errors
  };
}

/** Construye el informe desde un catálogo importado manualmente (CSV). */
export function reportFromRows(
  rows: RawQuote[],
  supplier: SupplierConfig,
  materials: MaterialQuery[],
  currency: string
): SyncReport {
  const now = new Date().toISOString();
  const quotesByMaterial = new Map<string, PriceQuote[]>();
  rows.forEach((row) => {
    const match = matchMaterial(row.name, materials, supplier.matchThreshold);
    if (!match) return;
    const quote = normalizeQuote(row, supplier, match.material, currency, match.score, 'manual');
    if (!quote) return;
    const list = quotesByMaterial.get(quote.materialName) ?? [];
    list.push(quote);
    quotesByMaterial.set(quote.materialName, list);
  });

  const items: SyncItem[] = materials.map((material) => {
    const quotes = (quotesByMaterial.get(material.name) ?? []).sort((a, b) => a.price - b.price);
    const best = quotes[0] ?? null;
    const delta = best ? Number((best.price - material.currentPrice).toFixed(4)) : 0;
    return {
      materialName: material.name,
      category: material.category,
      currentPrice: material.currentPrice,
      quotes,
      best,
      previousPrice: material.currentPrice,
      delta,
      deltaPct: material.currentPrice > 0 ? Number(((delta / material.currentPrice) * 100).toFixed(2)) : 0,
      status: best ? (Math.abs(delta) < 0.005 ? 'unchanged' : 'updated') : 'not-found'
    };
  });

  return {
    startedAt: now,
    finishedAt: now,
    durationMs: 0,
    currency,
    suppliers: [
      { id: supplier.id, name: supplier.name, kind: supplier.kind, quotes: rows.length, status: 'ok' }
    ],
    items,
    totals: {
      materials: materials.length,
      matched: items.filter((i) => i.best).length,
      updated: items.filter((i) => i.status === 'updated').length,
      notFound: items.filter((i) => i.status === 'not-found').length,
      errors: 0,
      potentialSavings: 0
    },
    errors: []
  };
}

/** Aplica los mejores precios al mapa de precios del proyecto. */
export function applyReportToPrices(report: SyncReport, existing: Record<string, number>): Record<string, number> {
  const next: Record<string, number> = { ...existing };
  report.items.forEach((item) => {
    if (item.best && item.best.price > 0) next[item.materialName] = Number(item.best.price.toFixed(2));
  });
  return next;
}

/** Genera el CSV de comparativa de proveedores (auditoría de compras). */
export function reportToCsv(report: SyncReport, sanitize: (value: unknown) => string): string {
  const header = [
    sanitize('Material'),
    sanitize('Proveedor'),
    sanitize('SKU'),
    sanitize('Articulo proveedor'),
    sanitize('Precio proyecto'),
    sanitize('Mejor precio'),
    sanitize('Diferencia'),
    sanitize('Diferencia %'),
    sanitize('Moneda'),
    sanitize('Stock'),
    sanitize('Confianza'),
    sanitize('Origen'),
    sanitize('Fecha')
  ];
  const lines = [header.join(',')];
  report.items.forEach((item) => {
    const quotes = item.quotes.length ? item.quotes : [null];
    quotes.forEach((quote) => {
      lines.push(
        [
          sanitize(item.materialName),
          sanitize(quote?.supplierName ?? 'Sin coincidencia'),
          sanitize(quote?.sku ?? ''),
          sanitize(quote?.matchedName ?? ''),
          sanitize(item.currentPrice.toFixed(2)),
          sanitize(quote ? quote.price.toFixed(2) : ''),
          sanitize(quote ? item.delta.toFixed(2) : ''),
          sanitize(quote ? item.deltaPct.toFixed(2) : ''),
          sanitize(quote?.currency ?? report.currency),
          sanitize(quote?.stock ?? ''),
          sanitize(quote ? quote.confidence.toFixed(2) : ''),
          sanitize(quote?.source ?? ''),
          sanitize(quote?.fetchedAt ?? report.finishedAt)
        ].join(',')
      );
    });
  });
  return `\uFEFF${lines.join('\n')}`;
}

/** Utilidad: cuántos materiales del presupuesto comparten tokens con el catálogo. */
export function matchingCoverage(materials: MaterialQuery[], rows: RawQuote[]): number {
  if (!materials.length || !rows.length) return 0;
  const catalog = rows.map((r) => normalizeText(r.name));
  const covered = materials.filter((m) => {
    const n = normalizeText(m.name);
    return catalog.some((c) => c.includes(n) || n.includes(c));
  }).length;
  return covered / materials.length;
}
