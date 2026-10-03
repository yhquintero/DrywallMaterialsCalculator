/**
 * Capa de abstracción sobre SQLite.
 *
 * Usa el driver NATIVO de Node (node:sqlite, Node >= 22.5) como primera opción y
 * hace fallback a `better-sqlite3` si estuviera instalado. De esta forma el
 * servidor no depende de módulos nativos compilados con node-gyp.
 *
 * GOTCHAS de node:sqlite que esta capa resuelve:
 *  1. Un StatementSync queda "consumido" tras llamar a .get() (step finaliza el
 *     cursor), por lo que NO se reutilizan statements entre llamadas.
 *  2. No permite bindear booleanos (se convierten a 0/1).
 *  3. Devuelve filas con prototipo null (se normalizan a objetos planos).
 */
import fs from 'node:fs';
import path from 'node:path';

let DatabaseSync = null;
let BetterSqlite3 = null;
let driverName = 'none';

try {
  ({ DatabaseSync } = await import('node:sqlite'));
  driverName = 'node:sqlite';
} catch {
  try {
    BetterSqlite3 = (await import('better-sqlite3')).default;
    driverName = 'better-sqlite3';
  } catch {
    throw new Error(
      'No hay driver SQLite disponible. Usa Node.js >= 22.5 (node:sqlite) ' +
        'o instala better-sqlite3 en server/.'
    );
  }
}

/** Convierte valores JS a algo bindeable por SQLite. */
function toBindable(value) {
  if (value === undefined || value === null) return null;
  if (typeof value === 'boolean') return value ? 1 : 0;
  if (value instanceof Date) return value.getTime();
  if (typeof value === 'bigint') return Number(value);
  if (typeof value === 'object') return JSON.stringify(value);
  return value;
}

function normalizeParams(params) {
  return (params ?? []).map(toBindable);
}

/** Las filas de node:sqlite tienen prototipo null: las convertimos a objetos planos. */
function plainRow(row) {
  if (row === undefined || row === null) return undefined;
  return { ...row };
}

class Statement {
  #db;
  #sql;

  constructor(db, sql) {
    this.#db = db;
    this.#sql = sql;
  }

  get(...params) {
    const row = this.#db.raw().prepare(this.#sql).get(...normalizeParams(params));
    return plainRow(row);
  }

  all(...params) {
    const rows = this.#db.raw().prepare(this.#sql).all(...normalizeParams(params));
    return rows.map(plainRow);
  }

  /** @returns {{ changes: number, lastInsertRowid: number }} */
  run(...params) {
    const res = this.#db.raw().prepare(this.#sql).run(...normalizeParams(params));
    return {
      changes: Number(res.changes ?? 0),
      lastInsertRowid: Number(res.lastInsertRowid ?? 0),
    };
  }

  /** Devuelve el id insertado o lanza si no se insertó nada. */
  insert(...params) {
    const res = this.run(...params);
    if (res.changes === 0) throw new Error('INSERT no tuvo efecto');
    return res.lastInsertRowid;
  }
}

export class Database {
  #handle;
  #file;

  constructor(file) {
    this.#file = file;
    if (file !== ':memory:') {
      fs.mkdirSync(path.dirname(path.resolve(file)), { recursive: true });
    }
    this.#handle =
      driverName === 'node:sqlite' ? new DatabaseSync(file) : new BetterSqlite3(file);
    this.exec('PRAGMA journal_mode = WAL;');
    this.exec('PRAGMA foreign_keys = ON;');
    this.exec('PRAGMA busy_timeout = 5000;');
  }

  static get driver() {
    return driverName;
  }

  get file() {
    return this.#file;
  }

  raw() {
    return this.#handle;
  }

  prepare(sql) {
    return new Statement(this, sql);
  }

  exec(sql) {
    if (driverName === 'node:sqlite') {
      this.#handle.exec(sql);
    } else {
      this.#handle.exec(sql);
    }
  }

  /** Transacción síncrona con rollback automático. */
  transaction(fn) {
    this.exec('BEGIN IMMEDIATE;');
    try {
      const result = fn(this);
      this.exec('COMMIT;');
      return result;
    } catch (err) {
      try {
        this.exec('ROLLBACK;');
      } catch {
        /* noop */
      }
      throw err;
    }
  }

  close() {
    try {
      this.#handle.close();
    } catch {
      /* noop */
    }
  }
}

export default Database;
