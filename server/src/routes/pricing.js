/**
 * Proxy de catálogos de distribuidores (precios en tiempo real).
 *
 *   POST /api/pricing/proxy   → descarga el catálogo por cuenta del navegador
 *   GET  /api/pricing/secrets → lista los nombres de secretos DISPONIBLES (sin valores)
 *
 * Ventajas del proxy frente a llamar desde el navegador:
 *   • Las API keys viven sólo en el servidor (nunca llegan al cliente).
 *   • Se evita CORS (muchos portales B2B no lo habilitan).
 *   • Se aplica lista blanca de hosts y bloqueo de rangos privados (SSRF).
 *   • Se limita tamaño y tiempo de respuesta, y queda traza en logs.
 */
import dns from 'node:dns/promises';
import net from 'node:net';
import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import config from '../config/index.js';
import { HttpError } from '../utils/errors.js';
import { sameOriginGuard } from '../security/httpsMiddleware.js';

const router = Router();

const pricingLimiter = rateLimit({
  windowMs: 60_000,
  limit: config.pricing.rateLimitPerMinute,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Demasiadas consultas de precios. Espera un minuto.' }
});

router.use(sameOriginGuard);
router.use(pricingLimiter);

// ── Protección SSRF ──────────────────────────────────────────────────────────

/** Rangos privados, de enlace local y de metadatos de nube. */
const PRIVATE_RANGES = [
  { v4: ['10.0.0.0', 8] },
  { v4: ['172.16.0.0', 12] },
  { v4: ['192.168.0.0', 16] },
  { v4: ['127.0.0.0', 8] },
  { v4: ['169.254.0.0', 16] },
  { v4: ['0.0.0.0', 8] },
  { v4: ['100.64.0.0', 10] },
  { v4: ['192.0.0.0', 24] },
  { v4: ['198.18.0.0', 15] },
  { v4: ['224.0.0.0', 4] },
  { v4: ['240.0.0.0', 4] }
];

function ipToLong(ip) {
  return ip.split('.').reduce((acc, octet) => (acc << 8) + Number(octet), 0) >>> 0;
}

function isPrivateIPv4(ip) {
  const value = ipToLong(ip);
  return PRIVATE_RANGES.some(({ v4: [base, bits] }) => {
    const mask = bits === 0 ? 0 : (~0 << (32 - bits)) >>> 0;
    return (value & mask) === (ipToLong(base) & mask);
  });
}

function isPrivateIPv6(ip) {
  const lower = ip.toLowerCase();
  return (
    lower === '::1' ||
    lower === '::' ||
    lower.startsWith('fc') ||
    lower.startsWith('fd') ||
    lower.startsWith('fe80') ||
    lower.startsWith('::ffff:127.') ||
    lower.startsWith('::ffff:10.') ||
    lower.startsWith('::ffff:192.168.')
  );
}

function isPrivateIp(ip) {
  if (!net.isIP(ip)) return true;
  return net.isIPv4(ip) ? isPrivateIPv4(ip) : isPrivateIPv6(ip);
}

/** Valida el host destino: protocolo, lista blanca y resolución DNS pública. */
async function assertSafeTarget(rawUrl) {
  let url;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new HttpError(400, 'URL de catálogo inválida.');
  }
  if (url.protocol !== 'https:') {
    throw new HttpError(400, 'Sólo se admiten catálogos servidos por HTTPS.');
  }
  if (url.username || url.password) {
    throw new HttpError(400, 'No incluyas credenciales en la URL.');
  }

  const host = url.hostname.toLowerCase();
  if (config.pricing.allowedHosts.length && !config.pricing.allowedHosts.includes(host)) {
    throw new HttpError(403, `El host ${host} no está en la lista blanca de proveedores (PRICING_ALLOWED_HOSTS).`);
  }
  if (!config.pricing.allowedHosts.length && !config.pricing.allowAnyHost) {
    throw new HttpError(403, 'El proxy de precios requiere configurar PRICING_ALLOWED_HOSTS.');
  }

  // Se resuelven TODAS las direcciones: basta una privada para bloquear la
  // petición (DNS rebinding / split-horizon).
  const records = await dns.lookup(host, { all: true, verbatim: true }).catch(() => []);
  if (!records.length) throw new HttpError(400, `No se pudo resolver el host ${host}.`);
  for (const record of records) {
    if (isPrivateIp(record.address)) {
      throw new HttpError(403, `El host ${host} resuelve a una dirección interna (${record.address}); bloqueado.`);
    }
  }
  return url;
}

/** Inyecta el secreto configurado en el servidor (env var `secretRef`). */
function applySecret(headers, url, secretRef) {
  if (!secretRef) return;
  const value = process.env[secretRef];
  if (!value) return;
  // El nombre del secreto sólo puede referenciar variables con prefijo
  // reconocido, para evitar leer JWT_SECRET o MASTER_KEY por accidente.
  const allowedPrefixes = ['SUPPLIER_', 'PRICING_', 'ERP_', 'DISTRIBUTOR_', 'CATALOG_'];
  if (!allowedPrefixes.some((prefix) => secretRef.startsWith(prefix))) return;

  if (headers['X-Auth-Mode'] === 'query') {
    url.searchParams.set(headers['X-Auth-Param'] || 'api_key', value);
  } else if (headers['X-Auth-Mode'] === 'header') {
    const name = headers['X-Auth-Header'];
    if (name && /^[A-Za-z0-9-]{1,64}$/.test(name)) headers[name] = value;
  } else {
    // Modo `bearer` (por defecto): Authorization: Bearer <secreto>
    headers.Authorization = `Bearer ${value}`;
  }
}

const proxySchema = z.object({
  url: z.string().url().max(2000),
  method: z.enum(['GET', 'POST']).default('GET'),
  headers: z.record(z.string().max(64), z.string().max(1024)).default({}),
  body: z.string().max(64 * 1024).optional(),
  secretRef: z.string().max(64).optional(),
  authMode: z.enum(['header', 'query', 'bearer']).optional(),
  authHeader: z.string().max(64).optional(),
  authQueryParam: z.string().max(64).optional()
});

/**
 * POST /api/pricing/proxy
 * Cuerpo: { url, method, headers, body, secretRef?, authMode?, authHeader?, authQueryParam? }
 */
router.post('/proxy', async (req, res, next) => {
  try {
    const parsed = proxySchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Petición de catálogo inválida.', parsed.error.issues);
    const { url: rawUrl, method, headers, body, secretRef, authMode, authHeader, authQueryParam } = parsed.data;

    const url = await assertSafeTarget(rawUrl);

    // Cabeceras permitidas: se descartan Hop-by-Hop y cualquier intento de
    // tocar el transporte (Host, Connection, Upgrade…).
    const forwarded = {};
    const BLOCKED = ['host', 'connection', 'upgrade', 'transfer-encoding', 'content-length', 'cookie', 'authorization'];
    for (const [key, value] of Object.entries(headers || {})) {
      const lower = key.toLowerCase();
      if (BLOCKED.includes(lower)) continue;
      if (lower.startsWith('proxy-') || lower.startsWith('sec-')) continue;
      forwarded[key] = value;
    }
    if (authMode) {
      forwarded['X-Auth-Mode'] = authMode;
      if (authHeader) forwarded['X-Auth-Header'] = authHeader;
      if (authQueryParam) forwarded['X-Auth-Param'] = authQueryParam;
    }
    applySecret(forwarded, url, secretRef);
    // Las cabeceras auxiliares nunca se reenvían al proveedor.
    delete forwarded['X-Auth-Mode'];
    delete forwarded['X-Auth-Header'];
    delete forwarded['X-Auth-Param'];

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), config.pricing.timeoutMs);

    let response;
    try {
      response = await fetch(url, {
        method,
        headers: forwarded,
        body: method === 'POST' ? body : undefined,
        signal: controller.signal,
        redirect: 'error' // evita redirecciones a hosts internos
      });
    } catch (error) {
      clearTimeout(timer);
      const aborted = error?.name === 'AbortError';
      throw new HttpError(
        aborted ? 504 : 502,
        aborted
          ? `El catálogo no respondió en ${config.pricing.timeoutMs} ms.`
          : `No se pudo contactar con el catálogo: ${error?.message || 'error de red'}.`
      );
    } finally {
      clearTimeout(timer);
    }

    const contentLength = Number(response.headers.get('content-length') || 0);
    if (contentLength && contentLength > config.pricing.maxResponseBytes) {
      throw new HttpError(413, 'El catálogo excede el tamaño máximo permitido.');
    }

    const text = await response.text();
    if (text.length > config.pricing.maxResponseBytes) {
      throw new HttpError(413, 'El catálogo excede el tamaño máximo permitido.');
    }

    res.setHeader('Cache-Control', 'no-store');
    res.status(response.status).type(response.headers.get('content-type') || 'application/json').send(text);
  } catch (error) {
    next(error);
  }
});

/**
 * GET /api/pricing/secrets?refs=SUPPLIER_API_KEY,ERP_SERVICE_TOKEN
 * Indica, para los secretos consultados, si están configurados en el servidor.
 * Nunca devuelve valores ni enumera el entorno: sólo confirma la existencia de
 * los nombres que el propio usuario ha escrito en la configuración.
 */
router.get('/secrets', (req, res) => {
  const allowedPrefixes = ['SUPPLIER_', 'PRICING_', 'ERP_', 'DISTRIBUTOR_', 'CATALOG_'];
  const refs = String(req.query.refs || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 20);

  const secrets = refs
    .filter((name) => /^[A-Z][A-Z0-9_]{2,63}$/.test(name))
    .map((name) => ({
      name,
      allowed: allowedPrefixes.some((prefix) => name.startsWith(prefix)),
      configured: allowedPrefixes.some((prefix) => name.startsWith(prefix)) && Boolean(process.env[name])
    }));

  res.setHeader('Cache-Control', 'no-store');
  res.json({
    secrets,
    note: 'Los valores nunca se envían al navegador; el proxy los inyecta en la petición al proveedor.'
  });
});

export default router;
