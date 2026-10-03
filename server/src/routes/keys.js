import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler, badRequest, notFound } from '../utils/errors.js';
import { requireAuth, requirePermission, requireRole } from '../security/auth.js';
import { APP_IDS, APP_META } from '../config/permissions.js';
import { getDb } from '../db/index.js';
import {
  listKeys,
  getActiveKey,
  ensureActiveKey,
  rotateKey,
  exportPrivateKeyPem,
  importPrivateKey,
  deleteKey,
} from '../services/keyService.js';
import { auditFromReq } from '../services/auditService.js';

const router = Router();
router.use(requireAuth);

function stripPrivate(key) {
  if (!key) return null;
  const { _row, ...rest } = key;
  return rest;
}

/** GET /api/keys */
router.get(
  '/',
  requirePermission('keys.view'),
  asyncHandler((req, res) => {
    const appId = APP_IDS.includes(req.query.appId) ? req.query.appId : null;
    res.json({ items: listKeys(appId).map(stripPrivate) });
  })
);

/** GET /api/apps — metadata de las dos apps gestionadas. */
router.get(
  '/meta/apps',
  requirePermission('keys.view'),
  asyncHandler((_req, res) => {
    const db = getDb();
    const counts = db
      .prepare('SELECT app_id, COUNT(*) total FROM licenses GROUP BY app_id')
      .all()
      .reduce((acc, r) => ({ ...acc, [r.app_id]: r.total }), {});
    res.json({
      items: APP_IDS.map((id) => ({
        ...APP_META[id],
        licenseCount: counts[id] ?? 0,
        hasActiveKey: Boolean(getActiveKey(id)),
      })),
    });
  })
);

/** GET /api/keys/active/:appId — clave pública activa (para copiar a la app). */
router.get(
  '/active/:appId',
  requirePermission('keys.view'),
  asyncHandler((req, res) => {
    const { appId } = req.params;
    if (!APP_IDS.includes(appId)) throw badRequest('appId inválido');
    const key = ensureActiveKey(appId, req.user);
    res.json(stripPrivate(key));
  })
);

/** GET /api/keys/:appId/public.pem */
router.get(
  '/:appId/public.pem',
  requirePermission('keys.view'),
  asyncHandler((req, res) => {
    const { appId } = req.params;
    if (!APP_IDS.includes(appId)) throw badRequest('appId inválido');
    const key = ensureActiveKey(appId, req.user);
    auditFromReq(req, 'keys.export_public', { entity: 'signing_key', entityId: String(key.id), appId });
    res.setHeader('Content-Type', 'application/x-pem-file; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="licensing-public-key-${appId}.pem"`);
    res.send(key.publicKeyPem);
  })
);

/** POST /api/keys/:appId/rotate */
router.post(
  '/:appId/rotate',
  requirePermission('keys.rotate'),
  asyncHandler((req, res) => {
    const { appId } = req.params;
    if (!APP_IDS.includes(appId)) throw badRequest('appId inválido');
    const schema = z.object({
      notes: z.string().trim().max(500).optional(),
      confirm: z.boolean().optional(),
    });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos inválidos', parsed.error.flatten());
    if (parsed.data.confirm !== true) {
      throw badRequest('Debes confirmar la rotación con { "confirm": true }: las licencias nuevas usarán la clave nueva');
    }
    const key = rotateKey(appId, req.user, parsed.data.notes ?? 'Rotación manual desde la consola web');
    res.status(201).json(stripPrivate(key));
  })
);

/** POST /api/keys/:appId/import — trae la llave privada del keygen Android. */
router.post(
  '/:appId/import',
  requirePermission('keys.rotate'),
  asyncHandler((req, res) => {
    const { appId } = req.params;
    if (!APP_IDS.includes(appId)) throw badRequest('appId inválido');
    const schema = z.object({
      privateKey: z.string().min(100).max(20000),
      notes: z.string().trim().max(500).optional(),
    });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Clave privada inválida', parsed.error.flatten());
    try {
      const key = importPrivateKey(appId, parsed.data.privateKey, req.user, parsed.data.notes ?? '');
      res.status(201).json(stripPrivate(key));
    } catch (err) {
      throw badRequest(`No se pudo importar la llave: ${err.message}`);
    }
  })
);

/** GET /api/keys/:appId/export-private — PELIGROSO, solo Admin. */
router.post(
  '/:appId/export-private',
  requirePermission('keys.export_private'),
  requireRole('ADMIN'),
  asyncHandler((req, res) => {
    const { appId } = req.params;
    if (!APP_IDS.includes(appId)) throw badRequest('appId inválido');
    const schema = z.object({ confirm: z.literal(true), reason: z.string().trim().min(4).max(300) });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Confirma la exportación e indica un motivo', parsed.error.flatten());
    const result = exportPrivateKeyPem(appId, req.user);
    res.json(result);
  })
);

/** DELETE /api/keys/:id */
router.delete(
  '/:id',
  requirePermission('keys.rotate'),
  asyncHandler((req, res) => {
    const ok = deleteKey(Number(req.params.id), req.user);
    if (!ok) throw notFound('Clave no encontrada');
    res.json({ ok: true });
  })
);

export default router;
