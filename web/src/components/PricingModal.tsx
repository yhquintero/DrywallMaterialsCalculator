import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  X,
  RefreshCw,
  Plus,
  Trash2,
  Pencil,
  CheckCircle2,
  AlertTriangle,
  TrendingDown,
  TrendingUp,
  Server,
  Store,
  Loader2,
  Download,
  Upload,
  Settings2,
  PlugZap,
  Layers,
  Minus
} from 'lucide-react';
import { CalculationSummary, ProjectConfig } from '../types';
import {
  DEFAULT_PRICING_SETTINGS,
  PricingClient,
  PricingSettings,
  SUPPLIER_TEMPLATES,
  SupplierConfig,
  SyncReport,
  buildMaterialQueries,
  reportToCsv,
  validateSupplier
} from '../lib/pricing';
import { sanitizeCsvCell } from '../security/csvSanitizer';
import { formatCurrency } from '../lib/calculator';

interface PricingModalProps {
  isOpen: boolean;
  onClose: () => void;
  config: ProjectConfig;
  summary: CalculationSummary;
  onApplyPrices: (prices: Record<string, number>) => void;
  onUpdateSettings?: (settings: Partial<ProjectConfig>) => void;
}

const currency = (value: number, code: string): string => formatCurrency(value, code);

export const PricingModal: React.FC<PricingModalProps> = ({
  isOpen,
  onClose,
  config,
  summary,
  onApplyPrices
}) => {
  const clientRef = useRef<PricingClient | null>(null);
  if (!clientRef.current) clientRef.current = new PricingClient();
  const client = clientRef.current;

  const [suppliers, setSuppliers] = useState<SupplierConfig[]>(() => client.getSuppliers());
  const [settings, setSettings] = useState<PricingSettings>(() => client.getSettings());
  const [report, setReport] = useState<SyncReport | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [progress, setProgress] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<SupplierConfig | null>(null);
  const [testing, setTesting] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<{ kind: 'ok' | 'warn' | 'error'; text: string } | null>(null);

  const materials = useMemo(() => buildMaterialQueries(summary, config), [summary, config]);

  useEffect(() => {
    if (!isOpen) return;
    const unsubscribe = client.subscribe((event) => {
      if (event.type === 'progress') setProgress(`${event.supplier}: ${event.message}`);
      if (event.type === 'report') setReport(event.report);
      if (event.type === 'error') setError(event.message);
      if (event.type === 'suppliers') setSuppliers(event.suppliers);
    });
    return unsubscribe;
  }, [isOpen, client]);

  useEffect(() => {
    if (!isOpen) {
      setReport(null);
      setProgress('');
      setError(null);
      setStatusMessage(null);
      setEditing(null);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const enabledSuppliers = suppliers.filter((s) => s.enabled);
  const matchedItems = report?.items.filter((i) => i.best) ?? [];
  const savings = report?.totals.potentialSavings ?? 0;

  const runSync = async (force: boolean): Promise<void> => {
    if (!enabledSuppliers.length) {
      setError('Activa al menos un proveedor antes de sincronizar.');
      return;
    }
    setSyncing(true);
    setError(null);
    setProgress('Preparando consulta…');
    try {
      const result = await client.sync(materials, config.currency, { force });
      setReport(result);
      setStatusMessage(
        result.totals.matched > 0
          ? {
              kind: 'ok',
              text: `${result.totals.matched} de ${result.totals.materials} materiales con precio en ${result.durationMs} ms.`
            }
          : { kind: 'warn', text: 'Ningún material pudo emparejarse con el catálogo. Revisa el mapeo de campos.' }
      );
      if (settings.autoApply && result.totals.matched > 0) {
        applyBestPrices(result);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error de sincronización');
    } finally {
      setSyncing(false);
      setProgress('');
    }
  };

  const applyBestPrices = (source?: SyncReport): void => {
    const current = source ?? report;
    if (!current) return;
    const prices: Record<string, number> = {};
    current.items.forEach((item) => {
      if (!item.best) return;
      if (settings.ignoreOutOfStock && item.best.stock === 0) return;
      if (Math.abs(item.deltaPct) < settings.autoApplyThresholdPct && item.best.source !== 'manual') return;
      prices[item.materialName] = Number(item.best.price.toFixed(2));
    });
    const count = Object.keys(prices).length;
    if (!count) {
      setStatusMessage({ kind: 'warn', text: 'Ningún precio supera el umbral configurado.' });
      return;
    }
    onApplyPrices(prices);
    setStatusMessage({ kind: 'ok', text: `${count} precios aplicados al presupuesto.` });
  };

  const saveSupplier = (supplier: SupplierConfig): void => {
    const errors = validateSupplier(supplier);
    if (errors.length) {
      setStatusMessage({ kind: 'error', text: errors.join(' ') });
      return;
    }
    client.upsertSupplier(supplier);
    setSuppliers(client.getSuppliers());
    setEditing(null);
    setStatusMessage({ kind: 'ok', text: `Proveedor «${supplier.name}» guardado.` });
  };

  const testSupplier = async (supplier: SupplierConfig): Promise<void> => {
    setTesting(supplier.id);
    setStatusMessage(null);
    try {
      const result = await client.testSupplier(supplier, materials, config.currency);
      setStatusMessage({ kind: result.ok ? 'ok' : 'error', text: result.message });
    } finally {
      setTesting(null);
    }
  };

  const importCsvFile = (file: File): void => {
    const reader = new FileReader();
    reader.onload = () => {
      const supplier: SupplierConfig = {
        id: 'importado',
        name: 'Lista importada (manual)',
        kind: 'manual',
        enabled: true,
        currency: config.currency,
        method: 'GET',
        timeoutMs: 5000,
        ttlMinutes: 1440,
        priceAdjustmentPct: 0,
        matchThreshold: 0.38
      };
      const result = client.importCsv(supplier, String(reader.result ?? ''), materials, config.currency);
      setReport(result);
      client.upsertSupplier(supplier);
      setStatusMessage({
        kind: result.totals.matched ? 'ok' : 'warn',
        text: `Lista importada: ${result.totals.matched} materiales emparejados de ${result.totals.materials}.`
      });
    };
    reader.readAsText(file, 'utf-8');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/85 backdrop-blur-sm p-2 sm:p-6">
      <div className="w-full max-w-6xl max-h-[94vh] bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl flex flex-col overflow-hidden">
        {/* Cabecera */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-amber-500 to-orange-400 flex items-center justify-center text-white shadow-lg shadow-amber-500/20">
              <Store className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">Precios en tiempo real de distribuidores</h2>
              <p className="text-xs text-slate-400">
                Conecta catálogos de proveedores, compara precios y actualiza el presupuesto con un clic.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            aria-label="Cerrar"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto grid grid-cols-1 lg:grid-cols-12">
          {/* Proveedores */}
          <div className="lg:col-span-5 border-r border-slate-800 p-4 space-y-4 overflow-y-auto">
            <section className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                  <PlugZap className="w-3.5 h-3.5 text-amber-400" /> Proveedores ({enabledSuppliers.length} activos)
                </h3>
                <button
                  onClick={() =>
                    setEditing({
                      id: `proveedor-${Date.now().toString(36)}`,
                      name: '',
                      kind: 'rest-json',
                      enabled: true,
                      currency: config.currency,
                      method: 'GET',
                      endpoint: '',
                      timeoutMs: 8000,
                      ttlMinutes: 120,
                      priceAdjustmentPct: 0,
                      matchThreshold: 0.4,
                      auth: { mode: 'server-proxy', headerName: 'X-API-Key' }
                    })
                  }
                  className="text-[11px] text-amber-300 hover:text-amber-200 flex items-center gap-1"
                >
                  <Plus className="w-3 h-3" /> Añadir proveedor
                </button>
              </div>

              <div className="space-y-2">
                {suppliers.map((supplier) => (
                  <div
                    key={supplier.id}
                    className="rounded-xl bg-slate-950 border border-slate-800 p-2.5 space-y-2"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold text-slate-200 truncate">{supplier.name}</span>
                          <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 uppercase">
                            {supplier.kind}
                          </span>
                        </div>
                        <p className="text-[10px] text-slate-500 truncate font-mono">
                          {supplier.endpoint || supplier.urlTemplate || 'proveedor simulado'}
                        </p>
                        <p className="text-[10px] text-slate-500">
                          {supplier.currency} · TTL {supplier.ttlMinutes} min · umbral{' '}
                          {Math.round(supplier.matchThreshold * 100)}% ·{' '}
                          {supplier.priceAdjustmentPct === 0
                            ? 'sin ajuste'
                            : `${supplier.priceAdjustmentPct > 0 ? '+' : ''}${supplier.priceAdjustmentPct}%`}
                        </p>
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        <label className="cursor-pointer" title="Activar / desactivar">
                          <input
                            type="checkbox"
                            checked={supplier.enabled}
                            onChange={(e) =>
                              saveSupplier({ ...supplier, enabled: e.target.checked })
                            }
                            className="accent-emerald-500"
                          />
                        </label>
                        <button
                          onClick={() => void testSupplier(supplier)}
                          className="p-1.5 rounded-md text-slate-400 hover:text-sky-300 hover:bg-slate-800"
                          title="Probar conexión"
                        >
                          {testing === supplier.id ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          ) : (
                            <RefreshCw className="w-3.5 h-3.5" />
                          )}
                        </button>
                        <button
                          onClick={() => setEditing(supplier)}
                          className="p-1.5 rounded-md text-slate-400 hover:text-amber-300 hover:bg-slate-800"
                          title="Editar"
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => {
                            client.removeSupplier(supplier.id);
                            setSuppliers(client.getSuppliers());
                          }}
                          className="p-1.5 rounded-md text-slate-400 hover:text-rose-400 hover:bg-slate-800"
                          title="Eliminar"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {/* Configuración global */}
            <section className="space-y-2 rounded-xl bg-slate-950 border border-slate-800 p-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Settings2 className="w-3.5 h-3.5 text-slate-400" /> Política de actualización
              </h3>
              {(
                [
                  ['useServerProxy', 'Usar el proxy del servidor (oculta claves y evita CORS)'],
                  ['autoSync', 'Sincronizar al abrir el módulo'],
                  ['autoApply', 'Aplicar precios automáticamente al sincronizar'],
                  ['ignoreOutOfStock', 'Ignorar ofertas sin existencias']
                ] as Array<[keyof PricingSettings, string]>
              ).map(([key, label]) => (
                <label key={key as string} className="flex items-center justify-between gap-2 text-[11px] text-slate-300">
                  <span>{label}</span>
                  <input
                    type="checkbox"
                    checked={Boolean(settings[key])}
                    onChange={(e) => {
                      const next = client.updateSettings({ [key]: e.target.checked } as Partial<PricingSettings>);
                      setSettings(next);
                    }}
                    className="accent-emerald-500"
                  />
                </label>
              ))}
              <div className="grid grid-cols-2 gap-2 pt-1">
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Umbral de auto-aplicación (%)</span>
                  <input
                    type="number"
                    min={0}
                    step={0.5}
                    value={settings.autoApplyThresholdPct}
                    onChange={(e) => {
                      const next = client.updateSettings({ autoApplyThresholdPct: Number(e.target.value) });
                      setSettings(next);
                    }}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-1.5 text-xs text-slate-200"
                  />
                </label>
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Reintentos por proveedor</span>
                  <input
                    type="number"
                    min={0}
                    max={3}
                    value={settings.retries}
                    onChange={(e) => {
                      const next = client.updateSettings({ retries: Number(e.target.value) });
                      setSettings(next);
                    }}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-1.5 text-xs text-slate-200"
                  />
                </label>
              </div>
            </section>

            {/* Importación manual */}
            <section className="space-y-2 rounded-xl bg-slate-950 border border-slate-800 p-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Upload className="w-3.5 h-3.5 text-slate-400" /> Lista de precios manual
              </h3>
              <p className="text-[10px] text-slate-500">
                Sube la tarifa en CSV/Excel exportado (columnas de descripción y precio). Se detectan el delimitador y
                los nombres de columna automáticamente.
              </p>
              <input
                type="file"
                accept=".csv,.txt,.tsv"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) importCsvFile(file);
                }}
                className="block w-full text-[11px] text-slate-400 file:mr-2 file:py-1.5 file:px-3 file:rounded-lg file:border-0 file:bg-slate-800 file:text-slate-200 file:text-[11px]"
              />
            </section>
          </div>

          {/* Resultados */}
          <div className="lg:col-span-7 flex flex-col">
            <div className="p-4 border-b border-slate-800 space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <button
                  onClick={() => void runSync(false)}
                  disabled={syncing}
                  className="px-4 py-2 rounded-lg bg-amber-600 hover:bg-amber-500 disabled:opacity-40 text-white text-xs font-semibold flex items-center gap-2"
                >
                  {syncing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
                  Sincronizar precios
                </button>
                <button
                  onClick={() => void runSync(true)}
                  disabled={syncing}
                  className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 border border-slate-700 text-slate-200 text-xs font-semibold"
                  title="Ignora la caché y vuelve a consultar todos los proveedores"
                >
                  Forzar recarga
                </button>
                <button
                  onClick={() => applyBestPrices()}
                  disabled={!report || !matchedItems.length}
                  className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-40 text-white text-xs font-semibold flex items-center gap-2"
                >
                  <CheckCircle2 className="w-3.5 h-3.5" /> Aplicar mejores precios
                </button>
                <button
                  onClick={() => {
                    if (!report) return;
                    const csv = reportToCsv(report, sanitizeCsvCell);
                    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = `comparativa_proveedores_${Date.now()}.csv`;
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    setTimeout(() => URL.revokeObjectURL(url), 2000);
                  }}
                  disabled={!report}
                  className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 border border-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-2"
                >
                  <Download className="w-3.5 h-3.5" /> Comparativa CSV
                </button>
                {progress && (
                  <span className="text-[11px] text-slate-400 flex items-center gap-1.5">
                    <Loader2 className="w-3 h-3 animate-spin" /> {progress}
                  </span>
                )}
              </div>

              {statusMessage && (
                <div
                  className={`flex items-start gap-2 rounded-lg px-3 py-2 text-[11px] border ${
                    statusMessage.kind === 'ok'
                      ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                      : statusMessage.kind === 'warn'
                        ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
                        : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                  }`}
                >
                  {statusMessage.kind === 'ok' ? (
                    <CheckCircle2 className="w-3.5 h-3.5 mt-0.5 shrink-0" />
                  ) : (
                    <AlertTriangle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
                  )}
                  <span>{statusMessage.text}</span>
                </div>
              )}
              {error && (
                <div className="flex items-start gap-2 rounded-lg px-3 py-2 text-[11px] bg-rose-500/10 border border-rose-500/30 text-rose-300">
                  <AlertTriangle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              {report && (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px] font-mono">
                  <div className="rounded-lg bg-slate-950 border border-slate-800 p-2">
                    <span className="block text-slate-500 text-[9px]">MATERIALES</span>
                    <span className="text-slate-200 font-bold">{report.totals.materials}</span>
                  </div>
                  <div className="rounded-lg bg-slate-950 border border-slate-800 p-2">
                    <span className="block text-slate-500 text-[9px]">CON PRECIO</span>
                    <span className="text-sky-400 font-bold">{report.totals.matched}</span>
                  </div>
                  <div className="rounded-lg bg-slate-950 border border-slate-800 p-2">
                    <span className="block text-slate-500 text-[9px]">ACTUALIZABLES</span>
                    <span className="text-emerald-400 font-bold">{report.totals.updated}</span>
                  </div>
                  <div className="rounded-lg bg-slate-950 border border-slate-800 p-2">
                    <span className="block text-slate-500 text-[9px]">AHORRO POTENCIAL</span>
                    <span className={`font-bold ${savings >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {currency(savings, config.currency)}
                    </span>
                  </div>
                </div>
              )}
            </div>

            {/* Tabla comparativa */}
            <div className="flex-1 overflow-auto p-4">
              {!report ? (
                <div className="h-full flex flex-col items-center justify-center text-center gap-3 py-12">
                  <Store className="w-10 h-10 text-slate-700" />
                  <p className="text-xs text-slate-400 max-w-sm">
                    Pulsa «Sincronizar precios» para consultar los catálogos de los proveedores activos. Por defecto hay
                    un proveedor de demostración para que veas el flujo completo sin configurar nada.
                  </p>
                  <p className="text-[11px] text-slate-500">
                    {materials.length} materiales del presupuesto listos para comparar · moneda {config.currency}
                  </p>
                </div>
              ) : (
                <table className="w-full text-[11px]">
                  <thead className="text-slate-400 uppercase tracking-wider text-[9px]">
                    <tr className="border-b border-slate-800">
                      <th className="text-left py-2 pr-2">Material</th>
                      <th className="text-right py-2 px-2">Actual</th>
                      <th className="text-right py-2 px-2">Mejor precio</th>
                      <th className="text-right py-2 px-2">Diferencia</th>
                      <th className="text-left py-2 px-2">Proveedor</th>
                      <th className="text-right py-2 pl-2">Stock</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.items.map((item) => {
                      const cheaper = item.delta < -0.004;
                      const dearer = item.delta > 0.004;
                      return (
                        <tr key={item.materialName} className="border-b border-slate-900 hover:bg-slate-950/60">
                          <td className="py-2 pr-2 text-slate-200 max-w-[240px] truncate" title={item.materialName}>
                            {item.materialName}
                            {item.best && item.best.source === 'cache' && (
                              <span className="ml-1.5 text-[9px] font-mono text-amber-400">caché</span>
                            )}
                          </td>
                          <td className="py-2 px-2 text-right font-mono text-slate-400">
                            {currency(item.currentPrice, config.currency)}
                          </td>
                          <td className="py-2 px-2 text-right font-mono text-slate-100">
                            {item.best ? currency(item.best.price, config.currency) : '—'}
                          </td>
                          <td className="py-2 px-2 text-right font-mono">
                            {item.best ? (
                              <span
                                className={`inline-flex items-center gap-1 ${
                                  cheaper ? 'text-emerald-400' : dearer ? 'text-rose-400' : 'text-slate-400'
                                }`}
                              >
                                {cheaper ? (
                                  <TrendingDown className="w-3 h-3" />
                                ) : dearer ? (
                                  <TrendingUp className="w-3 h-3" />
                                ) : (
                                  <Minus className="w-3 h-3" />
                                )}
                                {item.deltaPct.toFixed(1)}%
                              </span>
                            ) : (
                              <span className="text-slate-600">sin datos</span>
                            )}
                          </td>
                          <td className="py-2 px-2 text-slate-400 max-w-[180px] truncate" title={item.best?.matchedName}>
                            {item.best?.supplierName ?? '—'}
                            {item.best && (
                              <span className="block text-[9px] text-slate-500">
                                {Math.round(item.best.confidence * 100)}% coincidencia
                              </span>
                            )}
                          </td>
                          <td className="py-2 pl-2 text-right font-mono text-slate-400">
                            {item.best?.stock === null || item.best?.stock === undefined
                              ? '—'
                              : item.best.stock === 0
                                ? <span className="text-rose-400">agotado</span>
                                : item.best.stock}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}

              {report?.errors.length ? (
                <div className="mt-4 space-y-1">
                  {report.errors.map((err) => (
                    <div
                      key={`${err.supplierId}-${err.message}`}
                      className="text-[11px] text-amber-300 bg-amber-500/10 border border-amber-500/25 rounded-lg px-3 py-2"
                    >
                      <strong>{err.supplierName}:</strong> {err.message}
                      {!err.retriable && ' (requiere revisar la configuración)'}
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          </div>
        </div>

        {/* Editor de proveedor */}
        {editing && (
          <SupplierEditor
            supplier={editing}
            config={config}
            onCancel={() => setEditing(null)}
            onSave={saveSupplier}
          />
        )}
      </div>
    </div>
  );
};

// ── Editor de proveedor ──────────────────────────────────────────────────────

interface SupplierEditorProps {
  supplier: SupplierConfig;
  config: ProjectConfig;
  onCancel: () => void;
  onSave: (supplier: SupplierConfig) => void;
}

const SupplierEditor: React.FC<SupplierEditorProps> = ({ supplier, config, onCancel, onSave }) => {
  const [draft, setDraft] = useState<SupplierConfig>(supplier);
  const update = <K extends keyof SupplierConfig>(key: K, value: SupplierConfig[K]): void =>
    setDraft((prev) => ({ ...prev, [key]: value }));

  const applyTemplate = (templateId: string): void => {
    const template = SUPPLIER_TEMPLATES.find((t) => t.id === templateId);
    if (!template) return;
    setDraft((prev) => ({ ...prev, ...template.config, id: prev.id, name: prev.name || template.title }));
  };

  return (
    <div className="absolute inset-0 bg-slate-950/85 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-2xl bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl max-h-[88vh] flex flex-col overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-800 flex items-center justify-between">
          <h3 className="text-sm font-bold text-slate-100">
            {supplier.name ? `Editar «${supplier.name}»` : 'Nuevo proveedor de materiales'}
          </h3>
          <button onClick={onCancel} className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          <div className="flex flex-wrap gap-1.5">
            {SUPPLIER_TEMPLATES.map((template) => (
              <button
                key={template.id}
                onClick={() => applyTemplate(template.id)}
                title={template.description}
                className="px-2.5 py-1 rounded-lg bg-slate-950 hover:bg-slate-800 border border-slate-700 text-[10px] text-slate-300 flex items-center gap-1.5"
              >
                <Layers className="w-3 h-3 text-amber-400" /> {template.title}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <label className="text-[11px] text-slate-400 space-y-1 col-span-2">
              <span>Nombre del proveedor *</span>
              <input
                value={draft.name}
                onChange={(e) => update('name', e.target.value)}
                placeholder="Distribuidora Pladur Norte"
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
              />
            </label>
            <label className="text-[11px] text-slate-400 space-y-1">
              <span>Tipo de integración</span>
              <select
                value={draft.kind}
                onChange={(e) => update('kind', e.target.value as SupplierConfig['kind'])}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
              >
                <option value="rest-json">API REST (JSON)</option>
                <option value="csv">Lista CSV / TSV</option>
                <option value="mock">Simulado (demostración)</option>
                <option value="manual">Manual / importado</option>
              </select>
            </label>
            <label className="text-[11px] text-slate-400 space-y-1">
              <span>Moneda del catálogo</span>
              <input
                value={draft.currency}
                onChange={(e) => update('currency', e.target.value.toUpperCase().slice(0, 3))}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
              />
            </label>
            <label className="text-[11px] text-slate-400 space-y-1 col-span-2">
              <span>Endpoint del catálogo (https://…) {draft.kind === 'rest-json' || draft.kind === 'csv' ? '*' : ''}</span>
              <input
                value={draft.endpoint ?? ''}
                onChange={(e) => update('endpoint', e.target.value)}
                placeholder="https://api.proveedor.com/v1/productos"
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 font-mono"
              />
            </label>
            {draft.kind === 'rest-json' && (
              <>
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Método</span>
                  <select
                    value={draft.method}
                    onChange={(e) => update('method', e.target.value as 'GET' | 'POST')}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
                  >
                    <option value="GET">GET</option>
                    <option value="POST">POST</option>
                  </select>
                </label>
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Ruta al array de resultados</span>
                  <input
                    value={draft.responsePath ?? ''}
                    onChange={(e) => update('responsePath', e.target.value)}
                    placeholder="data.items"
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 font-mono"
                  />
                </label>
                <label className="text-[11px] text-slate-400 space-y-1 col-span-2">
                  <span>Plantilla de URL (marcadores {'{query}'} y {'{limit}'})</span>
                  <input
                    value={draft.urlTemplate ?? ''}
                    onChange={(e) => update('urlTemplate', e.target.value)}
                    placeholder="/api/products?q={query}&limit={limit}"
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 font-mono"
                  />
                </label>
                <label className="text-[11px] text-slate-400 space-y-1 col-span-2">
                  <span>Cuerpo JSON para POST (opcional)</span>
                  <textarea
                    value={draft.bodyTemplate ?? ''}
                    onChange={(e) => update('bodyTemplate', e.target.value)}
                    rows={2}
                    placeholder={'{"q":"{query}","perPage":50}'}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-[11px] text-slate-200 font-mono"
                  />
                </label>
                <div className="col-span-2 rounded-lg bg-slate-950 border border-slate-800 p-2.5 space-y-2">
                  <span className="text-[11px] text-slate-300 font-semibold">Mapeo de campos del proveedor</span>
                  <div className="grid grid-cols-2 gap-2">
                    {(
                      [
                        ['sku', 'SKU / Referencia'],
                        ['name', 'Nombre'],
                        ['price', 'Precio'],
                        ['currency', 'Moneda'],
                        ['unit', 'Unidad'],
                        ['stock', 'Stock']
                      ] as Array<[keyof NonNullable<SupplierConfig['fieldMap']>, string]>
                    ).map(([key, label]) => (
                      <label key={key as string} className="text-[10px] text-slate-400 space-y-1">
                        <span>{label}</span>
                        <input
                          value={draft.fieldMap?.[key] ?? ''}
                          onChange={(e) =>
                            update('fieldMap', { ...(draft.fieldMap ?? {}), [key]: e.target.value })
                          }
                          className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200 font-mono"
                        />
                      </label>
                    ))}
                  </div>
                </div>
              </>
            )}
            {draft.kind === 'csv' && (
              <div className="col-span-2 rounded-lg bg-slate-950 border border-slate-800 p-2.5 grid grid-cols-2 gap-2">
                <label className="text-[10px] text-slate-400 space-y-1">
                  <span>Delimitador</span>
                  <input
                    value={draft.csvOptions?.delimiter ?? ';'}
                    onChange={(e) =>
                      update('csvOptions', {
                        delimiter: e.target.value,
                        headerRow: draft.csvOptions?.headerRow ?? 1,
                        priceColumn: draft.csvOptions?.priceColumn ?? '',
                        nameColumn: draft.csvOptions?.nameColumn ?? '',
                        skuColumn: draft.csvOptions?.skuColumn ?? '',
                        unitColumn: draft.csvOptions?.unitColumn ?? '',
                        stockColumn: draft.csvOptions?.stockColumn ?? '',
                        currencyColumn: draft.csvOptions?.currencyColumn ?? ''
                      })
                    }
                    className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200 font-mono"
                  />
                </label>
                <label className="text-[10px] text-slate-400 space-y-1">
                  <span>Fila de cabecera</span>
                  <input
                    type="number"
                    min={1}
                    value={draft.csvOptions?.headerRow ?? 1}
                    onChange={(e) =>
                      update('csvOptions', { ...(draft.csvOptions ?? { delimiter: ';', headerRow: 1, priceColumn: '' }), headerRow: Number(e.target.value) })
                    }
                    className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200"
                  />
                </label>
                <label className="text-[10px] text-slate-400 space-y-1">
                  <span>Columna de precio *</span>
                  <input
                    value={draft.csvOptions?.priceColumn ?? ''}
                    onChange={(e) =>
                      update('csvOptions', { ...(draft.csvOptions ?? { delimiter: ';', headerRow: 1, priceColumn: '' }), priceColumn: e.target.value })
                    }
                    placeholder="Precio"
                    className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200"
                  />
                </label>
                <label className="text-[10px] text-slate-400 space-y-1">
                  <span>Columna de descripción</span>
                  <input
                    value={draft.csvOptions?.nameColumn ?? ''}
                    onChange={(e) =>
                      update('csvOptions', { ...(draft.csvOptions ?? { delimiter: ';', headerRow: 1, priceColumn: '' }), nameColumn: e.target.value })
                    }
                    placeholder="Descripcion"
                    className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200"
                  />
                </label>
              </div>
            )}
            <label className="text-[11px] text-slate-400 space-y-1">
              <span>Ajuste negociado (%)</span>
              <input
                type="number"
                step={0.5}
                value={draft.priceAdjustmentPct}
                onChange={(e) => update('priceAdjustmentPct', Number(e.target.value))}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
              />
            </label>
            <label className="text-[11px] text-slate-400 space-y-1">
              <span>Umbral de coincidencia (0–1)</span>
              <input
                type="number"
                step={0.05}
                min={0}
                max={1}
                value={draft.matchThreshold}
                onChange={(e) => update('matchThreshold', Number(e.target.value))}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
              />
            </label>
            <label className="text-[11px] text-slate-400 space-y-1">
              <span>Caché TTL (minutos)</span>
              <input
                type="number"
                min={0}
                value={draft.ttlMinutes}
                onChange={(e) => update('ttlMinutes', Number(e.target.value))}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
              />
            </label>
            <label className="text-[11px] text-slate-400 space-y-1">
              <span>Timeout (ms)</span>
              <input
                type="number"
                min={1000}
                max={60000}
                value={draft.timeoutMs}
                onChange={(e) => update('timeoutMs', Number(e.target.value))}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
              />
            </label>
            <div className="col-span-2 rounded-lg bg-slate-950 border border-slate-800 p-2.5 space-y-2">
              <span className="text-[11px] text-slate-300 font-semibold flex items-center gap-1.5">
                <Server className="w-3.5 h-3.5 text-emerald-400" /> Autenticación
              </span>
              <div className="grid grid-cols-2 gap-2">
                <label className="text-[10px] text-slate-400 space-y-1">
                  <span>Modo</span>
                  <select
                    value={draft.auth?.mode ?? 'server-proxy'}
                    onChange={(e) =>
                      update('auth', { ...(draft.auth ?? { mode: 'server-proxy' }), mode: e.target.value as never })
                    }
                    className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200"
                  >
                    <option value="server-proxy">Proxy del servidor (recomendado)</option>
                    <option value="header">Cabecera propia</option>
                    <option value="query">Parámetro de consulta</option>
                    <option value="none">Sin autenticación</option>
                  </select>
                </label>
                <label className="text-[10px] text-slate-400 space-y-1">
                  <span>{draft.auth?.mode === 'query' ? 'Nombre del parámetro' : 'Nombre de la cabecera'}</span>
                  <input
                    value={
                      draft.auth?.mode === 'query'
                        ? draft.auth?.queryParam ?? ''
                        : draft.auth?.headerName ?? ''
                    }
                    onChange={(e) =>
                      update(
                        'auth',
                        draft.auth?.mode === 'query'
                          ? { ...(draft.auth ?? { mode: 'query' }), queryParam: e.target.value }
                          : { ...(draft.auth ?? { mode: 'header' }), headerName: e.target.value }
                      )
                    }
                    placeholder={draft.auth?.mode === 'query' ? 'api_key' : 'X-API-Key'}
                    className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200 font-mono"
                  />
                </label>
                {draft.auth?.mode === 'server-proxy' && (
                  <label className="text-[10px] text-slate-400 space-y-1 col-span-2">
                    <span>Nombre del secreto en el servidor (variable de entorno)</span>
                    <input
                      value={draft.auth?.secretRef ?? ''}
                      onChange={(e) =>
                        update('auth', { ...(draft.auth ?? { mode: 'server-proxy' }), secretRef: e.target.value })
                      }
                      placeholder="SUPPLIER_API_KEY"
                      className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200 font-mono"
                    />
                  </label>
                )}
              </div>
              <p className="text-[10px] text-slate-500 leading-snug">
                En modo «proxy del servidor» la clave nunca llega al navegador: el backend la lee de su propia
                configuración (ver <code>server/.env.example</code>) y la inyecta en la petición.
              </p>
            </div>
          </div>
        </div>

        <div className="px-4 py-3 border-t border-slate-800 flex items-center justify-between">
          <span className="text-[10px] text-slate-500">
            Moneda del proyecto: {config.currency} · los precios se convierten y ajustan al embalaje comercial.
          </span>
          <div className="flex gap-2">
            <button
              onClick={onCancel}
              className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs"
            >
              Cancelar
            </button>
            <button
              onClick={() => onSave(draft)}
              className="px-4 py-1.5 rounded-lg bg-amber-600 hover:bg-amber-500 text-white text-xs font-semibold"
            >
              Guardar proveedor
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PricingModal;
