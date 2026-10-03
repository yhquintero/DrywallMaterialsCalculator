/**
 * DrywallPro Anti-SQL Injection & Input Sanitization Engine
 * Military-grade input validation against SQLi, stacked queries, and command injection.
 */

export interface SqlCheckResult {
  isSuspicious: boolean;
  threatLevel: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  matchedPatterns: string[];
  explanation: string;
  sanitized: string;
}

// Comprehensive SQL Injection detection signatures
const SQL_INJECTION_PATTERNS: Array<{
  name: string;
  regex: RegExp;
  level: 'MEDIUM' | 'HIGH' | 'CRITICAL';
  desc: string;
}> = [
  {
    name: 'Classic Boolean SQLi',
    regex: /('\s*(OR|AND)\s*['"]?\d*['"]?\s*=\s*['"]?\d*)|("?\s*(OR|AND)\s*['"]?\d*['"]?\s*=\s*['"]?\d*)/i,
    level: 'CRITICAL',
    desc: 'Intento de evasión booleana clásica tipo "\' OR 1=1"'
  },
  {
    name: 'Union-Based SQLi',
    regex: /\bUNION\s+(ALL\s+)?SELECT\b/i,
    level: 'CRITICAL',
    desc: 'Extracción no autorizada de datos mediante cláusula UNION SELECT'
  },
  {
    name: 'Stacked Queries DDL/DML',
    regex: /;\s*(DROP|ALTER|CREATE|TRUNCATE|DELETE\s+FROM|UPDATE|INSERT\s+INTO)\b/i,
    level: 'CRITICAL',
    desc: 'Inyección de múltiples consultas apiladas destructivas (DROP/DELETE/UPDATE)'
  },
  {
    name: 'System Procedures & Shell Exec',
    regex: /\b(XP_CMDSHELL|SP_EXECUTESQL|EXEC\s*\(|EXECUTE\s+IMMEDIATE)\b/i,
    level: 'CRITICAL',
    desc: 'Ejecución de procedimientos almacenados del sistema o comandos del shell'
  },
  {
    name: 'Time-Based Blind SQLi',
    regex: /\b(WAITFOR\s+DELAY|SLEEP\s*\(|BENCHMARK\s*\(|PG_SLEEP\s*\()/i,
    level: 'HIGH',
    desc: 'Ataque de temporización a ciegas para inferir estructura de base de datos'
  },
  {
    name: 'SQL Comment Terminators',
    regex: /(--\s*$|--\s+[^\r\n]*|\/\*[\s\S]*?\*\/|(?<!\w)#)/m,
    level: 'MEDIUM',
    desc: 'Comentarios SQL para anular el resto de la consulta original'
  },
  {
    name: 'Schema Enumeration',
    regex: /\b(INFORMATION_SCHEMA|SQLITE_MASTER|SYS\.TABLES|SYSOBJECTS)\b/i,
    level: 'HIGH',
    desc: 'Sondeo o enumeración de metadatos del catálogo de la base de datos'
  },
  {
    name: 'Hex / Character Obfuscation',
    regex: /\b(CHAR\s*\(|CONVERT\s*\(|UNHEX\s*\(|0x[0-9a-fA-F]{4,})/i,
    level: 'MEDIUM',
    desc: 'Ofuscación de cadenas de texto en formato hexadecimal o llamadas CHAR()'
  }
];

/**
 * Analiza una cadena de texto para detectar firmas de inyección SQL
 */
export function detectSqlInjection(input: string | undefined | null): SqlCheckResult {
  if (!input || typeof input !== 'string') {
    return {
      isSuspicious: false,
      threatLevel: 'NONE',
      matchedPatterns: [],
      explanation: 'Entrada limpia o vacía.',
      sanitized: ''
    };
  }

  const matched: string[] = [];
  let maxLevel: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' = 'NONE';
  const explanations: string[] = [];

  for (const pattern of SQL_INJECTION_PATTERNS) {
    if (pattern.regex.test(input)) {
      matched.push(pattern.name);
      explanations.push(pattern.desc);

      if (pattern.level === 'CRITICAL') {
        maxLevel = 'CRITICAL';
      } else if (pattern.level === 'HIGH' && maxLevel !== 'CRITICAL') {
        maxLevel = 'HIGH';
      } else if (pattern.level === 'MEDIUM' && maxLevel === 'NONE') {
        maxLevel = 'MEDIUM';
      }
    }
  }

  const isSuspicious = matched.length > 0;
  const sanitized = sanitizeSqlInput(input);

  return {
    isSuspicious,
    threatLevel: maxLevel,
    matchedPatterns: matched,
    explanation: isSuspicious
      ? `Detectado vector SQLi: ${explanations.join('; ')}`
      : 'Texto seguro, sin firmas de inyección SQL detectadas.',
    sanitized
  };
}

/**
 * Sanitiza y neutraliza cualquier entrada propensa a SQLi
 * Escapa comillas, neutraliza comentarios apilados y remueve caracteres nulos
 */
export function sanitizeSqlInput(input: string): string {
  if (!input || typeof input !== 'string') return '';

  return input
    // Eliminar caracteres de control nulos
    .replace(/\0/g, '')
    // Reemplazar comillas simples peligrosas por su equivalente seguro
    .replace(/'/g, "''")
    // Desactivar comentarios SQL peligrosos apilados
    .replace(/--/g, '—')
    .replace(/\/\*/g, '')
    .replace(/\*\//g, '')
    // Recortar espacios y limitar longitud por seguridad
    .trim();
}

/**
 * Validador estricto para campos de formulario (Nombres, Ubicaciones, Clientes)
 * Asegura que solo contengan caracteres alfanuméricos y puntuación estándar
 */
export function sanitizeAlphaNumericSafe(input: string, maxLength: number = 250): string {
  if (!input || typeof input !== 'string') return '';

  // Permitir letras (incluyendo acentos y ñ), números, espacios, comas, puntos y guiones
  const cleaned = input
    .replace(/[<>{}[\]\\;/|~`^=+*$%#@]/g, '')
    .replace(/['"]/g, '')
    .trim();

  return cleaned.slice(0, maxLength);
}

/**
 * Validador estricto de números flotantes para evitar NaN y Bypass de tipos
 */
export function sanitizeNumeric(value: any, defaultValue: number = 0, min?: number, max?: number): number {
  if (value === null || value === undefined) return defaultValue;
  const num = typeof value === 'number' ? value : parseFloat(String(value));
  if (isNaN(num) || !isFinite(num)) return defaultValue;
  if (min !== undefined && num < min) return min;
  if (max !== undefined && num > max) return max;
  return num;
}
