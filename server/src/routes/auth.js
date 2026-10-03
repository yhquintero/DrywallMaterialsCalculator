import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler, badRequest, unauthorized } from '../utils/errors.js';
import {
  login,
  refreshSession,
  logout,
  setRefreshCookie,
  clearRefreshCookie,
  changePassword,
  loadAuthUser,
  revokeAllSessions,
  requireAuth,
  effectivePermissions,
} from '../security/auth.js';
import config from '../config/index.js';
import { getDb } from '../db/index.js';
import { auditFromReq } from '../services/auditService.js';

const router = Router();

const loginSchema = z.object({
  username: z.string().trim().min(2).max(120),
  password: z.string().min(1).max(200),
});

function readRefresh(req) {
  return req.cookies?.[config.auth.refreshCookie] || req.body?.refreshToken || null;
}

/** POST /api/auth/login */
router.post(
  '/login',
  asyncHandler((req, res) => {
    const parsed = loginSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Datos de acceso inválidos', parsed.error.flatten());
    const result = login(parsed.data, req);
    setRefreshCookie(res, result.refreshToken);
    res.json({
      user: result.user,
      accessToken: result.accessToken,
      expiresInMinutes: result.expiresInMinutes,
      https: req.isSecure,
    });
  })
);

/** POST /api/auth/refresh */
router.post(
  '/refresh',
  asyncHandler((req, res) => {
    const token = readRefresh(req);
    const result = refreshSession(token, req);
    setRefreshCookie(res, result.refreshToken);
    res.json({ user: result.user, accessToken: result.accessToken, expiresInMinutes: result.expiresInMinutes });
  })
);

/** POST /api/auth/logout */
router.post(
  '/logout',
  asyncHandler((req, res) => {
    logout(readRefresh(req), req);
    clearRefreshCookie(res);
    res.json({ ok: true });
  })
);

/** GET /api/auth/me */
router.get(
  '/me',
  requireAuth,
  asyncHandler((req, res) => {
    const db = getDb();
    const sessions = db
      .prepare('SELECT id, ip_address, user_agent, issued_at, last_used_at, expires_at FROM sessions WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ? ORDER BY issued_at DESC LIMIT 10')
      .all(req.user.id, Date.now());
    res.json({ user: req.user, permissions: effectivePermissions(req.user.id), activeSessions: sessions });
  })
);

/** POST /api/auth/change-password */
router.post(
  '/change-password',
  requireAuth,
  asyncHandler((req, res) => {
    const { currentPassword, newPassword } = req.body ?? {};
    changePassword(req.user.id, currentPassword, newPassword, req);
    clearRefreshCookie(res);
    res.json({ ok: true, message: 'Contraseña actualizada. Vuelve a iniciar sesión.' });
  })
);

/** POST /api/auth/revoke-sessions — cierra las demás sesiones del usuario actual. */
router.post(
  '/revoke-sessions',
  requireAuth,
  asyncHandler((req, res) => {
    const count = revokeAllSessions(req.user.id, req.user, 'user_request');
    auditFromReq(req, 'auth.revoke_sessions', { entity: 'user', entityId: String(req.user.id) });
    clearRefreshCookie(res);
    res.json({ ok: true, revoked: count });
  })
);

/** GET /api/auth/session-policy — la UI la usa para mostrar el estado HTTPS/cookies. */
router.get('/session-policy', (req, res) => {
  res.json({
    accessTtlMinutes: config.auth.accessTtlMinutes,
    refreshTtlDays: config.auth.refreshTtlDays,
    maxFailedLogins: config.auth.maxFailedLogins,
    lockMinutes: config.auth.lockMinutes,
    secureCookies: config.auth.secureCookies,
    https: req.isSecure,
  });
});

export default router;
