/**
 * Parseador CSV/TSV tolerante (RFC 4180 con comillas, saltos de línea dentro
 * de campo, BOM y delimitador configurable). Sin dependencias externas.
 */

export interface CsvParseResult {
  rows: string[][];
  delimiter: string;
  /** Número de filas descartadas por estar vacías. */
  skipped: number;
}

export function parseCsv(input: string, delimiter = ',', maxRows = 20000): CsvParseResult {
  const text = input.replace(/^\uFEFF/, '');
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;
  let skipped = 0;

  const pushField = (): void => {
    row.push(field.trim());
    field = '';
  };
  const pushRow = (): void => {
    pushField();
    const isEmpty = row.every((cell) => cell === '');
    if (isEmpty) skipped += rows.length > 0 ? 1 : 0;
    else rows.push(row);
    row = [];
  };

  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (inQuotes) {
      if (char === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        field += char;
      }
      continue;
    }
    if (char === '"') {
      inQuotes = true;
      continue;
    }
    if (char === delimiter) {
      pushField();
      continue;
    }
    if (char === '\n') {
      pushRow();
      if (rows.length >= maxRows) break;
      continue;
    }
    if (char === '\r') continue;
    field += char;
  }
  if (field.length || row.length) pushRow();

  return { rows, delimiter, skipped };
}

/** Normaliza cabeceras: minúsculas, sin acentos ni puntuación. */
function normalizeHeader(value: string): string {
  return value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

/**
 * Localiza el índice de una columna: primero por el nombre configurado y, si
 * no hay coincidencia, por la lista de alias habituales.
 */
export function pickColumn(header: string[], configured: string | undefined, aliases: string[] = []): number {
  const normalized = header.map(normalizeHeader);
  if (configured) {
    const wanted = normalizeHeader(configured);
    const exact = normalized.indexOf(wanted);
    if (exact !== -1) return exact;
    const partial = normalized.findIndex((h) => h.includes(wanted) || wanted.includes(h));
    if (partial !== -1) return partial;
  }
  for (const alias of aliases) {
    const wanted = normalizeHeader(alias);
    const exact = normalized.indexOf(wanted);
    if (exact !== -1) return exact;
  }
  for (const alias of aliases) {
    const wanted = normalizeHeader(alias);
    const partial = normalized.findIndex((h) => h.includes(wanted));
    if (partial !== -1) return partial;
  }
  return -1;
}

/** Convierte filas CSV a objetos usando la primera fila como cabecera. */
export function csvToObjects(input: string, delimiter = ','): Array<Record<string, string>> {
  const { rows } = parseCsv(input, delimiter);
  if (rows.length < 2) return [];
  const header = rows[0];
  return rows.slice(1).map((row) => {
    const obj: Record<string, string> = {};
    header.forEach((key, i) => {
      obj[key] = row[i] ?? '';
    });
    return obj;
  });
}
