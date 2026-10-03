import React, { useEffect, useMemo, useState } from 'react';
import {
  X,
  FileCode2,
  Ruler,
  Layers,
  Download,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  Server,
  FileImage,
  Info,
  Loader2,
  Copy,
  Printer
} from 'lucide-react';
import { CalculationSummary, ProjectConfig, Room } from '../types';
import {
  CadUnits,
  DxfVersion,
  DEFAULT_PLAN_OPTIONS,
  PAPER_SIZES,
  PLOT_SCALES,
  PaperSize,
  PlanOptions,
  buildCadScene,
  getConverterStatus,
  convertDxf,
  sceneStats,
  sceneToDxf,
  exportSceneToSvg,
  sheetToSvg,
  downloadText,
  slugify,
  ConverterStatus
} from '../lib/cad';
import { formatBytes, downloadDxfOnServer } from '../lib/cad/download';

interface CadExportModalProps {
  isOpen: boolean;
  onClose: () => void;
  rooms: Room[];
  config: ProjectConfig;
  summary: CalculationSummary;
}

const VERSIONS: Array<{ id: DxfVersion; label: string; hint: string }> = [
  { id: 'R12', label: 'DXF R12 (AC1009)', hint: 'Máxima compatibilidad: LibreCAD, QCAD, DraftSight, CNC y láser.' },
  { id: 'R2000', label: 'DXF R2000 (AC1015)', hint: 'Recomendado para AutoCAD 2000–2026, BricsCAD, ZWCAD, GstarCAD.' },
  { id: 'R2007', label: 'DXF R2007 (AC1021)', hint: 'Formato moderno con soporte completo de Unicode.' }
];

const UNITS: Array<{ id: CadUnits; label: string }> = [
  { id: 'mm', label: 'Milímetros (mm)' },
  { id: 'cm', label: 'Centímetros (cm)' },
  { id: 'm', label: 'Metros (m)' },
  { id: 'in', label: 'Pulgadas (in)' },
  { id: 'ft', label: 'Pies (ft)' }
];

const LAYER_TOGGLES: Array<{ key: keyof PlanOptions; label: string; hint: string }> = [
  { key: 'includeFraming', label: 'Perfilería', hint: 'Parantes, canales, omegas y perfiles primarios' },
  { key: 'includeBoards', label: 'Modulación de placas', hint: 'Replanteo de placas con junta alternada' },
  { key: 'includeInsulation', label: 'Aislamiento', hint: 'Trama de lana mineral en las cavidades' },
  { key: 'includeOpenings', label: 'Aberturas', hint: 'Puertas, ventanas y refuerzos de dintel y jambas' },
  { key: 'includeFasteners', label: 'Fijaciones', hint: 'Patrón de tornillería (aumenta el tamaño del archivo)' },
  { key: 'includeDimensions', label: 'Cotas', hint: 'Cadenas de cotas y textos de medida' },
  { key: 'includeAxis', label: 'Ejes de replanteo', hint: 'Ejes y burbujas de referencia' },
  { key: 'includeLegend', label: 'Leyenda de capas', hint: 'Cuadro explicativo de capas en la primera lámina' },
  { key: 'includeBom', label: 'Cómputo de materiales', hint: 'Láminas finales con el cuadro de materiales y totales' },
  { key: 'includeTitleBlock', label: 'Cajetín', hint: 'Marco normalizado y cajetín con datos del proyecto' }
];

export const CadExportModal: React.FC<CadExportModalProps> = ({ isOpen, onClose, rooms, config, summary }) => {
  const [options, setOptions] = useState<PlanOptions>(() => ({
    ...DEFAULT_PLAN_OPTIONS,
    scaleDenominator: config.unitSystem === 'imperial' ? 10 : 50,
    units: config.unitSystem === 'imperial' ? 'in' : 'mm'
  }));
  const [version, setVersion] = useState<DxfVersion>('R2000');
  const [activeSheet, setActiveSheet] = useState(0);
  const [converter, setConverter] = useState<ConverterStatus | null>(null);
  const [checkingConverter, setCheckingConverter] = useState(false);
  const [converting, setConverting] = useState(false);
  const [message, setMessage] = useState<{ kind: 'ok' | 'warn' | 'error'; text: string } | null>(null);

  const scene = useMemo(() => {
    if (!isOpen) return null;
    try {
      return buildCadScene(rooms, config, summary, options);
    } catch (error) {
      console.error(error);
      return null;
    }
  }, [isOpen, rooms, config, summary, options]);

  const stats = useMemo(() => (scene ? sceneStats(scene) : null), [scene]);

  const previewSvg = useMemo(() => {
    if (!scene || !scene.sheets.length) return '';
    const sheet = scene.sheets[Math.min(activeSheet, scene.sheets.length - 1)];
    try {
      return sheetToSvg(sheet, scene, { width: 1100 });
    } catch {
      return '';
    }
  }, [scene, activeSheet]);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    setCheckingConverter(true);
    getConverterStatus()
      .then((status) => {
        if (!cancelled) setConverter(status);
      })
      .finally(() => {
        if (!cancelled) setCheckingConverter(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) setMessage(null);
  }, [isOpen]);

  if (!isOpen) return null;

  const update = <K extends keyof PlanOptions>(key: K, value: PlanOptions[K]): void => {
    setOptions((prev) => ({ ...prev, [key]: value }));
  };

  const handleExportDxf = (sheetIndex: number | null): void => {
    if (!scene) return;
    const dxf = sceneToDxf(scene, { version, units: options.units, sheetIndex });
    const tag = sheetIndex === null ? 'completo' : `lamina-${sheetIndex + 1}`;
    downloadText(
      `${slugify(config.projectName)}_${tag}_1-${options.scaleDenominator}.dxf`,
      dxf,
      'application/dxf'
    );
    setMessage({
      kind: 'ok',
      text: `DXF ${version} generado (${formatBytes(dxf.length)}). Ábrelo en AutoCAD y guárdalo como DWG si lo necesitas.`
    });
  };

  const handleExportSvg = (): void => {
    if (!scene) return;
    exportSceneToSvg(scene, activeSheet);
    setMessage({ kind: 'ok', text: 'Lámina exportada a SVG vectorial.' });
  };

  const handleConvert = async (): Promise<void> => {
    if (!scene) return;
    setConverting(true);
    setMessage(null);
    try {
      const dxf = sceneToDxf(scene, { version: 'R2000', units: options.units, sheetIndex: null });
      const result = await convertDxf(dxf, slugify(config.projectName), 'dwg');
      if (result.ok) {
        setMessage({ kind: 'ok', text: `Conversión completada: ${result.filename}` });
      } else {
        setMessage({
          kind: 'warn',
          text: `${result.error ?? 'No se pudo convertir'}${result.hint ? ` — ${result.hint}` : ''}`
        });
      }
    } finally {
      setConverting(false);
    }
  };

  const totalSheets = scene?.sheets.length ?? 0;
  const currentSheet = scene?.sheets[Math.min(activeSheet, Math.max(0, totalSheets - 1))];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-3 sm:p-6">
      <div className="w-full max-w-6xl max-h-[92vh] overflow-hidden bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl flex flex-col">
        {/* Cabecera */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800 bg-slate-900/95">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-sky-600 to-cyan-400 flex items-center justify-center text-white shadow-lg shadow-sky-500/20">
              <FileCode2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">Planos vectoriales para AutoCAD (DXF / DWG)</h2>
              <p className="text-xs text-slate-400">
                Alzados de tabique, plantas de cielo raso y plafones con capas normalizadas, cotas y cajetín profesional.
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

        <div className="flex-1 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 gap-0">
          {/* Panel de configuración */}
          <div className="lg:col-span-4 border-r border-slate-800 p-5 space-y-5 overflow-y-auto">
            {/* Láminas */}
            <section className="space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Layers className="w-3.5 h-3.5 text-sky-400" /> Láminas generadas ({totalSheets})
              </h3>
              <div className="grid grid-cols-2 gap-1.5 max-h-40 overflow-y-auto pr-1">
                {scene?.sheets.map((sheet, i) => (
                  <button
                    key={sheet.name}
                    onClick={() => setActiveSheet(i)}
                    className={`px-2 py-1.5 rounded-lg text-[11px] font-mono text-left transition-all border ${
                      i === activeSheet
                        ? 'bg-sky-500/15 border-sky-500/40 text-sky-300'
                        : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-slate-200'
                    }`}
                    title={sheet.title}
                  >
                    <span className="block font-bold">{sheet.name}</span>
                    <span className="block truncate text-[10px] text-slate-500">{sheet.title}</span>
                  </button>
                ))}
              </div>
            </section>

            {/* Formato */}
            <section className="space-y-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Ruler className="w-3.5 h-3.5 text-emerald-400" /> Formato y escala
              </h3>
              <div className="grid grid-cols-2 gap-2">
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Tamaño de lámina</span>
                  <select
                    value={options.paper}
                    onChange={(e) => update('paper', e.target.value as PaperSize)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2 py-1.5 text-xs text-slate-200"
                  >
                    {Object.entries(PAPER_SIZES).map(([id, p]) => (
                      <option key={id} value={id}>
                        {p.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Escala de trazado</span>
                  <select
                    value={options.scaleDenominator}
                    onChange={(e) => update('scaleDenominator', Number(e.target.value))}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2 py-1.5 text-xs text-slate-200"
                  >
                    {PLOT_SCALES.map((s) => (
                      <option key={s} value={s}>
                        1:{s}
                        {s === 50 ? ' (recomendada)' : ''}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Unidades del dibujo</span>
                  <select
                    value={options.units}
                    onChange={(e) => update('units', e.target.value as CadUnits)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2 py-1.5 text-xs text-slate-200"
                  >
                    {UNITS.map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Composición</span>
                  <select
                    value={options.layout}
                    onChange={(e) => update('layout', e.target.value as PlanOptions['layout'])}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-2 py-1.5 text-xs text-slate-200"
                  >
                    <option value="tiled">Varias estancias por lámina</option>
                    <option value="per-room">Una estancia por lámina</option>
                  </select>
                </label>
              </div>

              <div className="space-y-1">
                <span className="text-[11px] text-slate-400">Versión DXF</span>
                {VERSIONS.map((v) => (
                  <label
                    key={v.id}
                    className={`flex items-start gap-2 px-2.5 py-2 rounded-lg border cursor-pointer transition-all ${
                      version === v.id
                        ? 'bg-sky-500/10 border-sky-500/40'
                        : 'bg-slate-950 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    <input
                      type="radio"
                      name="dxf-version"
                      checked={version === v.id}
                      onChange={() => setVersion(v.id)}
                      className="mt-0.5 accent-sky-500"
                    />
                    <span>
                      <span className="block text-xs text-slate-200 font-medium">{v.label}</span>
                      <span className="block text-[10px] text-slate-500">{v.hint}</span>
                    </span>
                  </label>
                ))}
              </div>
            </section>

            {/* Capas */}
            <section className="space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Capas a incluir</h3>
              <div className="space-y-1">
                {LAYER_TOGGLES.map((toggle) => (
                  <label
                    key={toggle.key as string}
                    className="flex items-center justify-between gap-2 px-2 py-1.5 rounded-lg bg-slate-950 border border-slate-800 hover:border-slate-700 cursor-pointer"
                    title={toggle.hint}
                  >
                    <span className="text-xs text-slate-300">{toggle.label}</span>
                    <input
                      type="checkbox"
                      checked={Boolean(options[toggle.key])}
                      onChange={(e) => update(toggle.key, e.target.checked as never)}
                      className="accent-emerald-500"
                    />
                  </label>
                ))}
              </div>
            </section>

            {/* Estadísticas */}
            {stats && (
              <section className="rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-1.5">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Estadísticas del plano</h3>
                <div className="grid grid-cols-2 gap-2 text-[11px] font-mono">
                  <span className="text-slate-500">Láminas</span>
                  <span className="text-slate-200 text-right">{stats.sheets}</span>
                  <span className="text-slate-500">Entidades CAD</span>
                  <span className="text-slate-200 text-right">{stats.entities.toLocaleString()}</span>
                  <span className="text-slate-500">Capas</span>
                  <span className="text-slate-200 text-right">{stats.layers}</span>
                  <span className="text-slate-500">Tamaño DXF est.</span>
                  <span className="text-slate-200 text-right">{formatBytes(stats.estimatedBytes)}</span>
                </div>
              </section>
            )}
          </div>

          {/* Vista previa + acciones */}
          <div className="lg:col-span-8 flex flex-col">
            <div className="flex-1 bg-slate-950 p-4 min-h-[320px] flex flex-col">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-mono text-slate-400">
                  {currentSheet ? `${currentSheet.name} — ${currentSheet.title}` : 'Sin láminas'} · Escala 1:
                  {options.scaleDenominator} · {PAPER_SIZES[options.paper].label}
                </span>
                <button
                  onClick={() => scene && setActiveSheet(0)}
                  className="text-[11px] text-slate-400 hover:text-sky-300 flex items-center gap-1"
                >
                  <RefreshCw className="w-3 h-3" /> Recargar vista
                </button>
              </div>
              <div className="flex-1 rounded-xl border border-slate-800 bg-slate-900 overflow-auto">
                {previewSvg ? (
                  <img
                    src={`data:image/svg+xml;charset=utf-8,${encodeURIComponent(previewSvg)}`}
                    alt={`Vista previa de la lámina ${currentSheet?.name ?? ''}`}
                    className="w-full h-auto"
                  />
                ) : (
                  <div className="h-full flex items-center justify-center text-slate-500 text-xs p-8 text-center">
                    Añade al menos una estancia con medidas para generar los planos.
                  </div>
                )}
              </div>
            </div>

            {/* Barra de acciones */}
            <div className="border-t border-slate-800 p-4 space-y-3 bg-slate-900/95">
              {message && (
                <div
                  className={`flex items-start gap-2 rounded-lg px-3 py-2 text-xs border ${
                    message.kind === 'ok'
                      ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                      : message.kind === 'warn'
                        ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
                        : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                  }`}
                >
                  {message.kind === 'ok' ? (
                    <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" />
                  ) : (
                    <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                  )}
                  <span>{message.text}</span>
                </div>
              )}

              <div className="flex flex-wrap items-center gap-2">
                <button
                  onClick={() => handleExportDxf(null)}
                  disabled={!scene || !totalSheets}
                  className="px-3.5 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-semibold flex items-center gap-2 shadow-lg shadow-sky-900/40"
                >
                  <Download className="w-3.5 h-3.5" /> Descargar DXF completo ({totalSheets} láminas)
                </button>
                <button
                  onClick={() => handleExportDxf(activeSheet)}
                  disabled={!scene || !totalSheets}
                  className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 text-xs font-semibold flex items-center gap-2 border border-slate-700"
                >
                  <FileCode2 className="w-3.5 h-3.5 text-sky-400" /> Sólo la lámina activa
                </button>
                <button
                  onClick={handleExportSvg}
                  disabled={!scene || !totalSheets}
                  className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 text-xs font-semibold flex items-center gap-2 border border-slate-700"
                >
                  <FileImage className="w-3.5 h-3.5 text-fuchsia-400" /> SVG vectorial
                </button>
                <a
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    if (scene) downloadDxfOnServer(scene, { version: 'R2000', units: options.units }, config);
                  }}
                  className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-2 border border-slate-700"
                  title="Descarga el DXF ya escalado y listo para imprimir desde el servidor"
                >
                  <Printer className="w-3.5 h-3.5 text-amber-400" /> Cola de impresión
                </a>
              </div>

              {/* Bloque DWG */}
              <div className="rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Server className="w-4 h-4 text-violet-400" />
                    <span className="text-xs font-semibold text-slate-200">Conversión a DWG nativo</span>
                  </div>
                  {checkingConverter ? (
                    <span className="text-[11px] text-slate-400 flex items-center gap-1">
                      <Loader2 className="w-3 h-3 animate-spin" /> Comprobando…
                    </span>
                  ) : (
                    <span
                      className={`text-[11px] font-mono px-2 py-0.5 rounded ${
                        converter?.available
                          ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/30'
                          : 'bg-amber-500/10 text-amber-300 border border-amber-500/30'
                      }`}
                    >
                      {converter?.available ? `Disponible (${converter.provider})` : 'No disponible'}
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-slate-400 leading-relaxed">
                  {converter?.message ??
                    'El formato .dwg es propietario de Autodesk. DrywallPro exporta DXF (abierto y 100 % editable en AutoCAD) y, si el servidor tiene ODA File Converter instalado, también genera el .dwg nativo.'}
                </p>
                <div className="flex flex-wrap gap-2">
                  <button
                    onClick={() => void handleConvert()}
                    disabled={!converter?.available || converting || !scene}
                    className="px-3 py-1.5 rounded-lg bg-violet-600 hover:bg-violet-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-[11px] font-semibold flex items-center gap-2"
                  >
                    {converting ? <Loader2 className="w-3 h-3 animate-spin" /> : <Download className="w-3 h-3" />}
                    Generar DWG
                  </button>
                  <button
                    onClick={() => {
                      if (!scene) return;
                      const dxf = sceneToDxf(scene, { version: 'R2000', units: options.units, sheetIndex: null });
                      void navigator.clipboard?.writeText(dxf).then(
                        () => setMessage({ kind: 'ok', text: 'Contenido DXF copiado al portapapeles.' }),
                        () => setMessage({ kind: 'warn', text: 'El navegador bloqueó el acceso al portapapeles.' })
                      );
                    }}
                    disabled={!scene}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-300 text-[11px] font-semibold flex items-center gap-2 border border-slate-700"
                  >
                    <Copy className="w-3 h-3" /> Copiar DXF
                  </button>
                </div>
              </div>

              <div className="flex items-start gap-2 text-[11px] text-slate-500">
                <Info className="w-3.5 h-3.5 mt-0.5 shrink-0 text-slate-500" />
                <span>
                  El archivo se entrega en espacio modelo con las capas <code className="text-slate-400">DW-*</code>,
                  tipos de línea CENTER/DASHED, estilos de texto SHX y bloque de cajetín. En AutoCAD, BricsCAD o
                  ZWCAD puedes cambiar el grosor de línea por capa (CTB/STB) e imprimir directamente.
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
