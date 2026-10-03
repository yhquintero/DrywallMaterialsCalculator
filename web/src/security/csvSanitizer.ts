/**
 * DrywallPro CSV & Excel Formula Injection Defense (OWASP Formula Injection Protection)
 * Prevents remote command execution via DDE, Excel formulas and spreadsheet macros.
 */

const DANGEROUS_FORMULA_PREFIXES = ['=', '+', '-', '@', '\t', '\r', '%', '|'];

/**
 * Sanitiza una celda individual para exportación a CSV/Excel
 */
export function sanitizeCsvCell(value: any): string {
  if (value === null || value === undefined) return '""';

  let str = String(value).trim();

  // Si comienza con un caracter disparador de fórmulas en Excel/Sheets, anteponer apóstrofe seguro (')
  if (DANGEROUS_FORMULA_PREFIXES.some((prefix) => str.startsWith(prefix))) {
    str = `'${str}`;
  }

  // Escapar comillas dobles internas duplicándolas según estándar RFC 4180
  const escaped = str.replace(/"/g, '""');

  return `"${escaped}"`;
}

/**
 * Detecta si una celda contiene un intento de inyección de fórmula
 */
export function isFormulaInjectionAttempt(value: string): boolean {
  if (!value || typeof value !== 'string') return false;
  const trimmed = value.trim();
  return DANGEROUS_FORMULA_PREFIXES.some((prefix) => trimmed.startsWith(prefix));
}
