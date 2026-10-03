/**
 * Convierte un escaneo (AR o foto) en estancias del proyecto, listas para que
 * el motor de cálculo las procese exactamente igual que una captura manual.
 */

import { Opening, Room, Segment } from '../../types';
import { RoomScan, ScanToRoomsOptions } from './types';
import { roundToStep } from './measurement';

const M_TO_FT = 3.2808399;

function toUnit(meters: number, unitSystem: 'metric' | 'imperial', roundTo: number): number {
  const value = unitSystem === 'imperial' ? meters * M_TO_FT : meters;
  return Number(roundToStep(value, unitSystem === 'imperial' ? 0.25 : roundTo).toFixed(2));
}

function makeId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

/** ¿El polígono parece rectangular? (todos los ángulos interiores ≈ 90°). */
export function isRectangular(scan: RoomScan, toleranceDeg = 8): boolean {
  const { segments } = scan.measurement;
  if (segments.length !== 4) return false;
  return segments.every((s) => Math.abs(s.cornerAngle - 90) <= toleranceDeg);
}

/**
 * Genera las estancias a partir del escaneo.
 *
 * - `ceiling` → una estancia de techo con el rectángulo envolvente.
 * - `walls`   → una estancia de tabique con un paño por cada lado del polígono.
 * - `both`    → las dos anteriores.
 */
export function scanToRooms(scan: RoomScan, options: ScanToRoomsOptions): Room[] {
  const { measurement } = scan;
  const rooms: Room[] = [];
  const height = scan.measuredHeight && scan.measuredHeight > 0 ? scan.measuredHeight : options.defaultHeight;
  const unitSystem = options.unitSystem;
  const roundTo = options.roundTo > 0 ? options.roundTo : 0.05;

  if (!measurement.segments.length && !measurement.bounding.length) return rooms;

  if (options.target === 'ceiling' || options.target === 'both') {
    const length = toUnit(measurement.bounding.length, unitSystem, roundTo);
    const width = toUnit(measurement.bounding.width, unitSystem, roundTo);
    const segment: Segment = {
      id: makeId('seg'),
      name: 'Superficie escaneada',
      length,
      width,
      repetitions: 1,
      openings: []
    };
    rooms.push({
      id: makeId('room'),
      name: `${options.roomName} — Techo`,
      type: options.ceilingType,
      segments: [segment],
      notes: `Generado por escaneo (${scan.source === 'webxr' ? 'WebXR' : 'cámara'}) · área ${measurement.floorArea.toFixed(2)} m² · confianza ${(measurement.confidence * 100).toFixed(0)}%`
    });
  }

  if (options.target === 'walls' || options.target === 'both') {
    const walls: Segment[] = measurement.segments
      .filter((s) => s.length >= 0.3)
      .map((s, i) => ({
        id: makeId('seg'),
        name: `Paño ${i + 1} (${s.length.toFixed(2)} m)`,
        length: toUnit(s.length, unitSystem, roundTo),
        width: toUnit(height, unitSystem, roundTo),
        repetitions: 1,
        openings: [] as Opening[]
      }));

    if (walls.length) {
      rooms.push({
        id: makeId('room'),
        name: `${options.roomName} — Muros`,
        type: options.wallType,
        segments: walls,
        notes: `Perímetro ${measurement.perimeter.toFixed(2)} m · altura ${height.toFixed(2)} m · confianza ${(measurement.confidence * 100).toFixed(0)}%`
      });
    }
  }

  return rooms;
}

/** Resumen legible del escaneo para mostrar en la interfaz. */
export function scanSummary(scan: RoomScan, unitSystem: 'metric' | 'imperial'): string[] {
  const m = scan.measurement;
  const unit = unitSystem === 'imperial' ? 'sq ft' : 'm²';
  const area = unitSystem === 'imperial' ? m.floorArea * M_TO_FT * M_TO_FT : m.floorArea;
  const lines = [
    `Área de suelo/techo: ${area.toFixed(2)} ${unit}`,
    `Perímetro: ${m.perimeter.toFixed(2)} m (${m.segments.length} lados)`,
    `Rectángulo envolvente: ${m.bounding.length.toFixed(2)} × ${m.bounding.width.toFixed(2)} m`,
    `Altura de muro: ${m.height.toFixed(2)} m`,
    `Superficie de muros: ${m.wallArea.toFixed(2)} m²`,
    `Confianza: ${(m.confidence * 100).toFixed(0)}%`
  ];
  if (m.closureGap > 0.001) lines.push(`Desfase de cierre: ${(m.closureGap * 100).toFixed(1)} cm`);
  return lines;
}
