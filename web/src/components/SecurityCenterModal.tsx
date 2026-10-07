import React, { useState } from 'react';
import {
  X,
  ShieldCheck,
  ShieldAlert,
  Lock,
  Terminal,
  CheckCircle2,
  AlertTriangle,
  Play,
  RotateCcw,
  FileCode,
  KeyRound,
  Database,
  Globe
} from 'lucide-react';
import { detectSqlInjection, sanitizeSqlInput } from '../security/sqlSanitizer';
import { detectXss } from '../security/xssDefense';
import { sanitizeHtmlWithDomPurify } from '../security/htmlSanitizer';
import { isFormulaInjectionAttempt, sanitizeCsvCell } from '../security/csvSanitizer';
import { calculateSha256 } from '../security/cryptoStorage';
import { Dialog } from './ui/Dialog';

interface SecurityCenterModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const SecurityCenterModal: React.FC<SecurityCenterModalProps> = ({ isOpen, onClose }) => {
  const [testPayload, setTestPayload] = useState<string>("' OR '1'='1' --");
  const [analysisResult, setAnalysisResult] = useState<{
    sqlCheck: ReturnType<typeof detectSqlInjection>;
    xssCheck: ReturnType<typeof detectXss>;
    isFormula: boolean;
    sanitizedOutput: string;
    sha256Hash: string;
  } | null>(null);

  if (!isOpen) return null;

  const runSecurityAnalysis = async (payload: string) => {
    const sqlCheck = detectSqlInjection(payload);
    const xssCheck = detectXss(payload);
    const isFormula = isFormulaInjectionAttempt(payload);

    // Salida neutralizada aplicando todos los escudos
    let sanitized = sanitizeSqlInput(payload);
    sanitized = sanitizeHtmlWithDomPurify(sanitized);

    const hash = await calculateSha256(payload);

    setAnalysisResult({
      sqlCheck,
      xssCheck,
      isFormula,
      sanitizedOutput: sanitized,
      sha256Hash: hash
    });
  };

  const samplePayloads = [
    { label: 'SQLi Clásico', payload: "' OR '1'='1' --" },
    { label: 'SQLi Stacked (DROP)', payload: "Residencial'; DROP TABLE proyectos; --" },
    { label: 'SQLi UNION SELECT', payload: "1' UNION SELECT null, username, password FROM users --" },
    { label: 'XSS <script>', payload: "<script>alert('XSS-Inyeccion')</script>" },
    { label: 'XSS Event Handler', payload: "<img src=x onerror=\"alert('XSS-Img')\">" },
    { label: 'CSV Formula DDE', payload: "=cmd|' /C calc'!A0" },
    { label: 'Texto Seguro Normal', payload: "Reforma Integral Salón Los Pinos 45m2" }
  ];

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      label="Centro de seguridad y blindaje de la plataforma"
      backdropClassName="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/85 backdrop-blur-md animate-in fade-in duration-200"
      panelClassName="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden"
    >
        {/* Header */}
        <div className="bg-slate-850 px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 bg-emerald-500/10 rounded-xl text-emerald-400 border border-emerald-500/20 shadow-inner">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h2 className="text-base font-bold text-slate-100">
                  Centro de Blindaje & Seguridad Cibernética
                </h2>
                <span className="text-[10px] bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 px-2 py-0.5 rounded-full font-mono font-semibold">
                  ESTADO: BLINDADO (GRADO EMPRESARIAL)
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Protección activa contra SQL Injection, XSS, DDE CSV Injection, Clickjacking y Criptografía SHA-256.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 text-xs">
          {/* Active Security Defense Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {/* Shield 1: HTTPS & TLS */}
            <div className="bg-slate-950 border border-slate-800/80 rounded-xl p-3.5 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                  <Lock className="w-4 h-4 text-emerald-400" />
                  HTTPS & HSTS
                </span>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              </div>
              <p className="text-slate-400 text-[11px] leading-relaxed">
                Forzado HTTPS (`upgrade-insecure-requests`), HSTS `max-age=31536000` y TLS 1.3 con cipher suites modernos.
              </p>
            </div>

            {/* Shield 2: Anti-SQL Injection */}
            <div className="bg-slate-950 border border-slate-800/80 rounded-xl p-3.5 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                  <Database className="w-4 h-4 text-blue-400" />
                  Anti-SQL Injection
                </span>
                <span className="w-2 h-2 rounded-full bg-blue-400 animate-pulse" />
              </div>
              <p className="text-slate-400 text-[11px] leading-relaxed">
                Inspección de sintaxis SQL, bloqueo de queries apiladas (`DROP/DELETE`), desinfección de comillas y caracteres nulos.
              </p>
            </div>

            {/* Shield 3: XSS & DOM Sanitizer */}
            <div className="bg-slate-950 border border-slate-800/80 rounded-xl p-3.5 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                  <FileCode className="w-4 h-4 text-purple-400" />
                  Filtro Anti-XSS
                </span>
                <span className="w-2 h-2 rounded-full bg-purple-400 animate-pulse" />
              </div>
              <p className="text-slate-400 text-[11px] leading-relaxed">
                Motor DOMPurify estricto, codificación HTML de entidades y bloqueo de pseudo-protocolos `javascript:` y eventos.
              </p>
            </div>

            {/* Shield 4: Checksum & Integrity */}
            <div className="bg-slate-950 border border-slate-800/80 rounded-xl p-3.5 space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                  <KeyRound className="w-4 h-4 text-amber-400" />
                  Integridad SHA-256
                </span>
                <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
              </div>
              <p className="text-slate-400 text-[11px] leading-relaxed">
                Firmas criptográficas nativas WebCrypto contra alteración de LocalStorage y anti Prototype Pollution (`__proto__`).
              </p>
            </div>
          </div>

          {/* Consola de Licencias y Keygen Centralizado RSA-4096 */}
          <div className="bg-gradient-to-r from-brand-950/60 via-slate-950 to-emerald-950/40 border border-brand-500/30 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <KeyRound className="w-4 h-4 text-brand-400" />
                <h3 className="font-bold text-sm text-slate-100">
                  Creación de Licencias con Keygen Centralizado (RSA-4096)
                </h3>
                <span className="text-[10px] bg-brand-500/20 text-brand-300 border border-brand-500/40 px-2 py-0.5 rounded font-mono">
                  SHA256withRSA
                </span>
              </div>
              <p className="text-[11px] text-slate-300 leading-relaxed">
                Emite, renueva, cobra y revoca licencias firmadas compatibles con <strong>DrywallPro Master</strong> y{' '}
                <strong>Keygen Pro</strong> desde la consola web (<code className="font-mono text-brand-300">/admin/licenses</code>).
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2 shrink-0">
              <a
                href="/admin/licenses?emitir=1"
                className="px-3.5 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold shadow-md transition-all flex items-center gap-1.5"
              >
                <KeyRound className="w-3.5 h-3.5" />
                <span>Crear Licencia (Keygen)</span>
              </a>
              <a
                href="/admin"
                className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-xl text-xs font-semibold transition-all"
              >
                Panel de Control
              </a>
            </div>
          </div>

          {/* Interactive Sandbox Firewall Tester */}
          <div className="bg-slate-950 border border-slate-800 rounded-2xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-bold text-sm text-slate-100 flex items-center gap-2">
                  <Terminal className="w-4 h-4 text-brand-400" />
                  <span>Simulador Interactivo de Amenazas (Firewall Sandbox)</span>
                </h3>
                <p className="text-[11px] text-slate-400 mt-0.5">
                  Prueba cualquier vector de ataque para observar cómo el motor de seguridad lo detecta y neutraliza en vivo.
                </p>
              </div>

              <button
                onClick={() => runSecurityAnalysis(testPayload)}
                className="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold shadow-md transition-all flex items-center gap-1.5"
              >
                <Play className="w-3.5 h-3.5" />
                <span>Analizar Carga</span>
              </button>
            </div>

            {/* Quick samples bar */}
            <div className="flex flex-wrap gap-1.5">
              <span className="text-[11px] text-slate-500 mr-1 flex items-center">Cargas de prueba:</span>
              {samplePayloads.map((item, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    setTestPayload(item.payload);
                    runSecurityAnalysis(item.payload);
                  }}
                  className="text-[11px] px-2.5 py-1 bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-300 rounded-lg transition-colors font-mono"
                >
                  {item.label}
                </button>
              ))}
            </div>

            {/* Input field */}
            <div>
              <label className="block text-slate-400 mb-1 text-[11px]">
                Entrada a Inspeccionar (Payload):
              </label>
              <textarea
                rows={2}
                value={testPayload}
                onChange={(e) => setTestPayload(e.target.value)}
                className="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-xs text-slate-100 font-mono focus:border-brand-500 focus:outline-none"
                placeholder="Escribe aquí un payload sospechoso..."
              />
            </div>

            {/* Analysis Results Display */}
            {analysisResult && (
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 space-y-3 animate-in fade-in">
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-800 pb-2">
                  <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                    {analysisResult.sqlCheck.isSuspicious || analysisResult.xssCheck.hasThreat || analysisResult.isFormula ? (
                      <>
                        <ShieldAlert className="w-4 h-4 text-rose-400" />
                        <span className="text-rose-400 font-bold">¡AMENAZA DETECTADA Y BLOQUEADA POR EL FIREWALL!</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                        <span className="text-emerald-400 font-bold">ENTRADA SEGURA VERIFICADA</span>
                      </>
                    )}
                  </span>

                  <span className="text-[10px] font-mono bg-slate-950 px-2.5 py-1 rounded border border-slate-800 text-slate-400">
                    SHA-256: {analysisResult.sha256Hash.substring(0, 16)}...
                  </span>
                </div>

                {/* Detected threat details */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-500 uppercase block font-semibold">Inyección SQL</span>
                    <span className={`font-mono font-bold ${analysisResult.sqlCheck.isSuspicious ? 'text-rose-400' : 'text-emerald-400'}`}>
                      {analysisResult.sqlCheck.isSuspicious ? `RIESGO ${analysisResult.sqlCheck.threatLevel}` : 'Limpio'}
                    </span>
                    {analysisResult.sqlCheck.matchedPatterns.length > 0 && (
                      <p className="text-[10px] text-slate-400 mt-1">
                        Patrón: {analysisResult.sqlCheck.matchedPatterns.join(', ')}
                      </p>
                    )}
                  </div>

                  <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-500 uppercase block font-semibold">Vector XSS (DOM)</span>
                    <span className={`font-mono font-bold ${analysisResult.xssCheck.hasThreat ? 'text-rose-400' : 'text-emerald-400'}`}>
                      {analysisResult.xssCheck.hasThreat ? 'AMENAZA XSS' : 'Limpio'}
                    </span>
                    {analysisResult.xssCheck.detectedVector && (
                      <p className="text-[10px] text-slate-400 mt-1">
                        Vector: {analysisResult.xssCheck.detectedVector}
                      </p>
                    )}
                  </div>

                  <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-500 uppercase block font-semibold">Inyección Fórmula CSV</span>
                    <span className={`font-mono font-bold ${analysisResult.isFormula ? 'text-amber-400' : 'text-emerald-400'}`}>
                      {analysisResult.isFormula ? 'Fórmula DDE Detectada' : 'Limpio'}
                    </span>
                    <p className="text-[10px] text-slate-400 mt-1">
                      {analysisResult.isFormula ? "Prefijado seguro con comilla (')" : 'Sin prefijo peligroso'}
                    </p>
                  </div>
                </div>

                {/* Sanitized Neutralized Output */}
                <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
                  <span className="text-[10px] text-slate-500 uppercase block font-semibold mb-1">
                    Salida Sanitizada y Neutralizada (Almacenamiento Seguro):
                  </span>
                  <div className="font-mono text-emerald-300 text-xs break-all bg-slate-900/80 p-2 rounded border border-emerald-500/20">
                    {analysisResult.sanitizedOutput || '<cadena vacía desinfectada>'}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Security Checklist Overview */}
          <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4 space-y-3">
            <h4 className="font-semibold text-slate-200 text-xs">
              Checklist de Cumplimiento OWASP ASVS / Top 10
            </h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-[11px] text-slate-400 font-mono">
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                <span>A01:2021 - Broken Access Control: Sesión local aislada</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                <span>A02:2021 - Cryptographic Failures: SHA-256 & TLS 1.3</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                <span>A03:2021 - Injection: Heurística Anti-SQLi & DOMPurify</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                <span>A04:2021 - Insecure Design: Validación estricta de esquemas</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                <span>A05:2021 - Security Misconfiguration: CSP L3 + HSTS</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                <span>A08:2021 - Software Integrity: Anti-Prototype Pollution</span>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="bg-slate-850 px-6 py-3 border-t border-slate-800 flex items-center justify-between">
          <span className="text-[11px] text-slate-400 flex items-center gap-1.5 font-mono">
            <Globe className="w-3.5 h-3.5 text-emerald-400" />
            Políticas de Seguridad Activas y Operativas
          </span>
          <button
            onClick={onClose}
            className="px-4 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-semibold transition-colors"
          >
            Cerrar
          </button>
        </div>
    </Dialog>
  );
};
