/**
 * Capa HTTPS.
 *  • `enforceHttps` : redirige 301 cualquier petición HTTP al mismo recurso en HTTPS.
 *  • `secureHeaders`: HSTS (solo cuando realmente hay TLS) + cabeceras endurecidas.
 *  • `tlsContext`   : expone en la request si la conexión es segura, útil para la UI.
 */
import config from '../config/index.js';

const isLoopback = (host = '') =>
  ['localhost', '127.0.0.1', '::1', '[::1]', '0.0.0.0'].includes(host.split(':')[0]);

export function isSecureRequest(req) {
  if (req.secure) return true;
  const fwdProto = String(req.headers['x-forwarded-proto'] || '').split(',')[0].trim();
  if (fwdProto === 'https') return true;
  if (req.headers['x-forwarded-ssl'] === 'on') return true;
  if (req.connection?.encrypted) return true;
  return false;
}

/** Adjunta `req.isSecure` y `req.publicBaseUrl`. */
export function tlsContext(req, _res, next) {
  req.isSecure = isSecureRequest(req);
  const host = req.headers.host || config.server.publicHost;
  req.publicBaseUrl = `${req.isSecure ? 'https' : 'http'}://${host}`;
  next();
}

/**
 * Redirección HTTP → HTTPS.
 * En desarrollo con loopback se permite HTTP para no romper el preview del sandbox,
 * pero SIEMPRE se emite HSTS cuando la conexión ya es TLS.
 */
export function enforceHttps(req, res, next) {
  if (isSecureRequest(req)) return next();

  const host = (req.headers.host || '').split(':')[0];
  const devAllowed = !config.isProd && (isLoopback(host) || host === '' || host.endsWith('.e2b.app'));
  if (devAllowed) {
    res.setHeader('X-HTTPS-Status', 'http-dev-allowed');
    return next();
  }

  const target = config.server.publicUrl || `https://${req.headers.host}`;
  const base = target.replace(/\/$/, '');
  const url = `${base}${req.originalUrl}`;
  audit403(res);
  return res.redirect(301, url);
}

function audit403(res) {
  res.setHeader('Cache-Control', 'no-store');
}

/** Cabeceras de seguridad (complementan a helmet). */
export function secureHeaders(req, res, next) {
  // HSTS sólo tiene sentido (y sólo es seguro emitirlo) sobre una conexión TLS real.
  if (isSecureRequest(req)) {
    res.setHeader(
      'Strict-Transport-Security',
      'max-age=31536000; includeSubDomains; preload'
    );
  }
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
  res.setHeader('X-XSS-Protection', '0');
  res.setHeader('Cross-Origin-Resource-Policy', 'same-origin');
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
  next();
}

/** Bloquea mutaciones no seguras por CSRF cuando la sesión va en cookie. */
export function sameOriginGuard(req, res, next) {
  if (['GET', 'HEAD', 'OPTIONS'].includes(req.method)) return next();
  const origin = req.headers.origin;
  if (!origin) return next(); // peticiones same-origin / apps nativas
  const host = req.headers.host;
  try {
    const o = new URL(origin);
    const allowed =
      o.host === host ||
      (!config.isProd && (isLoopback(o.hostname) || o.hostname.endsWith('.e2b.app'))) ||
      config.server.corsOrigins.includes(origin);
    if (!allowed) {
      return res.status(403).json({ error: 'Origen no permitido (CSRF guard)' });
    }
  } catch {
    return res.status(400).json({ error: 'Cabecera Origin inválida' });
  }
  next();
}
