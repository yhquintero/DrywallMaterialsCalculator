import React, { useState, useEffect, useMemo } from 'react';
import {
  Layers,
  Calculator,
  FileText,
  DollarSign,
  Settings,
  FolderKanban,
  BookOpen,
  Package,
  Sparkles,
  Printer,
  Globe,
  Maximize2,
  CheckCircle2,
  TrendingUp,
  Download,
  Plus
} from 'lucide-react';
import { ProjectConfig, Room, UnitSystem } from './types';
import {
  DEFAULT_CONFIG,
  DEFAULT_ROOMS,
  SavedProject,
  getActiveProjectId,
  getSavedProjects,
  saveProjectToStorage,
  exportProjectToJson
} from './lib/storage';
import { calculateProjectMaterials, formatCurrency } from './lib/calculator';
import { MultiRoomManager } from './components/MultiRoomManager';
import { Visualizer2D3D } from './components/Visualizer2D3D';
import { BudgetEstimator } from './components/BudgetEstimator';
import { AdvisoryPanel } from './components/AdvisoryPanel';
import { ProjectSettingsModal } from './components/ProjectSettingsModal';
import { SavedProjectsModal } from './components/SavedProjectsModal';
import { MaterialCatalogModal } from './components/MaterialCatalogModal';
import { SecurityCenterModal } from './components/SecurityCenterModal';
/**
 * Los tres módulos profesionales se cargan en diferido: sólo se descarga su
 * código cuando el usuario abre la herramienta, de modo que el arranque de la
 * calculadora no se penaliza (los planos CAD y el motor de AR suman bastante
 * código).
 */
const CadExportModal = React.lazy(() =>
  import('./components/CadExportModal').then((m) => ({ default: m.CadExportModal }))
);
const ArScannerModal = React.lazy(() =>
  import('./components/ArScannerModal').then((m) => ({ default: m.ArScannerModal }))
);
const PricingModal = React.lazy(() =>
  import('./components/PricingModal').then((m) => ({ default: m.PricingModal }))
);

const ModuleFallback: React.FC<{ label: string }> = ({ label }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/85 backdrop-blur-sm">
    <div className="flex items-center gap-3 rounded-2xl border border-slate-700 bg-slate-900 px-5 py-4">
      <span className="w-4 h-4 rounded-full border-2 border-brand-500 border-t-transparent animate-spin" />
      <span className="text-xs text-slate-300">Cargando {label}…</span>
    </div>
  </div>
);
import { ShieldCheck, KeyRound, Lock, Unlock, DraftingCompass, ScanLine, Store, Users } from 'lucide-react';

export function App() {
  // La plataforma se sirve SIEMPRE sobre HTTPS en producción; en local el
  // indicador recuerda activar TLS (`npm run dev:https`).
  const isSecureContext =
    typeof window !== 'undefined' &&
    (window.location.protocol === 'https:' || window.isSecureContext);

  // Initialize project state from LocalStorage or defaults
  const [projectId, setProjectId] = useState<string>(() => {
    return getActiveProjectId() || `proj_${Date.now()}`;
  });

  const [config, setConfig] = useState<ProjectConfig>(() => {
    const saved = getSavedProjects().find((p) => p.id === getActiveProjectId());
    return saved ? saved.config : DEFAULT_CONFIG;
  });

  const [rooms, setRooms] = useState<Room[]>(() => {
    const saved = getSavedProjects().find((p) => p.id === getActiveProjectId());
    return saved ? saved.rooms : DEFAULT_ROOMS;
  });

  const [customPrices, setCustomPrices] = useState<Record<string, number>>(() => {
    const saved = getSavedProjects().find((p) => p.id === getActiveProjectId());
    return saved?.customPrices || {};
  });

  const [customStock, setCustomStock] = useState<Record<string, number>>(() => {
    const saved = getSavedProjects().find((p) => p.id === getActiveProjectId());
    return saved?.customStock || {};
  });

  // Selected room for visualization
  const [selectedRoomId, setSelectedRoomId] = useState<string>(rooms[0]?.id || '');

  // Navigation tab
  const [activeTab, setActiveTab] = useState<'calculator' | 'budget' | 'advisory'>('calculator');

  // Modals
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isProjectsOpen, setIsProjectsOpen] = useState(false);
  const [isCatalogOpen, setIsCatalogOpen] = useState(false);
  const [isSecurityOpen, setIsSecurityOpen] = useState(false);
  const [isCadOpen, setIsCadOpen] = useState(false);
  const [isArOpen, setIsArOpen] = useState(false);
  const [isPricingOpen, setIsPricingOpen] = useState(false);

  // Auto-save effect
  useEffect(() => {
    const projectToSave: SavedProject = {
      id: projectId,
      updatedAt: new Date().toISOString(),
      config,
      rooms,
      customPrices,
      customStock
    };
    saveProjectToStorage(projectToSave);
  }, [projectId, config, rooms, customPrices, customStock]);

  // Real-time calculation summary
  const summary = useMemo(() => {
    return calculateProjectMaterials(rooms, config, customPrices, customStock);
  }, [rooms, config, customPrices, customStock]);

  // Selected room details for visualizer
  const activeRoom = useMemo(() => {
    return rooms.find((r) => r.id === selectedRoomId) || rooms[0] || DEFAULT_ROOMS[0];
  }, [rooms, selectedRoomId]);

  const activeSegment = activeRoom?.segments[0] || {
    length: 5,
    width: 3,
    openings: []
  };

  const activeOpeningsCount = activeSegment.openings?.length || 0;

  // Handlers for custom prices and stock
  const handleUpdatePrice = (materialName: string, price: number) => {
    setCustomPrices((prev) => ({ ...prev, [materialName]: price }));
  };

  const handleUpdateStock = (materialName: string, stock: number) => {
    setCustomStock((prev) => ({ ...prev, [materialName]: stock }));
  };

  // Switch project handler
  const handleLoadProject = (project: SavedProject) => {
    setProjectId(project.id);
    setConfig(project.config);
    setRooms(project.rooms);
    setCustomPrices(project.customPrices || {});
    setCustomStock(project.customStock || {});
    setSelectedRoomId(project.rooms[0]?.id || '');
  };

  // Create new project
  const handleNewProject = () => {
    const newId = `proj_${Date.now()}`;
    const newConf: ProjectConfig = {
      ...DEFAULT_CONFIG,
      projectName: `Nueva Obra Drywall ${new Date().toLocaleDateString()}`
    };
    setProjectId(newId);
    setConfig(newConf);
    setRooms([
      {
        id: `room_${Date.now()}`,
        name: 'Zona 1 - Techo Continuo ST',
        type: 'techo_st',
        segments: [{ id: `seg_${Date.now()}`, name: 'Área 1', length: 5, width: 3, repetitions: 1, openings: [] }]
      }
    ]);
    setCustomPrices({});
    setCustomStock({});
    setSelectedRoomId(`room_${Date.now()}`);
  };

  /**
   * Incorpora al proyecto las estancias creadas por el escáner AR/cámara.
   * Se comporta igual que una captura manual: el cómputo se recalcula solo.
   */
  const handleAddScannedRooms = (newRooms: Room[]) => {
    if (!newRooms.length) return;
    setRooms((prev) => [...prev, ...newRooms]);
    setSelectedRoomId(newRooms[0].id);
    if (newRooms.length > 1) setSelectedRoomId(newRooms[0].id);
  };

  /** Aplica los precios obtenidos de los catálogos de distribuidores. */
  const handleApplyMarketPrices = (prices: Record<string, number>) => {
    setCustomPrices((prev) => ({ ...prev, ...prices }));
  };

  const handleDeleteProject = (id: string) => {
    const projects = getSavedProjects().filter((p) => p.id !== id);
    localStorage.setItem('drywall_calculator_projects_v2', JSON.stringify(projects));
  };

  const handleUnitSystemToggle = () => {
    const newUnit: UnitSystem = config.unitSystem === 'metric' ? 'imperial' : 'metric';
    setConfig((prev) => ({
      ...prev,
      unitSystem: newUnit,
      laborCostPerUnit: newUnit === 'imperial' ? Number((prev.laborCostPerUnit / 10.7639).toFixed(2)) : Number((prev.laborCostPerUnit * 10.7639).toFixed(2))
    }));
  };

  const unitAreaLabel = config.unitSystem === 'metric' ? 'm²' : 'sq ft';

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Professional Header Bar */}
      <header className="sticky top-0 z-40 bg-slate-900/90 backdrop-blur-md border-b border-slate-800 shadow-md">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
          {/* Logo & Brand */}
          <div className="flex items-center space-x-3 cursor-pointer" onClick={() => setIsSettingsOpen(true)}>
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-brand-600 to-emerald-400 flex items-center justify-center text-white shadow-lg shadow-brand-500/20">
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="font-extrabold text-base tracking-tight text-white flex items-center gap-1.5">
                  <span>DrywallPro</span>
                  <span className="text-brand-400 font-mono text-xs bg-brand-500/10 border border-brand-500/30 px-1.5 py-0.5 rounded">
                    MASTER TOP 1
                  </span>
                </span>
              </div>
              <p className="text-[11px] text-slate-400 font-medium truncate max-w-[220px] sm:max-w-xs">
                {config.projectName}
              </p>
            </div>
          </div>

          {/* Center Metric KPI Badges */}
          <div className="hidden md:flex items-center space-x-4 bg-slate-950/70 border border-slate-800/80 px-4 py-1.5 rounded-xl text-xs font-mono">
            <div className="flex items-center space-x-2">
              <span className="text-slate-400">Área Neta:</span>
              <span className="font-bold text-brand-400 text-sm">
                {summary.totalNetArea} {unitAreaLabel}
              </span>
            </div>
            <div className="h-3 w-px bg-slate-800" />
            <div className="flex items-center space-x-2">
              <span className="text-slate-400">Total Cotización:</span>
              <span className="font-bold text-emerald-400 text-sm">
                {formatCurrency(summary.grandTotal, config.currency)}
              </span>
            </div>
          </div>

          {/* Right Action Icons */}
          <div className="flex items-center space-x-2">
            {/* Unit System Switcher */}
            <button
              onClick={handleUnitSystemToggle}
              className="px-2.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-lg text-xs font-mono font-semibold transition-all flex items-center gap-1.5"
              title="Alternar Sistema Métrico / Imperial"
            >
              <Globe className="w-3.5 h-3.5 text-brand-400" />
              <span>{config.unitSystem === 'metric' ? 'Métrico (m²)' : 'Imperial (sq ft)'}</span>
            </button>

            {/* Acceso a la Consola de Licencias (Roles, Usuarios, Permisos, Keygen) */}
            <a
              href="/admin"
              className="px-2.5 py-1.5 bg-brand-500/10 hover:bg-brand-500/20 text-brand-300 border border-brand-500/30 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 shadow-sm"
              title="Consola profesional de licencias: Roles, Usuarios, Permisos y control del Keygen de ambas apps"
            >
              <KeyRound className="w-3.5 h-3.5" />
              <span className="hidden lg:inline">Consola</span>
            </a>

            {/* Indicador de conexión cifrada */}
            <span
              className={
                isSecureContext
                  ? 'px-2 py-1.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 rounded-lg text-[10px] font-mono font-bold flex items-center gap-1'
                  : 'px-2 py-1.5 bg-amber-500/10 text-amber-400 border border-amber-500/30 rounded-lg text-[10px] font-mono font-bold flex items-center gap-1'
              }
              title={
                isSecureContext
                  ? 'Conexión HTTPS cifrada (TLS)'
                  : 'Conexión HTTP sin cifrar — activa TLS con `npm run dev:https`'
              }
            >
              {isSecureContext ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
              {isSecureContext ? 'HTTPS' : 'HTTP'}
            </span>

            {/* Security Center Shield Button */}
            <button
              onClick={() => setIsSecurityOpen(true)}
              className="px-2.5 py-1.5 bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 shadow-sm"
              title="Centro de Seguridad & Blindaje Anti-SQLi / XSS"
            >
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
              <span className="hidden sm:inline">Seguridad</span>
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            </button>

            {/* Saved Projects Button */}
            <button
              onClick={() => setIsProjectsOpen(true)}
              className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 rounded-lg text-xs transition-all relative"
              title="Mis Obras y Proyectos Guardados"
            >
              <FolderKanban className="w-4 h-4 text-blue-400" />
            </button>

            {/* Material Catalog Button */}
            <button
              onClick={() => setIsCatalogOpen(true)}
              className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 rounded-lg text-xs transition-all"
              title="Catálogo de Materiales y Medidas"
            >
              <Package className="w-4 h-4 text-amber-400" />
            </button>

            {/* Planos CAD (DXF / DWG) */}
            <button
              onClick={() => setIsCadOpen(true)}
              className="px-2.5 py-1.5 bg-sky-500/10 hover:bg-sky-500/20 text-sky-300 border border-sky-500/30 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5"
              title="Exportar planos vectoriales DXF / DWG para AutoCAD"
            >
              <DraftingCompass className="w-3.5 h-3.5" />
              <span className="hidden xl:inline">Planos CAD</span>
            </button>

            {/* Escáner AR / cámara */}
            <button
              onClick={() => setIsArOpen(true)}
              className="px-2.5 py-1.5 bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5"
              title="Escanear la estancia con Realidad Aumentada (WebXR) o con la cámara"
            >
              <ScanLine className="w-3.5 h-3.5" />
              <span className="hidden xl:inline">Escanear AR</span>
            </button>

            {/* Precios de distribuidores */}
            <button
              onClick={() => setIsPricingOpen(true)}
              className="px-2.5 py-1.5 bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 border border-amber-500/30 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5"
              title="Conectar catálogos de distribuidores y actualizar precios en tiempo real"
            >
              <Store className="w-3.5 h-3.5" />
              <span className="hidden xl:inline">Precios</span>
            </button>

            {/* Asistencia remota en obra */}
            <a
              href="/asistencia"
              className="px-2.5 py-1.5 bg-violet-500/10 hover:bg-violet-500/20 text-violet-300 border border-violet-500/30 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5"
              title="Comparte la cámara con la oficina técnica por WebRTC"
            >
              <Users className="w-3.5 h-3.5" />
              <span className="hidden xl:inline">Asistencia</span>
            </a>

            {/* Settings Button */}
            <button
              onClick={() => setIsSettingsOpen(true)}
              className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 rounded-lg text-xs transition-all"
              title="Configuración de Proyecto"
            >
              <Settings className="w-4 h-4 text-slate-400" />
            </button>
          </div>
        </div>

        {/* Navigation Tabs Bar */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 border-t border-slate-800 flex items-center justify-between text-xs">
          <nav className="flex space-x-1 sm:space-x-3 py-2">
            <button
              onClick={() => setActiveTab('calculator')}
              className={`px-3.5 py-1.5 rounded-lg font-medium transition-all flex items-center gap-2 ${
                activeTab === 'calculator'
                  ? 'bg-brand-500/15 text-brand-400 border border-brand-500/30'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Calculator className="w-3.5 h-3.5" />
              <span>Calculadora & Planos 2D/3D</span>
            </button>

            <button
              onClick={() => setActiveTab('budget')}
              className={`px-3.5 py-1.5 rounded-lg font-medium transition-all flex items-center gap-2 ${
                activeTab === 'budget'
                  ? 'bg-brand-500/15 text-brand-400 border border-brand-500/30'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <FileText className="w-3.5 h-3.5" />
              <span>Presupuesto & Orden de Compra</span>
              <span className="bg-brand-500/20 text-brand-300 text-[10px] px-1.5 py-0.2 rounded font-mono">
                {summary.requirements.length}
              </span>
            </button>

            <button
              onClick={() => setActiveTab('advisory')}
              className={`px-3.5 py-1.5 rounded-lg font-medium transition-all flex items-center gap-2 ${
                activeTab === 'advisory'
                  ? 'bg-brand-500/15 text-brand-400 border border-brand-500/30'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <BookOpen className="w-3.5 h-3.5" />
              <span>Manual Técnico & Normas</span>
            </button>
          </nav>

          <div className="hidden sm:flex items-center space-x-3 text-[11px] text-slate-400 font-mono">
            <span>Factor Merma: {config.wastePercentage}%</span>
            <span>•</span>
            <span>Modulación: @ {(config.studSpacing * 100).toFixed(0)}cm</span>
          </div>
        </div>
      </header>

      {/* Main Container Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
        {activeTab === 'calculator' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Left Column: Room & Dimension Takeoff Manager (7 Cols) */}
            <div className="lg:col-span-7 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
                    <span>Estancias, Paredes & Cielos Rasos</span>
                    <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 font-normal">
                      {rooms.length} zonas
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400">
                    Defina medidas, tipologías constructivas y huecos a descontar por zona.
                  </p>
                </div>
              </div>

              <MultiRoomManager
                rooms={rooms}
                unitSystem={config.unitSystem}
                onUpdateRooms={setRooms}
                selectedRoomId={selectedRoomId}
                onSelectRoom={setSelectedRoomId}
              />
            </div>

            {/* Right Column: 2D/3D Interactive Visualizer + Quick Summary (5 Cols) */}
            <div className="lg:col-span-5 space-y-4 sticky top-24">
              <div>
                <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
                  <span>Esquema Técnico Estructural</span>
                </h2>
                <p className="text-xs text-slate-400">
                  Previsualización en tiempo real de la zona seleccionada: {activeRoom.name}.
                </p>
              </div>

              <Visualizer2D3D
                type={activeRoom.type}
                length={activeSegment.length}
                width={activeSegment.width}
                unitSystem={config.unitSystem}
                studSpacing={config.studSpacing}
                openingsCount={activeOpeningsCount}
              />

              {/* Quick Material Takeoff Summary Card */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Cómputo Global Rápido
                  </span>
                  <button
                    onClick={() => setActiveTab('budget')}
                    className="text-xs text-brand-400 hover:text-brand-300 font-medium"
                  >
                    Ver Desglose Completo →
                  </button>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs font-mono">
                  <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[10px] block">ÁREA BRUTA</span>
                    <span className="text-slate-200 font-bold">{summary.totalGrossArea} {unitAreaLabel}</span>
                  </div>
                  <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[10px] block">DEDUCCIONES HUECOS</span>
                    <span className="text-rose-400 font-bold">-{summary.totalOpeningsArea} {unitAreaLabel}</span>
                  </div>
                  <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[10px] block">SUPERFICIE NETA</span>
                    <span className="text-brand-400 font-bold">{summary.totalNetArea} {unitAreaLabel}</span>
                  </div>
                  <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[10px] block">COSTO TOTAL ESTIMADO</span>
                    <span className="text-emerald-400 font-bold">{formatCurrency(summary.grandTotal, config.currency)}</span>
                  </div>
                </div>

                <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between">
                  <span className="text-[11px] text-slate-400">
                    {summary.requirements.length} materiales requeridos
                  </span>
                  <button
                    onClick={() => setActiveTab('budget')}
                    className="px-3 py-1.5 bg-brand-600 hover:bg-brand-500 text-white rounded-lg text-xs font-semibold transition-all shadow"
                  >
                    Generar Presupuesto PDF
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'budget' && (
          <BudgetEstimator
            summary={summary}
            config={config}
            rooms={rooms}
            customPrices={customPrices}
            customStock={customStock}
            onUpdateConfig={setConfig}
            onUpdatePrice={handleUpdatePrice}
            onUpdateStock={handleUpdateStock}
          />
        )}

        {activeTab === 'advisory' && (
          <AdvisoryPanel
            unitSystem={config.unitSystem}
            tips={summary.advisoryTips}
          />
        )}
      </main>

      {/* Footer */}
      <footer className="mt-auto border-t border-slate-800 bg-slate-900/60 py-6 text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center space-x-2">
            <span className="font-semibold text-slate-400">DrywallPro Master</span>
            <span>•</span>
            <span>Plataforma Web Top 1 para Cálculo de Materiales Drywall, Tabiques y Cielos Rasos</span>
          </div>

          <div className="flex items-center space-x-4">
            <span>Conforme a normas ASTM C840 / EN 520 / USG</span>
            <span>•</span>
            <button
              onClick={() => setIsSettingsOpen(true)}
              className="text-slate-400 hover:text-brand-400 transition-colors"
            >
              Configuración
            </button>
          </div>
        </div>
      </footer>

      {/* Modals */}
      <ProjectSettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        config={config}
        onSaveConfig={setConfig}
      />

      <SavedProjectsModal
        isOpen={isProjectsOpen}
        onClose={() => setIsProjectsOpen(false)}
        currentProjectId={projectId}
        onLoadProject={handleLoadProject}
        onNewProject={handleNewProject}
        onDeleteProject={handleDeleteProject}
        onExportProject={exportProjectToJson}
        onImportProjectJson={handleLoadProject}
      />

      <MaterialCatalogModal
        isOpen={isCatalogOpen}
        onClose={() => setIsCatalogOpen(false)}
      />

      <SecurityCenterModal
        isOpen={isSecurityOpen}
        onClose={() => setIsSecurityOpen(false)}
      />

      {isCadOpen && (
        <React.Suspense fallback={<ModuleFallback label="módulo de planos CAD" />}>
          <CadExportModal
            isOpen={isCadOpen}
            onClose={() => setIsCadOpen(false)}
            rooms={rooms}
            config={config}
            summary={summary}
          />
        </React.Suspense>
      )}

      {isArOpen && (
        <React.Suspense fallback={<ModuleFallback label="escáner AR" />}>
          <ArScannerModal
            isOpen={isArOpen}
            onClose={() => setIsArOpen(false)}
            config={config}
            onAddRooms={handleAddScannedRooms}
          />
        </React.Suspense>
      )}

      {isPricingOpen && (
        <React.Suspense fallback={<ModuleFallback label="módulo de precios" />}>
          <PricingModal
            isOpen={isPricingOpen}
            onClose={() => setIsPricingOpen(false)}
            config={config}
            summary={summary}
            onApplyPrices={handleApplyMarketPrices}
          />
        </React.Suspense>
      )}
    </div>
  );
}
