import './setup.js';
import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

import { getDb } from '../src/db/index.js';
import { createApp } from '../src/app.js';
import { ensureBootstrapAdmin } from '../tools/bootstrap.js';
import { PERMISSION_IDS } from '../src/config/permissions.js';
import { effectivePermissions } from '../src/security/auth.js';

getDb();
const boot = ensureBootstrapAdmin({ quiet: true });
const ADMIN_PASSWORD = 'Temporal.2024!Admin';
assert.ok(boot.created || boot.exists);

const app = createApp();
const server = http.createServer(app);
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const BASE = `http://127.0.0.1:${server.address().port}`;

async function api(method, path, { token, body, headers = {} } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'content-type': 'application/json',
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: res.status, body: json, headers: res.headers };
}

async function loginAs(username, password) {
  const res = await api('POST', '/api/auth/login', { body: { username, password } });
  assert.equal(res.status, 200, `login de ${username} falló: ${JSON.stringify(res.body)}`);
  return res.body;
}

const admin = await loginAs('root', ADMIN_PASSWORD);

// Usuarios de prueba para RBAC
async function createUser(username, roleCode, password = 'Prueba.2024!Fuerte') {
  const res = await api('POST', '/api/users', {
    token: admin.accessToken,
    body: { username, roleCode, fullName: username, password },
  });
  assert.equal(res.status, 201, JSON.stringify(res.body));
  return { ...res.body, password };
}

const manager = await createUser('gestor1', 'MANAGER');
const operator = await createUser('operador1', 'OPERATOR');
const auditor = await createUser('auditor1', 'AUDITOR');
const client = await createUser('cliente1', 'CLIENT');

const managerTok = (await loginAs(manager.username, manager.password)).accessToken;
const operatorTok = (await loginAs(operator.username, operator.password)).accessToken;
const auditorTok = (await loginAs(auditor.username, auditor.password)).accessToken;
const clientTok = (await loginAs(client.username, client.password)).accessToken;

const DEVICE = 'RQVDRVNUREVWSUNFSUQwMTIzNDU2Nzg5QUJDREVG==' ;

after(() => server.close());

// ─────────────────────────── AUTENTICACIÓN ───────────────────────────
test('login devuelve usuario, permisos y access token', () => {
  assert.equal(admin.user.username, 'root');
  assert.equal(admin.user.roleCode, 'ADMIN');
  assert.ok(admin.accessToken);
  assert.ok(admin.user.permissions.includes('*'));
});

test('login con credenciales inválidas devuelve 401 y audita el fallo', async () => {
  const res = await api('POST', '/api/auth/login', { body: { username: 'root', password: 'incorrecta' } });
  assert.equal(res.status, 401);
  const auditRes = await api('GET', '/api/audit?action=auth.login_failed', { token: admin.accessToken });
  assert.equal(auditRes.status, 200);
  assert.ok(auditRes.body.total >= 1);
});

test('endpoints protegidos exigen token', async () => {
  assert.equal((await api('GET', '/api/licenses')).status, 401);
  assert.equal((await api('GET', '/api/users', { token: 'token.falso' })).status, 401);
  assert.equal((await api('GET', '/api/auth/me', { token: admin.accessToken })).status, 200);
});

test('bloqueo tras intentos fallidos repetidos', async () => {
  const user = await createUser('bloqueable', 'OPERATOR');
  let last;
  for (let i = 0; i < 6; i += 1) {
    last = await api('POST', '/api/auth/login', { body: { username: user.username, password: 'mala' } });
  }
  assert.equal(last.status, 409, JSON.stringify(last.body));
  assert.match(JSON.stringify(last.body), /bloqueada/i);
  // Ni con la contraseña correcta entra mientras esté bloqueado.
  const locked = await api('POST', '/api/auth/login', { body: { username: user.username, password: user.password } });
  assert.equal(locked.status, 409);
  // El admin puede desbloquearlo.
  const unlock = await api('PATCH', `/api/users/${user.id}`, { token: admin.accessToken, body: { unlock: true } });
  assert.equal(unlock.status, 200);
  const ok = await api('POST', '/api/auth/login', { body: { username: user.username, password: user.password } });
  assert.equal(ok.status, 200);
});

// ─────────────────────────── RBAC ───────────────────────────
test('cada rol recibe exactamente sus permisos', () => {
  const perms = (u) => effectivePermissions(u.id);
  assert.ok(perms(manager).includes('licenses.revoke'));
  assert.ok(!perms(operator).includes('licenses.revoke'));
  assert.ok(perms(auditor).includes('audit.view'));
  assert.ok(!perms(auditor).includes('licenses.issue'));
  assert.deepEqual(perms(client), ['licenses.view']);
});

test('MANAGER puede emitir y revocar licencias', async () => {
  const issue = await api('POST', '/api/licenses', {
    token: managerTok,
    body: { appId: 'drywall_calculator', userName: 'Cliente Manager', deviceId: DEVICE, planCode: 'ONE_MONTH' },
  });
  assert.equal(issue.status, 201, JSON.stringify(issue.body));
  assert.ok(issue.body.licenseJson.signature);

  const revoke = await api('POST', `/api/licenses/${issue.body.id}/revoke`, {
    token: managerTok,
    body: { reason: 'Devolución' },
  });
  assert.equal(revoke.status, 200);
  assert.equal(revoke.body.status, 'revoked');
});

test('OPERATOR puede emitir pero NO revocar ni eliminar', async () => {
  const issue = await api('POST', '/api/licenses', {
    token: operatorTok,
    body: { appId: 'keygen_pro', userName: 'Cliente Operador', deviceId: DEVICE, planCode: 'ONE_WEEK' },
  });
  assert.equal(issue.status, 201, JSON.stringify(issue.body));
  assert.equal(issue.body.licenseKey.startsWith('KG-'), true);

  const revoke = await api('POST', `/api/licenses/${issue.body.id}/revoke`, { token: operatorTok, body: { reason: 'x' } });
  assert.equal(revoke.status, 403);
  const del = await api('DELETE', `/api/licenses/${issue.body.id}`, { token: operatorTok });
  assert.equal(del.status, 403);
});

test('AUDITOR es de solo lectura', async () => {
  assert.equal((await api('GET', '/api/licenses', { token: auditorTok })).status, 200);
  assert.equal((await api('GET', '/api/audit', { token: auditorTok })).status, 200);
  assert.equal(
    (await api('POST', '/api/licenses', { token: auditorTok, body: { userName: 'x', deviceId: DEVICE } })).status,
    403
  );
  assert.equal((await api('GET', '/api/users', { token: auditorTok })).status, 200);
  assert.equal((await api('POST', '/api/users', { token: auditorTok, body: { username: 'nuevo', roleCode: 'OPERATOR' } })).status, 403);
});

test('OPERATOR no puede ver usuarios ni auditoría', async () => {
  assert.equal((await api('GET', '/api/users', { token: operatorTok })).status, 403);
  assert.equal((await api('GET', '/api/audit', { token: operatorTok })).status, 403);
});

test('solo ADMIN gestiona roles y crea administradores', async () => {
  assert.equal((await api('POST', '/api/roles', { token: admin.accessToken, body: { code: 'SOPORTE', name: 'Soporte', permissions: ['licenses.view'] } })).status, 201);
  assert.equal((await api('POST', '/api/roles', { token: managerTok, body: { code: 'OTRO', name: 'Otro', permissions: [] } })).status, 403);
  assert.equal((await api('POST', '/api/users', { token: managerTok, body: { username: 'admin2', roleCode: 'ADMIN', password: 'Otra.2024!Clave' } })).status, 403);
  assert.equal((await api('POST', '/api/users', { token: admin.accessToken, body: { username: 'admin2', roleCode: 'ADMIN', password: 'Otra.2024!Clave' } })).status, 201);
});

test('no se puede conceder el comodín "*" fuera de ADMIN', async () => {
  const roles = await api('GET', '/api/roles', { token: admin.accessToken });
  const managerRole = roles.body.items.find((r) => r.code === 'MANAGER');
  const forbiddenRes = await api('PATCH', `/api/roles/${managerRole.id}`, { token: managerTok, body: { permissions: ['*'] } });
  assert.equal(forbiddenRes.status, 403, 'un MANAGER no puede tocar roles');

  // Ni siquiera un ADMIN debería crear un segundo rol con poder absoluto.
  const wildcardToOther = await api('PATCH', `/api/roles/${managerRole.id}`, { token: admin.accessToken, body: { permissions: ['*'] } });
  assert.equal(wildcardToOther.status, 403);

  // Pero sí puede darle permisos concretos.
  const concrete = await api('PATCH', `/api/roles/${managerRole.id}`, {
    token: admin.accessToken,
    body: { permissions: managerRole.permissions.filter((p) => p !== '*') },
  });
  assert.equal(concrete.status, 200);
});

test('el rol ADMIN conserva siempre el comodín y no puede quedar sin admin activo', async () => {
  const roles = await api('GET', '/api/roles', { token: admin.accessToken });
  const adminRole = roles.body.items.find((r) => r.code === 'ADMIN');
  assert.equal(adminRole.isWildcard, true);
  const strip = await api('PATCH', `/api/roles/${adminRole.id}`, { token: admin.accessToken, body: { permissions: ['licenses.view'] } });
  assert.equal(strip.status, 403);
});

test('overrides por usuario: deny resta y allow suma', async () => {
  const target = await createUser('mixto', 'OPERATOR');
  const setDeny = await api('PUT', `/api/users/${target.id}/permissions`, {
    token: admin.accessToken,
    body: { overrides: [{ code: 'licenses.issue', effect: 'deny' }, { code: 'audit.view', effect: 'allow' }] },
  });
  assert.equal(setDeny.status, 200);
  assert.ok(!setDeny.body.permissions.includes('licenses.issue'));
  assert.ok(setDeny.body.permissions.includes('audit.view'));

  const tok = (await loginAs(target.username, target.password)).accessToken;
  assert.equal((await api('GET', '/api/audit', { token: tok })).status, 200);
  assert.equal(
    (await api('POST', '/api/licenses', { token: tok, body: { userName: 'x', deviceId: DEVICE } })).status,
    403
  );
});

test('un usuario no puede desactivarse ni degradarse a sí mismo', async () => {
  const self = await api('PATCH', `/api/users/${admin.user.id}`, { token: admin.accessToken, body: { isActive: false } });
  assert.equal(self.status, 400);
  const degrade = await api('PATCH', `/api/users/${admin.user.id}`, { token: admin.accessToken, body: { roleCode: 'AUDITOR' } });
  assert.equal(degrade.status, 400);
});

test('los intentos de acceso denegados quedan auditados (rbac.denied)', async () => {
  const res = await api('GET', '/api/audit?action=rbac.denied', { token: admin.accessToken });
  assert.equal(res.status, 200);
  assert.ok(res.body.total >= 1);
});

// ─────────────────────────── CLAVES DE FIRMA ───────────────────────────
test('rotación de claves exige confirmación y queda auditada', async () => {
  const before = await api('GET', '/api/keys/active/keygen_pro', { token: admin.accessToken });
  assert.equal(before.status, 200);

  const unconfirmed = await api('POST', '/api/keys/keygen_pro/rotate', { token: admin.accessToken, body: {} });
  assert.equal(unconfirmed.status, 400);

  const rotated = await api('POST', '/api/keys/keygen_pro/rotate', { token: admin.accessToken, body: { confirm: true, notes: 'test' } });
  assert.equal(rotated.status, 201);
  assert.notEqual(rotated.body.publicKeyB64, before.body.publicKeyB64);

  // Las licencias antiguas siguen verificando con su clave histórica.
  const pem = await api('GET', '/api/keys/keygen_pro/public.pem', { token: admin.accessToken });
  assert.equal(pem.status, 200);
  assert.match(String(pem.body), /BEGIN PUBLIC KEY/);
});

test('exportar la clave privada exige permiso de ADMIN y confirmación explícita', async () => {
  assert.equal(
    (await api('POST', '/api/keys/keygen_pro/export-private', { token: managerTok, body: { confirm: true, reason: 'prueba' } })).status,
    403
  );
  assert.equal(
    (await api('POST', '/api/keys/keygen_pro/export-private', { token: admin.accessToken, body: { confirm: false } })).status,
    400
  );
  const ok = await api('POST', '/api/keys/keygen_pro/export-private', { token: admin.accessToken, body: { confirm: true, reason: 'Migración de servidor' } });
  assert.equal(ok.status, 200);
  assert.match(ok.body.pem, /BEGIN PRIVATE KEY/);
});

// ─────────────────────────── ENDPOINTS PÚBLICOS ───────────────────────────
test('los endpoints públicos de las apps responden sin autenticación', async () => {
  const health = await api('GET', '/api/public/health');
  assert.equal(health.status, 200);
  assert.equal(health.body.status, 'ok');

  const rates = await api('GET', '/api/public/rates');
  assert.equal(rates.status, 200);
  assert.equal(typeof rates.body.rates, 'object');

  const pem = await fetch(`${BASE}/.well-known/licensing-public-key.pem`);
  assert.equal(pem.status, 200);
  assert.match(await pem.text(), /BEGIN PUBLIC KEY/);

  const pemV2 = await fetch(`${BASE}/.well-known/licensing-public-key-v2.pem`);
  assert.equal(pemV2.status, 200);

  const json = await api('GET', '/.well-known/licensing-public-key.json');
  assert.equal(json.status, 200);
  assert.equal(json.data?.verifyWith ?? json.body.verifyWith, 'SHA256withRSA');
  assert.equal(json.body.dataToSign, 'user|deviceId|creationDate|expiryDate');

  const apps = await api('GET', '/.well-known/apps.json');
  assert.equal(apps.body.apps.length, 2);
});

test('consulta de revocación pública para las apps Android', async () => {
  const issued = await api('POST', '/api/licenses', {
    token: admin.accessToken,
    body: { appId: 'drywall_calculator', userName: 'Revocable', deviceId: DEVICE, planCode: 'ONE_DAY' },
  });
  const sig = issued.body.signature;
  const before = await api('GET', `/api/public/revocation-status?signature=${encodeURIComponent(sig)}`);
  assert.equal(before.body.revoked, false);

  await api('POST', `/api/licenses/${issued.body.id}/revoke`, { token: admin.accessToken, body: { reason: 'test público' } });
  const after = await api('GET', `/api/public/revocation-status?signature=${encodeURIComponent(sig)}`);
  assert.equal(after.body.revoked, true);
  assert.equal(after.body.status, 'revoked');
});

test('POST /api/public/validate da el veredicto completo de una licencia', async () => {
  const issued = await api('POST', '/api/licenses', {
    token: admin.accessToken,
    body: { appId: 'drywall_calculator', userName: 'Validable', deviceId: DEVICE, planCode: 'ONE_YEAR' },
  });
  const ok = await api('POST', '/api/public/validate', { body: { license: issued.body.licenseJson, deviceId: DEVICE } });
  assert.equal(ok.body.valid, true);
  assert.equal(ok.body.registeredInConsole, true);

  const wrongDevice = await api('POST', '/api/public/validate', { body: { license: issued.body.licenseJson, deviceId: 'OTRO' } });
  assert.equal(wrongDevice.body.valid, false);
  assert.equal(wrongDevice.body.deviceMismatch, true);
});

// ─────────────────────────── TASAS Y PLANES ───────────────────────────
test('solo MANAGER+ publica tasas y quedan en el histórico', async () => {
  assert.equal((await api('PUT', '/api/rates', { token: operatorTok, body: { rates: { USD: 400 } } })).status, 403);
  const res = await api('PUT', '/api/rates', { token: managerTok, body: { rates: { USD: 450.5, EUR: 490 }, source: 'test' } });
  assert.equal(res.status, 200);
  assert.equal(res.body.updated.length, 2);

  const history = await api('GET', '/api/rates/history?code=USD', { token: managerTok });
  assert.ok(history.body.items.some((r) => r.rate === 450.5));

  const publicRates = await api('GET', '/api/public/rates');
  assert.equal(publicRates.body.rates.USD, 450.5);
  assert.equal(publicRates.body.success, true);
});

test('los planes se pueden editar y desactivar', async () => {
  const plans = await api('GET', '/api/plans?appId=drywall_calculator', { token: admin.accessToken });
  const month = plans.body.items.find((p) => p.code === 'ONE_MONTH');
  const updated = await api('PATCH', `/api/plans/${month.id}`, { token: admin.accessToken, body: { price: 55 } });
  assert.equal(updated.body.price, 55);
  assert.equal((await api('PATCH', `/api/plans/${month.id}`, { token: operatorTok, body: { price: 1 } })).status, 403);
});

// ─────────────────────────── SEGURIDAD HTTP ───────────────────────────
test('cabeceras de seguridad presentes en todas las respuestas', async () => {
  const res = await fetch(`${BASE}/api/public/health`);
  assert.equal(res.headers.get('x-content-type-options'), 'nosniff');
  assert.equal(res.headers.get('x-frame-options'), 'DENY');
  assert.equal(res.headers.get('referrer-policy'), 'strict-origin-when-cross-origin');
  assert.match(res.headers.get('permissions-policy') ?? '', /geolocation=\(\)/);
  assert.equal(res.headers.get('x-powered-by'), null);
  // HSTS solo se emite sobre TLS real; en HTTP plano no debe aparecer.
  assert.equal(res.headers.get('strict-transport-security'), null);
});

test('el guard CSRF rechaza orígenes desconocidos en mutaciones', async () => {
  const res = await api('POST', '/api/licenses', {
    token: admin.accessToken,
    body: { userName: 'x', deviceId: DEVICE },
    headers: { origin: 'https://evil.example.com' },
  });
  assert.equal(res.status, 403);
});

test('rutas inexistentes devuelven 404 JSON', async () => {
  const res = await api('GET', '/api/no-existe', { token: admin.accessToken });
  assert.equal(res.status, 404);
  assert.ok(res.body.error);
});

test('catálogo de permisos expuesto para la UI de RBAC', async () => {
  const res = await api('GET', '/api/roles/permissions', { token: admin.accessToken });
  assert.equal(res.status, 200);
  assert.ok(res.body.permissions.length >= 25);
  assert.deepEqual(res.body.permissions.map((p) => p.id).sort(), [...PERMISSION_IDS].sort());
  assert.ok(res.body.groups.length >= 6);
});

test('dashboard: estadísticas por app y por mes', async () => {
  const res = await api('GET', '/api/licenses/stats', { token: admin.accessToken });
  assert.equal(res.status, 200);
  assert.ok(res.body.total > 0);
  assert.equal(res.body.byApp.length, 2);
  assert.equal((await api('GET', '/api/licenses/stats', { token: operatorTok })).status, 200);
  assert.equal((await api('GET', '/api/licenses/stats', { token: clientTok })).status, 403);
});

test('exportación CSV y JSON de licencias', async () => {
  const csv = await fetch(`${BASE}/api/licenses/export.csv`, { headers: { authorization: `Bearer ${admin.accessToken}` } });
  assert.equal(csv.status, 200);
  const bytes = Buffer.from(await csv.arrayBuffer());
  // BOM UTF-8 (EF BB BF) para que Excel reconozca la codificación.
  // Ojo: fetch().text() elimina el BOM por especificación, por eso se lee en bytes.
  assert.deepEqual(bytes.subarray(0, 3), Buffer.from([0xef, 0xbb, 0xbf]), 'el CSV debe llevar BOM UTF-8');
  const text = bytes.toString('utf8').replace(/^\uFEFF/, '');
  assert.match(text, /^"?id/);
  assert.match(text, /\n/, 'las filas deben ir separadas por saltos de línea');
  assert.match(text, /DW-|KG-/, 'debe contener claves de licencia');
  assert.match(csv.headers.get('content-disposition') ?? '', /attachment/);

  const json = await fetch(`${BASE}/api/licenses/export.json`, { headers: { authorization: `Bearer ${admin.accessToken}` } });
  assert.equal(json.status, 200);
  const data = await json.json();
  assert.ok(data.licenses.length > 0);
  assert.ok(data.licenses[0].signature);
});
