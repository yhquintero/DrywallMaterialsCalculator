/**
 * Autenticación y RBAC.
 *  • Access token  : JWT corto (30 min) en memoria del cliente.
 *  • Refresh token : opaco, aleatorio, en cookie httpOnly + Secure + SameSite=Strict.
 *                    Se almacena SOLO su hash SHA-256 y se rota en cada refresco.
 *  • Contraseñas   : bcrypt (coste 12).
 *  • Bloqueo       : N intentos fallidos ⇒ lockout temporal.
 */
import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import config from '../config/index.js';
import { getDb } from '../db/index.js';
import { unauthorized, forbidden, badRequest, conflict } from '../utils/errors.js';
import { audit } from '../services/auditService.js';

const ACCESS_ISSUER = 'drywall-license-console';

// ─────────────────────────── hashes / tokens ───────────────────────────
export function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

export function generateRefreshToken() {
  return crypto.randomBytes(48).toString('base64url');
}

export function hashPassword(plain) {
  return bcrypt.hashSync(plain, config.auth.bcryptRounds);
}

export function verifyPassword(plain, hash) {
  try {
    return bcrypt.compareSync(plain, hash);
  } catch {
    return false;
  }
}

export function signAccessToken(user, permissions) {
  return jwt.sign(
    {
      sub: String(user.id),
      username: user.username,
      role: user.roleCode,
      perms: permissions,
    },
    config.auth.jwtSecret,
    {
      issuer: ACCESS_ISSUER,
      audience: 'drywall-console-web',
      expiresIn: `${config.auth.accessTtlMinutes}m`,
      jwtid: crypto.randomUUID(),
    }
  );
}

export function verifyAccessToken(token) {
  return jwt.verify(token, config.auth.jwtSecret, {
    issuer: ACCESS_ISSUER,
    audience: 'drywall-console-web',
  });
}

// ─────────────────────────── permisos ───────────────────────────
/**
 * Permisos efectivos = permisos del rol
 *                     + concesiones directas (effect='allow') del usuario
 *                     − denegaciones directas (effect='deny') del usuario.
 */
export function effectivePermissions(userId) {
  const db = getDb();

  const fromRole = db
    .prepare(
      `SELECT p.code
         FROM users u
         JOIN roles r             ON r.id = u.role_id
         JOIN role_permissions rp ON rp.role_id = r.id
         JOIN permissions p       ON p.id = rp.permission_id
        WHERE u.id = ?`
    )
    .all(userId)
    .map((r) => r.code);

  const overrides = db
    .prepare(
      `SELECT p.code, up.effect
         FROM user_permissions up
         JOIN permissions p ON p.id = up.permission_id
        WHERE up.user_id = ?`
    )
    .all(userId);

  const set = new Set(fromRole);
  for (const o of overrides) {
    if (o.effect === 'allow') set.add(o.code);
    else set.delete(o.code);
  }
  return [...set].sort();
}

export function hasPermission(user, required) {
  if (!user) return false;
  const perms = user.permissions ?? [];
  if (perms.includes('*')) return true;
  const list = Array.isArray(required) ? required : [required];
  return list.every((p) => perms.includes(p));
}

// ─────────────────────────── usuarios ───────────────────────────
export function loadAuthUser(userId) {
  const db = getDb();
  const row = db
    .prepare(
      `SELECT u.*, r.code AS role_code, r.name AS role_name, r.level AS role_level
         FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?`
    )
    .get(userId);
  if (!row) return null;
  return {
    id: row.id,
    username: row.username,
    email: row.email,
    fullName: row.full_name,
    roleId: row.role_id,
    roleCode: row.role_code,
    roleName: row.role_name,
    roleLevel: row.role_level,
    isActive: Boolean(row.is_active),
    mustChangePassword: Boolean(row.must_change_password),
    lockedUntil: row.locked_until,
    lastLoginAt: row.last_login_at,
    createdAt: row.created_at,
    permissions: effectivePermissions(row.id),
  };
}

function publicUser(user) {
  if (!user) return null;
  const { ...rest } = user;
  return rest;
}

// ─────────────────────────── login / logout ───────────────────────────
export function login({ username, password }, req) {
  const db = getDb();
  const now = Date.now();
  const row = db
    .prepare(
      `SELECT u.*, r.code AS role_code FROM users u JOIN roles r ON r.id = u.role_id
        WHERE lower(u.username) = lower(?) OR lower(COALESCE(u.email,'')) = lower(?)`
    )
    .get(String(username ?? '').trim(), String(username ?? '').trim());

  const ip = req?.ip || '';
  const ua = String(req?.get?.('user-agent') || '').slice(0, 300);

  if (!row) {
    audit({ action: 'auth.login_failed', entity: 'user', entityId: String(username), severity: 'warn', ip, userAgent: ua, detail: { reason: 'usuario_inexistente' } });
    throw unauthorized('Credenciales inválidas');
  }
  if (!row.is_active) {
    audit({ actor: { id: row.id, username: row.username }, action: 'auth.login_failed', entity: 'user', entityId: String(row.id), severity: 'warn', ip, userAgent: ua, detail: { reason: 'usuario_inactivo' } });
    throw forbidden('La cuenta está desactivada. Contacta al administrador.');
  }
  if (row.locked_until && row.locked_until > now) {
    const mins = Math.ceil((row.locked_until - now) / 60000);
    throw conflict(`Cuenta bloqueada por intentos fallidos. Reintenta en ${mins} min.`);
  }
  if (!verifyPassword(String(password ?? ''), row.password_hash)) {
    const attempts = (row.failed_attempts ?? 0) + 1;
    const lock = attempts >= config.auth.maxFailedLogins ? now + config.auth.lockMinutes * 60000 : null;
    db.prepare('UPDATE users SET failed_attempts = ?, locked_until = ?, updated_at = ? WHERE id = ?').run(
      attempts, lock, now, row.id
    );
    audit({
      actor: { id: row.id, username: row.username },
      action: lock ? 'auth.account_locked' : 'auth.login_failed',
      entity: 'user', entityId: String(row.id),
      severity: lock ? 'critical' : 'warn', ip, userAgent: ua,
      detail: { attempts, lockedUntil: lock },
    });
    throw unauthorized(lock ? 'Demasiados intentos fallidos: cuenta bloqueada temporalmente' : 'Credenciales inválidas');
  }

  // Éxito: reset de contadores
  db.prepare('UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = ?, last_login_ip = ?, updated_at = ? WHERE id = ?')
    .run(now, ip, now, row.id);

  const user = loadAuthUser(row.id);
  const tokens = createSession(user, { ip, userAgent: ua });

  audit({ actor: user, action: 'auth.login', entity: 'user', entityId: String(user.id), severity: 'info', ip, userAgent: ua, detail: { role: user.roleCode } });

  return { user: publicUser(user), ...tokens };
}

export function createSession(user, { ip = '', userAgent = '' } = {}) {
  const db = getDb();
  const now = Date.now();
  const accessToken = signAccessToken(user, user.permissions);
  const refreshToken = generateRefreshToken();
  const refreshExpires = now + config.auth.refreshTtlDays * 86400000;

  db.prepare(
    `INSERT INTO sessions (user_id, token_hash, user_agent, ip_address, issued_at, expires_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).insert(user.id, hashToken(refreshToken), userAgent, ip, now, refreshExpires);

  return { accessToken, refreshToken, refreshExpiresAt: refreshExpires, expiresInMinutes: config.auth.accessTtlMinutes };
}

export function refreshSession(refreshToken, req) {
  if (!refreshToken) throw unauthorized('Sesión expirada');
  const db = getDb();
  const now = Date.now();
  const hash = hashToken(refreshToken);
  const session = db.prepare('SELECT * FROM sessions WHERE token_hash = ?').get(hash);
  if (!session) throw unauthorized('Sesión inválida');
  if (session.revoked_at) throw unauthorized('Sesión revocada');
  if (session.expires_at <= now) throw unauthorized('Sesión expirada');

  const user = loadAuthUser(session.user_id);
  if (!user || !user.isActive) throw unauthorized('Usuario inactivo');

  // Rotación: invalida el token usado y emite uno nuevo.
  db.prepare('UPDATE sessions SET revoked_at = ?, revoke_reason = ?, last_used_at = ? WHERE id = ?')
    .run(now, 'rotated', now, session.id);

  const ip = req?.ip || session.ip_address || '';
  const ua = String(req?.get?.('user-agent') || session.user_agent || '').slice(0, 300);
  const tokens = createSession(user, { ip, userAgent: ua });
  return { user: publicUser(user), ...tokens };
}

export function logout(refreshToken, req) {
  const db = getDb();
  const now = Date.now();
  if (refreshToken) {
    db.prepare('UPDATE sessions SET revoked_at = ?, revoke_reason = ? WHERE token_hash = ? AND revoked_at IS NULL')
      .run(now, 'logout', hashToken(refreshToken));
  }
  if (req?.user) {
    audit({ actor: req.user, action: 'auth.logout', entity: 'user', entityId: String(req.user.id), ip: req.ip || '', userAgent: String(req.get('user-agent') || '').slice(0, 300) });
  }
  return { ok: true };
}

export function revokeAllSessions(userId, actor = null, reason = 'admin') {
  const db = getDb();
  const res = db
    .prepare('UPDATE sessions SET revoked_at = ?, revoke_reason = ? WHERE user_id = ? AND revoked_at IS NULL')
    .run(Date.now(), reason, userId);
  audit({ actor, action: 'auth.revoke_sessions', entity: 'user', entityId: String(userId), severity: 'warn', detail: { reason, count: res.changes } });
  return res.changes;
}

export function changePassword(userId, currentPassword, newPassword, req) {
  const db = getDb();
  const row = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  if (!row) throw unauthorized('Usuario no encontrado');
  if (!verifyPassword(String(currentPassword ?? ''), row.password_hash)) {
    audit({ actor: { id: row.id, username: row.username }, action: 'auth.password_change_failed', entity: 'user', entityId: String(row.id), severity: 'warn', ip: req?.ip || '' });
    throw badRequest('La contraseña actual no es correcta');
  }
  assertStrongPassword(newPassword);
  const now = Date.now();
  db.prepare('UPDATE users SET password_hash = ?, must_change_password = 0, password_changed_at = ?, updated_at = ? WHERE id = ?')
    .run(hashPassword(newPassword), now, now, userId);
  revokeAllSessions(userId, { id: userId, username: row.username }, 'password_changed');
  audit({ actor: { id: row.id, username: row.username }, action: 'auth.password_changed', entity: 'user', entityId: String(row.id), severity: 'info', ip: req?.ip || '' });
  return { ok: true };
}

export function assertStrongPassword(password) {
  const p = String(password ?? '');
  const errors = [];
  if (p.length < 10) errors.push('mínimo 10 caracteres');
  if (!/[a-z]/.test(p)) errors.push('una minúscula');
  if (!/[A-Z]/.test(p)) errors.push('una mayúscula');
  if (!/\d/.test(p)) errors.push('un número');
  if (!/[^A-Za-z0-9]/.test(p)) errors.push('un símbolo');
  if (errors.length) throw badRequest(`La contraseña debe tener: ${errors.join(', ')}`);
  return true;
}

// ─────────────────────────── middlewares ───────────────────────────
export function setRefreshCookie(res, refreshToken) {
  res.cookie(config.auth.refreshCookie, refreshToken, {
    httpOnly: true,
    secure: config.auth.secureCookies,
    sameSite: 'strict',
    path: '/api/auth',
    maxAge: config.auth.refreshTtlDays * 86400000,
  });
}

export function clearRefreshCookie(res) {
  res.clearCookie(config.auth.refreshCookie, { path: '/api/auth' });
}

/** Exige un access token válido y carga el usuario con sus permisos. */
export function requireAuth(req, _res, next) {
  try {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : null;
    if (!token) throw unauthorized('Falta el token de acceso');
    const payload = verifyAccessToken(token);
    const user = loadAuthUser(Number(payload.sub));
    if (!user) throw unauthorized('Sesión obsoleta');
    if (!user.isActive) throw forbidden('Cuenta desactivada');
    if (user.lockedUntil && user.lockedUntil > Date.now()) throw forbidden('Cuenta bloqueada temporalmente');
    req.user = user;
    req.tokenPayload = payload;
    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') return next(unauthorized('Sesión expirada, vuelve a iniciar sesión'));
    if (err.name === 'JsonWebTokenError') return next(unauthorized('Token inválido'));
    next(err);
  }
}

/** RBAC: exige uno o varios permisos (todos). */
export function requirePermission(...required) {
  return (req, _res, next) => {
    if (!req.user) return next(unauthorized());
    if (!hasPermission(req.user, required)) {
      audit({
        actor: req.user,
        action: 'rbac.denied',
        entity: 'permission',
        entityId: required.join(','),
        severity: 'warn',
        ip: req.ip || '',
        userAgent: String(req.get('user-agent') || '').slice(0, 300),
        detail: { path: req.originalUrl, method: req.method, required },
      });
      return next(forbidden(`Permiso requerido: ${required.join(', ')}`));
    }
    next();
  };
}

/** RBAC por rol (menos granular; preferir requirePermission). */
export function requireRole(...roles) {
  return (req, _res, next) => {
    if (!req.user) return next(unauthorized());
    if (!roles.includes(req.user.roleCode)) return next(forbidden(`Rol requerido: ${roles.join(' o ')}`));
    next();
  };
}

/** Un CLIENT solo puede ver sus propios recursos. */
export function clientScopeFilter(req) {
  return req.user?.roleCode === 'CLIENT' ? { search: req.user.fullName || req.user.username } : null;
}
