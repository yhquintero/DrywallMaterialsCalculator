/**
 * Tipos del módulo de escaneo de estancias (WebXR + cámara/WebRTC).
 */

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

export type ScanPointKind = 'floor' | 'wall-top' | 'ceiling' | 'reference';

export interface ScanPoint {
  id: string;
  /** Posición en metros, en el espacio de referencia de la sesión. */
  position: Vec3;
  kind: ScanPointKind;
  timestamp: number;
  source: 'webxr' | 'photo';
  /** Radio estimado de error del hit-test (metros), si el dispositivo lo aporta. */
  accuracy?: number;
}

export interface WallSegmentMeasure {
  index: number;
  /** Longitud del paño en metros. */
  length: number;
  /** Altura asignada al paño en metros. */
  height: number;
  /** Ángulo interior en grados respecto al paño anterior. */
  cornerAngle: number;
  /** Error de cierre relativo al final del recorrido (0 = polígono perfecto). */
  closureError: number;
}

export interface RoomMeasurement {
  /** Perímetro del polígono de suelo (m). */
  perimeter: number;
  /** Área de suelo/techo (m²). */
  floorArea: number;
  /** Área total de muros (m²) = perímetro × altura. */
  wallArea: number;
  /** Altura utilizada para los muros (m). */
  height: number;
  /** Lados del polígono ya rectificados. */
  segments: WallSegmentMeasure[];
  /** Rectángulo envolvente alineado con el lado más largo (m). */
  bounding: { length: number; width: number };
  /** Distancia entre el primer y el último punto (m). Idealmente ≈ 0. */
  closureGap: number;
  /** Error de cierre relativo (0 – 1). */
  closureRatio: number;
  /** Índice de confianza global (0 – 1). */
  confidence: number;
  /** Avisos para el usuario (p. ej. "polígono no cerrado"). */
  warnings: string[];
}

export interface RoomScan {
  id: string;
  name: string;
  createdAt: string;
  source: 'webxr' | 'photo';
  /** Puntos capturados en crudo. */
  points: ScanPoint[];
  /** Polígono de suelo en metros (proyectado sobre el plano de ajuste). */
  polygon: Vec3[];
  measurement: RoomMeasurement;
  /** Altura del techo medida o estimada (m). */
  measuredHeight?: number;
  notes?: string;
  /** Miniatura de la captura (solo en modo foto). */
  thumbnail?: string;
}

export type ScanTarget = 'ceiling' | 'walls' | 'both';

export interface ScanToRoomsOptions {
  target: ScanTarget;
  ceilingType: import('../../types').ConstructionType;
  wallType: import('../../types').ConstructionType;
  /** Altura por defecto si no se midió (m). */
  defaultHeight: number;
  unitSystem: 'metric' | 'imperial';
  roomName: string;
  /** Redondeo aplicado a las medidas (m). */
  roundTo: number;
}
