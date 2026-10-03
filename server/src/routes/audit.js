import { Router } from 'express';
import { asyncHandler } from '../utils/errors.js';
import { requireAuth, requirePermission } from '../security/auth.js';
import { queryAudit } from '../services/auditService.js';
import { getDb } from '../db/index.js';

const router = Router();

/** BOM UTF-8: hace que Excel/Sheets detecten la codificación al abrir el CSV. */
const UTF8_BOM = String.fromCharCode(0xfeff);
router.use(requireAuth);

function parseFilters(q) {
  return {
    limit: Number(q.limit) || 100,
    offset: Number(q.offset) || 0,
    action: q.action ? String(q.action) : undefined,
    actorId: q.actorId ? Number(q.actorId) : undefined,
    severity: q.severity ? String(q.severity) : undefined,
    appId: q.appId ? String(q.appId) : undefined,
    entity: q.entity ? String(q.entity) : undefined,
    since: q.since ? Number(q.since) : undefined,
    until: q.until ? Number(q.until) : undefined,
    search: q.search ? String(q.search) : undefined,
  };
}

/** GET /api/audit */
router.get(
  '/',
  requirePermission('audit.view'),
  asyncHandler((req, res) => {
    const result = queryAudit(parseFilters(req.query));
    const db = getDb();
    const actions = db
      .prepare('SELECT action, COUNT(*) c FROM audit_log GROUP BY action ORDER BY c DESC LIMIT 60')
      .all();
    res.json({ ...result, availableActions: actions });
  })
);

/** GET /api/audit/export.csv */
router.get(
  '/export.csv',
  requirePermission('audit.export'),
  asyncHandler((req, res) => {
    const { items } = queryAudit({ ...parseFilters(req.query), limit: 1000 });
    const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [['fecha', 'actor', 'accion', 'entidad', 'entidad_id', 'app', 'severidad', 'ip', 'detalle'].join(',')];
    for (const r of items) {
      lines.push(
        [new Date(r.created_at).toISOString(), r.actor_name, r.action, r.entity, r.entity_id, r.app_id ?? '', r.severity, r.ip_address, r.detail]
          .map(esc)
          .join(',')
      );
    }
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="auditoria_${Date.now()}.csv"`);
    res.send(Buffer.from(UTF8_BOM + lines.join('\n'), 'utf8'));
  })
);

/** GET /api/audit/summary */
router.get(
  '/summary',
  requirePermission('audit.view'),
  asyncHandler((_req, res) => {
    const db = getDb();
    const since = Date.now() - 24 * 3600000;
    res.json({
      last24h: db.prepare('SELECT COUNT(*) c FROM audit_log WHERE created_at >= ?').get(since).c,
      critical24h: db.prepare("SELECT COUNT(*) c FROM audit_log WHERE created_at >= ? AND severity = 'critical'").get(since).c,
      failedLogins24h: db.prepare("SELECT COUNT(*) c FROM audit_log WHERE created_at >= ? AND action = 'auth.login_failed'").get(since).c,
      bySeverity: db.prepare('SELECT severity, COUNT(*) c FROM audit_log GROUP BY severity').all(),
      recent: db.prepare('SELECT id, actor_name, action, severity, created_at FROM audit_log ORDER BY id DESC LIMIT 15').all(),
    });
  })
);

export default router;
