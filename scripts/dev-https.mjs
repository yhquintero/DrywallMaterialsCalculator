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

const API_PORT = Number(process.env.PORT || 8443);
const WEB_PORT = Number(process.env.VITE_PORT || 3000);

// Credenciales iniciales del usuario administrador (solo desarrollo).
// Se declaran aquí, en la cabecera de constantes, porque `start()` se invoca más
// abajo al cargar el módulo: un `const` evaluado después de su uso falla con
// ReferenceError (Temporal Dead Zone).
const DEV_ADMIN_USER = process.env.BOOTSTRAP_ADMIN_USERNAME || 'admin';
const DEV_ADMIN_PASS = process.env.BOOTSTRAP_ADMIN_PASSWORD || 'Admin.Drywall2026!';

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
function runNode(scriptArgs, cwd, env = {}, label = '', onFatal = null) {
  const child = spawn(process.execPath, scriptArgs, {
    cwd,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
    windowsHide: true,
  });
  const prefix = label ? `[${label}] ` : '';
  const stderrChunks = [];
  child.stdout?.on('data', (d) => process.stdout.write(`${prefix}${d}`));
  child.stderr?.on('data', (d) => {
    stderrChunks.push(d);
    if (stderrChunks.length > 40) stderrChunks.shift();
    process.stderr.write(`${prefix}${d}`);
  });
  child.on('error', (err) => {
    console.error(`${prefix}✗ Error al iniciar el proceso:`, err?.message || err);
    shutdown(1);
  });
  child.on('exit', (code) => {
    if (shuttingDown) return;
    const exitCode = code ?? 0;
    console.log(`${prefix}proceso finalizado (código ${exitCode})`);
    if (exitCode !== 0 && typeof onFatal === 'function') {
      const stderr = Buffer.concat(stderrChunks).toString('utf8');
      if (onFatal(stderr) === true) return; // el callback se encarga de relanzar
    }
    shutdown(exitCode);
  });
  return child;
}

/**
 * Localiza la carpeta de un paquete dentro de los `node_modules` propios o de
 * cualquier carpeta padre (árboles «hoisted»), igual que hace Node.
 */
function findPackageDir(pkgName, fromDir) {
  let dir = path.resolve(fromDir);
  for (;;) {
    const candidate = path.join(dir, 'node_modules', pkgName);
    if (fs.existsSync(path.join(candidate, 'package.json'))) return candidate;
    const parent = path.dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
}

/** Punto de entrada declarado por un `package.json` (best effort). */
function declaredEntry(manifest) {
  const exports = manifest.exports;
  const fromExports = (value) => {
    if (typeof value === 'string') return value;
    if (value && typeof value === 'object') {
      for (const key of ['default', 'import', 'require', 'node']) {
        const found = fromExports(value[key]);
        if (found) return found;
      }
    }
    return null;
  };
  const entry =
    manifest.main ||
    manifest.module ||
    fromExports(typeof exports === 'string' ? exports : exports?.['.']) ||
    null;
  if (!entry) return null;
  return String(entry).replace(/^\.\//, '');
}

/**
 * Comprueba que el paquete está instalado DE VERDAD: no basta con que exista la
 * carpeta, también debe estar su punto de entrada (un `npm install` interrumpido
 * o un `node_modules` copiado a medias dejan carpetas con `package.json` y sin
 * `dist/`, y eso revienta a Vite al cargar `vite.config.ts`).
 */
function checkDependency(pkgName, dir) {
  const pkgDir = findPackageDir(pkgName, dir);
  if (!pkgDir) return 'faltante';

  let manifest = {};
  try {
    manifest = JSON.parse(fs.readFileSync(path.join(pkgDir, 'package.json'), 'utf8'));
  } catch {
    return 'ilegible';
  }

  const entry = declaredEntry(manifest);
  if (entry) {
    return fs.existsSync(path.join(pkgDir, entry)) ? null : 'incompleto';
  }
  // Sin entrada declarada: Node resolvería index.{js,cjs,mjs}; si no hay ninguno
  // y la carpeta solo contiene el manifiesto, el paquete está roto.
  const hasIndex = ['index.js', 'index.cjs', 'index.mjs'].some((f) =>
    fs.existsSync(path.join(pkgDir, f))
  );
  if (hasIndex) return null;
  const contenido = fs.readdirSync(pkgDir).filter((f) => f !== 'package.json');
  return contenido.length === 0 ? 'incompleto' : null;
}

/**
 * Dependencias declaradas en `<dir>/package.json` que no están correctamente
 * instaladas.
 *
 * Antes solo se miraba si existían `vite` y `express`, así que un
 * `node_modules` antiguo (instalado antes de añadir, por ejemplo,
 * `@vitejs/plugin-basic-ssl`) pasaba la comprobación y el arranque moría con
 * `ERR_MODULE_NOT_FOUND: Cannot find package '@vitejs/plugin-basic-ssl'`.
 */
function brokenDependencies(dir) {
  let manifest;
  try {
    manifest = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
  } catch {
    return [];
  }
  const declared = Object.keys({
    ...(manifest.dependencies || {}),
    ...(manifest.devDependencies || {}),
  });
  const problemas = [];
  for (const name of declared) {
    const problema = checkDependency(name, dir);
    if (problema) problemas.push(`${name} (${problema})`);
  }
  return problemas;
}

function runNpm(dir, args, label) {
  const npmExec = process.env.npm_execpath;
  const isWin = process.platform === 'win32';
  console.log(`  • npm ${args.join(' ')} en ${label}/…`);
  const res = npmExec
    ? spawnSync(process.execPath, [npmExec, ...args], { cwd: dir, stdio: 'inherit', shell: false })
    : spawnSync(isWin ? 'npm.cmd' : 'npm', args, { cwd: dir, stdio: 'inherit', shell: isWin });
  return res.status === 0;
}

/**
 * Repara las dependencias de un paquete: `npm install` y, si el árbol sigue
 * roto (caché/lock desincronizados), `npm ci` cuando hay lockfile.
 * Devuelve `true` si al final todo está en su sitio.
 */
function repairDependencies(dir, label) {
  let problemas = brokenDependencies(dir);
  if (label === 'web' && !fs.existsSync(VITE_BIN)) problemas.push('vite (binario ausente)');
  if (!problemas.length) return true;

  const listar = (items) =>
    `${items.slice(0, 6).join(', ')}${items.length > 6 ? ` … (+${items.length - 6})` : ''}`;
  console.log(`  • ${label}/: ${problemas.length} dependencia(s) sin instalar → ${listar(problemas)}`);

  if (!runNpm(dir, ['install'], label)) {
    console.error(`✗ Falló 'npm install' en ${label}/.`);
    return false;
  }

  problemas = brokenDependencies(dir);
  if (!problemas.length) return true;

  if (fs.existsSync(path.join(dir, 'package-lock.json'))) {
    console.log(`  • ${label}/: siguen faltando (${listar(problemas)}) → reinstalación limpia con 'npm ci'…`);
    if (!runNpm(dir, ['ci'], label)) {
      console.error(`✗ Falló 'npm ci' en ${label}/.`);
      return false;
    }
    problemas = brokenDependencies(dir);
    if (!problemas.length) return true;
  }

  console.error(
    `✗ ${label}/ sigue sin dependencias: ${listar(problemas)}\n` +
      `  Prueba manualmente:  npm --prefix ${label} ci   (o borra ${label}/node_modules y ejecuta npm run install:all)`
  );
  return false;
}

function ensureDependenciesInstalled() {
  const targets = [
    [WEB, 'web'],
    [SERVER, 'server'],
  ];
  if (targets.every(([dir]) => !brokenDependencies(dir).length) && fs.existsSync(VITE_BIN)) return;

  console.log('→ Revisando/instalando dependencias (una sola vez)…');
  const ok = targets.map(([dir, label]) => repairDependencies(dir, label));
  if (ok.every(Boolean)) return;

  console.error('✗ No se pudieron preparar las dependencias. Ejecuta: npm run install:all');
  process.exit(1);
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
      BOOTSTRAP_ADMIN_USERNAME: DEV_ADMIN_USER,
      BOOTSTRAP_ADMIN_PASSWORD: DEV_ADMIN_PASS,
      SECURE_COOKIES: 'true',
      RSA_KEY_SIZE: process.env.RSA_KEY_SIZE || '4096',
    },
    'api'
  );
  children.push(api);

  startWeb();

  const caCertRel = path.join('server', 'certs', 'ca.crt');
  console.log('');
  console.log(`  Calculadora       →  https://localhost:${WEB_PORT}/`);
  console.log(`  Consola (Panel)   →  https://localhost:${WEB_PORT}/admin`);
  console.log(`  Keygen Licencias  →  https://localhost:${WEB_PORT}/admin/licenses?emitir=1`);
  console.log(`  API               →  https://localhost:${API_PORT}/api/public/health`);
  console.log('');
  console.log(`  Credenciales iniciales (desarrollo):`);
  console.log(`    usuario    : ${DEV_ADMIN_USER}`);
  console.log(`    contraseña : ${DEV_ADMIN_PASS}`);
  console.log('');
  console.log(`  Si el navegador avisa del certificado, importa ${caCertRel}`);
  console.log('  como autoridad de confianza (ejecuta `npm run certs` para ver los comandos).');
  console.log('──────────────────────────────────────────────────────────────');
}

/** Solo se reintenta una vez: si vuelve a fallar, se informa y se sale. */
let webRestarted = false;

function startWeb() {
  // Nota: Vite 6 eliminó el flag CLI `--https`; el TLS local se activa en
  // `web/vite.config.ts` mediante `VITE_HTTPS=true` usando `web/certs/dev.{crt,key}`.
  const args = [VITE_BIN, '--host', '0.0.0.0', '--port', String(WEB_PORT)];
  const env = {
    VITE_HTTPS: 'true',
    VITE_PORT: String(WEB_PORT),
    API_TARGET: `https://127.0.0.1:${API_PORT}`,
  };

  const web = runNode(args, WEB, env, 'web', (stderr) => {
    // Auto-reparación: un `node_modules` incompleto/desactualizado hace que Vite
    // no pueda cargar `vite.config.ts` (ERR_MODULE_NOT_FOUND, "Cannot find module",
    // "failed to load config"). Se reinstala y se relanza la web una sola vez.
    const faltaPaquete =
      /ERR_MODULE_NOT_FOUND|Cannot find (?:package|module)|failed to load config|Could not resolve/i.test(stderr);
    if (!faltaPaquete || webRestarted) return false;
    webRestarted = true;
    console.error('');
    console.error('✗ [web] Vite no pudo cargar su configuración: faltan dependencias en web/node_modules.');
    console.error('  → Reinstalando y reintentando una vez…');
    console.error('');
    if (!repairDependencies(WEB, 'web')) return false; // ya se imprimió la guía
    const retry = runNode(args, WEB, env, 'web');
    children.push(retry);
    return true;
  });
  children.push(web);
  return web;
}
