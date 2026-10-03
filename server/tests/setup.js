/**
 * Configuración compartida de los tests.
 * DEBE importarse antes que cualquier módulo del servidor, porque `config`
 * se evalúa al cargar el módulo.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'dw-console-test-'));

process.env.NODE_ENV = 'test';
process.env.JWT_SECRET = 'test-jwt-secret-0123456789-abcdefghijklmnopqrstuvwxyz';
process.env.MASTER_KEY = 'test-master-key-0123456789-abcdefghijklmnopqrstuvwxyz';
process.env.DB_FILE = path.join(tmp, 'test.db');
process.env.BCRYPT_ROUNDS = '4';
// 2048 bits mantiene los tests rápidos; la firma PKCS#1 v1.5 es idéntica a 4096.
process.env.RSA_KEY_SIZE = '2048';
process.env.SECURE_COOKIES = 'false';
process.env.BOOTSTRAP_ADMIN_USERNAME = 'root';
process.env.BOOTSTRAP_ADMIN_PASSWORD = 'Temporal.2024!Admin';
process.env.AUTO_SEED = 'true';

export const TEST_TMP_DIR = tmp;

export function cleanupTmp() {
  try {
    fs.rmSync(tmp, { recursive: true, force: true });
  } catch {
    /* noop */
  }
}
