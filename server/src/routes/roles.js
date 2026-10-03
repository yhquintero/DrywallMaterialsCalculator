import { Router } from 'express';
import { z } from 'zod';
import { getDb } from '../db/index.js';
import { asyncHandler, badRequest, notFound, conflict, forbidden } from '../utils/errors.js';
import { requireAuth, requirePermission, revokeAllSessions } from '../security/auth.js';
import { auditFromReq } from '../services/auditService.js';
import { PERMISSIONS, PERMISSION_GROUPS, PERMISSION_IDS } from '../config/permissions.js';

const router = Router();
router.use(requireAuth);

function mapRole(row, permissionCodes = []) {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    description: row.description,
    level: row.level,
    isSystem: Boolean(row.is_system),
    isActive: Boolean(row.is_active),
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    permissions: permissionCodes,
    isWildcard: permissionCodes.includes('*'),
  };
}

function permissionsOfRole(db, roleId) {
  return db
    .prepare(
      `SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = ?`
    )
    .all(roleId)
    .map((r) => r.code);
}

/** GET /api/roles */
router.get(
  '/',
  requirePermission('roles.view'),
  asyncHandler((_req, res) => {
    const db = getDb();
    const roles = db.prepare('SELECT * FROM roles ORDER BY level DESC').all();
    const counts = db
      .prepare('SELECT role_id, COUNT(*) c FROM users GROUP BY role_id')
      .all()
      .reduce((acc, r) => ({ ...acc, [r.role_id]: r.c }), {});
    res.json({
      items: roles.map((r) => ({ ...mapRole(r, permissionsOfRole(db, r.id)), userCount: counts[r.id] ?? 0 })),
    });
  })
);

/** GET /api/roles/permissions — catálogo completo agrupado (para la UI de RBAC). */
router.get(
  '/permissions',
  requirePermission('roles.view'),
  asyncHandler((_req, res) => {
    res.json({ groups: PERMISSION_GROUPS, permissions: PERMISSIONS });
  })
);

/** GET /api/roles/:id */
router.get(
  '/:id',
  requirePermission('roles.view'),
  asyncHandler((req, res) => {
    const db = getDb();
    const row = db.prepare('SELECT * FROM roles WHERE id = ?').get(Number(req.params.id));
    if (!row) throw notFound('Rol no encontrado');
    const users = db.prepare('SELECT id, username, full_name, is_active FROM users WHERE role_id = ?').all(row.id);
    res.json({ ...mapRole(row, permissionsOfRole(db, row.id)), users });
  })
);

const roleSchema = z.object({
  code: z.string().trim().min(2).max(30).regex(/^[A-Z0-9_]+$/, 'Código en MAYÚSCULAS sin espacios'),
  name: z.string().trim().min(2).max(80),
  description: z.string().trim().max(300).default(''),
  level: z.coerce.number().int().min(0).max(999).default(10),
  permissions: z.array(z.string()).default([]),
  isActive: z.boolean().default(true),
});

/** POST /api/roles */
router.post(
  '/',
  requirePermission('roles.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const parsed = roleSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Rol inválido', parsed.error.flatten());
    const data = parsed.data;
    if (db.prepare('SELECT id FROM roles WHERE code = ?').get(data.code)) throw conflict('Ya existe un rol con ese código');

    const unknown = data.permissions.filter((p) => p !== '*' && !PERMISSION_IDS.includes(p));
    if (unknown.length) throw badRequest(`Permisos desconocidos: ${unknown.join(', ')}`);
    if (data.permissions.includes('*') && data.code !== 'ADMIN') {
      throw forbidden('El permiso comodín "*" es exclusivo del rol ADMIN');
    }

    const ts = Date.now();
    const id = db.transaction(() => {
      const newId = db
        .prepare('INSERT INTO roles (code, name, description, level, is_system, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, 0, ?, ?, ?)')
        .insert(data.code, data.name, data.description, data.level, data.isActive ? 1 : 0, ts, ts);
      applyPermissions(db, newId, data.permissions);
      return newId;
    });

    auditFromReq(req, 'roles.create', { entity: 'role', entityId: String(id), detail: { code: data.code, permissions: data.permissions.length } });
    const row = db.prepare('SELECT * FROM roles WHERE id = ?').get(id);
    res.status(201).json(mapRole(row, permissionsOfRole(db, id)));
  })
);

/** PATCH /api/roles/:id */
router.patch(
  '/:id',
  requirePermission('roles.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare('SELECT * FROM roles WHERE id = ?').get(id);
    if (!row) throw notFound('Rol no encontrado');

    const parsed = roleSchema.partial().safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos de rol inválidos', parsed.error.flatten());
    const data = parsed.data;

    const affectedAdmins = db
      .prepare('SELECT id FROM users WHERE role_id = ? AND is_active = 1')
      .all(id);
    const wouldRemoveLastAdmin =
      row.code === 'ADMIN' &&
      (data.permissions || data.isActive === false) &&
      db.prepare("SELECT COUNT(*) c FROM users u JOIN roles r ON r.id=u.role_id WHERE r.code='ADMIN' AND u.is_active=1").get().c <= 1;
    if (wouldRemoveLastAdmin) throw conflict('No puedes dejar al sistema sin Administrador activo');

    if (row.is_system && data.code && data.code !== row.code) {
      throw forbidden('No se puede cambiar el código de un rol del sistema');
    }
    if (data.permissions) {
      const unknown = data.permissions.filter((p) => p !== '*' && !PERMISSION_IDS.includes(p));
      if (unknown.length) throw badRequest(`Permisos desconocidos: ${unknown.join(', ')}`);
      if (row.code === 'ADMIN' && !data.permissions.includes('*')) {
        throw forbidden('El rol ADMIN debe conservar el permiso comodín "*"');
      }
      if (data.permissions.includes('*') && row.code !== 'ADMIN') {
        throw forbidden('El permiso comodín "*" es exclusivo del rol ADMIN');
      }
    }

    const ts = Date.now();
    db.transaction(() => {
      const sets = ['updated_at = ?'];
      const params = [ts];
      for (const field of ['code', 'name', 'description']) {
        if (data[field] !== undefined) { sets.push(`${field} = ?`); params.push(data[field]); }
      }
      if (data.level !== undefined) { sets.push('level = ?'); params.push(data.level); }
      if (data.isActive !== undefined) { sets.push('is_active = ?'); params.push(data.isActive ? 1 : 0); }
      params.push(id);
      db.prepare(`UPDATE roles SET ${sets.join(', ')} WHERE id = ?`).run(...params);
      if (data.permissions) applyPermissions(db, id, data.permissions);
    });

    if (data.permissions) {
      for (const u of affectedAdmins) revokeAllSessions(u.id, req.user, 'role_permissions_changed');
    }

    auditFromReq(req, 'roles.update', { entity: 'role', entityId: String(id), detail: data });
    const updated = db.prepare('SELECT * FROM roles WHERE id = ?').get(id);
    res.json(mapRole(updated, permissionsOfRole(db, id)));
  })
);

/** DELETE /api/roles/:id */
router.delete(
  '/:id',
  requirePermission('roles.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare('SELECT * FROM roles WHERE id = ?').get(id);
    if (!row) throw notFound('Rol no encontrado');
    if (row.is_system) throw forbidden('Los roles del sistema no se pueden eliminar');
    const inUse = db.prepare('SELECT COUNT(*) c FROM users WHERE role_id = ?').get(id).c;
    if (inUse > 0) throw conflict(`El rol tiene ${inUse} usuario(s) asignado(s); reasígnalos antes de borrarlo`);
    db.prepare('DELETE FROM roles WHERE id = ?').run(id);
    auditFromReq(req, 'roles.delete', { entity: 'role', entityId: String(id), severity: 'warn' });
    res.json({ ok: true });
  })
);

function applyPermissions(db, roleId, codes) {
  db.prepare('DELETE FROM role_permissions WHERE role_id = ?').run(roleId);
  const ins = db.prepare('INSERT OR IGNORE INTO role_permissions (role_id, permission_id) VALUES (?, ?)');
  const unique = [...new Set(codes)];
  if (unique.includes('*')) {
    const wildcard = db.prepare("SELECT id FROM permissions WHERE code = '*'").get();
    if (wildcard) ins.run(roleId, wildcard.id);
  }
  for (const code of unique) {
    if (code === '*') continue;
    const perm = db.prepare('SELECT id FROM permissions WHERE code = ?').get(code);
    if (perm) ins.run(roleId, perm.id);
  }
}

export default router;
