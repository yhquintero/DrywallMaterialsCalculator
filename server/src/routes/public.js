/**
 * Endpoints PÚBLICOS bajo `/api/public` (sin autenticación).
 * Los consumen las apps Android:
 *   GET  /api/public/health              → salud del servicio
 *   GET  /api/public/rates               → tasas publicadas (montado desde routes/rates.js)
 *   GET  /api/public/revocation-status   → ¿está revocada esta firma?
 *   POST /api/public/validate            → veredicto completo de una licencia
 */
import { Router } from 'express';
import { APP_IDS } from '../config/permissions.js';
import { getDb } from '../db/index.js';
import { getActiveKey } from '../services/keyService.js';
import { isSignatureRevoked } from '../services/licenseService.js';
import { Database } from '../db/driver.js';
import config from '../config/index.js';
import { normalizeBase64 } from '../services/cryptoService.js';
import { resolveAppId } from './wellKnown.js';

const NO_STORE = 'no-store, no-cache, must-revalidate';

export const publicApiRouter = Router();

publicApiRouter.get('/health', (_req, res) => {
  res.setHeader('Cache-Control', NO_STORE);
  res.json({
    status: 'ok',
    service: 'drywall-license-console',
    version: '1.0.0',
    time: new Date().toISOString(),
    driver: Database.driver,
    https: Boolean(config.server.https),
  });
});

publicApiRouter.get('/revocation-status', (req, res) => {
  res.setHeader('Cache-Control', NO_STORE);
  const signature = String(req.query.signature || '').trim();
  if (!signature || signature.length > 2000) {
    return res.status(400).json({ error: 'Parámetro "signature" obligatorio' });
  }
  const appId = resolveAppId(req.query);
  const db = getDb();
  const license = db
    .prepare('SELECT license_key, status, expiry_date, user_name FROM licenses WHERE signature = ? AND app_id = ? LIMIT 1')
    .get(signature, appId);
  const revoked = isSignatureRevoked(appId, signature);
  res.json({
    appId,
    revoked,
    status: license?.status ?? 'unknown',
    expiryDate: license?.expiry_date ?? null,
    expired: license ? license.expiry_date <= Date.now() : null,
    licenseKey: license?.license_key ?? null,
    checkedAt: Date.now(),
  });
});

/**
 * Validación en línea: la app envía el JSON de la licencia (y opcionalmente su
 * deviceId actual) y recibe el veredicto — firma registrada, vigencia,
 * revocación y coincidencia de dispositivo.
 */
publicApiRouter.post('/validate', (req, res) => {
  res.setHeader('Cache-Control', NO_STORE);
  const { license, deviceId } = req.body ?? {};
  if (!license || typeof license !== 'object' || !license.signature) {
    return res.status(400).json({ error: 'Envía el JSON de la licencia en "license"' });
  }
  const appId = license.appId && APP_IDS.includes(license.appId) ? license.appId : resolveAppId(license);
  const db = getDb();
  const key = getActiveKey(appId);
  const revoked = isSignatureRevoked(appId, String(license.signature));
  const now = Date.now();
  const expiry = Number(license.expiryDate || 0);
  const expired = expiry <= now;

  // Compara normalizado: la app guarda el deviceId con padding Base64 y la
  // licencia puede llevarlo sin padding (LicensingManager.normalizeBase64).
  const licenseDevice = normalizeBase64(String(license.deviceId || '').trim());
  const currentDevice = deviceId ? normalizeBase64(String(deviceId).trim()) : null;
  const deviceMismatch = Boolean(currentDevice) && licenseDevice !== currentDevice;

  const registered = db
    .prepare('SELECT id, status FROM licenses WHERE signature = ? AND app_id = ? LIMIT 1')
    .get(String(license.signature), appId);

  res.json({
    valid: !revoked && !expired && !deviceMismatch,
    appId,
    revoked,
    expired,
    deviceMismatch,
    registeredInConsole: Boolean(registered),
    consoleStatus: registered?.status ?? null,
    keyId: key?.kid ?? null,
    daysRemaining: expired ? 0 : Math.floor((expiry - now) / 86400000),
    checkedAt: now,
  });
});

export default publicApiRouter;
