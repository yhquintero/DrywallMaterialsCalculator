/**
 * DrywallPro Cryptographic Storage & Anti-Tamper Engine
 * Employs SHA-256 checksums, prototype pollution elimination, and schema validation.
 */

/**
 * Calcula un checksum criptográfico SHA-256 de cualquier objeto o string
 */
export async function calculateSha256(data: string | object): Promise<string> {
  try {
    const text = typeof data === 'string' ? data : JSON.stringify(data);
    const encoder = new TextEncoder();
    const dataBuffer = encoder.encode(text);
    const hashBuffer = await crypto.subtle.digest('SHA-256', dataBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  } catch (err) {
    // Fallback simple si SubtleCrypto no está disponible en entornos antiguos
    console.warn('SubtleCrypto no disponible, usando fallback hash.');
    return fallbackHash(typeof data === 'string' ? data : JSON.stringify(data));
  }
}

function fallbackHash(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0;
  }
  return Math.abs(hash).toString(16);
}

/**
 * Sanitiza recursivamente objetos contra Prototype Pollution
 * Elimina '__proto__', 'constructor' y 'prototype'
 */
export function sanitizeAgainstPrototypePollution<T>(obj: T): T {
  if (!obj || typeof obj !== 'object') return obj;

  if (Array.isArray(obj)) {
    return obj.map((item) => sanitizeAgainstPrototypePollution(item)) as unknown as T;
  }

  const cleanObj: Record<string, any> = {};

  for (const [key, value] of Object.entries(obj)) {
    if (key === '__proto__' || key === 'constructor' || key === 'prototype') {
      console.warn(`[Security Alert] Clave maliciosa bloqueada: ${key}`);
      continue;
    }

    if (value && typeof value === 'object') {
      cleanObj[key] = sanitizeAgainstPrototypePollution(value);
    } else {
      cleanObj[key] = value;
    }
  }

  return cleanObj as T;
}

/**
 * Validador de integridad de proyectos
 */
export async function verifyProjectIntegrity(
  projectData: any,
  expectedChecksum?: string
): Promise<{ isValid: boolean; currentChecksum: string; error?: string }> {
  if (!projectData || typeof projectData !== 'object') {
    return { isValid: false, currentChecksum: '', error: 'Datos de proyecto nulos o no válidos' };
  }

  // Descontaminar prototipo
  const sanitized = sanitizeAgainstPrototypePollution(projectData);
  const currentChecksum = await calculateSha256(sanitized);

  if (expectedChecksum && currentChecksum !== expectedChecksum) {
    return {
      isValid: false,
      currentChecksum,
      error: 'Fallo de integridad: El checksum almacenado no coincide con el contenido actual.'
    };
  }

  return { isValid: true, currentChecksum };
}
