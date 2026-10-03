/**
 * Bootstrap: crea el usuario administrador inicial si no existe ninguno.
 *
 * Uso interactivo:  npm --prefix server run bootstrap
 * Automático     :  AUTO_SEED=true (por defecto) al arrancar el servidor.
 *
 * Credenciales por variables de entorno o generadas aleatoriamente e impresas UNA sola vez.
 */
import { pathToFileURL } from 'node:url';
import { getDb } from '../src/db/index.js';
import { hashPassword, assertStrongPassword } from '../src/security/auth.js';
import { generateTemporaryPassword } from '../src/services/cryptoService.js';
import { audit } from '../src/services/auditService.js';
import config from '../src/config/index.js';
import { APP_IDS } from '../src/config/permissions.js';
import { ensureActiveKey } from '../src/services/keyService.js';

export function ensureBootstrapAdmin({ quiet = false } = {}) {
  const db = getDb();
  const log = quiet ? () => {} : (...args) => console.log(...args);

  const existingAdmins = db
    .prepare("SELECT COUNT(*) c FROM users u JOIN roles r ON r.id = u.role_id WHERE r.code = 'ADMIN'")
    .get().c;
  if (existingAdmins > 0) {
    const first = db
      .prepare("SELECT u.username FROM users u JOIN roles r ON r.id = u.role_id WHERE r.code = 'ADMIN' ORDER BY u.id LIMIT 1")
      .get();
    return { created: false, exists: true, username: first.username };
  }

  const adminRole = db.prepare("SELECT id FROM roles WHERE code = 'ADMIN'").get();
  if (!adminRole) throw new Error('El rol ADMIN no está sembrado en la base de datos');

  const username = config.bootstrap.adminUsername || 'admin';
  const email = config.bootstrap.adminEmail || null;
  const provided = Boolean(config.bootstrap.adminPassword);
  const password = provided ? config.bootstrap.adminPassword : generateTemporaryPassword(18);
  assertStrongPassword(password);

  const ts = Date.now();
  const id = db
    .prepare(
      `INSERT INTO users (username, email, full_name, password_hash, role_id, is_active, must_change_password,
                          failed_attempts, password_changed_at, created_at, updated_at)
       VALUES (?, ?, 'Administrador del sistema', ?, ?, 1, ?, 0, ?, ?, ?)`
    )
    .insert(username, email, hashPassword(password), adminRole.id, provided ? 0 : 1, ts, ts, ts);

  audit({
    actor: { id, username },
    action: 'users.bootstrap_admin',
    entity: 'user',
    entityId: String(id),
    severity: 'critical',
    detail: { username, generated: !provided },
  });

  log('────────────────────────────────────────────────────────');
  log('  Consola de Licencias — usuario administrador creado');
  log(`    usuario : ${username}`);
  log(`    email   : ${email ?? '(sin email)'}`);
  if (!provided) log(`    password: ${password}   ← guárdala, no se vuelve a mostrar`);
  else log('    password: (la definida en BOOTSTRAP_ADMIN_PASSWORD)');
  log('────────────────────────────────────────────────────────');

  return { created: true, exists: false, username, password: provided ? undefined : password };
}

/**
 * Genera el par RSA de cada app si aún no existe, de modo que los endpoints
 * públicos `/.well-known/*` nunca devuelvan 503 a las apps Android.
 */
export function ensureSigningKeys({ quiet = false } = {}) {
  const log = quiet ? () => {} : (...args) => console.log(...args);
  const created = [];
  for (const appId of APP_IDS) {
    const before = getDb().prepare('SELECT id FROM signing_keys WHERE app_id = ? AND is_active = 1').get(appId);
    if (before) continue;
    const started = Date.now();
    const key = ensureActiveKey(appId, null);
    created.push({ appId, kid: key.kid, ms: Date.now() - started });
    log(`[bootstrap] 🔑 Clave RSA-${key.modulusBits} generada para ${appId}: ${key.kid} (${((Date.now() - started) / 1000).toFixed(1)} s)`);
  }
  return created;
}

const invokedDirectly = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (invokedDirectly) {
  ensureBootstrapAdmin();
  ensureSigningKeys();
}
