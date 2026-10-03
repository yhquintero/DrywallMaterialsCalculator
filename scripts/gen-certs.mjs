#!/usr/bin/env node
/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Generador multiplataforma (Windows / macOS / Linux) de CA local + certificados
 * TLS de servidor para desarrollo HTTPS (`https://localhost:8443` y `:3000`).
 *
 * Implementado íntegramente con `node:crypto` (X.509 v3 + ASN.1 DER + SAN),
 * sin depender de `bash` ni de un binario externo `openssl` en el PATH.
 *
 * Uso:
 *   node scripts/gen-certs.mjs                 # localhost, 127.0.0.1, ::1
 *   node scripts/gen-certs.mjs --force         # regenera aunque ya existan
 *   HOSTS="consola.local,192.168.1.50" node scripts/gen-certs.mjs
 *
 * Salida (ignorada por git):
 *   server/certs/ca.crt          ← instala esta CA en tu SO/navegador
 *   server/certs/ca.key
 *   server/certs/server.crt
 *   server/certs/server.key
 *   web/certs/dev.crt / dev.key  ← los usa `npm run dev:https`
 *   web/certs/ca.crt / dev-rootCA.pem
 * ─────────────────────────────────────────────────────────────────────────────
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_ROOT = path.resolve(SCRIPT_DIR, '..');

// ── Codificador ASN.1 DER minimalista (RFC 5280) ─────────────────────────────

function derLength(len) {
  if (len < 0x80) return Buffer.from([len]);
  const bytes = [];
  let n = len;
  while (n > 0) {
    bytes.unshift(n & 0xff);
    n >>= 8;
  }
  return Buffer.from([0x80 | bytes.length, ...bytes]);
}

function derTlv(tag, content) {
  const body = Buffer.isBuffer(content) ? content : Buffer.concat(content);
  return Buffer.concat([Buffer.from([tag]), derLength(body.length), body]);
}

const derSequence = (items) => derTlv(0x30, items);
const derSet = (items) => derTlv(0x31, items);
const derOctetString = (buf) => derTlv(0x04, buf);
const derNull = () => Buffer.from([0x05, 0x00]);
const derBoolean = (val) => Buffer.from([0x01, 0x01, val ? 0xff : 0x00]);
const derExplicit = (tagNum, inner) => derTlv(0xa0 | tagNum, inner);
const derImplicitPrimitive = (tagNum, buf) => derTlv(0x80 | tagNum, buf);

function derBitString(buf, unusedBits = 0) {
  return derTlv(0x03, Buffer.concat([Buffer.from([unusedBits]), buf]));
}

function derInteger(bufOrNum) {
  let bytes;
  if (typeof bufOrNum === 'number') {
    if (bufOrNum === 0) {
      bytes = Buffer.from([0x00]);
    } else {
      const arr = [];
      let n = bufOrNum;
      while (n > 0) {
        arr.unshift(n & 0xff);
        n >>= 8;
      }
      bytes = Buffer.from(arr);
    }
  } else {
    bytes = Buffer.from(bufOrNum);
  }
  while (bytes.length > 1 && bytes[0] === 0x00 && (bytes[1] & 0x80) === 0) {
    bytes = bytes.subarray(1);
  }
  if (bytes[0] & 0x80) {
    bytes = Buffer.concat([Buffer.from([0x00]), bytes]);
  }
  return derTlv(0x02, bytes);
}

function derOid(oidStr) {
  const parts = oidStr.split('.').map((n) => Number.parseInt(n, 10));
  const out = [40 * parts[0] + parts[1]];
  for (let i = 2; i < parts.length; i += 1) {
    let v = parts[i];
    if (v === 0) {
      out.push(0);
      continue;
    }
    const stack = [];
    while (v > 0) {
      stack.unshift(v & 0x7f);
      v = Math.floor(v / 128);
    }
    for (let j = 0; j < stack.length - 1; j += 1) stack[j] |= 0x80;
    out.push(...stack);
  }
  return derTlv(0x06, Buffer.from(out));
}

function derPrintableString(str) {
  return derTlv(0x13, Buffer.from(str, 'ascii'));
}

function derUtf8String(str) {
  return derTlv(0x0c, Buffer.from(str, 'utf8'));
}

function derTime(date) {
  const pad2 = (n) => String(n).padStart(2, '0');
  const year = date.getUTCFullYear();
  const rest =
    pad2(date.getUTCMonth() + 1) +
    pad2(date.getUTCDate()) +
    pad2(date.getUTCHours()) +
    pad2(date.getUTCMinutes()) +
    pad2(date.getUTCSeconds()) +
    'Z';
  if (year >= 2050) {
    return derTlv(0x18, Buffer.from(`${year}${rest}`, 'ascii'));
  }
  return derTlv(0x17, Buffer.from(`${pad2(year % 100)}${rest}`, 'ascii'));
}

// OIDs estándar
const OID_SHA256_WITH_RSA = '1.2.840.113549.1.1.11';
const OID_COUNTRY = '2.5.4.6';
const OID_ORG = '2.5.4.10';
const OID_COMMON_NAME = '2.5.4.3';
const OID_SUBJECT_KEY_ID = '2.5.29.14';
const OID_KEY_USAGE = '2.5.29.15';
const OID_SUBJECT_ALT_NAME = '2.5.29.17';
const OID_BASIC_CONSTRAINTS = '2.5.29.19';
const OID_AUTHORITY_KEY_ID = '2.5.29.35';
const OID_EXT_KEY_USAGE = '2.5.29.37';
const OID_EKU_SERVER_AUTH = '1.3.6.1.5.5.7.3.1';

function encodeAlgIdSha256Rsa() {
  return derSequence([derOid(OID_SHA256_WITH_RSA), derNull()]);
}

function encodeDistinguishedName({ C = 'CU', O = 'DrywallPro Master', CN }) {
  const rdn = (oid, valueBuf) => derSet([derSequence([derOid(oid), valueBuf])]);
  return derSequence([
    rdn(OID_COUNTRY, derPrintableString(C)),
    rdn(OID_ORG, derUtf8String(O)),
    rdn(OID_COMMON_NAME, derUtf8String(CN)),
  ]);
}

function encodeExtension(oid, critical, valueDer) {
  const fields = [derOid(oid)];
  if (critical) fields.push(derBoolean(true));
  fields.push(derOctetString(valueDer));
  return derSequence(fields);
}

function randomSerial() {
  const buf = crypto.randomBytes(16);
  buf[0] = (buf[0] & 0x7f) | 0x01; // siempre positivo y no nulo
  return buf;
}

function keyIdentifierFromSpki(spkiDer) {
  return crypto.createHash('sha1').update(spkiDer).digest();
}

function ipv4ToBuffer(ip) {
  return Buffer.from(ip.split('.').map((octet) => Number.parseInt(octet, 10)));
}

function ipv6ToBuffer(ip) {
  // Manejo de "::" en IPv6 (p. ej. "::1")
  let normalized = ip;
  if (normalized.includes('::')) {
    const [left, right] = normalized.split('::');
    const leftParts = left ? left.split(':') : [];
    const rightParts = right ? right.split(':') : [];
    const missing = 8 - (leftParts.length + rightParts.length);
    const middle = Array.from({ length: Math.max(0, missing) }, () => '0');
    normalized = [...leftParts, ...middle, ...rightParts].join(':');
  }
  const groups = normalized.split(':');
  const buf = Buffer.alloc(16);
  for (let i = 0; i < 8; i += 1) {
    buf.writeUInt16BE(Number.parseInt(groups[i] || '0', 16), i * 2);
  }
  return buf;
}

export function parseSanHosts(hostsInput = 'localhost,127.0.0.1,::1') {
  const raw = String(hostsInput)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  const items = raw.length > 0 ? raw : ['localhost', '127.0.0.1', '::1'];
  const entries = [];
  const generalNames = [];

  for (const item of items) {
    const ipVer = net.isIP(item);
    if (ipVer === 4) {
      entries.push(`IP:${item}`);
      generalNames.push(derImplicitPrimitive(7, ipv4ToBuffer(item)));
    } else if (ipVer === 6) {
      entries.push(`IP:${item}`);
      generalNames.push(derImplicitPrimitive(7, ipv6ToBuffer(item)));
    } else {
      entries.push(`DNS:${item}`);
      generalNames.push(derImplicitPrimitive(2, Buffer.from(item, 'ascii')));
    }
  }

  return {
    summary: entries.join(','),
    sanDer: derSequence(generalNames),
  };
}

function derToPem(derBuf, label) {
  const b64 = derBuf.toString('base64');
  const lines = b64.match(/.{1,64}/g) || [];
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----\n`;
}

function buildSignedCertificate({
  subjectCN,
  issuerCN,
  publicKey,
  signingPrivateKey,
  days,
  extensions,
}) {
  const now = Date.now();
  const notBefore = new Date(now - 5 * 60 * 1000); // -5 min para evitar desfases de reloj
  const notAfter = new Date(now + days * 24 * 60 * 60 * 1000);

  const spkiDer = publicKey.export({ type: 'spki', format: 'der' });

  const tbs = derSequence([
    derExplicit(0, derInteger(2)), // v3
    derInteger(randomSerial()),
    encodeAlgIdSha256Rsa(),
    encodeDistinguishedName({ CN: issuerCN }),
    derSequence([derTime(notBefore), derTime(notAfter)]),
    encodeDistinguishedName({ CN: subjectCN }),
    spkiDer,
    derExplicit(3, derSequence(extensions)),
  ]);

  const signature = crypto.sign('sha256', tbs, signingPrivateKey);
  const certDer = derSequence([tbs, encodeAlgIdSha256Rsa(), derBitString(signature, 0)]);
  return derToPem(certDer, 'CERTIFICATE');
}

function createCaCertificate({ rsaModulusLength = 4096, days = 3650 } = {}) {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: rsaModulusLength,
  });
  const spkiDer = publicKey.export({ type: 'spki', format: 'der' });
  const ski = keyIdentifierFromSpki(spkiDer);

  const extensions = [
    // basicConstraints = critical, CA:TRUE
    encodeExtension(OID_BASIC_CONSTRAINTS, true, derSequence([derBoolean(true)])),
    // keyUsage = critical, keyCertSign (bit 5) | cRLSign (bit 6) -> 0x06 (1 unused bit)
    encodeExtension(OID_KEY_USAGE, true, derBitString(Buffer.from([0x06]), 1)),
    // subjectKeyIdentifier
    encodeExtension(OID_SUBJECT_KEY_ID, false, derOctetString(ski)),
  ];

  const certPem = buildSignedCertificate({
    subjectCN: 'DrywallPro Local CA',
    issuerCN: 'DrywallPro Local CA',
    publicKey,
    signingPrivateKey: privateKey,
    days,
    extensions,
  });

  const keyPem = privateKey.export({ type: 'pkcs8', format: 'pem' });
  return { certPem, keyPem, publicKey, privateKey };
}

function createLeafCertificate({
  cn = 'localhost',
  caCertPem,
  caPrivateKey,
  hosts = 'localhost,127.0.0.1,::1',
  days = 825,
  rsaModulusLength = 2048,
}) {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: rsaModulusLength,
  });

  const caX509 = new crypto.X509Certificate(caCertPem);
  const caSpkiDer = caX509.publicKey.export({ type: 'spki', format: 'der' });
  const caKeyId = keyIdentifierFromSpki(caSpkiDer);

  const leafSpkiDer = publicKey.export({ type: 'spki', format: 'der' });
  const leafKeyId = keyIdentifierFromSpki(leafSpkiDer);

  const { sanDer } = parseSanHosts(hosts);

  const extensions = [
    // basicConstraints = critical, CA:FALSE (SEQUENCE vacío en DER canónico)
    encodeExtension(OID_BASIC_CONSTRAINTS, true, derSequence([])),
    // keyUsage = critical, digitalSignature (bit 0) | keyEncipherment (bit 2) -> 0xa0 (5 unused bits)
    encodeExtension(OID_KEY_USAGE, true, derBitString(Buffer.from([0xa0]), 5)),
    // extendedKeyUsage = serverAuth
    encodeExtension(OID_EXT_KEY_USAGE, false, derSequence([derOid(OID_EKU_SERVER_AUTH)])),
    // subjectAltName
    encodeExtension(OID_SUBJECT_ALT_NAME, false, sanDer),
    // subjectKeyIdentifier
    encodeExtension(OID_SUBJECT_KEY_ID, false, derOctetString(leafKeyId)),
    // authorityKeyIdentifier = SEQUENCE { [0] IMPLICIT KeyIdentifier }
    encodeExtension(
      OID_AUTHORITY_KEY_ID,
      false,
      derSequence([derImplicitPrimitive(0, caKeyId)])
    ),
  ];

  const certPem = buildSignedCertificate({
    subjectCN: cn,
    issuerCN: 'DrywallPro Local CA',
    publicKey,
    signingPrivateKey: caPrivateKey,
    days,
    extensions,
  });

  const keyPem = privateKey.export({ type: 'pkcs8', format: 'pem' });
  return { certPem, keyPem };
}

function writeRestrictedKey(filePath, content) {
  fs.writeFileSync(filePath, content, { encoding: 'utf8', mode: 0o600 });
  try {
    fs.chmodSync(filePath, 0o600);
  } catch {
    /* En Windows chmod es no-op */
  }
}

export function verifyCertBundle({ caCertFile, serverCertFile, serverKeyFile, webCertFile, webKeyFile }) {
  try {
    for (const f of [caCertFile, serverCertFile, serverKeyFile, webCertFile, webKeyFile]) {
      if (!fs.existsSync(f)) return false;
    }
    const caX509 = new crypto.X509Certificate(fs.readFileSync(caCertFile));
    if (!caX509.ca || !caX509.verify(caX509.publicKey)) return false;

    const now = Date.now();
    for (const [certFile, keyFile] of [
      [serverCertFile, serverKeyFile],
      [webCertFile, webKeyFile],
    ]) {
      const leaf = new crypto.X509Certificate(fs.readFileSync(certFile));
      const priv = crypto.createPrivateKey(fs.readFileSync(keyFile));
      if (!leaf.verify(caX509.publicKey)) return false;
      if (!leaf.checkPrivateKey(priv)) return false;
      if (Date.parse(leaf.validTo) <= now + 24 * 60 * 60 * 1000) return false;
    }
    return true;
  } catch {
    return false;
  }
}

export function ensureCerts({
  rootDir = DEFAULT_ROOT,
  hosts = process.env.HOSTS || 'localhost,127.0.0.1,::1',
  days = Number(process.env.DAYS || 825),
  force = false,
  quiet = false,
  caRsaBits = 4096,
} = {}) {
  const serverCertsDir = path.join(rootDir, 'server', 'certs');
  const webCertsDir = path.join(rootDir, 'web', 'certs');

  fs.mkdirSync(serverCertsDir, { recursive: true });
  fs.mkdirSync(webCertsDir, { recursive: true });

  const caKeyFile = path.join(serverCertsDir, 'ca.key');
  const caCertFile = path.join(serverCertsDir, 'ca.crt');
  const serverKeyFile = path.join(serverCertsDir, 'server.key');
  const serverCertFile = path.join(serverCertsDir, 'server.crt');
  const webKeyFile = path.join(webCertsDir, 'dev.key');
  const webCertFile = path.join(webCertsDir, 'dev.crt');
  const webCaCertFile = path.join(webCertsDir, 'ca.crt');
  const webRootPemFile = path.join(webCertsDir, 'dev-rootCA.pem');

  const log = (...args) => {
    if (!quiet) console.log(...args);
  };

  if (
    !force &&
    verifyCertBundle({
      caCertFile,
      serverCertFile,
      serverKeyFile,
      webCertFile,
      webKeyFile,
    })
  ) {
    if (!fs.existsSync(webCaCertFile)) fs.copyFileSync(caCertFile, webCaCertFile);
    if (!fs.existsSync(webRootPemFile)) fs.copyFileSync(caCertFile, webRootPemFile);
    log('ℹ Certificados locales válidos ya existen, se reutilizan.');
    return {
      created: false,
      caCertFile,
      serverCertFile,
      serverKeyFile,
      webCertFile,
      webKeyFile,
    };
  }

  const { summary } = parseSanHosts(hosts);
  log(`→ SAN: ${summary}`);

  let caCertPem;
  let caPrivateKey;

  let reuseCa = false;
  if (!force && fs.existsSync(caKeyFile) && fs.existsSync(caCertFile)) {
    try {
      const existingCaCert = fs.readFileSync(caCertFile, 'utf8');
      const existingCaKey = crypto.createPrivateKey(fs.readFileSync(caKeyFile, 'utf8'));
      const x509 = new crypto.X509Certificate(existingCaCert);
      if (x509.ca && x509.verify(x509.publicKey) && x509.checkPrivateKey(existingCaKey)) {
        caCertPem = existingCaCert;
        caPrivateKey = existingCaKey;
        reuseCa = true;
      }
    } catch {
      reuseCa = false;
    }
  }

  if (!reuseCa) {
    const ca = createCaCertificate({ rsaModulusLength: caRsaBits, days: 3650 });
    caCertPem = ca.certPem;
    caPrivateKey = ca.privateKey;
    writeRestrictedKey(caKeyFile, ca.keyPem);
    fs.writeFileSync(caCertFile, caCertPem, 'utf8');
    log(`✔ CA local creada: ${caCertFile}`);
  } else {
    log('ℹ CA local ya existe, se reutiliza.');
  }

  // Copia de la CA también en web/certs para comodidad de importación
  fs.copyFileSync(caCertFile, webCaCertFile);
  fs.copyFileSync(caCertFile, webRootPemFile);

  const serverLeaf = createLeafCertificate({
    cn: 'localhost',
    caCertPem,
    caPrivateKey,
    hosts,
    days,
  });
  writeRestrictedKey(serverKeyFile, serverLeaf.keyPem);
  fs.writeFileSync(serverCertFile, serverLeaf.certPem, 'utf8');
  log(`✔ Certificado: ${serverCertFile} (+ server.key)`);

  const webLeaf = createLeafCertificate({
    cn: 'localhost',
    caCertPem,
    caPrivateKey,
    hosts,
    days,
  });
  writeRestrictedKey(webKeyFile, webLeaf.keyPem);
  fs.writeFileSync(webCertFile, webLeaf.certPem, 'utf8');
  log(`✔ Certificado: ${webCertFile} (+ dev.key)`);

  if (
    !verifyCertBundle({
      caCertFile,
      serverCertFile,
      serverKeyFile,
      webCertFile,
      webKeyFile,
    })
  ) {
    throw new Error('La autoverificación X.509 de los certificados generados falló.');
  }

  log(`
────────────────────────────────────────────────────────────
 Certificados listos (carpetas ignoradas por git)

 Para que el navegador confíe en https://localhost instala la CA:
   • Windows (PowerShell Admin): certutil -addstore -f ROOT "${caCertFile}"
   • Windows (Usuario actual)  : certutil -user -addstore -f ROOT "${caCertFile}"
   • macOS                     : sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain "${caCertFile}"
   • Linux                     : sudo cp "${caCertFile}" /usr/local/share/ca-certificates/drywallpro-ca.crt && sudo update-ca-certificates
   • Chrome/Edge               : Ajustes → Privacidad y seguridad → Seguridad → Gestionar certificados → Autoridades → Importar

 Arrancar todo en HTTPS:
   npm run dev:https        (web + API con TLS, desde la raíz del repo)
────────────────────────────────────────────────────────────`);

  return {
    created: true,
    caCertFile,
    serverCertFile,
    serverKeyFile,
    webCertFile,
    webKeyFile,
  };
}

const isMain =
  process.argv[1] &&
  path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url));

if (isMain) {
  const force = process.argv.includes('--force') || process.argv.includes('-f');
  const quiet = process.argv.includes('--quiet') || process.argv.includes('-q');
  try {
    ensureCerts({ force, quiet });
  } catch (err) {
    console.error('✗ Error generando certificados locales:', err?.message || err);
    process.exit(1);
  }
}
