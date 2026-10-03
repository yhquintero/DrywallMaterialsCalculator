import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
/**
 * Raíz de la carpeta del paquete `server/` (contiene src/, certs/, data/ y
 * .env). Antes apuntaba a `server/src`, lo que hacía que los valores por
 * defecto de certificados, base de datos y build web cayesen en rutas
 * equivocadas y que no se leyese `server/.env`.
 */
export const SERVER_ROOT = path.resolve(__dirname, '..', '..');
export const REPO_ROOT = path.resolve(SERVER_ROOT, '..');

/** Carga .env (propio o de la raíz del repo) sin dependencias externas. */
function loadDotEnv() {
  for (const candidate of [
    path.join(SERVER_ROOT, '.env'),
    path.join(REPO_ROOT, '.env'),
  ]) {
    if (!fs.existsSync(candidate)) continue;
    const content = fs.readFileSync(candidate, 'utf8');
    for (const rawLine of content.split('\n')) {
      const line = rawLine.trim();
      if (!line || line.startsWith('#')) continue;
      const eq = line.indexOf('=');
      if (eq === -1) continue;
      const key = line.slice(0, eq).trim();
      let value = line.slice(eq + 1).trim();
      const quoted =
        (value.startsWith('"') && value.endsWith('"') && value.length > 1) ||
        (value.startsWith("'") && value.endsWith("'") && value.length > 1);
      if (quoted) {
        value = value.slice(1, -1);
      } else {
        // Comentario en línea: `HOST=127.0.0.1  # solo bucle local`.
        // Sin esto el `#…` formaba parte del valor y rompía getaddrinfo.
        // Los valores entrecomillados conservan el `#` literal.
        const hash = value.indexOf(' #');
        if (hash !== -1) value = value.slice(0, hash).trimEnd();
      }
      if (!(key in process.env)) process.env[key] = value;
    }
  }
}
loadDotEnv();

const env = process.env;
const bool = (v, def = false) =>
  v === undefined || v === '' ? def : ['1', 'true', 'yes', 'on'].includes(String(v).toLowerCase());
const int = (v, def) => (Number.isFinite(parseInt(v, 10)) ? parseInt(v, 10) : def);

const isProd = (env.NODE_ENV || 'development') === 'production';

/**
 * JWT_SECRET / MASTER_KEY obligatorios en producción.
 * En desarrollo se genera uno efímero y se avisa por consola.
 */
function requireSecret(name, fallbackWarn) {
  const value = env[name];
  if (value && value.length >= 16) return value;
  if (isProd) {
    throw new Error(
      `[config] La variable ${name} es OBLIGATORIA en producción y debe tener >= 16 caracteres.`
    );
  }
  const ephemeral = crypto.randomBytes(48).toString('base64url');
  // eslint-disable-next-line no-console
  console.warn(`[config] ⚠ ${name} no definida — usando valor efímero de desarrollo. ${fallbackWarn}`);
  return ephemeral;
}

export const config = {
  env: env.NODE_ENV || 'development',
  isProd,

  server: {
    host: env.HOST || '0.0.0.0',
    port: int(env.PORT, 8443),
    https: bool(env.ENABLE_HTTPS, !isProd ? false : true),
    httpPort: int(env.HTTP_PORT, 8080),
    certFile: env.TLS_CERT_FILE || path.join(SERVER_ROOT, 'certs', 'server.crt'),
    keyFile: env.TLS_KEY_FILE || path.join(SERVER_ROOT, 'certs', 'server.key'),
    trustProxy: bool(env.TRUST_PROXY, true),
    /** Dominio canónico HTTPS de la consola (para redirects y .well-known). */
    publicUrl: env.PUBLIC_URL || `https://${env.PUBLIC_HOST || 'localhost:' + int(env.PORT, 8443)}`,
    publicHost: env.PUBLIC_HOST || 'localhost',
    /** Orígenes extra permitidos para CORS (separados por coma). */
    corsOrigins: (env.CORS_ORIGINS || '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
  },

  db: {
    file: env.DB_FILE || path.join(SERVER_ROOT, 'data', 'drywall_console.db'),
  },

  auth: {
    jwtSecret: requireSecret('JWT_SECRET', 'Las sesiones se invalidarán al reiniciar.'),
    accessTtlMinutes: int(env.ACCESS_TOKEN_TTL_MIN, 30),
    refreshTtlDays: int(env.REFRESH_TOKEN_TTL_DAYS, 7),
    refreshCookie: 'dw_refresh',
    secureCookies: bool(env.SECURE_COOKIES, isProd),
    maxFailedLogins: int(env.MAX_FAILED_LOGINS, 5),
    lockMinutes: int(env.LOCK_MINUTES, 15),
    bcryptRounds: int(env.BCRYPT_ROUNDS, 12),
  },

  crypto: {
    /** Clave maestra usada para cifrar (AES-256-GCM) las llaves privadas RSA en BD. */
    masterKey: requireSecret('MASTER_KEY', 'No podrás descifrar las llaves privadas guardadas.'),
    rsaKeySize: int(env.RSA_KEY_SIZE, 4096),
  },

  bootstrap: {
    adminEmail: env.BOOTSTRAP_ADMIN_EMAIL || 'admin@drywallpro.local',
    adminUsername: env.BOOTSTRAP_ADMIN_USERNAME || 'admin',
    adminPassword: env.BOOTSTRAP_ADMIN_PASSWORD || '',
    autoSeed: bool(env.AUTO_SEED, true),
  },

  web: {
    /** Carpeta con el build de producción de la web (se sirve si existe). */
    dist: env.WEB_DIST || path.join(REPO_ROOT, 'web', 'dist'),
  },

  /**
   * ── Módulos profesionales (planos CAD, precios de distribuidores, AR) ──
   */

  cad: {
    /**
     * Conversor DXF→DWG. Si está definido, el endpoint /api/cad/convert lo
     * usa (p. ej. ODA File Converter). Sin él, el servidor devuelve 501 y la
     * web ofrece abrir el DXF directamente en AutoCAD.
     */
    converterCommand: env.CAD_CONVERTER_CMD || '',
    converterDir: env.CAD_WORK_DIR || path.join(SERVER_ROOT, 'data', 'cad'),
    /** Ruta donde el plotter/reprografía deposita los planos. */
    plotDir: env.CAD_PLOT_DIR || '',
    /** Tamaño máximo del DXF aceptado en la conversión (bytes). */
    maxDxfBytes: int(env.CAD_MAX_DXF_BYTES, 32 * 1024 * 1024),
  },

  pricing: {
    /** Lista blanca de hosts con catálogo de precios (separados por coma). */
    allowedHosts: (env.PRICING_ALLOWED_HOSTS || '')
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean),
    /** Si es true, cualquier host https público está permitido (menos seguro). */
    allowAnyHost: bool(env.PRICING_ALLOW_ANY_HOST, true),
    maxResponseBytes: int(env.PRICING_MAX_RESPONSE_BYTES, 4 * 1024 * 1024),
    timeoutMs: int(env.PRICING_TIMEOUT_MS, 12000),
    /** Límite de peticiones al proxy por minuto e IP. */
    rateLimitPerMinute: int(env.PRICING_RATE_LIMIT, 60),
  },

  ar: {
    /** TTL de las salas de asistencia remota (segundos). */
    roomTtlSeconds: int(env.AR_ROOM_TTL_SECONDS, 1800),
    /** Salas simultáneas máximas. */
    maxRooms: int(env.AR_MAX_ROOMS, 50),
    /** Mensajes de señalización retenidos por sala. */
    maxSignalsPerRoom: int(env.AR_MAX_SIGNALS, 200),
  },
};

/** Deriva una clave AES-256 determinista a partir del MASTER_KEY. */
export function masterAesKey() {
  return crypto.createHash('sha256').update(String(config.crypto.masterKey)).digest();
}

export default config;
