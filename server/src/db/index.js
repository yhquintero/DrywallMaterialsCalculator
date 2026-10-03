import path from 'node:path';
import config from '../config/index.js';
import { Database } from './driver.js';
import { SCHEMA_SQL, SCHEMA_VERSION } from './schema.js';
import { PERMISSIONS, DEFAULT_ROLES } from '../config/permissions.js';

let instance = null;

/** Planes idénticos al enum LicenseTypeBase del keygen Android. */
export const KEYGEN_PLANS = [
  { code: 'ONE_DAY', label: '1 Día Profesional', type_name: '1 DÍA PROFESIONAL', days: 1, price: 5, sort_order: 1 },
  { code: 'ONE_WEEK', label: '1 Semana Profesional', type_name: '1 SEMANA PROFESIONAL', days: 7, price: 20, sort_order: 2 },
  { code: 'ONE_MONTH', label: '1 Mes Profesional', type_name: '1 MES PROFESIONAL', days: 30, price: 50, sort_order: 3 },
  { code: 'ONE_YEAR', label: '1 Año Profesional', type_name: '1 AÑO PROFESIONAL', days: 365, price: 300, sort_order: 4 },
  { code: 'TWO_YEARS', label: '2 Años Profesional', type_name: '2 AÑOS PROFESIONAL', days: 730, price: 500, sort_order: 5 },
];

/** Códigos de moneda del enum Currency del keygen Android. */
export const CURRENCIES = [
  { code: 'USD', symbol: '$' },
  { code: 'EUR', symbol: '€' },
  { code: 'MLC', symbol: 'MLC' },
  { code: 'CAD', symbol: 'C$' },
  { code: 'MEX', symbol: '$' },
  { code: 'ZELLE', symbol: 'ZELLE' },
  { code: 'CLA', symbol: 'CLA' },
];

export function getDb() {
  if (instance) return instance;
  const db = new Database(config.db.file);
  db.exec(SCHEMA_SQL);
  db.prepare('INSERT INTO schema_meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value')
    .run('schema_version', SCHEMA_VERSION);
  seed(db);
  instance = db;
  return db;
}

/** DB efímera en memoria (tests). */
export function createMemoryDb() {
  const db = new Database(':memory:');
  db.exec(SCHEMA_SQL);
  seed(db);
  return db;
}

export function closeDb() {
  if (instance) {
    instance.close();
    instance = null;
  }
}

function now() {
  return Date.now();
}

function seed(db) {
  const ts = now();

  // 1) Permisos del catálogo
  const insertPermission = db.prepare(
    `INSERT INTO permissions (code, group_id, label, description) VALUES (?, ?, ?, ?)
     ON CONFLICT(code) DO UPDATE SET group_id = excluded.group_id, label = excluded.label, description = excluded.description`
  );
  for (const p of PERMISSIONS) insertPermission.run(p.id, p.group, p.label, p.description);

  // 2) Roles por defecto + sus permisos
  const insertRole = db.prepare(
    `INSERT INTO roles (code, name, description, level, is_system, is_active, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, 1, ?, ?)
     ON CONFLICT(code) DO UPDATE SET name = excluded.name, description = excluded.description,
       level = excluded.level, is_system = excluded.is_system, updated_at = excluded.updated_at`
  );
  const roleIdByCode = db.prepare('SELECT id FROM roles WHERE code = ?');
  const permIdByCode = db.prepare('SELECT id FROM permissions WHERE code = ?');
  const linkRolePerm = db.prepare(
    'INSERT OR IGNORE INTO role_permissions (role_id, permission_id) VALUES (?, ?)'
  );
  const wildcard = db.prepare('INSERT OR IGNORE INTO permissions (code, group_id, label, description) VALUES (?, ?, ?, ?)');
  wildcard.run('*', 'system', 'Todos los permisos', 'Comodín reservado para el rol Administrador.');
  const wildcardId = permIdByCode.get('*').id;

  for (const role of DEFAULT_ROLES) {
    const id = insertRole.insert(role.code, role.name, role.description, role.level, role.isSystem, ts, ts);
    const roleId = id || roleIdByCode.get(role.code).id;
    if (role.permissions.includes('*')) {
      linkRolePerm.run(roleId, wildcardId);
      // El ADMIN recibe además todos los permisos explícitos (para que la UI los muestre marcados).
      for (const p of PERMISSIONS) linkRolePerm.run(roleId, permIdByCode.get(p.id).id);
    } else {
      for (const code of role.permissions) {
        const row = permIdByCode.get(code);
        if (row) linkRolePerm.run(roleId, row.id);
      }
    }
  }

  // 3) Planes para las DOS apps
  const insertPlan = db.prepare(
    `INSERT INTO plans (app_id, code, label, type_name, days, price, currency, is_active, sort_order, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, 'USD', 1, ?, ?, ?)
     ON CONFLICT(app_id, code) DO UPDATE SET label = excluded.label, type_name = excluded.type_name,
       days = excluded.days, sort_order = excluded.sort_order, updated_at = excluded.updated_at`
  );
  for (const appId of ['drywall_calculator', 'keygen_pro']) {
    for (const plan of KEYGEN_PLANS) {
      insertPlan.run(appId, plan.code, plan.label, plan.type_name, plan.days, plan.price, plan.sort_order, ts, ts);
    }
  }

  // 4) Tasas iniciales (0 = sin publicar)
  const insertRate = db.prepare(
    `INSERT INTO rates (code, rate, symbol, source, updated_at) VALUES (?, 0, ?, 'seed', ?)
     ON CONFLICT(code) DO NOTHING`
  );
  for (const c of CURRENCIES) insertRate.run(c.code, c.symbol, ts);

  // 5) Ajustes por defecto
  const insertSetting = db.prepare(
    `INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO NOTHING`
  );
  insertSetting.run('license.default_app', 'drywall_calculator', ts);
  insertSetting.run('license.expiry_time_of_day', '23:59:59', ts);
  insertSetting.run('license.key_format', 'DW-XXXXXXXX-XXXX-XXXX', ts);
  insertSetting.run('security.require_https', 'true', ts);
  insertSetting.run('security.session_idle_minutes', '30', ts);
  insertSetting.run('company.name', 'DrywallPro Master — División de Licencias', ts);
}

export const DB_PATH = path.resolve(config.db.file);
