import { Router } from 'express';
import { z } from 'zod';
import { getDb } from '../db/index.js';
import { asyncHandler, badRequest, notFound } from '../utils/errors.js';
import { requireAuth, requirePermission } from '../security/auth.js';
import { APP_IDS, APP_META } from '../config/permissions.js';
import { auditFromReq } from '../services/auditService.js';

const router = Router();
router.use(requireAuth);

function mapDevice(d) {
  return {
    id: d.id,
    appId: d.app_id,
    appName: APP_META[d.app_id]?.name ?? d.app_id,
    deviceId: d.device_id,
    deviceLabel: d.device_label,
    ownerName: d.owner_name,
    firstSeenAt: d.first_seen_at,
    lastSeenAt: d.last_seen_at,
    isBlocked: Boolean(d.is_blocked),
    licenseCount: d.license_count,
  };
}

/** GET /api/devices */
router.get(
  '/',
  requirePermission('licenses.view'),
  asyncHandler((req, res) => {
    const db = getDb();
    const appId = APP_IDS.includes(req.query.appId) ? req.query.appId : null;
    const search = req.query.search ? `%${req.query.search}%` : null;
    let rows;
    if (appId && search) rows = db.prepare('SELECT * FROM devices WHERE app_id = ? AND (device_id LIKE ? OR owner_name LIKE ? OR device_label LIKE ?) ORDER BY last_seen_at DESC LIMIT 500').all(appId, search, search, search);
    else if (appId) rows = db.prepare('SELECT * FROM devices WHERE app_id = ? ORDER BY last_seen_at DESC LIMIT 500').all(appId);
    else if (search) rows = db.prepare('SELECT * FROM devices WHERE device_id LIKE ? OR owner_name LIKE ? OR device_label LIKE ? ORDER BY last_seen_at DESC LIMIT 500').all(search, search, search);
    else rows = db.prepare('SELECT * FROM devices ORDER BY last_seen_at DESC LIMIT 500').all();
    res.json({ items: rows.map(mapDevice), total: rows.length });
  })
);

/** PATCH /api/devices/:id — bloquear/desbloquear o etiquetar un dispositivo. */
router.patch(
  '/:id',
  requirePermission('licenses.revoke'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare('SELECT * FROM devices WHERE id = ?').get(id);
    if (!row) throw notFound('Dispositivo no encontrado');
    const parsed = z
      .object({ isBlocked: z.boolean().optional(), deviceLabel: z.string().trim().max(120).optional() })
      .safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos inválidos', parsed.error.flatten());
    const sets = [];
    const params = [];
    if (parsed.data.isBlocked !== undefined) { sets.push('is_blocked = ?'); params.push(parsed.data.isBlocked ? 1 : 0); }
    if (parsed.data.deviceLabel !== undefined) { sets.push('device_label = ?'); params.push(parsed.data.deviceLabel); }
    if (!sets.length) throw badRequest('Nada que actualizar');
    params.push(id);
    db.prepare(`UPDATE devices SET ${sets.join(', ')} WHERE id = ?`).run(...params);
    if (parsed.data.isBlocked !== undefined) {
      db.prepare("UPDATE licenses SET status = CASE WHEN ? = 1 THEN 'blocked' ELSE status END WHERE device_id = ? AND app_id = ? AND status NOT IN ('revoked','expired')")
        .run(parsed.data.isBlocked ? 1 : 0, row.device_id, row.app_id);
    }
    auditFromReq(req, parsed.data.isBlocked ? 'devices.block' : 'devices.update', {
      entity: 'device', entityId: String(id), appId: row.app_id,
      severity: parsed.data.isBlocked ? 'critical' : 'info', detail: parsed.data,
    });
    res.json(mapDevice(db.prepare('SELECT * FROM devices WHERE id = ?').get(id)));
  })
);

export default router;
