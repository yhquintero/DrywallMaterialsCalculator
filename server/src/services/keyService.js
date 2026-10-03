/**
 * Gestión de claves de firma por aplicación.
 * Cada app (DrywallPro Master / Keygen Pro) tiene su propio par RSA-4096;
 * la llave privada se guarda cifrada con AES-256-GCM derivado del MASTER_KEY.
 */
import crypto from 'node:crypto';
import { getDb } from '../db/index.js';
import {
  generateRsaKeyPair,
  encryptSecret,
  decryptSecret,
  sha256Hex,
  buildKid,
  keyFingerprint,
} from './cryptoService.js';
import { APP_IDS, APP_META } from '../config/permissions.js';
import { audit } from './auditService.js';

function mapKey(row) {
  if (!row) return null;
  return {
    id: row.id,
    appId: row.app_id,
    appName: APP_META[row.app_id]?.name ?? row.app_id,
    kid: row.kid,
    algorithm: row.algorithm,
    modulusBits: row.modulus_bits,
    publicKeyB64: row.public_key_b64,
    publicKeyPem: row.public_key_pem,
    publicKeySha256: row.public_key_sha256,
    fingerprint: keyFingerprint(row.public_key_b64),
    isActive: Boolean(row.is_active),
    createdAt: row.created_at,
    rotatedAt: row.rotated_at,
    createdBy: row.created_by,
    notes: row.notes,
  };
}

export function listKeys(appId = null) {
  const db = getDb();
  const rows = appId
    ? db.prepare('SELECT * FROM signing_keys WHERE app_id = ? ORDER BY is_active DESC, created_at DESC').all(appId)
    : db.prepare('SELECT * FROM signing_keys ORDER BY app_id, is_active DESC, created_at DESC').all();
  return rows.map(mapKey);
}

export function getActiveKey(appId) {
  const db = getDb();
  const row = db
    .prepare('SELECT * FROM signing_keys WHERE app_id = ? AND is_active = 1 ORDER BY created_at DESC LIMIT 1')
    .get(appId);
  return row ? { ...mapKey(row), _row: row } : null;
}

/** Devuelve la llave activa o la genera automáticamente (bootstrap). */
export function ensureActiveKey(appId, actor = null) {
  const existing = getActiveKey(appId);
  if (existing) return existing;
  return rotateKey(appId, actor, 'Generación inicial automática');
}

/** Genera un nuevo par y lo marca activo; desactiva los anteriores. */
export function rotateKey(appId, actor = null, notes = '') {
  if (!APP_IDS.includes(appId)) throw new Error(`app_id desconocido: ${appId}`);
  const db = getDb();

  return db.transaction(() => {
    const pair = generateRsaKeyPair();
    const kid = buildKid(appId);
    const enc = encryptSecret(pair.privateKeyB64);
    const ts = Date.now();

    db.prepare('UPDATE signing_keys SET is_active = 0, rotated_at = ? WHERE app_id = ? AND is_active = 1').run(ts, appId);

    const id = db
      .prepare(
        `INSERT INTO signing_keys
          (app_id, kid, algorithm, modulus_bits, public_key_b64, public_key_pem, public_key_sha256,
           private_key_enc, private_key_iv, private_key_tag, is_active, created_by, created_at, notes)
         VALUES (?, ?, 'SHA256withRSA', ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)`
      )
      .insert(
        appId,
        kid,
        pair.modulusBits,
        pair.publicKeyB64,
        pair.publicKeyPem,
        sha256Hex(Buffer.from(pair.publicKeyB64, 'base64')),
        enc.ciphertext,
        enc.iv,
        enc.tag,
        actor?.id ?? null,
        ts,
        String(notes || '').slice(0, 500)
      );

    audit({
      actor,
      action: 'keys.rotate',
      entity: 'signing_key',
      entityId: String(id),
      appId,
      severity: 'critical',
      detail: { kid, modulusBits: pair.modulusBits, sha256: sha256Hex(Buffer.from(pair.publicKeyB64, 'base64')) },
    });

    return { ...mapKey(db.prepare('SELECT * FROM signing_keys WHERE id = ?').get(id)), _row: db.prepare('SELECT * FROM signing_keys WHERE id = ?').get(id) };
  });
}

/** Recupera la llave privada DER en memoria (solo para firmar). Nunca se loguea. */
export function getPrivateKeyDer(keyRow) {
  const b64 = decryptSecret(keyRow.private_key_enc, keyRow.private_key_iv, keyRow.private_key_tag);
  return Buffer.from(b64, 'base64');
}

export function exportPrivateKeyPem(appId, actor = null) {
  const db = getDb();
  const active = getActiveKey(appId);
  if (!active) throw new Error('No hay clave activa para esta app');
  const der = getPrivateKeyDer(active._row);
  const b64 = der.toString('base64');
  const lines = b64.match(/.{1,64}/g) ?? [];
  audit({
    actor,
    action: 'keys.export_private',
    entity: 'signing_key',
    entityId: String(active.id),
    appId,
    severity: 'critical',
    detail: { kid: active.kid },
  });
  return {
    kid: active.kid,
    pem: `-----BEGIN PRIVATE KEY-----\n${lines.join('\n')}\n-----END PRIVATE KEY-----\n`,
    pkcs8Base64: b64,
  };
}

/**
 * Importa una llave privada existente (por ejemplo la generada por el keygen
 * Android) para que la web emita licencias verificables con la MISMA clave que
 * ya tienen instalada los clientes.
 */
export function importPrivateKey(appId, pkcs8Base64OrPem, actor = null, notes = '') {
  const db = getDb();
  const clean = String(pkcs8Base64OrPem)
    .replace(/-----BEGIN [^-]+-----/g, '')
    .replace(/-----END [^-]+-----/g, '')
    .replace(/\s+/g, '');
  if (clean.length < 100) throw new Error('Clave privada inválida o vacía');

  const der = Buffer.from(clean, 'base64');
    const publicKeyDer = crypto
      .createPublicKey({ key: der, format: 'der', type: 'pkcs8' })
      .export({ format: 'der', type: 'spki' });

  return db.transaction(() => {
    const pubB64 = publicKeyDer.toString('base64');
    const pubPem = `-----BEGIN PUBLIC KEY-----\n${(pubB64.match(/.{1,64}/g) ?? []).join('\n')}\n-----END PUBLIC KEY-----\n`;
    const enc = encryptSecret(clean);
    const ts = Date.now();
    const kid = buildKid(appId);
    const modulusBits = der.length > 2000 ? 4096 : 2048;

    db.prepare('UPDATE signing_keys SET is_active = 0, rotated_at = ? WHERE app_id = ? AND is_active = 1').run(ts, appId);
    const id = db
      .prepare(
        `INSERT INTO signing_keys
          (app_id, kid, algorithm, modulus_bits, public_key_b64, public_key_pem, public_key_sha256,
           private_key_enc, private_key_iv, private_key_tag, is_active, created_by, created_at, notes)
         VALUES (?, ?, 'SHA256withRSA', ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)`
      )
      .insert(appId, kid, modulusBits, pubB64, pubPem, sha256Hex(publicKeyDer), enc.ciphertext, enc.iv, enc.tag, actor?.id ?? null, ts, String(notes || '').slice(0, 500));

    audit({
      actor,
      action: 'keys.import',
      entity: 'signing_key',
      entityId: String(id),
      appId,
      severity: 'critical',
      detail: { kid, sha256: sha256Hex(publicKeyDer) },
    });

    const row = db.prepare('SELECT * FROM signing_keys WHERE id = ?').get(id);
    return { ...mapKey(row), _row: row };
  });
}

export function deleteKey(keyId, actor = null) {
  const db = getDb();
  const row = db.prepare('SELECT * FROM signing_keys WHERE id = ?').get(keyId);
  if (!row) return false;
  if (row.is_active) throw new Error('No puedes eliminar la clave activa; rota primero a otra clave.');
  db.prepare('DELETE FROM signing_keys WHERE id = ?').run(keyId);
  audit({ actor, action: 'keys.delete', entity: 'signing_key', entityId: String(keyId), appId: row.app_id, severity: 'warn', detail: { kid: row.kid } });
  return true;
}
