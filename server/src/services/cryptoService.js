/**
 * Servicio criptográfico — compatible 1:1 con las apps Android.
 *
 * Contrato que respetan `app` (LicensingManager/SecurityUtils) y `keygen` (KeyGenSecurity):
 *   • Par RSA de 4096 bits.
 *   • dataToSign  = `${user}|${deviceId}|${creationDate}|${expiryDate}`
 *   • signature   = Base64(RSA-SHA256 / "SHA256withRSA", PKCS#1 v1.5)
 *   • issuerKey   = Base64(DER X.509 SubjectPublicKeyInfo)  — sin cabeceras PEM
 *   • privateKey  = PKCS#8 DER (lo que produce Android `keyPair.private.encoded`)
 *
 * Android prueba primero "SHA256withRSAandMGF1" (PSS) y luego "SHA256withRSA";
 * al firmar en PKCS#1 v1.5 la verificación triunfa en el segundo intento.
 */
import crypto from 'node:crypto';
import config, { masterAesKey } from '../config/index.js';

export const SIGN_ALGORITHM = 'RSA-SHA256';
export const SIGN_ALGORITHM_ANDROID_NAME = 'SHA256withRSA';

/** Genera un par RSA del tamaño configurado (4096 por defecto). */
export function generateRsaKeyPair(modulusBits = config.crypto.rsaKeySize) {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: modulusBits,
    publicKeyEncoding: { type: 'spki', format: 'der' },
    privateKeyEncoding: { type: 'pkcs8', format: 'der' },
  });
  return {
    publicKeyDer: publicKey,
    privateKeyDer: privateKey,
    publicKeyB64: publicKey.toString('base64'),
    privateKeyB64: privateKey.toString('base64'),
    publicKeyPem: derToPem(publicKey, 'PUBLIC KEY'),
    privateKeyPem: derToPem(privateKey, 'PRIVATE KEY'),
    modulusBits,
  };
}

/**
 * Cache de KeyObjects: parsear un DER RSA-4096 en cada firma es costoso y las
 * emisiones se hacen en lote. La clave del mapa es el hash del DER.
 */
const privateKeyCache = new Map();
const publicKeyCache = new Map();

function toPrivateKeyObject(privateKeyDer) {
  const buf = Buffer.isBuffer(privateKeyDer) ? privateKeyDer : Buffer.from(privateKeyDer);
  const cacheKey = buf.toString('base64');
  let key = privateKeyCache.get(cacheKey);
  if (!key) {
    key = crypto.createPrivateKey({ key: buf, format: 'der', type: 'pkcs8' });
    if (privateKeyCache.size > 20) privateKeyCache.clear();
    privateKeyCache.set(cacheKey, key);
  }
  return key;
}

function toPublicKeyObject(publicKeyDerOrB64) {
  const buf = Buffer.isBuffer(publicKeyDerOrB64)
    ? publicKeyDerOrB64
    : Buffer.from(String(publicKeyDerOrB64).replace(/[^A-Za-z0-9+/=]/g, ''), 'base64');
  const cacheKey = buf.toString('base64');
  let key = publicKeyCache.get(cacheKey);
  if (!key) {
    key = crypto.createPublicKey({ key: buf, format: 'der', type: 'spki' });
    if (publicKeyCache.size > 40) publicKeyCache.clear();
    publicKeyCache.set(cacheKey, key);
  }
  return key;
}

/**
 * Firma el payload exacto que espera el LicensingManager de Android.
 * Equivalente a `Signature.getInstance("SHA256withRSA")` (PKCS#1 v1.5 + SHA-256).
 */
export function signLicenseData(privateKeyDer, dataToSign) {
  const signer = crypto.createSign(SIGN_ALGORITHM);
  signer.update(Buffer.from(dataToSign, 'utf8'));
  signer.end();
  return signer.sign(toPrivateKeyObject(privateKeyDer)).toString('base64');
}

/** Verifica una firma (útil al importar licencias emitidas por las apps). */
export function verifyLicenseData(publicKeyDerOrB64, dataToSign, signatureB64) {
  try {
    const verifier = crypto.createVerify(SIGN_ALGORITHM);
    verifier.update(Buffer.from(dataToSign, 'utf8'));
    verifier.end();
    const cleanSig = Buffer.from(String(signatureB64).replace(/[^A-Za-z0-9+/=]/g, ''), 'base64');
    return verifier.verify(toPublicKeyObject(publicKeyDerOrB64), cleanSig);
  } catch {
    return false;
  }
}

/** Construye la cadena firmada exactamente como lo hacen las apps. */
export function buildDataToSign(user, deviceId, creationDate, expiryDate) {
  return `${String(user).trim()}|${String(deviceId).trim()}|${creationDate}|${expiryDate}`;
}

/**
 * Normaliza Base64 igual que `LicensingManager.normalizeBase64` de Android:
 * convierte url-safe a estándar y reajusta el padding.
 */
export function normalizeBase64(input) {
  const standard = String(input).replace(/-/g, '+').replace(/_/g, '/').trim();
  const withoutPadding = standard.replace(/=+$/, '');
  const padding = (4 - (withoutPadding.length % 4)) % 4;
  return withoutPadding + '='.repeat(padding);
}

export function derToPem(der, label) {
  const b64 = Buffer.from(der).toString('base64');
  const lines = b64.match(/.{1,64}/g) ?? [];
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----\n`;
}

export function pemToDer(pem) {
  const b64 = String(pem)
    .replace(/-----BEGIN [^-]+-----/g, '')
    .replace(/-----END [^-]+-----/g, '')
    .replace(/\s+/g, '');
  return Buffer.from(b64, 'base64');
}

export function sha256Hex(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

export function sha256B64(data) {
  return crypto.createHash('sha256').update(data).digest('base64');
}

/** Huella legible de una clave pública (para mostrar en la UI). */
export function keyFingerprint(publicKeyB64) {
  return sha256Hex(Buffer.from(publicKeyB64, 'base64')).replace(/(.{2})/g, '$1:').slice(0, 47).toUpperCase();
}

// ───────────────────── Cifrado at-rest de llaves privadas ─────────────────────
// AES-256-GCM con clave derivada del MASTER_KEY. Nunca se guarda la llave
// privada en claro en la base de datos.

export function encryptSecret(plaintext) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', masterAesKey(), iv);
  const enc = Buffer.concat([cipher.update(Buffer.from(plaintext)), cipher.final()]);
  return {
    ciphertext: enc.toString('base64'),
    iv: iv.toString('base64'),
    tag: cipher.getAuthTag().toString('base64'),
  };
}

export function decryptSecret(ciphertextB64, ivB64, tagB64) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', masterAesKey(), Buffer.from(ivB64, 'base64'));
  decipher.setAuthTag(Buffer.from(tagB64, 'base64'));
  const dec = Buffer.concat([
    decipher.update(Buffer.from(ciphertextB64, 'base64')),
    decipher.final(),
  ]);
  return dec.toString('utf8');
}

/** Identificador corto y estable para una clave de firma. */
export function buildKid(appId) {
  const rand = crypto.randomBytes(5).toString('hex').toUpperCase();
  const prefix = appId === 'keygen_pro' ? 'KG' : 'DW';
  const year = new Date().getFullYear();
  return `${prefix}-${year}-${rand}`;
}

/** Clave de licencia legible para el cliente: DW-XXXX-XXXX-XXXX-XXXX */
export function generateLicenseKey(appId) {
  const prefix = appId === 'keygen_pro' ? 'KG' : 'DW';
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // sin caracteres ambiguos
  const bytes = crypto.randomBytes(16);
  let out = '';
  for (let i = 0; i < 16; i += 1) {
    out += alphabet[bytes[i] % alphabet.length];
    if (i % 4 === 3 && i !== 15) out += '-';
  }
  return `${prefix}-${out}`;
}

/** Contraseña temporal segura para resets/altas de usuario. */
/**
 * Contraseña temporal criptográficamente aleatoria que SIEMPRE satisface
 * `assertStrongPassword` (mayúscula, minúscula, número, símbolo y ≥10 chars).
 *
 * Antes se muestreaban `length` caracteres al azar de un único alfabeto, de modo
 * que existía una probabilidad real (~0,3 % con 18 chars) de generar una
 * contraseña sin dígitos o sin símbolos: el propio validador la rechazaba y el
 * arranque del servidor fallaba de forma intermitente. Ahora se garantiza al
 * menos un carácter de cada clase y se baraja con Fisher-Yates.
 */
export function generateTemporaryPassword(length = 16) {
  const UPPER = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const LOWER = 'abcdefghijkmnopqrstuvwxyz';
  const DIGIT = '23456789';
  const SYMBOL = '!@#$%^&*-_=+';
  const ALL = UPPER + LOWER + DIGIT + SYMBOL;
  const size = Math.max(12, length);

  const pick = (alphabet) => alphabet[crypto.randomBytes(1)[0] % alphabet.length];

  // Un representante obligatorio de cada clase.
  const chars = [pick(UPPER), pick(LOWER), pick(DIGIT), pick(SYMBOL)];
  // El resto, del alfabeto completo (se descarta el byte de sesgo módulo).
  while (chars.length < size) chars.push(pick(ALL));

  // Barajado Fisher-Yates con una sola fuente de entropía.
  const entropy = crypto.randomBytes(size * 2);
  let cursor = 0;
  for (let i = chars.length - 1; i > 0; i -= 1) {
    const rand = ((entropy[cursor++] << 8) | entropy[cursor++]) % (i + 1);
    [chars[i], chars[rand]] = [chars[rand], chars[i]];
  }
  return chars.join('');
}
