#!/usr/bin/env node
/**
 * `npm run dev:https` — levanta TODA la plataforma en HTTPS local:
 *
 *   1. Genera certificados autofirmados si no existen (scripts/gen-certs.sh).
 *   2. Arranca la API con TLS en  https://localhost:8443
 *   3. Arranca la web  con TLS en  https://localhost:3000  (proxifica /api → 8443)
 *
 * Así se reproduce en local el comportamiento de producción (HTTPS obligatorio,
 * HSTS, cookies Secure) sin depender de ningún servicio externo.
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WEB = path.join(ROOT, 'web');
const SERVER = path.join(ROOT, 'server');

const API_PORT = Number(process.env.PORT || 8443);
const WEB_PORT = Number(process.env.VITE_PORT || 3000);

const certsReady =
  fs.existsSync(path.join(SERVER, 'certs', 'server.crt')) &&
  fs.existsSync(path.join(WEB, 'certs', 'dev.crt'));

function run(cmd, args, cwd, env = {}, label = '') {
  const child = spawn(cmd, args, {
    cwd,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: process.platform === 'win32',
  });
  const prefix = label ? `[${label}] ` : '';
  child.stdout?.on('data', (d) => process.stdout.write(`${prefix}${d}`));
  child.stderr?.on('data', (d) => process.stderr.write(`${prefix}${d}`));
  child.on('exit', (code) => {
    console.log(`${prefix}proceso finalizado (código ${code})`);
    shutdown(code ?? 0);
  });
  return child;
}

const children = [];
let shuttingDown = false;

function shutdown(code = 0) {
  if (shuttingDown) return;
  shuttingDown = true;
  for (const c of children) {
    try {
      c.kill('SIGTERM');
    } catch {
      /* noop */
    }
  }
  setTimeout(() => process.exit(code), 400);
}

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));

console.log('──────────────────────────────────────────────────────────────');
console.log('  DrywallPro — entorno de desarrollo HTTPS');
console.log('──────────────────────────────────────────────────────────────');

if (!certsReady) {
  console.log('→ Generando certificados locales (una sola vez)…');
  const gen = spawn('bash', [path.join(ROOT, 'scripts', 'gen-certs.sh')], { cwd: ROOT, stdio: 'inherit' });
  gen.on('exit', (code) => {
    if (code !== 0) {
      console.error('✗ No se pudieron generar los certificados. Revisa que openssl esté instalado.');
      process.exit(1);
    }
    start();
  });
} else {
  start();
}

function start() {
  const api = run(
    process.execPath,
    ['--no-warnings', 'src/index.js'],
    SERVER,
    {
      NODE_ENV: 'development',
      ENABLE_HTTPS: 'true',
      PORT: String(API_PORT),
      HOST: '0.0.0.0',
      JWT_SECRET: process.env.JWT_SECRET || 'dev-jwt-secret-cambiar-en-produccion-0123456789',
      MASTER_KEY: process.env.MASTER_KEY || 'dev-master-key-cambiar-en-produccion-0123456789',
      SECURE_COOKIES: 'true',
      RSA_KEY_SIZE: process.env.RSA_KEY_SIZE || '4096',
    },
    'api'
  );
  children.push(api);

  const web = run(
    process.platform === 'win32' ? 'npm.cmd' : 'npm',
    ['run', 'dev', '--', '--https'],
    WEB,
    {
      VITE_HTTPS: 'true',
      VITE_PORT: String(WEB_PORT),
      API_TARGET: `https://127.0.0.1:${API_PORT}`,
    },
    'web'
  );
  children.push(web);

  console.log('');
  console.log(`  Consola (web)  →  https://localhost:${WEB_PORT}/admin`);
  console.log(`  Calculadora    →  https://localhost:${WEB_PORT}/`);
  console.log(`  API            →  https://localhost:${API_PORT}/api/public/health`);
  console.log('');
  console.log('  Si el navegador avisa del certificado, importa server/certs/ca.crt');
  console.log('  como autoridad de confianza (instrucciones en scripts/gen-certs.sh).');
  console.log('──────────────────────────────────────────────────────────────');
}
