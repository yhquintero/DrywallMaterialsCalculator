import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  X,
  Camera,
  Scan,
  Ruler,
  Boxes,
  Undo2,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  Video,
  VideoOff,
  Share2,
  Gauge,
  MousePointerClick,
  Info,
  CornerDownRight,
  RotateCcw,
  Smartphone
} from 'lucide-react';
import { ProjectConfig, Room, ConstructionType } from '../types';
import { CONSTRUCTION_TYPES } from '../data/materials';
import {
  ArCapabilities,
  ArRoomScanner,
  COMMON_REFERENCES,
  Point2,
  RoomScan,
  ScanPointKind,
  ScanToRoomsOptions,
  ScannerState,
  captureFrame,
  detectArCapabilities,
  measureFromPhoto,
  buildPhotoScan,
  modeLabel,
  openCamera,
  scanSummary,
  scanToRooms,
  stopCamera
} from '../lib/ar';
import { Dialog } from './ui/Dialog';

interface ArScannerModalProps {
  isOpen: boolean;
  onClose: () => void;
  config: ProjectConfig;
  onAddRooms: (rooms: Room[]) => void;
}

type Mode = 'webxr' | 'photo';
type PhotoTool = 'polygon' | 'reference-corners' | 'reference-line';

const CEILING_TYPES: ConstructionType[] = ['techo_st', 'techo_rh', 'plafon_reticulado', 'multi_partes'];
const WALL_TYPES: ConstructionType[] = [
  'tabique_divisor',
  'muro_sencillo',
  'muro_rf',
  'steel_framing',
  'fachada_eifs',
  'cajillo_viga'
];

export const ArScannerModal: React.FC<ArScannerModalProps> = ({ isOpen, onClose, config, onAddRooms }) => {
  const [capabilities, setCapabilities] = useState<ArCapabilities | null>(null);
  const [mode, setMode] = useState<Mode>('photo');
  const [status, setStatus] = useState<'idle' | 'running' | 'done'>('idle');
  const [messages, setMessages] = useState<Array<{ kind: 'info' | 'ok' | 'warn' | 'error'; text: string }>>([]);

  // Estado del escáner WebXR
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const scannerRef = useRef<ArRoomScanner | null>(null);
  const [scanState, setScanState] = useState<ScannerState | null>(null);
  const [heightInput, setHeightInput] = useState<string>('2.60');
  const [scan, setScan] = useState<RoomScan | null>(null);

  // Estado de la cámara / foto
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const cameraRef = useRef<MediaStream | null>(null);
  const [cameraStream, setCameraStream] = useState<MediaStream | null>(null);
  const [frame, setFrame] = useState<{ dataUrl: string; width: number; height: number } | null>(null);
  const [tool, setTool] = useState<PhotoTool>('polygon');
  const [polygon, setPolygon] = useState<Point2[]>([]);
  const [refCorners, setRefCorners] = useState<Point2[]>([]);
  const [refLine, setRefLine] = useState<Point2[]>([]);
  const [refWidth, setRefWidth] = useState('1.20');
  const [refHeight, setRefHeight] = useState('1.20');
  const [refMeters, setRefMeters] = useState('0.60');

  // Destino de las estancias
  const [target, setTarget] = useState<ScanToRoomsOptions['target']>('both');
  const [roomName, setRoomName] = useState('Estancia escaneada');
  const [ceilingType, setCeilingType] = useState<ConstructionType>('techo_st');
  const [wallType, setWallType] = useState<ConstructionType>('tabique_divisor');

  const log = useCallback((kind: 'info' | 'ok' | 'warn' | 'error', text: string) => {
    setMessages((prev) => [...prev.slice(-5), { kind, text }]);
  }, []);

  // Detección de capacidades al abrir
  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    detectArCapabilities().then((caps) => {
      if (cancelled) return;
      setCapabilities(caps);
      setMode(caps.recommendedMode === 'webxr' ? 'webxr' : 'photo');
    });
    return () => {
      cancelled = true;
    };
  }, [isOpen]);

  // Limpieza completa al cerrar
  useEffect(() => {
    if (isOpen) return;
    void scannerRef.current?.stop();
    scannerRef.current = null;
    stopCamera(cameraRef.current ?? cameraStream);
    cameraRef.current = null;
    setCameraStream(null);
    setStatus('idle');
    setScan(null);
    setPolygon([]);
    setRefCorners([]);
    setRefLine([]);
    setFrame(null);
  }, [isOpen, cameraStream]);

  const photoResult = useMemo(() => {
    if (polygon.length < 3) return null;
    try {
      if (tool === 'polygon' && refCorners.length === 4) {
        return measureFromPhoto({
          polygonPx: polygon,
          reference: {
            kind: 'homography',
            corners: refCorners,
            widthMeters: Number(refWidth) || 0,
            heightMeters: Number(refHeight) || 0
          },
          height: Number(heightInput) || 2.6
        });
      }
      if (refLine.length === 2) {
        return measureFromPhoto({
          polygonPx: polygon,
          reference: {
            kind: 'scale',
            from: refLine[0],
            to: refLine[1],
            meters: Number(refMeters) || 0
          },
          height: Number(heightInput) || 2.6
        });
      }
      return null;
    } catch (error) {
      log('error', error instanceof Error ? error.message : 'No se pudo calcular la medición.');
      return null;
    }
  }, [polygon, refCorners, refLine, refWidth, refHeight, refMeters, heightInput, tool, log]);

  // ── WebXR ──────────────────────────────────────────────────────────────────
  const startXr = async (): Promise<void> => {
    if (!canvasRef.current) return;
    try {
      const scanner = new ArRoomScanner(canvasRef.current, {
        onState: (state) => setScanState(state),
        onError: (error) => log('error', error.message),
        onEnded: () => {
          const result = scannerRef.current?.buildScan(roomName, Number(heightInput)) ?? null;
          setScan(result);
          setStatus(result ? 'done' : 'idle');
          if (result) log('ok', 'Escaneo finalizado. Revisa las medidas antes de generar las estancias.');
        }
      });
      scannerRef.current = scanner;
      await scanner.start(overlayRef.current ?? undefined);
      setStatus('running');
      log('info', 'Sesión AR iniciada: apunta al suelo y toca la pantalla en cada esquina.');
    } catch (error) {
      log('error', error instanceof Error ? error.message : 'No se pudo iniciar la sesión AR.');
      setStatus('idle');
    }
  };

  const overlayRef = useRef<HTMLDivElement | null>(null);

  const finishXr = (): void => {
    const result = scannerRef.current?.buildScan(roomName, Number(heightInput));
    if (!result) {
      log('warn', 'Necesitas al menos 3 puntos de suelo para cerrar la estancia.');
      return;
    }
    setScan(result);
    setStatus('done');
    void scannerRef.current?.stop();
  };

  // ── Cámara ─────────────────────────────────────────────────────────────────
  const startCamera = async (): Promise<void> => {
    try {
      const stream = await openCamera();
      setCameraStream(stream);
      cameraRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play().catch(() => undefined);
      }
      setStatus('running');
      log('info', 'Cámara activa. Encuadra la estancia y pulsa «Capturar imagen».');
    } catch (error) {
      log('error', error instanceof Error ? error.message : 'No se pudo abrir la cámara.');
    }
  };

  const takePhoto = (): void => {
    if (!videoRef.current) return;
    const captured = captureFrame(videoRef.current);
    if (!captured) {
      log('warn', 'La cámara aún no ha entregado imagen. Espera un segundo y reintenta.');
      return;
    }
    setFrame(captured);
    setPolygon([]);
    setRefCorners([]);
    setRefLine([]);
    stopCamera(cameraStream);
    cameraRef.current = null;
    setCameraStream(null);
    log('ok', 'Imagen capturada. Marca la referencia de escala y luego el contorno de la estancia.');
  };

  const handleCanvasClick = (event: React.MouseEvent<HTMLDivElement>): void => {
    if (!frame) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const scaleX = frame.width / rect.width;
    const scaleY = frame.height / rect.height;
    const point: Point2 = {
      x: (event.clientX - rect.left) * scaleX,
      y: (event.clientY - rect.top) * scaleY
    };
    if (tool === 'polygon') setPolygon((prev) => [...prev, point]);
    else if (tool === 'reference-corners') setRefCorners((prev) => (prev.length >= 4 ? [point] : [...prev, point]));
    else setRefLine((prev) => (prev.length >= 2 ? [point] : [...prev, point]));
  };

  // ── Aplicar al proyecto ────────────────────────────────────────────────────
  const applyToProject = (): void => {
    const effectiveScan =
      scan ??
      (photoResult
        ? buildPhotoScan(photoResult, roomName, Number(heightInput) || 2.6, frame?.dataUrl)
        : null);
    if (!effectiveScan) {
      log('warn', 'Todavía no hay ninguna medición válida.');
      return;
    }
    const rooms = scanToRooms(effectiveScan, {
      target,
      ceilingType,
      wallType,
      defaultHeight: Number(heightInput) || 2.6,
      unitSystem: config.unitSystem,
      roomName,
      roundTo: config.unitSystem === 'imperial' ? 0.25 : 0.05
    });
    if (!rooms.length) {
      log('warn', 'El escaneo no contiene medidas suficientes para generar estancias.');
      return;
    }
    onAddRooms(rooms);
    log('ok', `${rooms.length} estancia(s) añadidas al proyecto.`);
    onClose();
  };

  if (!isOpen) return null;

  const bbox = scan?.measurement.bounding;
  const summaryLines = scan
    ? scanSummary(scan, config.unitSystem)
    : photoResult
      ? [
          `Área: ${photoResult.measurement.floorArea.toFixed(2)} m²`,
          `Perímetro: ${photoResult.measurement.perimeter.toFixed(2)} m`,
          `Confianza: ${(photoResult.measurement.confidence * 100).toFixed(0)} %`
        ]
      : [];

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      label="Escaneo de estancias con cámara y realidad aumentada"
      backdropClassName="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/85 backdrop-blur-sm p-2 sm:p-6"
      panelClassName="w-full max-w-6xl max-h-[94vh] bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl flex flex-col overflow-hidden"
    >
        {/* Cabecera */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 to-lime-400 flex items-center justify-center text-white shadow-lg shadow-emerald-500/20">
              <Scan className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">Escaneo de estancias con cámara y Realidad Aumentada</h2>
              <p className="text-xs text-slate-400">
                Mide la habitación en segundos y vuelca las superficies directamente al cómputo de materiales.
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
          {/* Columna izquierda: captura */}
          <div className="lg:col-span-7 border-r border-slate-800 p-4 space-y-3">
            {/* Selector de modo */}
            <div className="flex items-center gap-2 bg-slate-950 border border-slate-800 rounded-xl p-1.5">
              <button
                onClick={() => setMode('webxr')}
                disabled={!capabilities?.immersiveAr}
                className={`flex-1 px-3 py-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-2 transition-all ${
                  mode === 'webxr'
                    ? 'bg-emerald-600 text-white'
                    : 'text-slate-400 hover:text-slate-200 disabled:opacity-40 disabled:cursor-not-allowed'
                }`}
                title={
                  capabilities?.immersiveAr
                    ? 'Medición 3D real con hit-test'
                    : 'Este dispositivo no soporta WebXR immersive-ar'
                }
              >
                <Boxes className="w-3.5 h-3.5" /> Realidad Aumentada (WebXR)
              </button>
              <button
                onClick={() => setMode('photo')}
                className={`flex-1 px-3 py-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-2 transition-all ${
                  mode === 'photo' ? 'bg-sky-600 text-white' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Camera className="w-3.5 h-3.5" /> Cámara / foto ({capabilities ? modeLabel('photo') : '…'})
              </button>
            </div>

            {/* Área de captura */}
            {mode === 'webxr' ? (
              <div className="space-y-2">
                <div ref={overlayRef} className="relative rounded-xl overflow-hidden border border-slate-800 bg-slate-950 min-h-[280px]">
                  <canvas ref={canvasRef} className="w-full h-auto block" />
                  {status !== 'running' && (
                    <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-center p-6 bg-slate-950/70">
                      {capabilities?.immersiveAr ? (
                        <>
                          <Smartphone className="w-8 h-8 text-emerald-400" />
                          <p className="text-xs text-slate-300 max-w-sm">
                            Al iniciar, la cámara se abrirá en pantalla completa con seguimiento espacial. Apunta al
                            suelo, toca la pantalla en cada esquina y cierra el recorrido con el botón «Finalizar».
                          </p>
                          <button
                            onClick={() => void startXr()}
                            className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold flex items-center gap-2"
                          >
                            <Boxes className="w-4 h-4" /> Iniciar sesión AR
                          </button>
                        </>
                      ) : (
                        <>
                          <AlertTriangle className="w-8 h-8 text-amber-400" />
                          <p className="text-xs text-slate-300 max-w-md">
                            Este dispositivo/navegador no soporta <code>immersive-ar</code>. Usa Chrome en Android con
                            ARCore, o Safari en iOS 18+ con Quick Look, o el modo de cámara.
                          </p>
                          <button
                            onClick={() => setMode('photo')}
                            className="px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold"
                          >
                            Usar modo cámara
                          </button>
                        </>
                      )}
                    </div>
                  )}

                  {/* HUD de medición en vivo */}
                  {status === 'running' && scanState && (
                    <div className="absolute bottom-0 left-0 right-0 bg-slate-950/85 backdrop-blur px-3 py-2 grid grid-cols-4 gap-2 text-[11px] font-mono">
                      <div>
                        <span className="block text-slate-500 text-[9px]">PUNTOS</span>
                        <span className="text-slate-100 font-bold">{scanState.points.length}</span>
                      </div>
                      <div>
                        <span className="block text-slate-500 text-[9px]">TRAMO</span>
                        <span className="text-emerald-400 font-bold">{scanState.liveDistance.toFixed(2)} m</span>
                      </div>
                      <div>
                        <span className="block text-slate-500 text-[9px]">PERÍMETRO</span>
                        <span className="text-sky-400 font-bold">{scanState.livePerimeter.toFixed(2)} m</span>
                      </div>
                      <div>
                        <span className="block text-slate-500 text-[9px]">ÁREA</span>
                        <span className="text-amber-400 font-bold">{scanState.liveArea.toFixed(2)} m²</span>
                      </div>
                    </div>
                  )}
                </div>

                {status === 'running' && (
                  <div className="flex flex-wrap gap-2">
                    <button
                      onClick={() => scannerRef.current?.addPointAtReticle()}
                      className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold flex items-center gap-2"
                    >
                      <MousePointerClick className="w-3.5 h-3.5" /> Fijar punto
                    </button>
                    <button
                      onClick={() => scannerRef.current?.setCaptureMode('ceiling')}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold border ${
                        scanState?.captureMode === 'ceiling'
                          ? 'bg-amber-500/20 border-amber-500/40 text-amber-300'
                          : 'bg-slate-800 border-slate-700 text-slate-300'
                      }`}
                    >
                      Marcar techo (altura)
                    </button>
                    <button
                      onClick={() => scannerRef.current?.setCaptureMode('floor')}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold border ${
                        scanState?.captureMode === 'floor'
                          ? 'bg-sky-500/20 border-sky-500/40 text-sky-300'
                          : 'bg-slate-800 border-slate-700 text-slate-300'
                      }`}
                    >
                      Marcar suelo
                    </button>
                    <button
                      onClick={() => scannerRef.current?.undo()}
                      className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-2"
                    >
                      <Undo2 className="w-3.5 h-3.5" /> Deshacer
                    </button>
                    <button
                      onClick={() => scannerRef.current?.clear()}
                      className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-2"
                    >
                      <Trash2 className="w-3.5 h-3.5" /> Borrar
                    </button>
                    <button
                      onClick={finishXr}
                      className="px-3 py-1.5 rounded-lg bg-brand-600 hover:bg-brand-500 text-white text-xs font-semibold flex items-center gap-2"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" /> Finalizar escaneo
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="space-y-2">
                {!frame ? (
                  <>
                    <div className="relative rounded-xl overflow-hidden border border-slate-800 bg-slate-950 min-h-[280px] flex items-center justify-center">
                      <video ref={videoRef} playsInline muted className="w-full h-auto max-h-[380px] object-contain" />
                      {!cameraStream && (
                        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center bg-slate-950/80">
                          <Video className="w-8 h-8 text-sky-400" />
                          <p className="text-xs text-slate-300 max-w-md">
                            La cámara se usa únicamente en tu dispositivo: las imágenes no se suben a ningún servidor.
                            Necesitas HTTPS o localhost.
                          </p>
                          <button
                            onClick={() => void startCamera()}
                            className="px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold flex items-center gap-2"
                          >
                            <Camera className="w-4 h-4" /> Activar cámara
                          </button>
                        </div>
                      )}
                    </div>
                    {cameraStream && (
                      <div className="flex gap-2">
                        <button
                          onClick={takePhoto}
                          className="px-3 py-1.5 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold flex items-center gap-2"
                        >
                          <Camera className="w-3.5 h-3.5" /> Capturar imagen
                        </button>
                        <button
                          onClick={() => {
                            stopCamera(cameraStream);
                            cameraRef.current = null;
                            setCameraStream(null);
                          }}
                          className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-2"
                        >
                          <VideoOff className="w-3.5 h-3.5" /> Detener cámara
                        </button>
                      </div>
                    )}
                  </>
                ) : (
                  <>
                    {/* Herramientas de marcación */}
                    <div className="flex flex-wrap items-center gap-1.5">
                      <button
                        onClick={() => setTool('reference-corners')}
                        className={`px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border flex items-center gap-1.5 ${
                          tool === 'reference-corners'
                            ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                            : 'bg-slate-800 border-slate-700 text-slate-300'
                        }`}
                      >
                        <Ruler className="w-3 h-3" /> 1. Referencia ({refCorners.length}/4)
                      </button>
                      <button
                        onClick={() => setTool('reference-line')}
                        className={`px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border flex items-center gap-1.5 ${
                          tool === 'reference-line'
                            ? 'bg-amber-500/20 border-amber-500/40 text-amber-300'
                            : 'bg-slate-800 border-slate-700 text-slate-300'
                        }`}
                      >
                        <Ruler className="w-3 h-3" /> Escala simple ({refLine.length}/2)
                      </button>
                      <button
                        onClick={() => setTool('polygon')}
                        className={`px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border flex items-center gap-1.5 ${
                          tool === 'polygon'
                            ? 'bg-sky-500/20 border-sky-500/40 text-sky-300'
                            : 'bg-slate-800 border-slate-700 text-slate-300'
                        }`}
                      >
                        <CornerDownRight className="w-3 h-3" /> 2. Contorno ({polygon.length})
                      </button>
                      <button
                        onClick={() => {
                          setPolygon([]);
                          setRefCorners([]);
                          setRefLine([]);
                        }}
                        className="px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border bg-slate-800 border-slate-700 text-slate-300 flex items-center gap-1.5"
                      >
                        <RotateCcw className="w-3 h-3" /> Reiniciar marcas
                      </button>
                    </div>

                    {/* Imagen con overlay de marcación */}
                    <div
                      onClick={handleCanvasClick}
                      className="relative rounded-xl overflow-hidden border border-slate-800 bg-slate-950 cursor-crosshair select-none"
                    >
                      <img src={frame.dataUrl} alt="Captura de la estancia" className="w-full h-auto block" />
                      <svg
                        className="absolute inset-0 w-full h-full"
                        viewBox={`0 0 ${frame.width} ${frame.height}`}
                        preserveAspectRatio="none"
                      >
                        {refCorners.length > 0 && (
                          <polyline
                            points={refCorners.map((p) => `${p.x},${p.y}`).join(' ')}
                            fill={refCorners.length === 4 ? 'rgba(16,185,129,0.15)' : 'none'}
                            stroke="#10b981"
                            strokeWidth={frame.width / 350}
                          />
                        )}
                        {refCorners.map((p, i) => (
                          <circle key={`rc-${i}`} cx={p.x} cy={p.y} r={frame.width / 160} fill="#10b981" />
                        ))}
                        {refLine.length === 2 && (
                          <line
                            x1={refLine[0].x}
                            y1={refLine[0].y}
                            x2={refLine[1].x}
                            y2={refLine[1].y}
                            stroke="#f59e0b"
                            strokeWidth={frame.width / 320}
                          />
                        )}
                        {refLine.map((p, i) => (
                          <circle key={`rl-${i}`} cx={p.x} cy={p.y} r={frame.width / 170} fill="#f59e0b" />
                        ))}
                        {polygon.length > 1 && (
                          <polyline
                            points={polygon.map((p) => `${p.x},${p.y}`).join(' ')}
                            fill="rgba(56,189,248,0.12)"
                            stroke="#38bdf8"
                            strokeWidth={frame.width / 320}
                          />
                        )}
                        {polygon.map((p, i) => (
                          <g key={`pg-${i}`}>
                            <circle cx={p.x} cy={p.y} r={frame.width / 170} fill="#38bdf8" />
                            <text
                              x={p.x + frame.width / 90}
                              y={p.y - frame.width / 120}
                              fill="#e0f2fe"
                              fontSize={frame.width / 55}
                              fontFamily="monospace"
                            >
                              {i + 1}
                            </text>
                          </g>
                        ))}
                      </svg>
                    </div>

                    <div className="flex items-center gap-2 text-[11px] text-slate-400">
                      <Info className="w-3.5 h-3.5 shrink-0" />
                      <span>
                        Marca primero las 4 esquinas de un elemento de medida conocida (una baldosa, dos baldosas, un
                        folio A3) y después las esquinas de la estancia. El sistema corrige la perspectiva.
                      </span>
                    </div>

                    <button
                      onClick={() => setFrame(null)}
                      className="text-[11px] text-slate-400 hover:text-sky-300 flex items-center gap-1"
                    >
                      <Camera className="w-3 h-3" /> Volver a tomar la foto
                    </button>
                  </>
                )}
              </div>
            )}

            {/* Registro de mensajes */}
            <div className="space-y-1 max-h-28 overflow-y-auto">
              {messages.map((m, i) => (
                <div
                  key={i}
                  className={`text-[11px] px-2.5 py-1.5 rounded-lg border ${
                    m.kind === 'ok'
                      ? 'bg-emerald-500/10 border-emerald-500/25 text-emerald-300'
                      : m.kind === 'warn'
                        ? 'bg-amber-500/10 border-amber-500/25 text-amber-300'
                        : m.kind === 'error'
                          ? 'bg-rose-500/10 border-rose-500/25 text-rose-300'
                          : 'bg-slate-950 border-slate-800 text-slate-400'
                  }`}
                >
                  {m.text}
                </div>
              ))}
            </div>
          </div>

          {/* Columna derecha: medidas y destino */}
          <div className="lg:col-span-5 p-4 space-y-4 overflow-y-auto">
            {/* Capacidades */}
            <section className="rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Gauge className="w-3.5 h-3.5 text-emerald-400" /> Capacidades del dispositivo
              </h3>
              {capabilities ? (
                <div className="grid grid-cols-2 gap-1.5 text-[11px] font-mono">
                  {[
                    ['Contexto seguro', capabilities.secureContext],
                    ['WebXR', capabilities.hasWebXR],
                    ['AR inmersiva', capabilities.immersiveAr],
                    ['Hit-test', capabilities.hitTest],
                    ['Dom overlay', capabilities.domOverlay],
                    ['Anchors', capabilities.anchors],
                    ['Cámara', capabilities.hasCamera],
                    ['WebRTC', capabilities.hasWebRTC]
                  ].map(([label, value]) => (
                    <div key={label as string} className="flex items-center justify-between gap-2">
                      <span className="text-slate-500">{label}</span>
                      <span className={value ? 'text-emerald-400' : 'text-slate-600'}>
                        {value ? 'sí' : 'no'}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <span className="text-[11px] text-slate-400 flex items-center gap-1">
                  <Loader2 className="w-3 h-3 animate-spin" /> Analizando…
                </span>
              )}
              {capabilities?.reasons.map((reason, i) => (
                <p key={i} className="text-[10px] text-amber-400/80 leading-snug">
                  • {reason}
                </p>
              ))}
            </section>

            {/* Referencias rápidas */}
            {mode === 'photo' && frame && (
              <section className="rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-2">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Escala de referencia</h3>
                <div className="flex flex-wrap gap-1">
                  {COMMON_REFERENCES.map((ref) => (
                    <button
                      key={ref.id}
                      onClick={() => {
                        setRefMeters(String(ref.meters));
                        setRefWidth(String(ref.meters));
                        setRefHeight(String(ref.meters));
                      }}
                      className="px-2 py-1 rounded-md bg-slate-900 hover:bg-slate-800 border border-slate-700 text-[10px] text-slate-300"
                      title={`${ref.label} = ${ref.meters} m`}
                    >
                      {ref.label}
                    </button>
                  ))}
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <label className="text-[10px] text-slate-400 space-y-1">
                    <span>Ancho ref. (m)</span>
                    <input
                      value={refWidth}
                      onChange={(e) => setRefWidth(e.target.value)}
                      inputMode="decimal"
                      className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200"
                    />
                  </label>
                  <label className="text-[10px] text-slate-400 space-y-1">
                    <span>Alto ref. (m)</span>
                    <input
                      value={refHeight}
                      onChange={(e) => setRefHeight(e.target.value)}
                      inputMode="decimal"
                      className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200"
                    />
                  </label>
                  <label className="text-[10px] text-slate-400 space-y-1">
                    <span>Línea (m)</span>
                    <input
                      value={refMeters}
                      onChange={(e) => setRefMeters(e.target.value)}
                      inputMode="decimal"
                      className="w-full bg-slate-900 border border-slate-700 rounded-md px-2 py-1 text-[11px] text-slate-200"
                    />
                  </label>
                </div>
              </section>
            )}

            {/* Resultado */}
            <section className="rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Ruler className="w-3.5 h-3.5 text-sky-400" /> Medidas obtenidas
              </h3>
              <label className="text-[11px] text-slate-400 space-y-1 block">
                <span>Altura de techo (m) — se usa si no la mediste con AR</span>
                <input
                  value={heightInput}
                  onChange={(e) => setHeightInput(e.target.value)}
                  inputMode="decimal"
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
                />
              </label>

              {scan && bbox ? (
                <div className="space-y-1.5">
                  {summaryLines.map((line, i) => (
                    <div key={i} className="text-[11px] font-mono text-slate-300 flex items-center gap-2">
                      <CheckCircle2 className="w-3 h-3 text-emerald-400 shrink-0" /> {line}
                    </div>
                  ))}
                  <div className="text-[11px] font-mono text-slate-400">
                    Lados: {scan.measurement.segments.map((s) => `${s.length.toFixed(2)}m`).join(' · ')}
                  </div>
                  {scan.measurement.warnings.map((w, i) => (
                    <div key={i} className="text-[11px] text-amber-400 flex items-start gap-2">
                      <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" /> {w}
                    </div>
                  ))}
                </div>
              ) : photoResult ? (
                <div className="space-y-1.5">
                  {summaryLines.map((line, i) => (
                    <div key={i} className="text-[11px] font-mono text-slate-300">
                      {line}
                    </div>
                  ))}
                  <div className="text-[11px] text-slate-400">
                    Error de reproyección: {(photoResult.reprojectionError * 100).toFixed(1)} cm
                  </div>
                  {photoResult.warnings.map((w, i) => (
                    <div key={i} className="text-[11px] text-amber-400 flex items-start gap-2">
                      <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" /> {w}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-[11px] text-slate-500">
                  Marca la referencia y al menos 3 esquinas del contorno para ver las medidas en vivo.
                </p>
              )}
            </section>

            {/* Destino en el proyecto */}
            <section className="rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Destino en el proyecto</h3>
              <label className="text-[11px] text-slate-400 space-y-1 block">
                <span>Nombre de la estancia</span>
                <input
                  value={roomName}
                  onChange={(e) => setRoomName(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
                />
              </label>
              <label className="text-[11px] text-slate-400 space-y-1 block">
                <span>Qué generar</span>
                <select
                  value={target}
                  onChange={(e) => setTarget(e.target.value as ScanToRoomsOptions['target'])}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
                >
                  <option value="ceiling">Sólo techo / cielo raso</option>
                  <option value="walls">Sólo muros (un paño por lado)</option>
                  <option value="both">Techo + muros</option>
                </select>
              </label>
              <div className="grid grid-cols-2 gap-2">
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Tipo de techo</span>
                  <select
                    value={ceilingType}
                    onChange={(e) => setCeilingType(e.target.value as ConstructionType)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-1.5 text-[11px] text-slate-200"
                  >
                    {CEILING_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {CONSTRUCTION_TYPES[t].name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-[11px] text-slate-400 space-y-1">
                  <span>Tipo de muro</span>
                  <select
                    value={wallType}
                    onChange={(e) => setWallType(e.target.value as ConstructionType)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-1.5 text-[11px] text-slate-200"
                  >
                    {WALL_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {CONSTRUCTION_TYPES[t].name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </section>

            {/* Asistencia remota */}
            <section className="rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Share2 className="w-3.5 h-3.5 text-violet-400" /> Asistencia remota (WebRTC)
              </h3>
              <p className="text-[11px] text-slate-400 leading-snug">
                Comparte la cámara con la oficina técnica para validar el replanteo sin desplazarte: sólo se transmite
                el vídeo entre pares (P2P cifrado con DTLS-SRTP).
              </p>
              <a
                href="/asistencia"
                className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-violet-600/90 hover:bg-violet-500 text-white text-[11px] font-semibold"
              >
                <Video className="w-3.5 h-3.5" /> Abrir sala de asistencia
              </a>
            </section>

            <button
              onClick={applyToProject}
              disabled={!scan && !photoResult}
              className="w-full px-4 py-2.5 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-semibold flex items-center justify-center gap-2"
            >
              <CheckCircle2 className="w-4 h-4" /> Añadir estancias al proyecto
            </button>
          </div>
        </div>
    </Dialog>
  );
};
