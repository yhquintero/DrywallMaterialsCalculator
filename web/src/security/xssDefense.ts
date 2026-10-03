/**
 * DrywallPro XSS & DOM Injection Defense Engine
 * Prevents Stored, Reflected and DOM-based Cross-Site Scripting.
 */
import DOMPurify from 'dompurify';

export interface XssCheckResult {
  hasThreat: boolean;
  threatLevel: 'NONE' | 'MEDIUM' | 'CRITICAL';
  detectedVector: string;
  sanitized: string;
}

const XSS_PATTERNS = [
  { name: '<script> tag execution', regex: /<\s*script\b[^>]*>[\s\S]*?<\s*\/\s*script\s*>/i },
  { name: 'Inline event handlers (onload, onerror, onclick)', regex: /\bon\w+\s*=\s*['"][^'"]*['"]/i },
  { name: 'JavaScript: pseudo-protocol', regex: /\bjavascript\s*:\s*[^"'>\s]+/i },
  { name: 'Data URI HTML/SVG payload', regex: /\bdata:\s*(text\/html|image\/svg\+xml)/i },
  { name: '<iframe/embed/object> injection', regex: /<\s*(iframe|embed|object|applet)\b/i },
  { name: 'Expression/Eval call', regex: /\b(eval|alert|prompt|confirm)\s*\(/i }
];

/**
 * Escapa todas las entidades HTML para salida de texto seguro
 */
export function escapeHtml(str: string): string {
  if (!str || typeof str !== 'string') return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;')
    .replace(/\//g, '&#x2F;');
}

/**
 * Detecta y analiza posibles vectores XSS en una cadena
 */
export function detectXss(input: string | undefined | null): XssCheckResult {
  if (!input || typeof input !== 'string') {
    return { hasThreat: false, threatLevel: 'NONE', detectedVector: '', sanitized: '' };
  }

  for (const pattern of XSS_PATTERNS) {
    if (pattern.regex.test(input)) {
      return {
        hasThreat: true,
        threatLevel: 'CRITICAL',
        detectedVector: pattern.name,
        sanitized: sanitizeHtmlStrict(input)
      };
    }
  }

  return {
    hasThreat: false,
    threatLevel: 'NONE',
    detectedVector: '',
    sanitized: sanitizeHtmlStrict(input)
  };
}

/**
 * Sanitiza HTML usando DOMPurify con fallback robusto universal
 */
export function sanitizeHtmlStrict(dirty: string): string {
  if (!dirty || typeof dirty !== 'string') return '';

  try {
    // Si DOMPurify.sanitize está disponible directamente (Navegador)
    if (typeof (DOMPurify as any).sanitize === 'function') {
      return (DOMPurify as any).sanitize(dirty, {
        ALLOWED_TAGS: [],
        ALLOWED_ATTR: [],
        KEEP_CONTENT: true
      }).trim();
    }
    // Si DOMPurify es una función factory (Node / JSDOM)
    if (typeof DOMPurify === 'function' && typeof window !== 'undefined') {
      const purifier = (DOMPurify as any)(window);
      if (typeof purifier.sanitize === 'function') {
        return purifier.sanitize(dirty, {
          ALLOWED_TAGS: [],
          ALLOWED_ATTR: [],
          KEEP_CONTENT: true
        }).trim();
      }
    }
  } catch (e) {
    // Continuar al filtro estricto por expresiones regulares
  }

  // Fallback estricto universal en caso de entorno headless sin DOM
  return dirty
    .replace(/<\s*script\b[^>]*>[\s\S]*?<\s*\/\s*script\s*>/gi, '')
    .replace(/<\s*[^>]*>/gi, '') // Elimina todos los tags HTML <...>
    .replace(/\bon\w+\s*=\s*['"][^'"]*['"]/gi, '')
    .replace(/javascript:/gi, '')
    .trim();
}

/**
 * Sanitiza URLs para prevenir ataques mediante `javascript:` o `data:`
 */
export function sanitizeUrl(url: string): string {
  if (!url || typeof url !== 'string') return '';
  const trimmed = url.trim();
  if (/^(javascript:|data:|vbscript:)/i.test(trimmed)) {
    return '#blocked-insecure-url';
  }
  return trimmed;
}
