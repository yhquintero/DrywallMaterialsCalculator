import { Router } from 'express';
import { z } from 'zod';
import { getDb } from '../db/index.js';
import { asyncHandler, badRequest, notFound, conflict } from '../utils/errors.js';
import { requireAuth, requirePermission } from '../security/auth.js';
import { APP_IDS } from '../config/permissions.js';
import { auditFromReq } from '../services/auditService.js';
import { listPlans } from '../services/licenseService.js';

const router = Router();
router.use(requireAuth);

function mapPlan(row) {
  return {
    id: row.id, appId: row.app_id, code: row.code, label: row.label, typeName: row.type_name,
    days: row.days, price: row.price, currency: row.currency,
    isActive: Boolean(row.is_active), sortOrder: row.sort_order,
    createdAt: row.created_at, updatedAt: row.updated_at,
  };
}

/** GET /api/plans?appId= */
router.get(
  '/',
  requirePermission('licenses.view'),
  asyncHandler((req, res) => {
    const appId = APP_IDS.includes(req.query.appId) ? req.query.appId : null;
    const db = getDb();
    const rows = appId
      ? db.prepare('SELECT * FROM plans WHERE app_id = ? ORDER BY sort_order').all(appId)
      : db.prepare('SELECT * FROM plans ORDER BY app_id, sort_order').all();
    res.json({ items: rows.map(mapPlan) });
  })
);

const planSchema = z.object({
  appId: z.enum(['drywall_calculator', 'keygen_pro']),
  code: z.string().trim().min(2).max(40).regex(/^[A-Z0-9_]+$/),
  label: z.string().trim().min(2).max(80),
  typeName: z.string().trim().min(2).max(80),
  days: z.coerce.number().int().positive().max(36500),
  price: z.coerce.number().min(0).max(1_000_000),
  currency: z.string().trim().length(3).toUpperCase().default('USD'),
  isActive: z.boolean().default(true),
  sortOrder: z.coerce.number().int().min(0).max(999).default(0),
});

/** POST /api/plans */
router.post(
  '/',
  requirePermission('plans.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const parsed = planSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Plan inválido', parsed.error.flatten());
    const d = parsed.data;
    if (db.prepare('SELECT id FROM plans WHERE app_id = ? AND code = ?').get(d.appId, d.code)) {
      throw conflict('Ya existe un plan con ese código para esta app');
    }
    const ts = Date.now();
    const id = db
      .prepare(
        `INSERT INTO plans (app_id, code, label, type_name, days, price, currency, is_active, sort_order, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .insert(d.appId, d.code, d.label, d.typeName, d.days, d.price, d.currency, d.isActive ? 1 : 0, d.sortOrder, ts, ts);
    auditFromReq(req, 'plans.create', { entity: 'plan', entityId: String(id), appId: d.appId, detail: d });
    res.status(201).json(mapPlan(db.prepare('SELECT * FROM plans WHERE id = ?').get(id)));
  })
);

/** GET /api/plans/active/:appId — lista corta para el formulario de emisión. */
router.get(
  '/active/:appId',
  requirePermission('licenses.view'),
  asyncHandler((req, res) => {
    const { appId } = req.params;
    if (!APP_IDS.includes(appId)) throw badRequest('appId inválido');
    res.json({ items: listPlans(appId) });
  })
);

/** PATCH /api/plans/:id */
router.patch(
  '/:id',
  requirePermission('plans.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare('SELECT * FROM plans WHERE id = ?').get(id);
    if (!row) throw notFound('Plan no encontrado');
    const parsed = planSchema.partial().safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos inválidos', parsed.error.flatten());
    const d = parsed.data;
    const sets = ['updated_at = ?'];
    const params = [Date.now()];
    const columns = { label: 'label', typeName: 'type_name', days: 'days', price: 'price', currency: 'currency', isActive: 'is_active', sortOrder: 'sort_order', code: 'code' };
    for (const [key, column] of Object.entries(columns)) {
      if (d[key] === undefined) continue;
      sets.push(`${column} = ?`);
      params.push(typeof d[key] === 'boolean' ? (d[key] ? 1 : 0) : d[key]);
    }
    params.push(id);
    db.prepare(`UPDATE plans SET ${sets.join(', ')} WHERE id = ?`).run(...params);
    auditFromReq(req, 'plans.update', { entity: 'plan', entityId: String(id), appId: row.app_id, detail: d });
    res.json(mapPlan(db.prepare('SELECT * FROM plans WHERE id = ?').get(id)));
  })
);

/** DELETE /api/plans/:id */
router.delete(
  '/:id',
  requirePermission('plans.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare('SELECT * FROM plans WHERE id = ?').get(id);
    if (!row) throw notFound('Plan no encontrado');
    const inUse = db.prepare('SELECT COUNT(*) c FROM licenses WHERE plan_code = ? AND app_id = ?').get(row.code, row.app_id).c;
    if (inUse > 0) {
      db.prepare('UPDATE plans SET is_active = 0, updated_at = ? WHERE id = ?').run(Date.now(), id);
      auditFromReq(req, 'plans.deactivate', { entity: 'plan', entityId: String(id), appId: row.app_id, detail: { inUse } });
      return res.json({ ok: true, deactivated: true, reason: `Tiene ${inUse} licencia(s) asociada(s): se desactivó en lugar de borrar` });
    }
    db.prepare('DELETE FROM plans WHERE id = ?').run(id);
    auditFromReq(req, 'plans.delete', { entity: 'plan', entityId: String(id), appId: row.app_id, severity: 'warn' });
    res.json({ ok: true });
  })
);

export default router;
