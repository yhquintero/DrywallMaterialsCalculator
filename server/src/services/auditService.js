import { getDb } from '../db/index.js';

const SEVERITIES = new Set(['debug', 'info', 'warn', 'critical']);

/**
 * Registra un evento de auditoría. Nunca lanza: un fallo de log no debe
 * romper la operación principal.
 */
export function audit({
  actor = null,
  action,
  entity = '',
  entityId = '',
  appId = null,
  severity = 'info',
  ip = '',
  userAgent = '',
  detail = '',
}) {
  try {
    const db = getDb();
    const sev = SEVERITIES.has(severity) ? severity : 'info';
    const payload = typeof detail === 'string' ? detail : JSON.stringify(detail ?? {});
    db.prepare(
      `INSERT INTO audit_log
        (actor_id, actor_name, action, entity, entity_id, app_id, severity, ip_address, user_agent, detail, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(
      actor?.id ?? null,
      actor?.username ?? 'sistema',
      action,
      entity,
      String(entityId ?? ''),
      appId,
      sev,
      ip || '',
      userAgent || '',
      payload.slice(0, 8000),
      Date.now()
    );
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error('[audit] no se pudo registrar el evento:', err.message);
  }
}

/** Helper para rutas: extrae actor/ip/ua de la request. */
export function auditFromReq(req, action, extra = {}) {
  return audit({
    actor: req.user ?? null,
    action,
    ip: req.ip || req.socket?.remoteAddress || '',
    userAgent: String(req.get('user-agent') || '').slice(0, 300),
    ...extra,
  });
}

export function queryAudit({ limit = 100, offset = 0, action, actorId, severity, appId, entity, since, until, search }) {
  const db = getDb();
  const where = [];
  const params = [];

  if (action) { where.push('action = ?'); params.push(action); }
  if (actorId) { where.push('actor_id = ?'); params.push(actorId); }
  if (severity) { where.push('severity = ?'); params.push(severity); }
  if (appId) { where.push('app_id = ?'); params.push(appId); }
  if (entity) { where.push('entity = ?'); params.push(entity); }
  if (since) { where.push('created_at >= ?'); params.push(since); }
  if (until) { where.push('created_at <= ?'); params.push(until); }
  if (search) {
    where.push('(action LIKE ? OR detail LIKE ? OR actor_name LIKE ? OR entity_id LIKE ?)');
    const like = `%${search}%`;
    params.push(like, like, like, like);
  }

  const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const rows = db
    .prepare(`SELECT * FROM audit_log ${clause} ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?`)
    .all(...params, Math.min(Math.max(limit, 1), 1000), Math.max(offset, 0));
  const total = db.prepare(`SELECT COUNT(*) AS c FROM audit_log ${clause}`).get(...params).c;
  return { items: rows, total };
}
