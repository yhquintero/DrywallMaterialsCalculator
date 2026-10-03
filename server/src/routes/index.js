import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import authRoutes from './auth.js';
import userRoutes from './users.js';
import roleRoutes from './roles.js';
import licenseRoutes from './licenses.js';
import deviceRoutes from './devices.js';
import keyRoutes from './keys.js';
import auditRoutes from './audit.js';
import rateRoutes from './rates.js';
import planRoutes from './plans.js';
import cadRoutes from './cad.js';
import pricingRoutes from './pricing.js';
import arRoutes from './ar.js';
import { publicApiRouter } from './public.js';
import wellKnownRouter from './wellKnown.js';
import { getDb } from '../db/index.js';
import config from '../config/index.js';
import { Database } from '../db/driver.js';

const router = Router();

/** Límite global de la API por IP. */
const apiLimiter = rateLimit({
  windowMs: 60_000,
  limit: 300,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Demasiadas solicitudes. Espera un minuto.' },
});

/** Límite agresivo para credenciales y endpoints públicos consumidos por las apps. */
const authLimiter = rateLimit({
  windowMs: 15 * 60_000,
  limit: 30,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  skipSuccessfulRequests: true,
  message: { error: 'Demasiados intentos. Espera 15 minutos.' },
});

const publicLimiter = rateLimit({
  windowMs: 60_000,
  limit: 120,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Demasiadas solicitudes al endpoint público.' },
});

/**
 * Límite propio de los módulos profesionales (planos CAD, precios y AR).
 * Son endpoints que usa la calculadora pública, por lo que no exigen login,
 * pero sí llevan guardia de origen, límite de tasa y validación estricta.
 */
const toolsLimiter = rateLimit({
  windowMs: 60_000,
  limit: 120,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Demasiadas solicitudes a los módulos profesionales.' },
});

router.use(apiLimiter);

// Públicos (apps Android)
router.use('/public', publicLimiter, publicApiRouter);
// Tasas públicas: también accesibles como /api/public/rates desde routes/rates.js
router.use('/auth/login', authLimiter);
router.use('/auth', authRoutes);

// Protegidos
router.use('/users', userRoutes);
router.use('/roles', roleRoutes);
router.use('/licenses', licenseRoutes);
router.use('/devices', deviceRoutes);
router.use('/keys', keyRoutes);
router.use('/audit', auditRoutes);
router.use('/rates', rateRoutes);
router.use('/plans', planRoutes);

// ── Módulos profesionales (usan la calculadora pública) ────────────────────
// Planos vectoriales DXF/DWG, catálogos de precios de distribuidores y
// señalización de la asistencia remota por WebRTC.
router.use('/cad', toolsLimiter, cadRoutes);
router.use('/pricing', toolsLimiter, pricingRoutes);
router.use('/ar', toolsLimiter, arRoutes);

/** GET /api/system — estado interno (solo Admin/Auditor). */
router.get('/system', (req, res) => {
  if (!req.user || !['ADMIN', 'AUDITOR'].includes(req.user.roleCode)) {
    return res.status(403).json({ error: 'Requiere rol ADMIN o AUDITOR' });
  }
  const db = getDb();
  res.json({
    env: config.env,
    driver: Database.driver,
    dbFile: db.file,
    https: config.server.https,
    secureCookies: config.auth.secureCookies,
    counts: {
      users: db.prepare('SELECT COUNT(*) c FROM users').get().c,
      activeUsers: db.prepare('SELECT COUNT(*) c FROM users WHERE is_active = 1').get().c,
      roles: db.prepare('SELECT COUNT(*) c FROM roles').get().c,
      permissions: db.prepare('SELECT COUNT(*) c FROM permissions').get().c,
      licenses: db.prepare('SELECT COUNT(*) c FROM licenses').get().c,
      signingKeys: db.prepare('SELECT COUNT(*) c FROM signing_keys').get().c,
      devices: db.prepare('SELECT COUNT(*) c FROM devices').get().c,
      blacklist: db.prepare('SELECT COUNT(*) c FROM license_blacklist').get().c,
      auditEntries: db.prepare('SELECT COUNT(*) c FROM audit_log').get().c,
      activeSessions: db.prepare('SELECT COUNT(*) c FROM sessions WHERE revoked_at IS NULL AND expires_at > ?').get(Date.now()).c,
    },
    policy: {
      accessTtlMinutes: config.auth.accessTtlMinutes,
      refreshTtlDays: config.auth.refreshTtlDays,
      maxFailedLogins: config.auth.maxFailedLogins,
      lockMinutes: config.auth.lockMinutes,
      rsaKeySize: config.crypto.rsaKeySize,
    },
    time: new Date().toISOString(),
  });
});

export default router;
