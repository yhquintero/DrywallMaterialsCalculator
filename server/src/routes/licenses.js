import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler, badRequest, notFound } from '../utils/errors.js';
import { requireAuth, requirePermission } from '../security/auth.js';
import { getDb } from '../db/index.js';
import { APP_IDS } from '../config/permissions.js';
import {
  queryLicenses,
  getLicense,
  issueLicense,
  renewLicense,
  revokeLicense,
  markPaid,
  deleteLicense,
  importLicenses,
  listBlacklist,
  getStats,
} from '../services/licenseService.js';
import { auditFromReq } from '../services/auditService.js';

const router = Router();

/** BOM UTF-8: hace que Excel/Sheets detecten la codificación al abrir el CSV. */
const UTF8_BOM = String.fromCharCode(0xfeff);
router.use(requireAuth);

const appFilter = (v) => (v && APP_IDS.includes(v) ? v : undefined);

/** Un CLIENT solo ve lo suyo: se fuerza el filtro por su nombre/usuario. */
function scopeFor(req) {
  if (req.user.roleCode !== 'CLIENT') return {};
  const identity = req.user.fullName || req.user.username;
  return { search: identity, appId: appFilter(req.query.appId) };
}

/** GET /api/licenses */
router.get(
  '/',
  requirePermission('licenses.view'),
  asyncHandler((req, res) => {
    const q = req.query;
    const result = queryLicenses({
      appId: appFilter(q.appId),
      status: q.status ? String(q.status) : undefined,
      search: q.search ? String(q.search) : undefined,
      deviceId: q.deviceId ? String(q.deviceId) : undefined,
      paid: q.paid === undefined ? undefined : q.paid === 'true' || q.paid === '1',
      expiringInDays: q.expiringInDays ? Number(q.expiringInDays) : undefined,
      sortBy: q.sortBy ? String(q.sortBy) : 'created_at',
      sortDir: q.sortDir ? String(q.sortDir) : 'DESC',
      limit: Number(q.limit) || 50,
      offset: Number(q.offset) || 0,
      ...scopeFor(req),
    });
    res.json(result);
  })
);

/** GET /api/licenses/stats */
router.get(
  '/stats',
  requirePermission('dashboard.view'),
  asyncHandler((req, res) => {
    res.json(getStats(appFilter(req.query.appId)));
  })
);

/** GET /api/licenses/blacklist */
router.get(
  '/blacklist',
  requirePermission('licenses.view'),
  asyncHandler((req, res) => {
    res.json({ items: listBlacklist(appFilter(req.query.appId)) });
  })
);

/** GET /api/licenses/export.json */
router.get(
  '/export.json',
  requirePermission('licenses.export'),
  asyncHandler((req, res) => {
    const { items } = queryLicenses({ appId: appFilter(req.query.appId), limit: 500 });
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="licencias_${Date.now()}.json"`);
    res.json({ exportedAt: Date.now(), app: req.query.appId ?? 'all', count: items.length, licenses: items.map((l) => l.licenseJson) });
  })
);

/** GET /api/licenses/export.csv */
router.get(
  '/export.csv',
  requirePermission('licenses.export'),
  asyncHandler((req, res) => {
    const { items } = queryLicenses({ appId: appFilter(req.query.appId), limit: 500 });
    const headers = ['id', 'license_key', 'app', 'usuario', 'device_id', 'plan', 'precio', 'moneda', 'emitida', 'vence', 'estado', 'cobrada', 'firma'];
    const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [headers.join(',')];
    for (const l of items) {
      lines.push(
        [
          l.id, l.licenseKey, l.appName, l.userName, l.deviceId, l.planLabel,
          l.price.toFixed(2), l.currency,
          new Date(l.creationDate).toISOString(), new Date(l.expiryDate).toISOString(),
          l.status, l.isPaid ? 'SI' : 'NO', l.signature.slice(0, 32) + '…',
        ].map(esc).join(',')
      );
    }
    // Prefijo BOM para que Excel reconozca UTF-8.
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="licencias_${Date.now()}.csv"`);
    res.send(Buffer.from(UTF8_BOM + lines.join('\n'), 'utf8'));
  })
);

/** GET /api/licenses/:id */
router.get(
  '/:id',
  requirePermission('licenses.view'),
  asyncHandler((req, res) => {
    const license = getLicense(Number(req.params.id));
    if (!license) throw notFound('Licencia no encontrada');
    if (req.user.roleCode === 'CLIENT' && license.userName !== (req.user.fullName || req.user.username)) {
      throw notFound('Licencia no encontrada');
    }
    const db = getDb();
    const chain = db
      .prepare('SELECT id, license_key, creation_date, expiry_date, status FROM licenses WHERE renewed_from = ? OR id = ? ORDER BY creation_date')
      .all(license.id, license.id);
    res.json({ ...license, renewalChain: chain });
  })
);

/** GET /api/licenses/:id/download — el .json que se envía al cliente. */
router.get(
  '/:id/download',
  requirePermission('licenses.export'),
  asyncHandler((req, res) => {
    const license = getLicense(Number(req.params.id));
    if (!license) throw notFound('Licencia no encontrada');
    auditFromReq(req, 'licenses.download', { entity: 'license', entityId: String(license.id), appId: license.appId });
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="Licencia_${license.licenseKey}.json"`);
    res.send(JSON.stringify(license.licenseJson, null, 2));
  })
);

const issueSchema = z.object({
  appId: z.enum(['drywall_calculator', 'keygen_pro']).default('drywall_calculator'),
  userName: z.string().trim().min(1).max(100),
  deviceId: z.string().trim().min(4).max(200),
  deviceLabel: z.string().trim().max(120).optional(),
  planCode: z.string().trim().max(40).optional(),
  planLabel: z.string().trim().max(60).optional(),
  days: z.coerce.number().int().positive().max(36500).optional(),
  price: z.coerce.number().min(0).max(1_000_000).optional(),
  currency: z.string().trim().length(3).toUpperCase().optional(),
  email: z.string().trim().email().max(160).optional().or(z.literal('')),
  phone: z.string().trim().max(40).optional().or(z.literal('')),
  notes: z.string().trim().max(1000).optional(),
  paymentMethod: z.string().trim().max(60).optional(),
  markPaid: z.boolean().optional(),
});

/** POST /api/licenses — emite y FIRMA una licencia nueva. */
router.post(
  '/',
  requirePermission('licenses.issue'),
  asyncHandler((req, res) => {
    const parsed = issueSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos de licencia inválidos', parsed.error.flatten());
    const license = issueLicense(parsed.data, req.user);
    res.status(201).json(license);
  })
);

/** POST /api/licenses/:id/renew */
router.post(
  '/:id/renew',
  requirePermission('licenses.renew'),
  asyncHandler((req, res) => {
    const schema = z.object({
      planCode: z.string().trim().max(40).optional(),
      days: z.coerce.number().int().positive().max(36500).optional(),
      price: z.coerce.number().min(0).optional(),
      currency: z.string().trim().length(3).toUpperCase().optional(),
      notes: z.string().trim().max(1000).optional(),
      markPaid: z.boolean().optional(),
    });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos de renovación inválidos', parsed.error.flatten());
    const license = renewLicense(Number(req.params.id), parsed.data, req.user);
    res.status(201).json(license);
  })
);

/** POST /api/licenses/:id/revoke */
router.post(
  '/:id/revoke',
  requirePermission('licenses.revoke'),
  asyncHandler((req, res) => {
    const schema = z.object({ reason: z.string().trim().max(500).default('') });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Motivo inválido', parsed.error.flatten());
    res.json(revokeLicense(Number(req.params.id), parsed.data.reason, req.user));
  })
);

/** POST /api/licenses/:id/mark-paid */
router.post(
  '/:id/mark-paid',
  requirePermission('licenses.mark_paid'),
  asyncHandler((req, res) => {
    const schema = z.object({
      paymentMethod: z.string().trim().max(60).optional(),
      price: z.coerce.number().min(0).optional(),
      currency: z.string().trim().length(3).toUpperCase().optional(),
    });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos de pago inválidos', parsed.error.flatten());
    res.json(markPaid(Number(req.params.id), parsed.data, req.user));
  })
);

/** POST /api/licenses/import — ingesta licencias emitidas por las apps Android. */
router.post(
  '/import',
  requirePermission('licenses.import'),
  asyncHandler((req, res) => {
    const body = req.body ?? {};
    const entries = Array.isArray(body) ? body : body.licenses ?? body.items ?? [];
    if (!Array.isArray(entries) || entries.length === 0) throw badRequest('Envía un array de licencias en "licenses"');
    if (entries.length > 5000) throw badRequest('Máximo 5000 licencias por importación');
    res.json(importLicenses(entries, req.user));
  })
);

/** PATCH /api/licenses/:id — datos de contacto/notas. */
router.patch(
  '/:id',
  requirePermission('licenses.issue'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare('SELECT * FROM licenses WHERE id = ?').get(id);
    if (!row) throw notFound('Licencia no encontrada');
    const schema = z.object({
      email: z.string().trim().email().max(160).nullable().optional().or(z.literal('')),
      phone: z.string().trim().max(40).nullable().optional().or(z.literal('')),
      notes: z.string().trim().max(1000).optional(),
      deviceLabel: z.string().trim().max(120).nullable().optional().or(z.literal('')),
      userName: z.string().trim().min(1).max(100).optional(),
    });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos inválidos', parsed.error.flatten());
    const data = parsed.data;
    const sets = ['updated_at = ?'];
    const params = [Date.now()];
    for (const [field, column] of [['email', 'email'], ['phone', 'phone'], ['notes', 'notes'], ['deviceLabel', 'device_label'], ['userName', 'user_name']]) {
      if (data[field] !== undefined) { sets.push(`${column} = ?`); params.push(data[field] || null); }
    }
    params.push(id);
    db.prepare(`UPDATE licenses SET ${sets.join(', ')} WHERE id = ?`).run(...params);
    auditFromReq(req, 'licenses.update', { entity: 'license', entityId: String(id), appId: row.app_id, detail: data });
    res.json(getLicense(id));
  })
);

/** DELETE /api/licenses/:id */
router.delete(
  '/:id',
  requirePermission('licenses.delete'),
  asyncHandler((req, res) => {
    deleteLicense(Number(req.params.id), req.user);
    res.json({ ok: true });
  })
);

export default router;
