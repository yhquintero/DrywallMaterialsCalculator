/**
 * Sanitizador DOM basado en DOMPurify.
 *
 * Vive en un módulo aparte a propósito: `dompurify` pesa ~11 kB gzip y sólo lo
 * necesita la consola de seguridad, que se carga en diferido. De este modo la
 * calculadora no lo descarga en el arranque (ver `scripts/check-bundle.mjs`).
 */
import DOMPurify from 'dompurify';

/** Elimina todo el marcado y los atributos, conservando el texto. */
export function sanitizeHtmlWithDomPurify(dirty: string): string {
  if (!dirty || typeof dirty !== 'string') return '';
  try {
    const purifier = (DOMPurify as unknown as { sanitize?: (value: string, cfg?: object) => string });
    if (typeof purifier.sanitize === 'function') {
      return purifier
        .sanitize(dirty, { ALLOWED_TAGS: [], ALLOWED_ATTR: [], KEEP_CONTENT: true })
        .trim();
    }
  } catch {
    // Sin DOM disponible se cae al filtro estricto por expresiones regulares.
  }
  return stripMarkup(dirty);
}

/** Filtro conservador por expresiones regulares, sin dependencias. */
export function stripMarkup(value: string): string {
  if (!value || typeof value !== 'string') return '';
  return value
    .replace(/<\s*script\b[^>]*>[\s\S]*?<\s*\/\s*script\s*>/gi, '')
    .replace(/<\s*[^>]*>/gi, '')
    .replace(/\bon\w+\s*=\s*['"][^'"]*['"]/gi, '')
    .replace(/javascript:/gi, '')
    .trim();
}
