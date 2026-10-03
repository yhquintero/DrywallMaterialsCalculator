/**
 * Endpoints `/.well-known/*` — PÚBLICOS, sin autenticación.
 * Los consumen las apps Android:
 *   • RemoteKeyProvider → licensing-public-key-v2.pem
 *   • Pantalla de activación → licensing-public-key.json / apps.json
 */
import { Router } from 'express';
import { APP_IDS, APP_META } from '../config/permissions.js';
import { getDb } from '../db/index.js';
import { getActiveKey } from '../services/keyService.js';

const router = Router();

export function resolveAppId(query = {}) {
  const candidate = String(query.app || query.appId || '');
  if (APP_IDS.includes(candidate)) return candidate;
  if (candidate.toLowerCase().includes('keygen')) return 'keygen_pro';
  return 'drywall_calculator';
}

function sendPem(res, pem, filename) {
  res.setHeader('Content-Type', 'application/x-pem-file; charset=utf-8');
  res.setHeader('Cache-Control', 'public, max-age=300');
  res.setHeader('Content-Disposition', `inline; filename="${filename}"`);
  res.send(pem);
}

router.get('/licensing-public-key.pem', (req, res) => {
  const key = getActiveKey(resolveAppId(req.query));
  if (!key) return res.status(503).json({ error: 'Clave de firma no disponible' });
  sendPem(res, key.publicKeyPem, 'licensing-public-key.pem');
});

/** Alias exacto que usa `RemoteKeyProvider.KEY_SERVER_URL`. */
router.get('/licensing-public-key-v2.pem', (req, res) => {
  const key = getActiveKey(resolveAppId(req.query));
  if (!key) return res.status(503).json({ error: 'Clave de firma no disponible' });
  sendPem(res, key.publicKeyPem, 'licensing-public-key-v2.pem');
});

router.get('/licensing-public-key.json', (req, res) => {
  const appId = resolveAppId(req.query);
  const key = getActiveKey(appId);
  if (!key) return res.status(503).json({ error: 'Clave de firma no disponible' });
  res.setHeader('Cache-Control', 'public, max-age=300');
  res.json({
    app: APP_META[appId],
    kid: key.kid,
    algorithm: key.algorithm,
    modulusBits: key.modulusBits,
    publicKeyBase64: key.publicKeyB64,
    publicKeySha256: key.publicKeySha256,
    fingerprint: key.fingerprint,
    publishedAt: new Date(key.createdAt).toISOString(),
    verifyWith: 'SHA256withRSA',
    dataToSign: 'user|deviceId|creationDate|expiryDate',
  });
});

router.get('/apps.json', (_req, res) => {
  const db = getDb();
  res.setHeader('Cache-Control', 'public, max-age=300');
  res.json({
    apps: APP_IDS.map((id) => {
      const key = getActiveKey(id);
      return {
        ...APP_META[id],
        keyId: key?.kid ?? null,
        hasActiveKey: Boolean(key),
        publicKeySha256: key?.publicKeySha256 ?? null,
        plans: db
          .prepare(
            'SELECT code, label, type_name, days, price, currency FROM plans WHERE app_id = ? AND is_active = 1 ORDER BY sort_order'
          )
          .all(id),
      };
    }),
  });
});

export default router;
