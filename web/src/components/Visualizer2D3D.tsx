import React, { useState } from 'react';
import {
  Layers,
  Eye,
  Maximize2,
  ZoomIn,
  ZoomOut,
  RotateCcw,
  Sparkles,
  ShieldAlert,
  Info
} from 'lucide-react';
import { ConstructionType, UnitSystem } from '../types';
import { CONSTRUCTION_TYPES } from '../data/materials';

interface VisualizerProps {
  type: ConstructionType;
  length: number;
  width: number;
  unitSystem: UnitSystem;
  studSpacing: number;
  openingsCount: number;
}

export const Visualizer2D3D: React.FC<VisualizerProps> = ({
  type,
  length,
  width,
  unitSystem,
  studSpacing,
  openingsCount
}) => {
  const [viewMode, setViewMode] = useState<'2d' | '3d'>('3d');
  const [showStructure, setShowStructure] = useState(true);
  const [showPlates, setShowPlates] = useState(true);
  const [showInsulation, setShowInsulation] = useState(true);
  const [showScrews, setShowScrews] = useState(true);
  const [showDimensions, setShowDimensions] = useState(true);
  const [zoomLevel, setZoomLevel] = useState(1);
  const [rotationAngle, setRotationAngle] = useState(18); // isometric tilt

  const isMetric = unitSystem === 'metric';
  const unitSuffix = isMetric ? 'm' : 'ft';
  const typeDef = CONSTRUCTION_TYPES[type] || CONSTRUCTION_TYPES.techo_st;
  const isCeiling = typeDef.category === 'ceiling';

  // Stud count calculation for visualization
  const actualLength = Math.max(1, length);
  const spacingM = isMetric ? studSpacing : studSpacing * 0.0254;
  const totalStuds = Math.max(3, Math.floor(actualLength / spacingM) + 1);

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl flex flex-col">
      {/* Visualizer Top Bar */}
      <div className="bg-slate-850 px-4 py-3 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center space-x-2">
          <div
            className="w-3 h-3 rounded-full animate-pulse"
            style={{ backgroundColor: typeDef.accentColor }}
          />
          <h3 className="font-semibold text-sm text-slate-200 flex items-center gap-1.5">
            <span>Visualizador Técnico Estructural</span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-normal">
              {typeDef.name}
            </span>
          </h3>
        </div>

        {/* View Mode Toggle */}
        <div className="flex items-center space-x-1 bg-slate-950 p-1 rounded-lg border border-slate-800 text-xs font-medium">
          <button
            onClick={() => setViewMode('2d')}
            className={`px-3 py-1 rounded-md transition-all flex items-center gap-1.5 ${
              viewMode === '2d'
                ? 'bg-brand-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Maximize2 className="w-3.5 h-3.5" />
            <span>2D Plano</span>
          </button>
          <button
            onClick={() => setViewMode('3d')}
            className={`px-3 py-1 rounded-md transition-all flex items-center gap-1.5 ${
              viewMode === '3d'
                ? 'bg-brand-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>3D Corte Isométrico</span>
          </button>
        </div>

        {/* Layer Visibility Toggles */}
        <div className="flex items-center space-x-1.5 text-xs">
          <button
            onClick={() => setShowStructure(!showStructure)}
            className={`px-2.5 py-1 rounded border transition-all ${
              showStructure
                ? 'bg-blue-500/10 border-blue-500/40 text-blue-400'
                : 'bg-slate-800/40 border-slate-700 text-slate-500 line-through'
            }`}
            title="Estructura de perfiles metálicos"
          >
            Perfiles
          </button>
          <button
            onClick={() => setShowPlates(!showPlates)}
            className={`px-2.5 py-1 rounded border transition-all ${
              showPlates
                ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400'
                : 'bg-slate-800/40 border-slate-700 text-slate-500 line-through'
            }`}
            title="Placas de yeso / drywalls"
          >
            Placas
          </button>
          <button
            onClick={() => setShowInsulation(!showInsulation)}
            className={`px-2.5 py-1 rounded border transition-all ${
              showInsulation
                ? 'bg-amber-500/10 border-amber-500/40 text-amber-400'
                : 'bg-slate-800/40 border-slate-700 text-slate-500 line-through'
            }`}
            title="Lana mineral acústica"
          >
            Aislamiento
          </button>
          <button
            onClick={() => setShowScrews(!showScrews)}
            className={`px-2.5 py-1 rounded border transition-all ${
              showScrews
                ? 'bg-purple-500/10 border-purple-500/40 text-purple-400'
                : 'bg-slate-800/40 border-slate-700 text-slate-500 line-through'
            }`}
            title="Puntos de atornillado"
          >
            Tornillos
          </button>
          <button
            onClick={() => setShowDimensions(!showDimensions)}
            className={`px-2.5 py-1 rounded border transition-all ${
              showDimensions
                ? 'bg-cyan-500/10 border-cyan-500/40 text-cyan-400'
                : 'bg-slate-800/40 border-slate-700 text-slate-500 line-through'
            }`}
            title="Líneas de cota y medidas"
          >
            Cotas
          </button>
        </div>

        {/* Zoom Controls */}
        <div className="flex items-center space-x-1 text-slate-400 bg-slate-950 p-1 rounded-lg border border-slate-800">
          <button
            onClick={() => setZoomLevel((z) => Math.min(1.6, z + 0.1))}
            className="p-1 hover:text-white rounded"
            title="Acercar zoom"
          >
            <ZoomIn className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setZoomLevel((z) => Math.max(0.6, z - 0.1))}
            className="p-1 hover:text-white rounded"
            title="Alejar zoom"
          >
            <ZoomOut className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => {
              setZoomLevel(1);
              setRotationAngle(18);
            }}
            className="p-1 hover:text-white rounded"
            title="Restablecer vista"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Main Canvas Area */}
      <div className="relative w-full h-[360px] bg-slate-950 blueprint-grid flex items-center justify-center overflow-hidden p-6 select-none">
        {/* Dynamic Watermark / Dimension info badge */}
        <div className="absolute top-4 left-4 z-10 flex flex-col gap-1 pointer-events-none">
          <div className="bg-slate-900/90 backdrop-blur border border-slate-800 rounded-lg px-3 py-1.5 shadow-lg flex items-center gap-2">
            <span className="text-xs text-slate-400 font-mono">Modulación:</span>
            <span className="text-xs font-semibold text-brand-400 font-mono">
              @ {isMetric ? (studSpacing * 100).toFixed(1) + ' cm' : (studSpacing * 39.37).toFixed(0) + ' in'}
            </span>
          </div>
          <div className="bg-slate-900/90 backdrop-blur border border-slate-800 rounded-lg px-3 py-1.5 shadow-lg flex items-center gap-2">
            <span className="text-xs text-slate-400 font-mono">Dimensiones:</span>
            <span className="text-xs font-semibold text-slate-200 font-mono">
              {length} {unitSuffix} × {width} {unitSuffix}
            </span>
          </div>
        </div>

        {/* 3D Interactive Rotation Hint */}
        {viewMode === '3d' && (
          <div className="absolute bottom-4 right-4 z-10 flex items-center gap-2 bg-slate-900/80 backdrop-blur px-3 py-1.5 rounded-lg border border-slate-800 text-xs text-slate-400">
            <span>Inclinación:</span>
            <input
              type="range"
              min="0"
              max="45"
              value={rotationAngle}
              onChange={(e) => setRotationAngle(Number(e.target.value))}
              className="w-20 accent-brand-500 cursor-pointer"
            />
            <span className="font-mono text-[10px] text-slate-300">{rotationAngle}°</span>
          </div>
        )}

        {/* Graphic Render Container */}
        <div
          className="transition-transform duration-300 ease-out origin-center"
          style={{
            transform: `scale(${zoomLevel})`
          }}
        >
          {viewMode === '3d' ? (
            /* 3D Isometric View */
            <svg
              width="600"
              height="300"
              viewBox="0 0 600 300"
              className="overflow-visible filter drop-shadow-2xl"
            >
              <defs>
                <linearGradient id="metalStud" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor="#94a3b8" />
                  <stop offset="50%" stopColor="#cbd5e1" />
                  <stop offset="100%" stopColor="#64748b" />
                </linearGradient>
                <linearGradient id="drywallBoard" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#f1f5f9" />
                  <stop offset="100%" stopColor="#e2e8f0" />
                </linearGradient>
                <linearGradient id="insulationWool" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stopColor="#fbbf24" />
                  <stop offset="100%" stopColor="#d97706" />
                </linearGradient>
                <pattern id="woolPattern" width="10" height="10" patternUnits="userSpaceOnUse">
                  <path d="M 0 5 Q 2.5 0, 5 5 T 10 5" fill="none" stroke="#f59e0b" strokeWidth="1" opacity="0.6"/>
                </pattern>
              </defs>

              <g transform={`rotate(-${rotationAngle * 0.4} 300 150) skewX(-${rotationAngle * 0.6})`}>
                {/* Floor reference plane */}
                <path
                  d="M 60 240 L 540 240 L 510 270 L 30 270 Z"
                  fill="rgba(30, 41, 59, 0.4)"
                  stroke="#334155"
                  strokeWidth="1"
                />

                {/* Bottom Track (Canal Guía U) */}
                {showStructure && (
                  <g id="bottom-track">
                    <rect x="70" y="234" width="460" height="8" rx="1" fill="url(#metalStud)" stroke="#475569" strokeWidth="1" />
                    <text x="75" y="240" fill="#1e293b" fontSize="6" fontFamily="monospace" fontWeight="bold">CANAL U 64mm</text>
                  </g>
                )}

                {/* Top Track (Canal Superior) */}
                {showStructure && (
                  <g id="top-track">
                    <rect x="70" y="48" width="460" height="8" rx="1" fill="url(#metalStud)" stroke="#475569" strokeWidth="1" />
                  </g>
                )}

                {/* Acoustic Insulation Layer between studs */}
                {showInsulation && (
                  <g id="insulation-layer">
                    <rect x="85" y="56" width="430" height="178" fill="url(#insulationWool)" opacity="0.8" rx="2" />
                    <rect x="85" y="56" width="430" height="178" fill="url(#woolPattern)" opacity="0.9" />
                  </g>
                )}

                {/* Vertical Studs (Parantes Montantes C) */}
                {showStructure && (
                  <g id="vertical-studs">
                    {[80, 150, 220, 290, 360, 430, 500].map((xPos, idx) => (
                      <g key={idx}>
                        {/* Stud Front Face */}
                        <rect
                          x={xPos}
                          y="56"
                          width="14"
                          height="178"
                          fill="url(#metalStud)"
                          stroke="#334155"
                          strokeWidth="1"
                          rx="1"
                        />
                        {/* Service knockouts holes */}
                        <circle cx={xPos + 7} cy="110" r="3" fill="#1e293b" />
                        <circle cx={xPos + 7} cy="180" r="3" fill="#1e293b" />
                      </g>
                    ))}
                  </g>
                )}

                {/* Gypsum Plasterboards (Front layer cutaway to show insides) */}
                {showPlates && (
                  <g id="drywall-boards" opacity="0.92">
                    {/* First Sheet (Left) */}
                    <path
                      d="M 70 50 L 290 50 L 290 238 L 70 238 Z"
                      fill="url(#drywallBoard)"
                      stroke="#94a3b8"
                      strokeWidth="1.5"
                    />
                    <text x="85" y="75" fill="#475569" fontSize="10" fontWeight="bold">PLACA YESO ST 12.5mm</text>
                    <text x="85" y="88" fill="#64748b" fontSize="8">Junta calafateada Nivel 4</text>

                    {/* Second Sheet cutaway to show skeleton */}
                    <path
                      d="M 294 50 L 530 50 L 530 140 L 410 238 L 294 238 Z"
                      fill="url(#drywallBoard)"
                      stroke="#94a3b8"
                      strokeWidth="1.5"
                    />

                    {/* Joint tape strip along the center vertical joint */}
                    <rect x="288" y="50" width="8" height="188" fill="#f8fafc" opacity="0.75" />
                    <line x1="292" y1="50" x2="292" y2="238" stroke="#cbd5e1" strokeWidth="1" strokeDasharray="3 2" />
                  </g>
                )}

                {/* Screws Pattern */}
                {showScrews && (
                  <g id="screw-points">
                    {[80, 150, 220, 290].map((x) =>
                      [65, 95, 125, 155, 185, 215].map((y, sIdx) => (
                        <g key={`${x}-${sIdx}`}>
                          <circle cx={x + 7} cy={y} r="2" fill="#334155" stroke="#94a3b8" strokeWidth="0.5" />
                          <line x1={x + 6} y1={y} x2={x + 8} y2={y} stroke="#f8fafc" strokeWidth="0.6" />
                        </g>
                      ))
                    )}
                  </g>
                )}

                {/* Dimensions Lines */}
                {showDimensions && (
                  <g id="dimension-lines">
                    {/* Width dimension */}
                    <line x1="70" y1="26" x2="530" y2="26" stroke="#38bdf8" strokeWidth="1.5" markerEnd="url(#arrow)" />
                    <line x1="70" y1="20" x2="70" y2="32" stroke="#38bdf8" strokeWidth="1" />
                    <line x1="530" y1="20" x2="530" y2="32" stroke="#38bdf8" strokeWidth="1" />
                    <rect x="260" y="16" width="80" height="16" rx="3" fill="#0f172a" stroke="#38bdf8" strokeWidth="1" />
                    <text x="300" y="28" fill="#38bdf8" fontSize="9" fontWeight="bold" textAnchor="middle" fontFamily="monospace">
                      Largo: {length} {unitSuffix}
                    </text>

                    {/* Height dimension */}
                    <line x1="552" y1="48" x2="552" y2="242" stroke="#38bdf8" strokeWidth="1.5" />
                    <line x1="546" y1="48" x2="558" y2="48" stroke="#38bdf8" strokeWidth="1" />
                    <line x1="546" y1="242" x2="558" y2="242" stroke="#38bdf8" strokeWidth="1" />
                    <rect x="540" y="135" width="55" height="16" rx="3" fill="#0f172a" stroke="#38bdf8" strokeWidth="1" />
                    <text x="567" y="147" fill="#38bdf8" fontSize="8" fontWeight="bold" textAnchor="middle" fontFamily="monospace">
                      H: {width} {unitSuffix}
                    </text>
                  </g>
                )}
              </g>
            </svg>
          ) : (
            /* 2D Technical Blueprint View */
            <svg
              width="580"
              height="280"
              viewBox="0 0 580 280"
              className="overflow-visible"
            >
              {/* Outer boundary wall */}
              <rect
                x="40"
                y="30"
                width="500"
                height="210"
                fill="#090d16"
                stroke="#0ea5e9"
                strokeWidth="2"
                strokeDasharray="4 2"
                rx="4"
              />

              {/* Grid background on blueprint */}
              <g stroke="#0369a1" strokeWidth="0.5" opacity="0.3">
                {[70, 110, 150, 190].map((y) => (
                  <line key={y} x1="40" y1={y} x2="540" y2={y} />
                ))}
              </g>

              {/* Top and Bottom tracks in 2D */}
              {showStructure && (
                <>
                  <rect x="40" y="30" width="500" height="12" fill="#38bdf8" opacity="0.3" stroke="#38bdf8" strokeWidth="1" />
                  <text x="50" y="40" fill="#38bdf8" fontSize="8" fontFamily="monospace">SOLERA / CANAL GUÍA SUPERIOR</text>

                  <rect x="40" y="228" width="500" height="12" fill="#38bdf8" opacity="0.3" stroke="#38bdf8" strokeWidth="1" />
                  <text x="50" y="238" fill="#38bdf8" fontSize="8" fontFamily="monospace">CANAL GUÍA INFERIOR (ANCLADO CADA 40cm)</text>
                </>
              )}

              {/* Studs distribution in 2D */}
              {showStructure && (
                <g>
                  {[45, 115, 185, 255, 325, 395, 465, 530].map((x, i) => (
                    <g key={i}>
                      <rect x={x} y="42" width="6" height="186" fill="#38bdf8" stroke="#0284c7" strokeWidth="1" />
                      {/* Stud label */}
                      <text x={x - 2} y="130" fill="#93c5fd" fontSize="7" fontFamily="monospace" transform={`rotate(-90 ${x} 130)`}>
                        C-64 #{i + 1}
                      </text>
                    </g>
                  ))}
                </g>
              )}

              {/* Stud spacing dimensions */}
              {showDimensions && (
                <g>
                  <line x1="185" y1="16" x2="255" y2="16" stroke="#fbbf24" strokeWidth="1.2" />
                  <line x1="185" y1="12" x2="185" y2="20" stroke="#fbbf24" strokeWidth="1" />
                  <line x1="255" y1="12" x2="255" y2="20" stroke="#fbbf24" strokeWidth="1" />
                  <text x="220" y="10" fill="#fbbf24" fontSize="8" fontWeight="bold" textAnchor="middle" fontFamily="monospace">
                    @ {isMetric ? (studSpacing * 100).toFixed(1) + ' cm' : (studSpacing * 39.37).toFixed(0) + '"'}
                  </text>
                </g>
              )}

              {/* Plasterboard plates boundary */}
              {showPlates && (
                <g>
                  <rect x="40" y="42" width="245" height="186" fill="rgba(34, 197, 94, 0.08)" stroke="#22c55e" strokeWidth="1.5" strokeDasharray="6 3" />
                  <rect x="285" y="42" width="245" height="186" fill="rgba(34, 197, 94, 0.08)" stroke="#22c55e" strokeWidth="1.5" strokeDasharray="6 3" />
                  <text x="130" y="80" fill="#4ade80" fontSize="10" fontWeight="bold" fontFamily="monospace">PLACA 1 (1.20×2.40)</text>
                  <text x="375" y="80" fill="#4ade80" fontSize="10" fontWeight="bold" fontFamily="monospace">PLACA 2 (1.20×2.40)</text>
                </g>
              )}

              {/* Door Opening representation if openings exist */}
              {openingsCount > 0 && (
                <g id="opening-representation">
                  <rect x="210" y="100" width="70" height="128" fill="#0f172a" stroke="#f43f5e" strokeWidth="2" />
                  <text x="245" y="160" fill="#f43f5e" fontSize="9" fontWeight="bold" textAnchor="middle">HUECO PUERTA</text>
                  <line x1="205" y1="42" x2="205" y2="228" stroke="#f43f5e" strokeWidth="2" strokeDasharray="2 2" />
                  <line x1="285" y1="42" x2="285" y2="228" stroke="#f43f5e" strokeWidth="2" strokeDasharray="2 2" />
                  <text x="245" y="175" fill="#f43f5e" fontSize="7" textAnchor="middle">JAMBAS DOBLES REFORZADAS</text>
                </g>
              )}

              {/* Dimension indicators */}
              {showDimensions && (
                <g>
                  <line x1="40" y1="262" x2="540" y2="262" stroke="#38bdf8" strokeWidth="1.5" />
                  <line x1="40" y1="256" x2="40" y2="268" stroke="#38bdf8" strokeWidth="1.5" />
                  <line x1="540" y1="256" x2="540" y2="268" stroke="#38bdf8" strokeWidth="1.5" />
                  <text x="290" y="274" fill="#38bdf8" fontSize="10" fontWeight="bold" textAnchor="middle" fontFamily="monospace">
                    LONGITUD TOTAL: {length} {unitSuffix}
                  </text>
                </g>
              )}
            </svg>
          )}
        </div>
      </div>

      {/* Visualizer Footer Bar / Legend */}
      <div className="bg-slate-850 px-4 py-2.5 border-t border-slate-800 flex flex-wrap items-center justify-between text-xs text-slate-400 gap-3">
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-1.5">
            <div className="w-2.5 h-2.5 rounded-sm bg-blue-400" />
            <span>Perfil Montante C ({totalStuds} uds calculadas)</span>
          </div>
          <div className="flex items-center space-x-1.5">
            <div className="w-2.5 h-2.5 rounded-sm bg-emerald-400" />
            <span>{typeDef.standardPlate}</span>
          </div>
          <div className="flex items-center space-x-1.5">
            <div className="w-2.5 h-2.5 rounded-sm bg-amber-400" />
            <span>Lana Mineral Acústica</span>
          </div>
        </div>

        <div className="flex items-center space-x-2 text-[11px] text-slate-500 font-mono">
          <Info className="w-3.5 h-3.5 text-brand-400" />
          <span>Norma de ensamble: ASTM C840 / EN 520</span>
        </div>
      </div>
    </div>
  );
};
