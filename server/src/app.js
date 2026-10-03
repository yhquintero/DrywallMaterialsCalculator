/**
 * Aplicación Express de la Consola de Licencias.
 * Se exporta como factory para poder montarla en tests sin abrir puertos.
 */
import fs from 'node:fs';
import path from 'node:path';
import express from 'express';
import helmet from 'helmet';
import cookieParser from 'cookie-parser';
import rateLimit from 'express-rate-limit';
import config from './config/index.js';
import apiRouter from './routes/index.js';
import wellKnownRouter from './routes/wellKnown.js';
import { publicRatesHandler } from './routes/rates.js';
import { publicApiRouter } from './routes/public.js';
import { tlsContext, secureHeaders, enforceHttps, sameOriginGuard, isSecureRequest } from './security/httpsMiddleware.js';
import { HttpError } from './utils/errors.js';

export function createApp() {
  const app = express();

  if (config.server.trustProxy) app.set('trust proxy', 1);
  app.disable('x-powered-by');

  // ── Cabeceras base ────────────────────────────────────────────────
  app.use(
    helmet({
      contentSecurityPolicy: false, // la SPA trae su propia CSP (ver index.html / reverse proxy)
      crossOriginEmbedderPolicy: false,
      hsts: false,                  // lo gestiona secureHeaders solo cuando hay TLS real
      referrerPolicy: { policy: 'strict-origin-when-cross-origin' },
    })
  );
  app.use(tlsContext);
  app.use(secureHeaders);

  // ── Body parsing con límites estrictos ─────────────────────────────
  app.use(express.json({ limit: '2mb' }));
  app.use(express.urlencoded({ extended: false, limit: '1mb' }));
  app.use(cookieParser());

  // ── CORS ──────────────────────────────────────────────────────────
  app.use((req, res, next) => {
    const origin = req.headers.origin;
    const allowed = new Set([
      config.server.publicUrl,
      ...config.server.corsOrigins,
      ...(config.isProd ? [] : ['http://localhost:3000', 'https://localhost:3000', 'http://localhost:5173', 'https://localhost:5173']),
    ]);
    if (origin && (allowed.has(origin) || origin.endsWith('.e2b.app') || (!config.isProd && /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin)))) {
      res.setHeader('Access-Control-Allow-Origin', origin);
      res.setHeader('Vary', 'Origin');
      res.setHeader('Access-Control-Allow-Credentials', 'true');
      res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PATCH,PUT,DELETE,OPTIONS');
      res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization,X-Requested-With');
      res.setHeader('Access-Control-Max-Age', '600');
    }
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });

  // ── HTTPS obligatorio (fuera de loopback/dev) ──────────────────────
  app.use(enforceHttps);
  app.use('/api', sameOriginGuard);

  // ── Endpoints públicos para las apps Android ───────────────────────
  app.use('/.well-known', wellKnownRouter);
  app.use('/api/public', publicApiRouter);
  // Las tasas publicadas también se leen sin autenticarse.
  app.get('/api/public/rates', publicRatesHandler);

  // ── API ───────────────────────────────────────────────────────────
  // Cada router aplica su propio `requireAuth`/`requirePermission`.
  app.use('/api', apiRouter);

  // ── Web estática (build de producción) ─────────────────────────────
  const dist = config.web.dist;
  if (fs.existsSync(path.join(dist, 'index.html'))) {
    app.use(
      express.static(dist, {
        maxAge: '1h',
        setHeaders(res, filePath) {
          if (filePath.endsWith('index.html')) res.setHeader('Cache-Control', 'no-cache');
          if (/\.(js|css|woff2?|png|jpg|svg|webp)$/.test(filePath)) {
            res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
          }
        },
      })
    );
    // SPA fallback
    app.get(/^\/(?!api|\.well-known).*/, (_req, res) => {
      res.setHeader('Cache-Control', 'no-cache');
      res.sendFile(path.join(dist, 'index.html'));
    });
  } else {
    app.get('/', (_req, res) => {
      res.status(200).json({
        service: 'DrywallPro — Consola de Licencias (API)',
        docs: '/api/public/health',
        hint: 'Ejecuta `npm --prefix web run build` para servir la consola web desde este mismo proceso.',
        https: isSecureRequest(_req),
      });
    });
  }

  // ── 404 + manejo de errores ────────────────────────────────────────
  app.use((req, res) => res.status(404).json({ error: `Ruta no encontrada: ${req.method} ${req.originalUrl}` }));

  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, _next) => {
    if (err instanceof HttpError) {
      return res.status(err.status).json({ error: err.message, details: err.details });
    }
    if (err?.type === 'entity.parse.failed') {
      return res.status(400).json({ error: 'JSON mal formado' });
    }
    if (err?.code === 'EBADCSRFTOKEN') {
      return res.status(403).json({ error: 'Token CSRF inválido' });
    }
    // eslint-disable-next-line no-console
    console.error('[api] error no controlado:', err);
    res.status(500).json({ error: 'Error interno del servidor' });
  });

  return app;
}

export default createApp;
