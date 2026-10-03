#!/usr/bin/env node
/**
 * `npm run dev:https` — levanta TODA la plataforma en HTTPS local
 * (compatible con Windows PowerShell/CMD, macOS y Linux):
 *
 *   1. Genera certificados autofirmados X.509 v3 si no existen (scripts/gen-certs.mjs),
 *      usando únicamente `node:crypto` (sin depender de `bash` ni de `openssl`).
 *   2. Arranca la API con TLS en  https://localhost:8443
 *   3. Arranca la web  con TLS en  https://localhost:3000  (proxifica /api → 8443)
 *
 * Así se reproduce en local el comportamiento de producción (HTTPS obligatorio,
 * HSTS, cookies Secure) sin depender de ningún servicio externo.
 */
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ensureCerts } from './gen-certs.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WEB = path.join(ROOT, 'web');
const SERVER = path.join(ROOT, 'server');
const VITE_BIN = path.join(WEB, 'node_modules', 'vite', 'bin', 'vite.js');
const SERVER_EXPRESS = path.join(SERVER, 'node_modules', 'express', 'package.json');

const API_PORT = Number(process.env.PORT || 8443);
const WEB_PORT = Number(process.env.VITE_PORT || 3000);

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
  setTimeout(() => process.exit(code), 400).unref();
}

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));

/**
 * Ejecuta un script de Node usando `process.execPath` con `shell: false`.
 * Evita en Windows los fallos con rutas que contienen espacios (`C:\Program Files\nodejs\node.exe`),
 * el aviso `DEP0190` de Node 22/24 y los procesos huérfanos de `cmd.exe`.
 */
function runNode(scriptArgs, cwd, env = {}, label = '') {
  const child = spawn(process.execPath, scriptArgs, {
    cwd,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
    windowsHide: true,
  });
  const prefix = label ? `[${label}] ` : '';
  child.stdout?.on('data', (d) => process.stdout.write(`${prefix}${d}`));
  child.stderr?.on('data', (d) => process.stderr.write(`${prefix}${d}`));
  child.on('error', (err) => {
    console.error(`${prefix}✗ Error al iniciar el proceso:`, err?.message || err);
    shutdown(1);
  });
  child.on('exit', (code) => {
    if (!shuttingDown) {
      console.log(`${prefix}proceso finalizado (código ${code ?? 0})`);
      shutdown(code ?? 0);
    }
  });
  return child;
}

function ensureDependenciesInstalled() {
  const missingWeb = !fs.existsSync(VITE_BIN);
  const missingServer = !fs.existsSync(SERVER_EXPRESS);
  if (!missingWeb && !missingServer) return;

  const npmExec = process.env.npm_execpath;
  console.log('→ Instalando dependencias faltantes (una sola vez)…');

  for (const [missing, dir, name] of [
    [missingWeb, WEB, 'web'],
    [missingServer, SERVER, 'server'],
  ]) {
    if (!missing) continue;
    console.log(`  • npm install en ${name}/…`);
    const res = npmExec
      ? spawnSync(process.execPath, [npmExec, 'install'], { cwd: dir, stdio: 'inherit', shell: false })
      : spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['install'], {
          cwd: dir,
          stdio: 'inherit',
          shell: process.platform === 'win32',
        });
    if (res.status !== 0) {
      console.error(`✗ Falló la instalación de dependencias en ${name}/. Ejecuta: npm run install:all`);
      process.exit(1);
    }
  }
}

console.log('──────────────────────────────────────────────────────────────');
console.log('  DrywallPro — entorno de desarrollo HTTPS');
console.log('──────────────────────────────────────────────────────────────');

try {
  ensureCerts({ rootDir: ROOT });
} catch (err) {
  console.error('✗ No se pudieron preparar los certificados locales:', err?.message || err);
  process.exit(1);
}

ensureDependenciesInstalled();
start();

function start() {
  const api = runNode(
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

  // Nota: Vite 6 eliminó el flag CLI `--https`; el TLS local se activa en
  // `web/vite.config.ts` mediante `VITE_HTTPS=true` usando `web/certs/dev.{crt,key}`.
  const web = runNode(
    [VITE_BIN, '--host', '0.0.0.0', '--port', String(WEB_PORT)],
    WEB,
    {
      VITE_HTTPS: 'true',
      VITE_PORT: String(WEB_PORT),
      API_TARGET: `https://127.0.0.1:${API_PORT}`,
    },
    'web'
  );
  children.push(web);

  const caCertRel = path.join('server', 'certs', 'ca.crt');
  console.log('');
  console.log(`  Consola (web)  →  https://localhost:${WEB_PORT}/admin`);
  console.log(`  Calculadora    →  https://localhost:${WEB_PORT}/`);
  console.log(`  API            →  https://localhost:${API_PORT}/api/public/health`);
  console.log('');
  console.log(`  Si el navegador avisa del certificado, importa ${caCertRel}`);
  console.log('  como autoridad de confianza (ejecuta `npm run certs` para ver los comandos).');
  console.log('──────────────────────────────────────────────────────────────');
}
