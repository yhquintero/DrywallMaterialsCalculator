import { Router } from 'express';
import { z } from 'zod';
import { getDb } from '../db/index.js';
import { asyncHandler, badRequest, notFound, conflict, forbidden } from '../utils/errors.js';
import { requireAuth, requirePermission, hashPassword, assertStrongPassword, effectivePermissions, revokeAllSessions } from '../security/auth.js';
import { generateTemporaryPassword } from '../services/cryptoService.js';
import { auditFromReq } from '../services/auditService.js';
import { ROLE_CODES } from '../config/permissions.js';

const router = Router();
router.use(requireAuth);

function mapUser(row) {
  return {
    id: row.id,
    username: row.username,
    email: row.email,
    fullName: row.full_name,
    roleId: row.role_id,
    roleCode: row.role_code,
    roleName: row.role_name,
    roleLevel: row.level,
    isActive: Boolean(row.is_active),
    mustChangePassword: Boolean(row.must_change_password),
    failedAttempts: row.failed_attempts,
    lockedUntil: row.locked_until,
    lastLoginAt: row.last_login_at,
    lastLoginIp: row.last_login_ip,
    passwordChangedAt: row.password_changed_at,
    createdBy: row.created_by,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

const USER_SELECT = `SELECT u.*, r.code AS role_code, r.name AS role_name, r.level
                       FROM users u JOIN roles r ON r.id = u.role_id`;

/** GET /api/users */
router.get(
  '/',
  requirePermission('users.view'),
  asyncHandler((req, res) => {
    const db = getDb();
    const search = req.query.search ? `%${req.query.search}%` : null;
    const rows = search
      ? db.prepare(`${USER_SELECT} WHERE u.username LIKE ? OR u.full_name LIKE ? OR COALESCE(u.email,'') LIKE ? ORDER BY u.id`).all(search, search, search)
      : db.prepare(`${USER_SELECT} ORDER BY r.level DESC, u.username`).all();
    res.json({ items: rows.map(mapUser), total: rows.length });
  })
);

/** GET /api/users/roles — catálogo de roles para los selectores. */
router.get(
  '/roles',
  requirePermission('users.view'),
  asyncHandler((_req, res) => {
    const db = getDb();
    res.json({
      items: db
        .prepare('SELECT id, code, name, description, level, is_system, is_active FROM roles WHERE is_active = 1 ORDER BY level DESC')
        .all()
        .map((r) => ({ ...r, isSystem: Boolean(r.is_system), isActive: Boolean(r.is_active) })),
    });
  })
);

/** GET /api/users/:id */
router.get(
  '/:id',
  requirePermission('users.view'),
  asyncHandler((req, res) => {
    const db = getDb();
    const row = db.prepare(`${USER_SELECT} WHERE u.id = ?`).get(Number(req.params.id));
    if (!row) throw notFound('Usuario no encontrado');
    const overrides = db
      .prepare(
        `SELECT p.code, up.effect FROM user_permissions up JOIN permissions p ON p.id = up.permission_id WHERE up.user_id = ?`
      )
      .all(row.id);
    res.json({ ...mapUser(row), permissions: effectivePermissions(row.id), overrides });
  })
);

const createSchema = z.object({
  username: z.string().trim().min(3).max(40).regex(/^[a-zA-Z0-9._-]+$/, 'Solo letras, números, punto, guion y guion bajo'),
  email: z.string().trim().email().max(160).optional().or(z.literal('')),
  fullName: z.string().trim().max(120).default(''),
  password: z.string().min(10).max(200).optional(),
  roleCode: z.string().trim().toUpperCase(),
  isActive: z.boolean().default(true),
});

/** POST /api/users */
router.post(
  '/',
  requirePermission('users.create'),
  asyncHandler((req, res) => {
    const db = getDb();
    const parsed = createSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos de usuario inválidos', parsed.error.flatten());
    const data = parsed.data;

    if (!ROLE_CODES.includes(data.roleCode)) {
      const exists = db.prepare('SELECT code FROM roles WHERE code = ? AND is_active = 1').get(data.roleCode);
      if (!exists) throw badRequest(`Rol desconocido: ${data.roleCode}`);
    }
    const role = db.prepare('SELECT * FROM roles WHERE code = ?').get(data.roleCode);
    if (!role) throw badRequest(`Rol desconocido: ${data.roleCode}`);

    // Escalada de privilegios: nadie puede crear un ADMIN sin ser ADMIN.
    if (role.code === 'ADMIN' && req.user.roleCode !== 'ADMIN') {
      throw forbidden('Solo un Administrador puede crear cuentas con rol ADMIN');
    }

    if (db.prepare('SELECT id FROM users WHERE lower(username) = lower(?)').get(data.username)) {
      throw conflict('Ese nombre de usuario ya existe');
    }
    if (data.email && db.prepare('SELECT id FROM users WHERE lower(email) = lower(?)').get(data.email)) {
      throw conflict('Ese correo ya está registrado');
    }

    const generated = !data.password;
    const password = data.password || generateTemporaryPassword();
    if (generated) assertStrongPassword(password);
    else assertStrongPassword(password);

    const ts = Date.now();
    const id = db
      .prepare(
        `INSERT INTO users (username, email, full_name, password_hash, role_id, is_active, must_change_password,
                            failed_attempts, password_changed_at, created_by, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)`
      )
      .insert(
        data.username,
        data.email || null,
        data.fullName,
        hashPassword(password),
        role.id,
        data.isActive ? 1 : 0,
        generated ? 1 : 0,
        ts,
        req.user.id,
        ts,
        ts
      );

    auditFromReq(req, 'users.create', { entity: 'user', entityId: String(id), detail: { username: data.username, role: role.code } });
    const row = db.prepare(`${USER_SELECT} WHERE u.id = ?`).get(id);
    res.status(201).json({ ...mapUser(row), temporaryPassword: generated ? password : undefined });
  })
);

const updateSchema = z
  .object({
    email: z.string().trim().email().max(160).nullable().optional().or(z.literal('')),
    fullName: z.string().trim().max(120).optional(),
    roleCode: z.string().trim().toUpperCase().optional(),
    isActive: z.boolean().optional(),
    unlock: z.boolean().optional(),
  })
  .refine((v) => Object.keys(v).length > 0, { message: 'Nada que actualizar' });

/** PATCH /api/users/:id */
router.patch(
  '/:id',
  requirePermission('users.update'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare(`${USER_SELECT} WHERE u.id = ?`).get(id);
    if (!row) throw notFound('Usuario no encontrado');

    const parsed = updateSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos inválidos', parsed.error.flatten());
    const data = parsed.data;

    const isAdminActor = req.user.roleCode === 'ADMIN';
    if (data.roleCode) {
      const role = db.prepare('SELECT * FROM roles WHERE code = ?').get(data.roleCode);
      if (!role) throw badRequest(`Rol desconocido: ${data.roleCode}`);
      if ((role.code === 'ADMIN' || row.role_code === 'ADMIN') && !isAdminActor) {
        throw forbidden('Solo un Administrador puede cambiar el rol ADMIN');
      }
    }
    if (id === req.user.id && data.isActive === false) throw badRequest('No puedes desactivar tu propia cuenta');
    if (id === req.user.id && data.roleCode && data.roleCode !== 'ADMIN') {
      throw badRequest('No puedes degradar tu propio rol desde esta pantalla');
    }
    const lastAdmin =
      row.role_code === 'ADMIN' &&
      db.prepare("SELECT COUNT(*) c FROM users u JOIN roles r ON r.id=u.role_id WHERE r.code='ADMIN' AND u.is_active=1").get().c <= 1;
    if (lastAdmin && (data.isActive === false || (data.roleCode && data.roleCode !== 'ADMIN'))) {
      throw conflict('Debe existir al menos un Administrador activo');
    }

    const ts = Date.now();
    const sets = ['updated_at = ?'];
    const params = [ts];
    if (data.email !== undefined) { sets.push('email = ?'); params.push(data.email || null); }
    if (data.fullName !== undefined) { sets.push('full_name = ?'); params.push(data.fullName); }
    if (data.isActive !== undefined) { sets.push('is_active = ?'); params.push(data.isActive ? 1 : 0); }
    if (data.unlock) { sets.push('failed_attempts = 0'); sets.push('locked_until = NULL'); }
    if (data.roleCode) {
      const role = db.prepare('SELECT id FROM roles WHERE code = ?').get(data.roleCode);
      sets.push('role_id = ?');
      params.push(role.id);
    }
    params.push(id);
    db.prepare(`UPDATE users SET ${sets.join(', ')} WHERE id = ?`).run(...params);

    if (data.isActive === false || data.roleCode) revokeAllSessions(id, req.user, data.roleCode ? 'role_changed' : 'deactivated');

    auditFromReq(req, 'users.update', { entity: 'user', entityId: String(id), detail: data });
    res.json(mapUser(db.prepare(`${USER_SELECT} WHERE u.id = ?`).get(id)));
  })
);

/** POST /api/users/:id/reset-password */
router.post(
  '/:id/reset-password',
  requirePermission('users.reset_password'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare(`${USER_SELECT} WHERE u.id = ?`).get(id);
    if (!row) throw notFound('Usuario no encontrado');
    if (row.role_code === 'ADMIN' && req.user.roleCode !== 'ADMIN') {
      throw forbidden('Solo un Administrador puede restablecer la contraseña de otro Administrador');
    }
    const temp = generateTemporaryPassword();
    const ts = Date.now();
    db.prepare('UPDATE users SET password_hash = ?, must_change_password = 1, failed_attempts = 0, locked_until = NULL, password_changed_at = ?, updated_at = ? WHERE id = ?')
      .run(hashPassword(temp), ts, ts, id);
    revokeAllSessions(id, req.user, 'password_reset');
    auditFromReq(req, 'users.reset_password', { entity: 'user', entityId: String(id), severity: 'warn' });
    res.json({ ok: true, temporaryPassword: temp, mustChangePassword: true });
  })
);

/** PUT /api/users/:id/permissions — overrides finos (allow/deny) sobre el rol. */
router.put(
  '/:id/permissions',
  requirePermission('roles.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare(`${USER_SELECT} WHERE u.id = ?`).get(id);
    if (!row) throw notFound('Usuario no encontrado');
    const schema = z.object({
      overrides: z.array(z.object({ code: z.string(), effect: z.enum(['allow', 'deny']) })).default([]),
    });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Overrides inválidos', parsed.error.flatten());

    const ts = Date.now();
    db.transaction(() => {
      db.prepare('DELETE FROM user_permissions WHERE user_id = ?').run(id);
      const ins = db.prepare('INSERT OR IGNORE INTO user_permissions (user_id, permission_id, effect, granted_by, created_at) VALUES (?, ?, ?, ?, ?)');
      for (const o of parsed.data.overrides) {
        const perm = db.prepare('SELECT id FROM permissions WHERE code = ?').get(o.code);
        if (perm) ins.run(id, perm.id, o.effect, req.user.id, ts);
      }
    });
    revokeAllSessions(id, req.user, 'permissions_changed');
    auditFromReq(req, 'users.permissions_update', { entity: 'user', entityId: String(id), detail: parsed.data.overrides });
    res.json({ ok: true, permissions: effectivePermissions(id), overrides: parsed.data.overrides });
  })
);

/** DELETE /api/users/:id */
router.delete(
  '/:id',
  requirePermission('users.delete'),
  asyncHandler((req, res) => {
    const db = getDb();
    const id = Number(req.params.id);
    const row = db.prepare(`${USER_SELECT} WHERE u.id = ?`).get(id);
    if (!row) throw notFound('Usuario no encontrado');
    if (id === req.user.id) throw badRequest('No puedes eliminar tu propia cuenta');
    if (row.role_code === 'ADMIN' && req.user.roleCode !== 'ADMIN') throw forbidden('Solo un Administrador puede eliminar otro Administrador');
    const admins = db.prepare("SELECT COUNT(*) c FROM users u JOIN roles r ON r.id=u.role_id WHERE r.code='ADMIN' AND u.is_active=1").get().c;
    if (row.role_code === 'ADMIN' && admins <= 1) throw conflict('Debe existir al menos un Administrador activo');

    const hard = req.query.hard === 'true';
    if (hard) {
      db.prepare('UPDATE licenses SET issued_by = NULL WHERE issued_by = ?').run(id);
      db.prepare('UPDATE users SET created_by = NULL WHERE created_by = ?').run(id);
      db.prepare('DELETE FROM users WHERE id = ?').run(id);
    } else {
      db.prepare('UPDATE users SET is_active = 0, updated_at = ? WHERE id = ?').run(Date.now(), id);
      revokeAllSessions(id, req.user, 'deleted');
    }
    auditFromReq(req, hard ? 'users.delete_hard' : 'users.deactivate', { entity: 'user', entityId: String(id), severity: 'warn' });
    res.json({ ok: true });
  })
);

export default router;
