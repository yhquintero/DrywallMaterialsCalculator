/**
 * Esquema SQLite de la Consola de Licencias.
 * Idempotente: puede ejecutarse en cada arranque sin efectos secundarios.
 */

export const SCHEMA_SQL = /* sql */ `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- ─────────────────────────── IDENTIDAD Y RBAC ───────────────────────────
CREATE TABLE IF NOT EXISTS roles (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  code         TEXT    NOT NULL UNIQUE,
  name         TEXT    NOT NULL,
  description  TEXT    NOT NULL DEFAULT '',
  level        INTEGER NOT NULL DEFAULT 0,
  is_system    INTEGER NOT NULL DEFAULT 0,
  is_active    INTEGER NOT NULL DEFAULT 1,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  code        TEXT    NOT NULL UNIQUE,
  group_id    TEXT    NOT NULL DEFAULT 'system',
  label       TEXT    NOT NULL,
  description TEXT    NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id       INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id INTEGER NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS users (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  username           TEXT    NOT NULL UNIQUE,
  email              TEXT    UNIQUE,
  full_name          TEXT    NOT NULL DEFAULT '',
  password_hash      TEXT    NOT NULL,
  role_id            INTEGER NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
  is_active          INTEGER NOT NULL DEFAULT 1,
  must_change_password INTEGER NOT NULL DEFAULT 0,
  failed_attempts    INTEGER NOT NULL DEFAULT 0,
  locked_until       INTEGER,
  last_login_at      INTEGER,
  last_login_ip      TEXT,
  password_changed_at INTEGER NOT NULL,
  totp_secret        TEXT,
  created_by         INTEGER REFERENCES users(id) ON DELETE SET NULL,
  created_at         INTEGER NOT NULL,
  updated_at         INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role_id);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active);

-- Permisos extra concedidos/denegados a un usuario concreto (override del rol).
CREATE TABLE IF NOT EXISTS user_permissions (
  user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  permission_id INTEGER NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  effect        TEXT    NOT NULL DEFAULT 'allow' CHECK (effect IN ('allow','deny')),
  granted_by    INTEGER REFERENCES users(id) ON DELETE SET NULL,
  created_at    INTEGER NOT NULL,
  PRIMARY KEY (user_id, permission_id)
);

-- Sesiones / refresh tokens (rotación + revocación).
CREATE TABLE IF NOT EXISTS sessions (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash     TEXT    NOT NULL UNIQUE,
  user_agent     TEXT    NOT NULL DEFAULT '',
  ip_address     TEXT    NOT NULL DEFAULT '',
  issued_at      INTEGER NOT NULL,
  expires_at     INTEGER NOT NULL,
  last_used_at   INTEGER,
  revoked_at     INTEGER,
  revoke_reason  TEXT
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);

-- ─────────────────────────── CLAVES DE FIRMA ───────────────────────────
CREATE TABLE IF NOT EXISTS signing_keys (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  app_id         TEXT    NOT NULL,
  kid            TEXT    NOT NULL UNIQUE,
  algorithm      TEXT    NOT NULL DEFAULT 'SHA256withRSA',
  modulus_bits   INTEGER NOT NULL DEFAULT 4096,
  public_key_b64 TEXT    NOT NULL,
  public_key_pem TEXT    NOT NULL,
  public_key_sha256 TEXT NOT NULL,
  private_key_enc TEXT   NOT NULL,
  private_key_iv  TEXT   NOT NULL,
  private_key_tag TEXT   NOT NULL,
  is_active      INTEGER NOT NULL DEFAULT 0,
  created_by     INTEGER REFERENCES users(id) ON DELETE SET NULL,
  created_at     INTEGER NOT NULL,
  rotated_at     INTEGER,
  notes          TEXT    NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_keys_app_active ON signing_keys(app_id, is_active);

-- ─────────────────────────── PLANES DE LICENCIA ───────────────────────────
CREATE TABLE IF NOT EXISTS plans (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  app_id       TEXT    NOT NULL,
  code         TEXT    NOT NULL,
  label        TEXT    NOT NULL,
  type_name    TEXT    NOT NULL,
  days         INTEGER NOT NULL,
  price        REAL    NOT NULL DEFAULT 0,
  currency     TEXT    NOT NULL DEFAULT 'USD',
  is_active    INTEGER NOT NULL DEFAULT 1,
  sort_order   INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL,
  UNIQUE (app_id, code)
);

-- ─────────────────────────── LICENCIAS / KEYGEN ───────────────────────────
CREATE TABLE IF NOT EXISTS licenses (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  license_key    TEXT    NOT NULL UNIQUE,
  app_id         TEXT    NOT NULL,
  user_name      TEXT    NOT NULL,
  device_id      TEXT    NOT NULL,
  plan_code      TEXT    NOT NULL,
  plan_label     TEXT    NOT NULL,
  price          REAL    NOT NULL DEFAULT 0,
  currency       TEXT    NOT NULL DEFAULT 'USD',
  price_text     TEXT    NOT NULL DEFAULT '',
  creation_date  INTEGER NOT NULL,
  expiry_date    INTEGER NOT NULL,
  signature      TEXT    NOT NULL,
  license_json   TEXT    NOT NULL,
  key_id         INTEGER REFERENCES signing_keys(id) ON DELETE SET NULL,
  status         TEXT    NOT NULL DEFAULT 'draft'
                 CHECK (status IN ('draft','issued','paid','expired','revoked','blocked')),
  is_paid        INTEGER NOT NULL DEFAULT 0,
  paid_at        INTEGER,
  payment_method TEXT,
  email          TEXT,
  phone          TEXT,
  notes          TEXT    NOT NULL DEFAULT '',
  source         TEXT    NOT NULL DEFAULT 'web' CHECK (source IN ('web','android_app','android_keygen','import')),
  issued_by      INTEGER REFERENCES users(id) ON DELETE SET NULL,
  revoked_at     INTEGER,
  revoked_by     INTEGER REFERENCES users(id) ON DELETE SET NULL,
  revoke_reason  TEXT,
  renewed_from   INTEGER REFERENCES licenses(id) ON DELETE SET NULL,
  device_label   TEXT,
  last_seen_at   INTEGER,
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_licenses_app ON licenses(app_id);
CREATE INDEX IF NOT EXISTS idx_licenses_status ON licenses(status);
CREATE INDEX IF NOT EXISTS idx_licenses_device ON licenses(device_id);
CREATE INDEX IF NOT EXISTS idx_licenses_expiry ON licenses(expiry_date);
CREATE INDEX IF NOT EXISTS idx_licenses_user ON licenses(user_name);

-- Lista negra de firmas revocadas (la consultan las apps Android).
CREATE TABLE IF NOT EXISTS license_blacklist (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  app_id      TEXT    NOT NULL,
  signature   TEXT    NOT NULL,
  license_id  INTEGER REFERENCES licenses(id) ON DELETE SET NULL,
  reason      TEXT    NOT NULL DEFAULT '',
  created_by  INTEGER REFERENCES users(id) ON DELETE SET NULL,
  created_at  INTEGER NOT NULL,
  UNIQUE (app_id, signature)
);

-- Dispositivos registrados (huella de activaciones).
CREATE TABLE IF NOT EXISTS devices (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  app_id        TEXT    NOT NULL,
  device_id     TEXT    NOT NULL,
  device_label  TEXT    NOT NULL DEFAULT '',
  owner_name    TEXT    NOT NULL DEFAULT '',
  first_seen_at INTEGER NOT NULL,
  last_seen_at  INTEGER NOT NULL,
  is_blocked    INTEGER NOT NULL DEFAULT 0,
  license_count INTEGER NOT NULL DEFAULT 0,
  UNIQUE (app_id, device_id)
);

-- ─────────────────────────── TASAS DE CAMBIO ───────────────────────────
CREATE TABLE IF NOT EXISTS rates (
  code        TEXT    PRIMARY KEY,
  rate        REAL    NOT NULL,
  symbol      TEXT    NOT NULL DEFAULT '',
  source      TEXT    NOT NULL DEFAULT 'manual',
  updated_at  INTEGER NOT NULL,
  updated_by  INTEGER REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS rate_history (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  code        TEXT    NOT NULL,
  rate        REAL    NOT NULL,
  source      TEXT    NOT NULL DEFAULT 'manual',
  created_by  INTEGER REFERENCES users(id) ON DELETE SET NULL,
  timestamp   INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rate_history_code ON rate_history(code, timestamp);

-- ─────────────────────────── AUDITORÍA Y SISTEMA ───────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  actor_id    INTEGER REFERENCES users(id) ON DELETE SET NULL,
  actor_name  TEXT    NOT NULL DEFAULT 'sistema',
  action      TEXT    NOT NULL,
  entity      TEXT    NOT NULL DEFAULT '',
  entity_id   TEXT    NOT NULL DEFAULT '',
  app_id      TEXT,
  severity    TEXT    NOT NULL DEFAULT 'info' CHECK (severity IN ('debug','info','warn','critical')),
  ip_address  TEXT    NOT NULL DEFAULT '',
  user_agent  TEXT    NOT NULL DEFAULT '',
  detail      TEXT    NOT NULL DEFAULT '',
  created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_log(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log(action);

CREATE TABLE IF NOT EXISTS settings (
  key         TEXT    PRIMARY KEY,
  value       TEXT    NOT NULL,
  updated_at  INTEGER NOT NULL,
  updated_by  INTEGER REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS schema_meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
`;

export const SCHEMA_VERSION = '1';
