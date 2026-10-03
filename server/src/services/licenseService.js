/**
 * Servicio de licencias — emisor web equivalente al botón "Cobrar" del keygen Android.
 *
 * Produce EXACTAMENTE el mismo JSON que consume `LicensingManager` de la app:
 * {
 *   user, deviceId, creationDate, expiryDate, signature, type, issuerKey
 * }
 * con signature = Base64(RSA-SHA256(`${user}|${deviceId}|${creationDate}|${expiryDate}`))
 * e issuerKey   = Base64(SPKI DER) de la clave pública del emisor.
 */
import { getDb, KEYGEN_PLANS } from '../db/index.js';
import { APP_IDS, APP_META } from '../config/permissions.js';
import {
  signLicenseData,
  verifyLicenseData,
  buildDataToSign,
  generateLicenseKey,
  normalizeBase64,
} from './cryptoService.js';
import { ensureActiveKey, getPrivateKeyDer, getActiveKey } from './keyService.js';
import { audit } from './auditService.js';
import { badRequest, notFound, conflict } from '../utils/errors.js';

const VALID_STATUS = ['draft', 'issued', 'paid', 'expired', 'revoked', 'blocked'];

/** Vigencia hasta las 23:59:59 del día final, igual que el keygen Android. */
function computeExpiry(creationDate, days) {
  const d = new Date(creationDate);
  d.setDate(d.getDate() + days);
  d.setHours(23, 59, 59, 0);
  return d.getTime();
}

export function listPlans(appId) {
  const db = getDb();
  const rows = db
    .prepare('SELECT * FROM plans WHERE app_id = ? AND is_active = 1 ORDER BY sort_order')
    .all(appId);
  return rows.map(mapPlan);
}

function mapPlan(row) {
  return {
    id: row.id,
    appId: row.app_id,
    code: row.code,
    label: row.label,
    typeName: row.type_name,
    days: row.days,
    price: row.price,
    currency: row.currency,
    isActive: Boolean(row.is_active),
    sortOrder: row.sort_order,
  };
}

export function mapLicense(row) {
  if (!row) return null;
  let parsed = null;
  try {
    parsed = JSON.parse(row.license_json);
  } catch {
    parsed = null;
  }
  const now = Date.now();
  const status =
    row.status === 'revoked' || row.status === 'blocked' || row.status === 'draft'
      ? row.status
      : row.expiry_date <= now
        ? 'expired'
        : row.is_paid
          ? 'paid'
          : row.status;
  return {
    id: row.id,
    licenseKey: row.license_key,
    appId: row.app_id,
    appName: APP_META[row.app_id]?.name ?? row.app_id,
    userName: row.user_name,
    deviceId: row.device_id,
    deviceLabel: row.device_label,
    planCode: row.plan_code,
    planLabel: row.plan_label,
    price: row.price,
    currency: row.currency,
    priceText: row.price_text,
    creationDate: row.creation_date,
    expiryDate: row.expiry_date,
    signature: row.signature,
    licenseJson: parsed,
    status,
    isPaid: Boolean(row.is_paid),
    paidAt: row.paid_at,
    paymentMethod: row.payment_method,
    email: row.email,
    phone: row.phone,
    notes: row.notes,
    source: row.source,
    issuedBy: row.issued_by,
    revokedAt: row.revoked_at,
    revokeReason: row.revoke_reason,
    renewedFrom: row.renewed_from,
    lastSeenAt: row.last_seen_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    daysRemaining: Math.max(0, Math.floor((row.expiry_date - now) / 86400000)),
  };
}

export function getLicense(id) {
  const db = getDb();
  return mapLicense(db.prepare('SELECT * FROM licenses WHERE id = ?').get(id));
}

export function queryLicenses({
  appId,
  status,
  search,
  deviceId,
  paid,
  expiringInDays,
  sortBy = 'created_at',
  sortDir = 'DESC',
  limit = 50,
  offset = 0,
}) {
  const db = getDb();
  const where = [];
  const params = [];

  if (appId) { where.push('app_id = ?'); params.push(appId); }
  if (status === 'expired') {
    where.push("status NOT IN ('revoked','blocked','draft') AND expiry_date <= ?");
    params.push(Date.now());
  } else if (status === 'active') {
    where.push("status NOT IN ('revoked','blocked','draft') AND expiry_date > ?");
    params.push(Date.now());
  } else if (status) {
    where.push('status = ?');
    params.push(status);
  }
  if (deviceId) { where.push('device_id = ?'); params.push(deviceId); }
  if (paid !== undefined && paid !== null) { where.push('is_paid = ?'); params.push(paid ? 1 : 0); }
  if (expiringInDays) {
    where.push('expiry_date BETWEEN ? AND ?');
    params.push(Date.now(), Date.now() + Number(expiringInDays) * 86400000);
  }
  if (search) {
    where.push('(user_name LIKE ? OR device_id LIKE ? OR license_key LIKE ? OR email LIKE ? OR phone LIKE ? OR notes LIKE ?)');
    const like = `%${search}%`;
    params.push(like, like, like, like, like, like);
  }

  const allowedSort = {
    created_at: 'created_at',
    expiry_date: 'expiry_date',
    user_name: 'user_name',
    price: 'price',
    status: 'status',
  };
  const sortCol = allowedSort[sortBy] ?? 'created_at';
  const dir = String(sortDir).toUpperCase() === 'ASC' ? 'ASC' : 'DESC';
  const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';

  const items = db
    .prepare(`SELECT * FROM licenses ${clause} ORDER BY ${sortCol} ${dir}, id ${dir} LIMIT ? OFFSET ?`)
    .all(...params, Math.min(Math.max(Number(limit) || 50, 1), 500), Math.max(Number(offset) || 0, 0))
    .map(mapLicense);
  const total = db.prepare(`SELECT COUNT(*) AS c FROM licenses ${clause}`).get(...params).c;
  return { items, total };
}

/**
 * Emite (y firma) una licencia. Equivalente web del flujo completo del keygen:
 * registro de solicitud + botón "Cobrar".
 */
export function issueLicense(payload, actor = null) {
  const db = getDb();
  const appId = payload.appId || 'drywall_calculator';
  if (!APP_IDS.includes(appId)) throw badRequest('appId inválido');

  const userName = String(payload.userName ?? '').trim();
  const deviceId = normalizeBase64(String(payload.deviceId ?? '').trim());
  if (!userName) throw badRequest('El nombre de usuario es obligatorio');
  if (userName.length > 100) throw badRequest('El nombre de usuario no puede exceder 100 caracteres');
  if (!deviceId) throw badRequest('El ID de dispositivo es obligatorio');
  if (deviceId.length > 200) throw badRequest('El ID de dispositivo es demasiado largo');

  const plan = resolvePlan(appId, payload.planCode ?? payload.planLabel);
  if (!plan) throw badRequest('Plan no encontrado para esta app');

  const currency = String(payload.currency || plan.currency || 'USD').toUpperCase();
  const price = Number(payload.price ?? plan.price);
  if (!Number.isFinite(price) || price < 0) throw badRequest('Precio inválido');

  const creationDate = Number(payload.creationDate) || Date.now();
  const days = Number(payload.days) || plan.days;
  if (days <= 0 || days > 36500) throw badRequest('Días de vigencia inválidos');
  // `baseDate` permite encadenar renovaciones sobre el vencimiento anterior.
  const expiryBase = Number(payload.baseDate) || creationDate;
  const expiryDate = Number(payload.expiryDate) || computeExpiry(expiryBase, days);

  // Dispositivo bloqueado ⇒ no se emite.
  const blockedDevice = db
    .prepare('SELECT is_blocked FROM devices WHERE app_id = ? AND device_id = ?')
    .get(appId, deviceId);
  if (blockedDevice?.is_blocked) throw conflict('El dispositivo está bloqueado y no puede recibir licencias');

  const key = ensureActiveKey(appId, actor);
  const dataToSign = buildDataToSign(userName, deviceId, creationDate, expiryDate);
  const signature = signLicenseData(getPrivateKeyDer(key._row), dataToSign);

  const licenseJson = JSON.stringify({
    user: userName,
    deviceId,
    creationDate,
    expiryDate,
    signature,
    type: plan.typeName,
    issuerKey: key.publicKeyB64,
  });

  const ts = Date.now();
  const licenseKey = generateLicenseKey(appId);
  const isPaid = payload.markPaid === undefined ? true : Boolean(payload.markPaid);
  const status = payload.status && VALID_STATUS.includes(payload.status) ? payload.status : isPaid ? 'paid' : 'issued';
  const priceText = `${price.toFixed(2)} ${currency}`;

  const id = db.transaction(() => {
    const newId = db
      .prepare(
        `INSERT INTO licenses
          (license_key, app_id, user_name, device_id, plan_code, plan_label, price, currency, price_text,
           creation_date, expiry_date, signature, license_json, key_id, status, is_paid, paid_at, payment_method,
           email, phone, notes, source, issued_by, device_label, renewed_from, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .insert(
        licenseKey, appId, userName, deviceId, plan.code, plan.label, price, currency, priceText,
        creationDate, expiryDate, signature, licenseJson, key.id, status, isPaid ? 1 : 0,
        isPaid ? ts : null, payload.paymentMethod ?? null,
        payload.email ?? null, payload.phone ?? null, String(payload.notes ?? '').slice(0, 1000),
        payload.source ?? 'web', actor?.id ?? null, payload.deviceLabel ?? null,
        payload.renewedFrom ?? null, ts, ts
      );

    upsertDevice(db, appId, deviceId, userName, payload.deviceLabel, ts);
    return newId;
  });

  audit({
    actor,
    action: 'licenses.issue',
    entity: 'license',
    entityId: String(id),
    appId,
    severity: 'info',
    detail: { licenseKey, userName, plan: plan.code, expiryDate, keyId: key.kid },
  });

  return getLicense(id);
}

function resolvePlan(appId, planCodeOrLabel) {
  const db = getDb();
  if (!planCodeOrLabel) return mapPlan(db.prepare('SELECT * FROM plans WHERE app_id = ? ORDER BY sort_order LIMIT 1').get(appId));
  const byCode = db.prepare('SELECT * FROM plans WHERE app_id = ? AND code = ?').get(appId, String(planCodeOrLabel).toUpperCase());
  if (byCode) return mapPlan(byCode);
  const all = db.prepare('SELECT * FROM plans WHERE app_id = ?').all(appId).map(mapPlan);
  const wanted = String(planCodeOrLabel).trim().toLowerCase();
  return (
    all.find((p) => p.label.toLowerCase() === wanted) ||
    all.find((p) => p.typeName.toLowerCase() === wanted) ||
    all.find((p) => p.label.toLowerCase().includes(wanted) || wanted.includes(p.label.toLowerCase())) ||
    null
  );
}

function upsertDevice(db, appId, deviceId, ownerName, label, ts) {
  const existing = db.prepare('SELECT * FROM devices WHERE app_id = ? AND device_id = ?').get(appId, deviceId);
  if (existing) {
    db.prepare(
      'UPDATE devices SET last_seen_at = ?, owner_name = COALESCE(NULLIF(?, \'\'), owner_name), device_label = COALESCE(NULLIF(?, \'\'), device_label), license_count = license_count + 1 WHERE id = ?'
    ).run(ts, ownerName ?? '', label ?? '', existing.id);
  } else {
    db.prepare(
      `INSERT INTO devices (app_id, device_id, device_label, owner_name, first_seen_at, last_seen_at, is_blocked, license_count)
       VALUES (?, ?, ?, ?, ?, ?, 0, 1)`
    ).run(appId, deviceId, label ?? '', ownerName ?? '', ts, ts);
  }
}

/** Renueva: genera una NUEVA licencia firmada encadenada a la anterior. */
export function renewLicense(licenseId, payload = {}, actor = null) {
  const db = getDb();
  const current = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licenseId);
  if (!current) throw notFound('Licencia no encontrada');
  if (current.status === 'revoked' || current.status === 'blocked') {
    throw conflict('No se puede renovar una licencia revocada o bloqueada');
  }

  const plan = resolvePlan(current.app_id, payload.planCode ?? current.plan_code);
  const days = Number(payload.days) || plan.days;
  // La renovación extiende desde el vencimiento actual si la licencia aún está vigente.
  const base = current.expiry_date > Date.now() ? current.expiry_date : Date.now();

  return issueLicense(
    {
      appId: current.app_id,
      userName: current.user_name,
      deviceId: current.device_id,
      deviceLabel: current.device_label,
      planCode: plan.code,
      days,
      baseDate: base,
      price: payload.price ?? current.price,
      currency: payload.currency ?? current.currency,
      email: payload.email ?? current.email,
      phone: payload.phone ?? current.phone,
      notes: payload.notes ?? `Renovación de ${current.license_key}`,
      creationDate: Date.now(),
      markPaid: payload.markPaid ?? true,
      source: 'web',
      renewedFrom: current.id,
    },
    actor
  );
}

export function markPaid(licenseId, { paymentMethod = null, price, currency } = {}, actor = null) {
  const db = getDb();
  const row = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licenseId);
  if (!row) throw notFound('Licencia no encontrada');
  if (row.is_paid) throw conflict('La licencia ya está cobrada');
  const ts = Date.now();
  const newPrice = price !== undefined ? Number(price) : row.price;
  const newCurrency = (currency || row.currency || 'USD').toUpperCase();
  db.prepare(
    `UPDATE licenses SET is_paid = 1, paid_at = ?, status = 'paid', payment_method = ?, price = ?, currency = ?, price_text = ?, updated_at = ? WHERE id = ?`
  ).run(ts, paymentMethod, newPrice, newCurrency, `${newPrice.toFixed(2)} ${newCurrency}`, ts, licenseId);
  audit({ actor, action: 'licenses.mark_paid', entity: 'license', entityId: String(licenseId), appId: row.app_id, severity: 'info', detail: { paymentMethod, price: newPrice } });
  return getLicense(licenseId);
}

/** Revoca: marca el estado y publica la firma en la lista negra de la app. */
export function revokeLicense(licenseId, reason = '', actor = null) {
  const db = getDb();
  const row = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licenseId);
  if (!row) throw notFound('Licencia no encontrada');
  if (row.status === 'revoked') throw conflict('La licencia ya estaba revocada');
  const ts = Date.now();

  db.transaction(() => {
    db.prepare(
      `UPDATE licenses SET status = 'revoked', revoked_at = ?, revoked_by = ?, revoke_reason = ?, updated_at = ? WHERE id = ?`
    ).run(ts, actor?.id ?? null, String(reason || '').slice(0, 500), ts, licenseId);
    db.prepare(
      `INSERT OR IGNORE INTO license_blacklist (app_id, signature, license_id, reason, created_by, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).run(row.app_id, row.signature, licenseId, String(reason || '').slice(0, 500), actor?.id ?? null, ts);
  });

  audit({ actor, action: 'licenses.revoke', entity: 'license', entityId: String(licenseId), appId: row.app_id, severity: 'critical', detail: { reason, licenseKey: row.license_key } });
  return getLicense(licenseId);
}

export function deleteLicense(licenseId, actor = null) {
  const db = getDb();
  const row = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licenseId);
  if (!row) throw notFound('Licencia no encontrada');
  db.prepare('DELETE FROM licenses WHERE id = ?').run(licenseId);
  audit({ actor, action: 'licenses.delete', entity: 'license', entityId: String(licenseId), appId: row.app_id, severity: 'warn', detail: { licenseKey: row.license_key, userName: row.user_name } });
  return true;
}

/** ¿Está revocada una firma? Lo consultan las apps Android sin autenticarse. */
export function isSignatureRevoked(appId, signature) {
  const db = getDb();
  const row = db.prepare('SELECT id FROM license_blacklist WHERE app_id = ? AND signature = ?').get(appId, String(signature));
  return Boolean(row);
}

export function listBlacklist(appId = null) {
  const db = getDb();
  const rows = appId
    ? db.prepare('SELECT * FROM license_blacklist WHERE app_id = ? ORDER BY created_at DESC').all(appId)
    : db.prepare('SELECT * FROM license_blacklist ORDER BY created_at DESC').all();
  return rows.map((r) => ({
    id: r.id,
    appId: r.app_id,
    signature: r.signature,
    licenseId: r.license_id,
    reason: r.reason,
    createdAt: r.created_at,
    createdBy: r.created_by,
  }));
}

/**
 * Importa licencias emitidas por las apps Android (export JSON del keygen o
 * carpeta Licencias_Generadas). Verifica la firma antes de aceptarla.
 */
export function importLicenses(entries, actor = null) {
  const db = getDb();
  const results = { imported: 0, updated: 0, skipped: 0, invalidSignature: 0, errors: [] };

  for (const raw of entries ?? []) {
    try {
      const entry = normalizeImportEntry(raw);
      if (!entry) { results.skipped += 1; continue; }

      const key = getActiveKey(entry.appId);
      let signatureOk = false;
      if (key) {
        const dataToSign = buildDataToSign(entry.user, entry.deviceId, entry.creationDate, entry.expiryDate);
        signatureOk = verifyLicenseData(key.publicKeyB64, dataToSign, entry.signature);
      }
      if (!signatureOk) {
        results.invalidSignature += 1;
        results.errors.push({ user: entry.user, reason: 'Firma no verificable con la clave activa' });
        continue;
      }

      const existing = db.prepare('SELECT id FROM licenses WHERE signature = ? AND app_id = ?').get(entry.signature, entry.appId);
      const ts = Date.now();
      const licenseJson = JSON.stringify({
        user: entry.user,
        deviceId: entry.deviceId,
        creationDate: entry.creationDate,
        expiryDate: entry.expiryDate,
        signature: entry.signature,
        type: entry.typeName,
        issuerKey: key.publicKeyB64,
      });

      if (existing) {
        db.prepare(
          `UPDATE licenses SET user_name = ?, device_id = ?, plan_label = ?, price = ?, currency = ?, price_text = ?,
             creation_date = ?, expiry_date = ?, license_json = ?, is_paid = ?, status = ?, source = ?, updated_at = ?
           WHERE id = ?`
        ).run(
          entry.user, entry.deviceId, entry.typeName, entry.price, entry.currency, `${entry.price.toFixed(2)} ${entry.currency}`,
          entry.creationDate, entry.expiryDate, licenseJson, entry.isPaid ? 1 : 0,
          entry.isPaid ? 'paid' : 'issued', 'import', ts, existing.id
        );
        results.updated += 1;
      } else {
        db.prepare(
          `INSERT INTO licenses
            (license_key, app_id, user_name, device_id, plan_code, plan_label, price, currency, price_text,
             creation_date, expiry_date, signature, license_json, key_id, status, is_paid, paid_at, source, issued_by, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'import', ?, ?, ?)`
        ).insert(
          entry.licenseKey || generateLicenseKey(entry.appId), entry.appId, entry.user, entry.deviceId,
          entry.planCode || 'IMPORTED', entry.typeName, entry.price, entry.currency, `${entry.price.toFixed(2)} ${entry.currency}`,
          entry.creationDate, entry.expiryDate, entry.signature, licenseJson, key.id,
          entry.isPaid ? 'paid' : 'issued', entry.isPaid ? 1 : 0, entry.isPaid ? ts : null,
          actor?.id ?? null, ts, ts
        );
        results.imported += 1;
        upsertDevice(db, entry.appId, entry.deviceId, entry.user, '', ts);
      }
    } catch (err) {
      results.skipped += 1;
      results.errors.push({ reason: err.message });
    }
  }

  audit({
    actor,
    action: 'licenses.import',
    entity: 'license',
    severity: 'info',
    detail: { imported: results.imported, updated: results.updated, invalid: results.invalidSignature, skipped: results.skipped },
  });
  return results;
}

/** Acepta los formatos: LicenseInfo puro, {codigoLicencia:{...}} del LicenseSaver, e IssuedLicense de Room. */
function normalizeImportEntry(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const lic = raw.codigoLicencia && typeof raw.codigoLicencia === 'object' ? raw.codigoLicencia : raw;
  const signature = lic.signature ?? raw.signature;
  const user = lic.user ?? raw.userName ?? raw.usuario;
  const deviceId = lic.deviceId ?? raw.deviceId ?? raw.dispositivo;
  if (!signature || !user || !deviceId) return null;

  const creationDate = Number(lic.creationDate ?? raw.dateIssued ?? Date.now());
  const expiryDate = Number(lic.expiryDate ?? creationDate + 30 * 86400000);
  const type = lic.type ?? raw.planType ?? raw.plan ?? 'LICENCIA IMPORTADA';
  const appId = raw.appId && APP_IDS.includes(raw.appId) ? raw.appId : inferAppId(type, raw);
  const plan = KEYGEN_PLANS.find((p) => p.typeName === type || p.label === type);

  const priceRaw = raw.price ?? raw.precio ?? plan?.price ?? 0;
  const price = Number(String(priceRaw).replace(/[^\d.]/g, '')) || 0;
  const currency = (String(priceRaw).match(/[A-Z]{3}/)?.[0] ?? raw.currency ?? 'USD').toUpperCase();

  return {
    appId,
    user: String(user).trim(),
    deviceId: normalizeBase64(String(deviceId).trim()),
    creationDate,
    expiryDate,
    signature: String(signature),
    typeName: String(type),
    planCode: plan?.code ?? null,
    price,
    currency,
    isPaid: raw.isPaid === true || raw.isPaid === 1 || Boolean(signature && raw.licenseJson),
    licenseKey: raw.licenseKey ?? raw.license_key ?? null,
  };
}

function inferAppId(type, raw) {
  const text = `${type} ${raw.app ?? ''} ${raw.package ?? ''}`.toLowerCase();
  if (text.includes('keygen')) return 'keygen_pro';
  return 'drywall_calculator';
}

/** Métricas para el dashboard. */
export function getStats(appId = null) {
  const db = getDb();
  const filter = appId ? 'WHERE app_id = ?' : '';
  const params = appId ? [appId] : [];
  const now = Date.now();
  const in7 = now + 7 * 86400000;
  const in30 = now + 30 * 86400000;

  const one = (sql, p = params) => db.prepare(sql).get(...p);

  const total = one(`SELECT COUNT(*) c FROM licenses ${filter}`, params).c;
  const active = one(
    `SELECT COUNT(*) c FROM licenses ${filter ? filter + ' AND' : 'WHERE'} status NOT IN ('revoked','blocked','draft') AND expiry_date > ?`,
    [...params, now]
  ).c;
  const expired = one(
    `SELECT COUNT(*) c FROM licenses ${filter ? filter + ' AND' : 'WHERE'} status NOT IN ('revoked','blocked','draft') AND expiry_date <= ?`,
    [...params, now]
  ).c;
  const revoked = one(`SELECT COUNT(*) c FROM licenses ${filter ? filter + ' AND' : 'WHERE'} status = 'revoked'`, params).c;
  const pending = one(`SELECT COUNT(*) c FROM licenses ${filter ? filter + ' AND' : 'WHERE'} is_paid = 0`, params).c;
  const expiring7 = one(
    `SELECT COUNT(*) c FROM licenses ${filter ? filter + ' AND' : 'WHERE'} expiry_date BETWEEN ? AND ?`,
    [...params, now, in7]
  ).c;
  const expiring30 = one(
    `SELECT COUNT(*) c FROM licenses ${filter ? filter + ' AND' : 'WHERE'} expiry_date BETWEEN ? AND ?`,
    [...params, now, in30]
  ).c;
  const revenue = one(
    `SELECT COALESCE(SUM(price), 0) s FROM licenses ${filter ? filter + ' AND' : 'WHERE'} is_paid = 1`,
    params
  ).s;

  const byApp = db
    .prepare(
      `SELECT app_id, COUNT(*) total,
              SUM(CASE WHEN is_paid = 1 THEN 1 ELSE 0 END) paid,
              SUM(CASE WHEN is_paid = 1 THEN price ELSE 0 END) revenue,
              SUM(CASE WHEN expiry_date > ${now} AND status NOT IN ('revoked','blocked') THEN 1 ELSE 0 END) active
       FROM licenses GROUP BY app_id`
    )
    .all()
    .map((r) => ({
      appId: r.app_id,
      appName: APP_META[r.app_id]?.name ?? r.app_id,
      total: r.total,
      paid: r.paid ?? 0,
      revenue: r.revenue ?? 0,
      active: r.active ?? 0,
    }));

  const byPlan = db
    .prepare(`SELECT plan_label, COUNT(*) total, SUM(CASE WHEN is_paid=1 THEN price ELSE 0 END) revenue
              FROM licenses ${filter} GROUP BY plan_label ORDER BY total DESC`)
    .all(...params);

  const monthly = db
    .prepare(
      `SELECT strftime('%Y-%m', datetime(created_at/1000, 'unixepoch')) AS month,
              COUNT(*) total, SUM(CASE WHEN is_paid=1 THEN price ELSE 0 END) revenue
       FROM licenses ${filter ? filter + ' AND' : 'WHERE'} created_at >= ?
       GROUP BY month ORDER BY month ASC`
    )
    .all(...params, now - 365 * 86400000);

  const devices = one(`SELECT COUNT(*) c FROM devices ${filter}`, params).c;
  const blockedDevices = one(`SELECT COUNT(*) c FROM devices ${filter ? filter + ' AND' : 'WHERE'} is_blocked = 1`, params).c;

  return {
    total, active, expired, revoked, pending, expiring7, expiring30,
    revenue: Number(revenue), devices, blockedDevices,
    byApp, byPlan, monthly,
    generatedAt: now,
  };
}
