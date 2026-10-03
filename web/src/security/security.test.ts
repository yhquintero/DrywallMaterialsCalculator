import { describe, it, expect } from 'vitest';
import { detectSqlInjection, sanitizeSqlInput, sanitizeAlphaNumericSafe } from './sqlSanitizer';
import { detectXss, sanitizeHtmlStrict, escapeHtml, sanitizeUrl } from './xssDefense';
import { sanitizeCsvCell, isFormulaInjectionAttempt } from './csvSanitizer';
import { sanitizeAgainstPrototypePollution, calculateSha256 } from './cryptoStorage';

describe('DrywallPro Security Shield - Defense in Depth Suite', () => {
  describe('1. Anti-SQL Injection Engine', () => {
    it('detects and classifies classic boolean SQL injection attempts', () => {
      const payload = "' OR 1=1 --";
      const result = detectSqlInjection(payload);

      expect(result.isSuspicious).toBe(true);
      expect(result.threatLevel).toBe('CRITICAL');
      expect(result.matchedPatterns).toContain('Classic Boolean SQLi');
    });

    it('detects stacked queries attempting table destruction', () => {
      const payload = "Residencial Central'; DROP TABLE proyectos; --";
      const result = detectSqlInjection(payload);

      expect(result.isSuspicious).toBe(true);
      expect(result.threatLevel).toBe('CRITICAL');
      expect(result.matchedPatterns).toContain('Stacked Queries DDL/DML');
    });

    it('detects UNION-based information harvesting attacks', () => {
      const payload = "1' UNION SELECT null, username, password FROM users --";
      const result = detectSqlInjection(payload);

      expect(result.isSuspicious).toBe(true);
      expect(result.matchedPatterns).toContain('Union-Based SQLi');
    });

    it('neutralizes quotes and SQL comments in sanitized output', () => {
      const payload = "O'Connor -- comment";
      const sanitized = sanitizeSqlInput(payload);

      expect(sanitized).toBe("O''Connor — comment");
      expect(sanitized).not.toContain('--');
    });

    it('strictly filters alphanumeric safe fields', () => {
      const dirtyName = "Obra <script> ' OR 1=1 --; 2026!";
      const safe = sanitizeAlphaNumericSafe(dirtyName);

      expect(safe).not.toContain('<');
      expect(safe).not.toContain('>');
      expect(safe).not.toContain("'");
      expect(safe).not.toContain(';');
    });
  });

  describe('2. XSS & HTML Sanitization Engine', () => {
    it('detects and blocks <script> tags', () => {
      const payload = "<script>alert('XSS-Injected')</script>";
      const result = detectXss(payload);

      expect(result.hasThreat).toBe(true);
      expect(result.threatLevel).toBe('CRITICAL');
      expect(result.sanitized).not.toContain('<script>');
    });

    it('strips inline onerror / onload event handlers', () => {
      const payload = '<img src="invalid" onerror="alert(document.cookie)">';
      const sanitized = sanitizeHtmlStrict(payload);

      expect(sanitized).not.toContain('onerror');
      expect(sanitized).not.toContain('<img');
    });

    it('blocks javascript: and data: pseudo-protocols in URLs', () => {
      const maliciousUrl = "javascript:alert('pwned')";
      const safe = sanitizeUrl(maliciousUrl);

      expect(safe).toBe('#blocked-insecure-url');
    });

    it('escapes HTML special entities properly', () => {
      const unescaped = '<div>"Hello" & \'World\'</div>';
      const escaped = escapeHtml(unescaped);

      expect(escaped).toBe('&lt;div&gt;&quot;Hello&quot; &amp; &#x27;World&#x27;&lt;&#x2F;div&gt;');
    });
  });

  describe('3. CSV / Excel Formula Injection (OWASP DDE)', () => {
    it('detects dangerous spreadsheet formula prefixes (=, +, -, @, %, |)', () => {
      expect(isFormulaInjectionAttempt("=cmd|' /C calc'!A0")).toBe(true);
      expect(isFormulaInjectionAttempt("+2+5")).toBe(true);
      expect(isFormulaInjectionAttempt("-5")).toBe(true);
      expect(isFormulaInjectionAttempt("@SUM(A1:A10)")).toBe(true);
      expect(isFormulaInjectionAttempt("Normal text")).toBe(false);
    });

    it('neutralizes formula injection by prepending a single quote', () => {
      const payload = "=cmd|' /C calc'!A0";
      const sanitized = sanitizeCsvCell(payload);

      expect(sanitized.startsWith("\"'=")).toBe(true);
    });
  });

  describe('4. Cryptographic Storage & Anti-Prototype Pollution', () => {
    it('recursively removes __proto__, constructor, and prototype keys', () => {
      const dirtyObject = {
        name: 'Safe Project',
        __proto__: { isAdmin: true },
        nested: {
          constructor: 'malicious',
          data: 'ok'
        }
      };

      const clean = sanitizeAgainstPrototypePollution(dirtyObject);
      expect(clean.hasOwnProperty('__proto__')).toBe(false);
      expect((clean as any).nested.hasOwnProperty('constructor')).toBe(false);
      expect(clean.name).toBe('Safe Project');
      expect((clean as any).nested.data).toBe('ok');
    });

    it('generates a valid 64-character SHA-256 checksum', async () => {
      const data = { project: 'Test', area: 150 };
      const hash = await calculateSha256(data);

      expect(typeof hash).toBe('string');
      expect(hash.length).toBe(64);
      expect(/^[0-9a-fA-F]{64}$/.test(hash)).toBe(true);
    });
  });
});
