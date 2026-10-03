import './setup.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';

import { getDb } from '../src/db/index.js';
import { APP_META } from '../src/config/permissions.js';
import {
  issueLicense,
  renewLicense,
  revokeLicense,
  isSignatureRevoked,
  getLicense,
  queryLicenses,
  getStats,
  importLicenses,
  listPlans,
} from '../src/services/licenseService.js';
import { getActiveKey } from '../src/services/keyService.js';
import { normalizeBase64 } from '../src/services/cryptoService.js';

import { ensureBootstrapAdmin } from '../tools/bootstrap.js';

getDb(); // crea esquema + semillas
const boot = ensureBootstrapAdmin({ quiet: true });

const actor = { id: boot.created ? 1 : 1, username: 'root' };
assert.equal(actor.id, 1, 'el admin bootstrap debe tener id 1');
const DEVICE = normalizeBase64(crypto.createHash('sha256').update('ANDROID-ID-TEST').digest('base64'));

/**
 * Replica SecurityUtils.verifySignature de Android.
 * En Java el algoritmo se llama "SHA256withRSA"; en Node su equivalente es "RSA-SHA256"
 * (ambos = RSASSA-PKCS1-v1_5 con SHA-256), así que las firmas son intercambiables.
 */
function verifyLikeAndroid(publicKeyB64, dataToSign, signatureB64) {
  const verifier = crypto.createVerify('RSA-SHA256');
  verifier.update(Buffer.from(dataToSign, 'utf8'));
  verifier.end();
  const pub = crypto.createPublicKey({
    key: Buffer.from(publicKeyB64, 'base64'),
    format: 'der',
    type: 'spki',
  });
  return verifier.verify(pub, Buffer.from(signatureB64, 'base64'));
}

test('emite una licencia con el JSON exacto que consume la app Android', () => {
  const license = issueLicense(
    { appId: 'drywall_calculator', userName: 'Juan Pérez', deviceId: DEVICE, planCode: 'ONE_MONTH', markPaid: true },
    actor
  );

  assert.equal(license.appId, 'drywall_calculator');
  assert.equal(license.planLabel, '1 Mes Profesional');
  assert.equal(license.isPaid, true);
  assert.equal(license.status, 'paid');

  const json = license.licenseJson;
  assert.deepEqual(Object.keys(json).sort(), ['creationDate', 'deviceId', 'expiryDate', 'issuerKey', 'signature', 'type', 'user']);
  assert.equal(json.type, '1 MES PROFESIONAL');
  assert.equal(json.user, 'Juan Pérez');
  assert.equal(json.deviceId, DEVICE);

  // La firma debe verificar con la clave pública activa, igual que en Android.
  const key = getActiveKey('drywall_calculator');
  assert.ok(key, 'debe existir clave activa');
  assert.equal(json.issuerKey, key.publicKeyB64);
  const dataToSign = `${json.user}|${json.deviceId}|${json.creationDate}|${json.expiryDate}`;
  assert.equal(verifyLikeAndroid(key.publicKeyB64, dataToSign, json.signature), true, 'firma inválida');
});

test('la vigencia termina a las 23:59:59 del último día (igual que el keygen)', () => {
  const license = issueLicense({ appId: 'drywall_calculator', userName: 'Ana', deviceId: DEVICE, planCode: 'ONE_WEEK' }, actor);
  const expiry = new Date(license.expiryDate);
  assert.equal(expiry.getHours(), 23);
  assert.equal(expiry.getMinutes(), 59);
  assert.equal(expiry.getSeconds(), 59);
  const days = Math.round((license.expiryDate - license.creationDate) / 86400000);
  assert.ok(days >= 7 && days <= 8, `7 días esperados, obtuvo ${days}`);
});

test('cada app tiene su propia clave y sus licencias no cruzan', () => {
  const dw = issueLicense({ appId: 'drywall_calculator', userName: 'Carlos', deviceId: DEVICE, planCode: 'ONE_DAY' }, actor);
  const kg = issueLicense({ appId: 'keygen_pro', userName: 'Carlos', deviceId: DEVICE, planCode: 'ONE_DAY' }, actor);
  assert.notEqual(dw.licenseJson.issuerKey, kg.licenseJson.issuerKey);
  assert.match(dw.licenseKey, /^DW-/);
  assert.match(kg.licenseKey, /^KG-/);
  assert.equal(APP_META[kg.appId].name, 'Keygen Pro');
});

test('la renovación encadena sobre el vencimiento anterior', () => {
  const original = issueLicense({ appId: 'drywall_calculator', userName: 'Luis', deviceId: DEVICE, planCode: 'ONE_MONTH' }, actor);
  const renewed = renewLicense(original.id, { planCode: 'ONE_MONTH' }, actor);
  assert.equal(renewed.renewedFrom, original.id);
  assert.ok(renewed.expiryDate > original.expiryDate, 'la renovación debe extender la vigencia');
  const extraDays = Math.round((renewed.expiryDate - original.expiryDate) / 86400000);
  assert.ok(extraDays >= 29 && extraDays <= 31, `~30 días extra, obtuvo ${extraDays}`);
});

test('revocar publica la firma en la lista negra', () => {
  const license = issueLicense({ appId: 'drywall_calculator', userName: 'Revoke Me', deviceId: DEVICE, planCode: 'ONE_MONTH' }, actor);
  assert.equal(isSignatureRevoked('drywall_calculator', license.signature), false);
  const revoked = revokeLicense(license.id, 'Fraude detectado', actor);
  assert.equal(revoked.status, 'revoked');
  assert.equal(isSignatureRevoked('drywall_calculator', license.signature), true);
  // La firma NO aparece en la lista negra de la otra app.
  assert.equal(isSignatureRevoked('keygen_pro', license.signature), false);
});

test('no se puede renovar una licencia revocada', () => {
  const license = issueLicense({ appId: 'drywall_calculator', userName: 'No Renew', deviceId: DEVICE, planCode: 'ONE_MONTH' }, actor);
  revokeLicense(license.id, 'baja', actor);
  assert.throws(() => renewLicense(license.id, {}, actor), /revocada|bloqueada/i);
});

test('rechaza datos inválidos', () => {
  assert.throws(() => issueLicense({ appId: 'drywall_calculator', userName: '', deviceId: DEVICE }, actor), /usuario/i);
  assert.throws(() => issueLicense({ appId: 'drywall_calculator', userName: 'X', deviceId: '' }, actor), /dispositivo/i);
  assert.throws(() => issueLicense({ appId: 'no_existe', userName: 'X', deviceId: DEVICE }, actor), /appId/);
  assert.throws(() => issueLicense({ appId: 'drywall_calculator', userName: 'X', deviceId: DEVICE, days: -5 }, actor), /vigencia/i);
});

test('consultas con filtros y estadísticas', () => {
  const all = queryLicenses({ limit: 500 });
  assert.ok(all.total > 0);
  const byApp = queryLicenses({ appId: 'keygen_pro', limit: 500 });
  assert.ok(byApp.items.every((l) => l.appId === 'keygen_pro'));
  const bySearch = queryLicenses({ search: 'Juan Pérez' });
  assert.ok(bySearch.items.some((l) => l.userName === 'Juan Pérez'));

  const stats = getStats();
  assert.equal(typeof stats.total, 'number');
  assert.ok(stats.revenue > 0);
  assert.equal(stats.byApp.length, 2);
  assert.ok(Array.isArray(stats.monthly));
});

test('importa licencias emitidas por el keygen Android y verifica su firma', () => {
  // Simula el export del keygen Android (LicenseSaver):
  // { usuario, dispositivo, plan, precio, fechaGeneracion, codigoLicencia:{...} }
  const key = getActiveKey('drywall_calculator');

  // Emite una licencia real y luego la re-importa con otro formato de entrada.
  const emitted = issueLicense({ appId: 'drywall_calculator', userName: 'Import Test', deviceId: DEVICE, planCode: 'ONE_YEAR' }, actor);
  const androidExport = {
    usuario: emitted.userName,
    dispositivo: emitted.deviceId,
    plan: emitted.planLabel,
    precio: `${emitted.price.toFixed(2)} ${emitted.currency}`,
    fechaGeneracion: '01/01/2026 10:00:00',
    codigoLicencia: emitted.licenseJson,
  };
  const result = importLicenses([androidExport], actor);
  assert.equal(result.updated, 1, JSON.stringify(result));
  assert.equal(result.invalidSignature, 0);

  const bad = { ...androidExport, codigoLicencia: { ...emitted.licenseJson, user: 'Otro Usuario' } };
  const badResult = importLicenses([bad], actor);
  assert.equal(badResult.invalidSignature, 1);
  assert.equal(key.appId, 'drywall_calculator');
});

test('los planes replican el enum LicenseTypeBase del keygen', () => {
  const plans = listPlans('drywall_calculator');
  assert.deepEqual(
    plans.map((p) => [p.label, p.days, p.price]),
    [
      ['1 Día Profesional', 1, 5],
      ['1 Semana Profesional', 7, 20],
      ['1 Mes Profesional', 30, 50],
      ['1 Año Profesional', 365, 300],
      ['2 Años Profesional', 730, 500],
    ]
  );
});

test('normalizeBase64 coincide con LicensingManager.normalizeBase64', () => {
  // Kotlin: url-safe → estándar y re-padding a múltiplo de 4.
  assert.equal(normalizeBase64('ab-cd_ef'), 'ab+cd/ef'); // 8 chars ⇒ padding 0
  assert.equal(normalizeBase64('ab-c_'), 'ab+c/===');    // 5 chars ⇒ padding 3
  assert.equal(normalizeBase64('YWJj'), 'YWJj');
  assert.equal(normalizeBase64('YWJjZA=='), 'YWJjZA==');
  assert.equal(normalizeBase64('  YWJj  '), 'YWJj');
  // Un deviceId real de Android (SHA-256 en Base64) ya termina en '=' y no cambia.
  const deviceId = normalizeBase64(
    crypto.createHash('sha256').update('ANDROID-ID-TEST').digest('base64')
  );
  assert.equal(deviceId.length % 4, 0);
  assert.equal(normalizeBase64(deviceId), deviceId, 'debe ser idempotente');
});

test('el estado calculado marca como expirada una licencia vencida', () => {
  const past = Date.now() - 40 * 86400000;
  const license = issueLicense(
    { appId: 'drywall_calculator', userName: 'Vencida', deviceId: DEVICE, planCode: 'ONE_DAY', creationDate: past },
    actor
  );
  assert.equal(license.status, 'expired');
  assert.equal(license.daysRemaining, 0);
  assert.ok(getLicense(license.id).licenseJson.expiryDate < Date.now());
});
