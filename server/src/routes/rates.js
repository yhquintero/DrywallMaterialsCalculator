import { Router } from 'express';
import { z } from 'zod';
import { getDb } from '../db/index.js';
import { asyncHandler, badRequest } from '../utils/errors.js';
import { requireAuth, requirePermission } from '../security/auth.js';
import { auditFromReq } from '../services/auditService.js';
import { CURRENCIES } from '../db/index.js';

const router = Router();

const RATE_CODES = CURRENCIES.map((c) => c.code);

function mapRate(r) {
  return { code: r.code, rate: r.rate, symbol: r.symbol, source: r.source, updatedAt: r.updated_at, updatedBy: r.updated_by };
}

/**
 * Handler PÚBLICO (sin login) que consumen las apps Android como alternativa
 * estable al scraping de eltoque.com. Montado en GET /api/public/rates.
 */
export function publicRatesHandler(_req, res) {
  const db = getDb();
  const rows = db.prepare('SELECT * FROM rates').all();
  const rates = {};
  for (const r of rows) if (r.rate > 0) rates[r.code] = r.rate;
  const last = rows.reduce((max, r) => Math.max(max, r.updated_at || 0), 0);
  res.setHeader('Cache-Control', 'public, max-age=60');
  res.json({
    success: Object.keys(rates).length > 0,
    rates,
    symbols: Object.fromEntries(rows.map((r) => [r.code, r.symbol])),
    timestamp: new Date(last || Date.now()).toISOString(),
    source: 'drywall-license-console',
  });
}

/** GET /api/rates */
router.get(
  '/',
  requireAuth,
  requirePermission('rates.view'),
  asyncHandler((_req, res) => {
    const db = getDb();
    res.json({ items: db.prepare('SELECT * FROM rates ORDER BY code').all().map(mapRate), codes: RATE_CODES });
  })
);

/** GET /api/rates/history */
router.get(
  '/history',
  requireAuth,
  requirePermission('rates.view'),
  asyncHandler((req, res) => {
    const db = getDb();
    const code = req.query.code ? String(req.query.code).toUpperCase() : null;
    const limit = Math.min(Number(req.query.limit) || 200, 2000);
    const rows = code
      ? db.prepare('SELECT * FROM rate_history WHERE code = ? ORDER BY timestamp DESC LIMIT ?').all(code, limit)
      : db.prepare('SELECT * FROM rate_history ORDER BY timestamp DESC LIMIT ?').all(limit);
    res.json({ items: rows.map((r) => ({ id: r.id, code: r.code, rate: r.rate, source: r.source, timestamp: r.timestamp })) });
  })
);

/** PUT /api/rates — publicación masiva (formato compatible con el keygen). */
router.put(
  '/',
  requireAuth,
  requirePermission('rates.manage'),
  asyncHandler((req, res) => {
    const db = getDb();
    const schema = z.object({
      rates: z.record(z.coerce.number().positive().max(1_000_000)).refine((r) => Object.keys(r).length > 0, 'Envía al menos una tasa'),
      source: z.string().trim().max(40).default('manual'),
    });
    const parsed = schema.safeParse(req.body ?? {});
    if (!parsed.success) throw badRequest('Tasas inválidas', parsed.error.flatten());

    const ts = Date.now();
    const accepted = [];
    db.transaction(() => {
      for (const [code, rate] of Object.entries(parsed.data.rates)) {
        const upper = code.toUpperCase();
        if (!RATE_CODES.includes(upper)) continue;
        const symbol = CURRENCIES.find((c) => c.code === upper)?.symbol ?? '';
        db.prepare(
          `INSERT INTO rates (code, rate, symbol, source, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT(code) DO UPDATE SET rate = excluded.rate, source = excluded.source, updated_at = excluded.updated_at, updated_by = excluded.updated_by`
        ).run(upper, rate, symbol, parsed.data.source, ts, req.user.id);

        const last = db.prepare('SELECT rate FROM rate_history WHERE code = ? ORDER BY timestamp DESC LIMIT 1').get(upper);
        if (!last || last.rate !== rate) {
          db.prepare('INSERT INTO rate_history (code, rate, source, created_by, timestamp) VALUES (?, ?, ?, ?, ?)').run(
            upper, rate, parsed.data.source, req.user.id, ts
          );
        }
        accepted.push({ code: upper, rate });
      }
    });

    auditFromReq(req, 'rates.update', { entity: 'rate', severity: 'info', detail: { accepted, source: parsed.data.source } });
    res.json({ ok: true, updated: accepted, timestamp: ts });
  })
);

export default router;
