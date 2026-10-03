/**
 * Medición por cámara (WebRTC / getUserMedia) para dispositivos sin WebXR.
 *
 * Dos estrategias, elegibles por el usuario:
 *
 *  A) Escala por referencia: se dibuja una línea sobre un objeto de longitud
 *     conocida (una baldosa, un zócalo, el ancho de una puerta). Rápido, pero
 *     asume que la foto es aproximadamente paralela al plano medido.
 *
 *  B) Homografía de suelo: se marcan las 4 esquinas de un rectángulo de
 *     dimensiones conocidas en el suelo (p. ej. dos baldosas de 60 × 60 cm).
 *     Corrige la perspectiva y da resultados fiables incluso en fotos
 *     oblicuas. Es la que se recomienda en obra.
 */

import {
  Matrix3,
  Point2,
  applyHomography,
  homographyError,
  homographyFromReference
} from './homography';
import { RoomMeasurement, RoomScan, ScanPoint, Vec3 } from './types';
import { measureRoom, polygonArea2D, polygonPerimeter } from './measurement';

export interface CameraConstraints {
  facingMode?: 'environment' | 'user';
  width?: number;
  height?: number;
}

/** Abre la cámara trasera con la máxima resolución razonable. */
export async function openCamera(constraints: CameraConstraints = {}): Promise<MediaStream> {
  if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
    throw new Error('Este navegador no expone navigator.mediaDevices.getUserMedia.');
  }
  if (typeof window !== 'undefined' && !window.isSecureContext && window.location.protocol !== 'https:') {
    throw new Error('La cámara sólo está disponible en contextos seguros (HTTPS o localhost).');
  }
  const stream = await navigator.mediaDevices.getUserMedia({
    video: {
      facingMode: constraints.facingMode ?? 'environment',
      width: { ideal: constraints.width ?? 1920 },
      height: { ideal: constraints.height ?? 1080 }
    },
    audio: false
  });
  return stream;
}

export function stopCamera(stream: MediaStream | null): void {
  stream?.getTracks().forEach((track) => track.stop());
}

export interface CapturedFrame {
  dataUrl: string;
  width: number;
  height: number;
}

/** Congela el vídeo en un fotograma (canvas) y devuelve su DataURL. */
export function captureFrame(video: HTMLVideoElement, maxWidth = 1920): CapturedFrame | null {
  const vw = video.videoWidth;
  const vh = video.videoHeight;
  if (!vw || !vh) return null;
  const ratio = Math.min(1, maxWidth / vw);
  const width = Math.round(vw * ratio);
  const height = Math.round(vh * ratio);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;
  ctx.drawImage(video, 0, 0, width, height);
  return { dataUrl: canvas.toDataURL('image/jpeg', 0.92), width, height };
}

export type ReferenceMode =
  | { kind: 'homography'; corners: Point2[]; widthMeters: number; heightMeters: number }
  | { kind: 'scale'; from: Point2; to: Point2; meters: number };

export interface PhotoMeasureInput {
  /** Contorno de la estancia marcado sobre la foto, en píxeles. */
  polygonPx: Point2[];
  reference: ReferenceMode;
  /** Altura de techo para el cálculo de superficie de muros (m). */
  height: number;
  /** ¿El contorno está cerrado (último punto = primero)? */
  closed?: boolean;
}

export interface PhotoMeasureResult {
  /** Contorno en metros sobre el plano del suelo (y = 0). */
  polygon: Vec3[];
  measurement: RoomMeasurement;
  /** Error medio de reproyección en metros (sólo homografía). */
  reprojectionError: number;
  homography: Matrix3 | null;
  warnings: string[];
}

/** Convierte el contorno en píxeles a metros y calcula las magnitudes. */
export function measureFromPhoto(input: PhotoMeasureInput): PhotoMeasureResult {
  const warnings: string[] = [];
  const polygonPx = input.polygonPx ?? [];
  if (polygonPx.length < 3) {
    return {
      polygon: [],
      measurement: measureRoom([], input.height),
      reprojectionError: 0,
      homography: null,
      warnings: ['Marca al menos 3 esquinas de la estancia sobre la foto.']
    };
  }

  let real: Point2[] = [];
  let homography: Matrix3 | null = null;
  let reprojectionError = 0;

  if (input.reference.kind === 'homography') {
    const { corners, widthMeters, heightMeters } = input.reference;
    if (corners.length !== 4) {
      throw new Error('La homografía requiere las 4 esquinas del rectángulo de referencia.');
    }
    if (widthMeters <= 0.02 || heightMeters <= 0.02) {
      throw new Error('Las dimensiones del rectángulo de referencia deben ser mayores de 2 cm.');
    }
    homography = homographyFromReference(corners, widthMeters, heightMeters);
    real = polygonPx.map((p) => applyHomography(homography as Matrix3, p));
    const dst: Point2[] = [
      { x: 0, y: 0 },
      { x: widthMeters, y: 0 },
      { x: widthMeters, y: heightMeters },
      { x: 0, y: heightMeters }
    ];
    reprojectionError = homographyError(homography, corners, dst);
    if (reprojectionError > 0.05) {
      warnings.push(
        `El rectángulo de referencia no encaja bien (error ${(reprojectionError * 100).toFixed(1)} cm). Repite la marcación.`
      );
    }
  } else {
    const { from, to, meters } = input.reference;
    const pixelDistance = Math.hypot(to.x - from.x, to.y - from.y);
    if (pixelDistance < 1) throw new Error('La línea de referencia es demasiado corta.');
    if (meters <= 0.02) throw new Error('La longitud de referencia debe ser mayor de 2 cm.');
    const metersPerPixel = meters / pixelDistance;
    const origin = polygonPx[0];
    real = polygonPx.map((p) => ({
      x: (p.x - origin.x) * metersPerPixel,
      y: (p.y - origin.y) * metersPerPixel
    }));
    warnings.push(
      'Medición con escala simple: la foto debe estar lo más paralela posible al plano medido. Para mayor precisión usa el modo homografía.'
    );
  }

  // En foto, el eje Y de la imagen se interpreta como profundidad (z).
  const polygon: Vec3[] = real.map((p) => ({ x: p.x, y: 0, z: p.y }));
  const measurement = measureRoom(polygon, input.height, { closed: input.closed ?? true });

  if (measurement.perimeter < 1) warnings.push('El contorno medido es demasiado pequeño: revisa la referencia.');

  return {
    polygon,
    measurement,
    reprojectionError: Number(reprojectionError.toFixed(4)),
    homography,
    warnings: [...warnings, ...measurement.warnings]
  };
}

/** Construye un `RoomScan` a partir del resultado de la medición por foto. */
export function buildPhotoScan(
  result: PhotoMeasureResult,
  name: string,
  height: number,
  thumbnail?: string
): RoomScan {
  const now = Date.now();
  const points: ScanPoint[] = result.polygon.map((p, i) => ({
    id: `photo_${i}`,
    position: p,
    kind: 'floor' as const,
    timestamp: now,
    source: 'photo' as const
  }));
  return {
    id: `scan_photo_${now.toString(36)}`,
    name,
    createdAt: new Date(now).toISOString(),
    source: 'photo',
    points,
    polygon: result.polygon,
    measurement: result.measurement,
    measuredHeight: height,
    notes: `Captura por cámara. Error de reproyección: ${(result.reprojectionError * 100).toFixed(1)} cm.`,
    thumbnail
  };
}

/** Referencias habituales en obra para calibrar la escala. */
export const COMMON_REFERENCES: Array<{ id: string; label: string; meters: number }> = [
  { id: 'tile60', label: 'Baldosa de 60 × 60 cm', meters: 0.6 },
  { id: 'tile40', label: 'Baldosa de 40 × 40 cm', meters: 0.4 },
  { id: 'a4', label: 'Folio A4 (29.7 cm)', meters: 0.297 },
  { id: 'a3', label: 'Folio A3 (42 cm)', meters: 0.42 },
  { id: 'door', label: 'Ancho de puerta estándar (82.5 cm)', meters: 0.825 },
  { id: 'door_height', label: 'Alto de puerta estándar (2.03 m)', meters: 2.03 },
  { id: 'tape1m', label: 'Metro plegado (1 m)', meters: 1.0 },
  { id: 'step', label: 'Huella de escalón (28 cm)', meters: 0.28 }
];

/** Utilidad: perímetro y área del contorno en píxeles (para la UI en vivo). */
export function pixelContourStats(polygonPx: Point2[]): { perimeterPx: number; areaPx: number } {
  const asVec = polygonPx.map((p) => ({ x: p.x, y: 0, z: p.y }));
  return {
    perimeterPx: Number(polygonPerimeter(asVec, true).toFixed(2)),
    areaPx: Number(polygonArea2D(asVec).toFixed(2))
  };
}
