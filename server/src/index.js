/**
 * Punto de entrada del servidor.
 *
 * Modos de arranque:
 *  • ENABLE_HTTPS=true  → HTTPS en PORT (8443) con los certificados indicados.
 *  • ALWAYS_REDIRECT=true → además levanta un puerto HTTP (8080) que redirige 301 a HTTPS.
 *  • Sin TLS (desarrollo/preview) → HTTP plano en PORT y aviso en consola.
 */
import fs from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import config from './config/index.js';
import { createApp } from './app.js';
import { getDb, closeDb } from './db/index.js';
import { Database } from './db/driver.js';
import { ensureBootstrapAdmin, ensureSigningKeys } from '../tools/bootstrap.js';

const app = createApp();

// Inicializa BD + semillas + admin bootstrap.
getDb();
const bootstrapInfo = ensureBootstrapAdmin({ quiet: true });
const newKeys = ensureSigningKeys({ quiet: true });

function readTls() {
  const { certFile, keyFile } = config.server;
  if (!fs.existsSync(certFile) || !fs.existsSync(keyFile)) return null;
  return { cert: fs.readFileSync(certFile), key: fs.readFileSync(keyFile) };
}

const tls = config.server.https ? readTls() : null;
if (config.server.https && !tls) {
  console.error(
    `[server] ENABLE_HTTPS=true pero no se encuentran los certificados:\n` +
      `  cert: ${config.server.certFile}\n  key : ${config.server.keyFile}\n` +
      `Genera unos locales con:  npm run certs   (script scripts/gen-certs.mjs)`
  );
  process.exit(1);
}

const servers = [];

if (tls) {
  const secure = https.createServer({ ...tls, minVersion: 'TLSv1.2', honorCipherOrder: true }, app);
  secure.listen(config.server.port, config.server.host, () => {
    console.log(`[server] 🔒 HTTPS listo en https://${config.server.host}:${config.server.port}`);
  });
  servers.push(secure);

  if (process.env.ALWAYS_REDIRECT === 'true') {
    const redirector = http.createServer((req, res) => {
      const host = (req.headers.host || config.server.publicHost).split(':')[0];
      res.writeHead(301, { Location: `https://${host}:${config.server.port}${req.url}`, 'Cache-Control': 'no-store' });
      res.end();
    });
    redirector.listen(config.server.httpPort, config.server.host, () => {
      console.log(`[server] ↪  HTTP ${config.server.httpPort} redirige a HTTPS ${config.server.port}`);
    });
    servers.push(redirector);
  }
} else {
  const plain = http.createServer(app);
  plain.listen(config.server.port, config.server.host, () => {
    console.log(`[server] ⚠  HTTP (sin TLS) en http://${config.server.host}:${config.server.port}`);
    console.log(`[server]    Para activar HTTPS: ENABLE_HTTPS=true npm start  (previo npm run certs)`);
  });
  servers.push(plain);
}

console.log(`[server] entorno=${config.env} driver=${Database.driver} db=${config.db.file}`);
if (bootstrapInfo.created && bootstrapInfo.password) {
  console.log(`[server] ✅ Usuario administrador creado: ${bootstrapInfo.username}`);
  console.log(`[server]    contraseña temporal: ${bootstrapInfo.password}`);
  console.log(`[server]    ⚠ Guárdala: no se vuelve a mostrar. Cámbiala al entrar en la consola.`);
} else if (bootstrapInfo.created) {
  console.log(`[server] ✅ Usuario administrador creado: ${bootstrapInfo.username} (contraseña de BOOTSTRAP_ADMIN_PASSWORD)`);
} else if (bootstrapInfo.exists) {
  console.log(`[server] ℹ  Admin de bootstrap ya existe (${bootstrapInfo.username}).`);
}
for (const k of newKeys) {
  console.log(`[server] 🔑 Clave RSA-${config.crypto.rsaKeySize} creada para ${k.appId}: ${k.kid}`);
}

function shutdown(signal) {
  console.log(`\n[server] ${signal} recibido, cerrando…`);
  let pending = servers.length;
  if (pending === 0) process.exit(0);
  for (const s of servers) {
    s.close(() => {
      pending -= 1;
      if (pending === 0) {
        closeDb();
        process.exit(0);
      }
    });
  }
  setTimeout(() => process.exit(1), 8000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('unhandledRejection', (err) => console.error('[server] unhandledRejection:', err));

export { app };
