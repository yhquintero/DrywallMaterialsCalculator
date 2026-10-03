/**
 * Proveedor de catálogo en CSV / TSV (lista de precios publicada por el
 * distribuidor en su web o en un FTP/Drive).
 */

import { RawQuote, SupplierProvider, SupplierConfig } from '../types';
import { findFirstArray, getByPath, parsePrice } from '../normalize';
import { parseCsv, pickColumn } from '../csv';

export const csvProvider: SupplierProvider = {
  kind: 'csv',

  async fetchQuotes({ supplier, transport }): Promise<RawQuote[]> {
    const response = await transport({
      url: supplier.endpoint ?? '',
      method: supplier.method ?? 'GET',
      headers: { Accept: 'text/csv,text/plain,*/*', ...(supplier.headers ?? {}) },
      supplier,
      timeoutMs: supplier.timeoutMs
    });

    if (response.status >= 400) {
      throw new Error(`El proveedor respondió ${response.status} al descargar el CSV.`);
    }

    const delimiter = supplier.csvOptions?.delimiter || detectDelimiter(response.text);
    const { rows } = parseCsv(response.text, delimiter);
    if (!rows.length) throw new Error('El CSV no contiene filas legibles.');

    const headerIndex = Math.max(0, (supplier.csvOptions?.headerRow ?? 1) - 1);
    const header = rows[headerIndex] ?? rows[0];
    const dataRows = rows.slice(headerIndex + 1);

    const options = supplier.csvOptions;
    const skuCol = pickColumn(header, options?.skuColumn, ['sku', 'codigo', 'código', 'ref', 'referencia', 'id', 'ean']);
    const nameCol = pickColumn(header, options?.nameColumn, [
      'nombre',
      'name',
      'descripcion',
      'descripción',
      'producto',
      'articulo',
      'artículo',
      'description'
    ]);
    const priceCol = pickColumn(header, options?.priceColumn, [
      'precio',
      'price',
      'pvp',
      'importe',
      'precio unitario',
      'precio_unitario',
      'unit price'
    ]);
    const currencyCol = pickColumn(header, options?.currencyColumn, ['moneda', 'currency', 'divisa']);
    const unitCol = pickColumn(header, options?.unitColumn, ['unidad', 'unit', 'ud', 'umedida', 'unidad de medida']);
    const stockCol = pickColumn(header, options?.stockColumn, ['stock', 'existencias', 'disponible', 'cantidad']);

    if (priceCol === -1) {
      throw new Error('No se encontró la columna de precio en el CSV. Configúrala en el proveedor.');
    }

    const quotes: RawQuote[] = [];
    dataRows.forEach((row) => {
      const name = (nameCol >= 0 ? row[nameCol] : row[0]) ?? '';
      const price = parsePrice(row[priceCol]);
      if (!name || !Number.isFinite(price) || price <= 0) return;
      quotes.push({
        sku: skuCol >= 0 ? row[skuCol] : undefined,
        name: name.trim(),
        price,
        currency: currencyCol >= 0 ? row[currencyCol]?.trim() : undefined,
        unit: unitCol >= 0 ? row[unitCol]?.trim() : undefined,
        stock: stockCol >= 0 ? Number.parseFloat(row[stockCol] ?? '') : null
      });
    });

    return quotes;
  }
};

function detectDelimiter(text: string): string {
  const sample = text.split(/\r?\n/).slice(0, 5).join('\n');
  const candidates = [';', ',', '\t', '|'];
  let best = ',';
  let bestCount = 0;
  candidates.forEach((c) => {
    const count = sample.split(c).length - 1;
    if (count > bestCount) {
      bestCount = count;
      best = c;
    }
  });
  return best;
}

/** Alias exportado para reutilizar el parseo de arrays embebidos en JSON. */
export function extractArray(payload: unknown, responsePath?: string): unknown[] {
  if (responsePath) {
    const found = getByPath(payload, responsePath);
    if (Array.isArray(found)) return found;
  }
  return findFirstArray(payload) ?? [];
}
