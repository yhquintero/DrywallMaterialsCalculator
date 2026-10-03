/**
 * Proveedor REST/JSON genérico.
 *
 * Cubre la mayoría de portales B2B de distribuidores de materiales de
 * construcción: se configura el endpoint, la plantilla de búsqueda, la ruta
 * al array de resultados y el mapeo de campos. Si el proveedor exige una API
 * key, ésta se guarda en el servidor y se inyecta en el proxy (`server-proxy`),
 * de modo que el navegador nunca la ve.
 */

import { RawQuote, SupplierProvider, SupplierConfig } from '../types';
import { findFirstArray, getByPath, parsePrice } from '../normalize';

/** Sustituye los marcadores {query}, {sku}, {page}, {limit} de una plantilla. */
export function renderTemplate(template: string, vars: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (_match, key: string) =>
    vars[key] !== undefined ? encodeURIComponent(vars[key]) : ''
  );
}

function buildHeaders(supplier: SupplierConfig): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json,text/plain,*/*',
    ...(supplier.headers ?? {})
  };
  const auth = supplier.auth;
  if (auth?.mode === 'header' && auth.headerName) {
    // Sólo se envía si el valor es público (sin secreto). En modo
    // `server-proxy` la clave la añade el backend.
    if (auth.value) headers[auth.headerName] = auth.value;
  }
  return headers;
}

function buildUrl(supplier: SupplierConfig, query: { name: string; sku?: string }): string {
  const base = supplier.endpoint ?? '';
  const template = supplier.urlTemplate?.trim();
  if (!template) return base;
  const rendered = renderTemplate(template, {
    query: query.name,
    sku: query.sku ?? query.name,
    page: '1',
    limit: '50'
  });
  return /^https?:\/\//i.test(rendered) ? rendered : `${base.replace(/\/$/, '')}/${rendered.replace(/^\//, '')}`;
}

export const restJsonProvider: SupplierProvider = {
  kind: 'rest-json',

  async fetchQuotes({ supplier, transport, queries }): Promise<RawQuote[]> {
    if (!supplier.endpoint && !supplier.urlTemplate) {
      throw new Error('El proveedor no tiene configurado ningún endpoint.');
    }

    // Se lanzan las búsquedas de todos los materiales, pero se limita la
    // concurrencia para no saturar el portal del distribuidor.
    const limit = Math.min(queries.length, 12);
    const targets = queries.slice(0, limit);

    const results = await Promise.all(
      targets.map(async (query) => {
        const url = buildUrl(supplier, { name: query.name });
        const body = supplier.bodyTemplate
          ? renderTemplate(supplier.bodyTemplate, { query: query.name, sku: query.name })
          : undefined;

        const response = await transport({
          url,
          method: supplier.method ?? 'GET',
          headers: {
            ...buildHeaders(supplier),
            ...(body ? { 'Content-Type': 'application/json' } : {})
          },
          body,
          supplier,
          timeoutMs: supplier.timeoutMs
        });

        if (response.status >= 400) {
          throw new Error(`HTTP ${response.status} en ${new URL(url, 'https://invalid.local').host || 'el proveedor'}`);
        }
        let payload: unknown;
        try {
          payload = JSON.parse(response.text);
        } catch {
          throw new Error('La respuesta del proveedor no es JSON válido.');
        }

        const rawRows = supplier.responsePath
          ? (getByPath(payload, supplier.responsePath) as unknown[])
          : findFirstArray(payload);
        if (!Array.isArray(rawRows) || !rawRows.length) return [];

        const map = supplier.fieldMap ?? {};
        return rawRows
          .map((row) => {
            const name = String(
              (map.name ? getByPath(row, map.name) : undefined) ??
                getByPath(row, 'name') ??
                getByPath(row, 'nombre') ??
                getByPath(row, 'descripcion') ??
                ''
            );
            const priceRaw =
              (map.price ? getByPath(row, map.price) : undefined) ??
              getByPath(row, 'price') ??
              getByPath(row, 'precio') ??
              getByPath(row, 'pvp');
            const price = parsePrice(priceRaw);
            if (!name || !Number.isFinite(price) || price <= 0) return null;
            const stockRaw = map.stock ? getByPath(row, map.stock) : getByPath(row, 'stock');
            return {
              sku: map.sku ? String(getByPath(row, map.sku) ?? '') : undefined,
              name: name.trim(),
              price,
              currency: map.currency ? String(getByPath(row, map.currency) ?? '') : undefined,
              unit: map.unit ? String(getByPath(row, map.unit) ?? '') : undefined,
              stock: stockRaw === undefined || stockRaw === null ? null : Number.parseFloat(String(stockRaw)),
              brand: map.brand ? String(getByPath(row, map.brand) ?? '') : undefined,
              updatedAt: map.updatedAt ? String(getByPath(row, map.updatedAt) ?? '') : undefined,
              url: map.url ? String(getByPath(row, map.url) ?? '') : undefined
            } as RawQuote;
          })
          .filter((q): q is RawQuote => q !== null);
      })
    );

    // Se deduplican los resultados por nombre normalizado.
    const seen = new Map<string, RawQuote>();
    results.flat().forEach((quote) => {
      const key = `${quote.sku ?? ''}|${quote.name.toLowerCase()}`;
      const existing = seen.get(key);
      if (!existing || quote.price < existing.price) seen.set(key, quote);
    });
    return Array.from(seen.values());
  }
};
