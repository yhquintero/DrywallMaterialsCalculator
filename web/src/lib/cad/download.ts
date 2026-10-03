/**
 * Utilidades de descarga para los planos CAD y el envío a la cola de
 * impresión/plotter del servidor.
 */

import { CadScene, CadUnits, DxfVersion } from './types';
import { ProjectConfig } from '../../types';
import { sceneToDxf } from './exporter';

const UNIT_LABEL: Record<CadUnits, string> = {
  mm: 'mm',
  cm: 'cm',
  m: 'm',
  in: 'in',
  ft: 'ft'
};

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i += 1;
  }
  return `${value.toFixed(value >= 100 || i === 0 ? 0 : 1)} ${units[i]}`;
}

/** Metadatos que acompañan al DXF cuando se envía a la cola del servidor. */
export interface PlotRequest {
  filename: string;
  dxf: string;
  units: CadUnits;
  version: DxfVersion;
  scaleDenominator: number;
  paper: string;
  project: {
    name: string;
    client: string;
    contractor: string;
    currency: string;
  };
}

export function buildPlotRequest(
  scene: CadScene,
  options: { version: DxfVersion; units: CadUnits },
  config: ProjectConfig
): PlotRequest {
  return {
    filename: `${scene.name.replace(/[^\w\-]+/g, '_')}_${UNIT_LABEL[options.units]}_1-${scene.scaleDenominator}.dxf`,
    dxf: sceneToDxf(scene, { version: options.version, units: options.units, sheetIndex: null }),
    units: options.units,
    version: options.version,
    scaleDenominator: scene.scaleDenominator,
    paper: scene.paper,
    project: {
      name: config.projectName,
      client: config.clientName,
      contractor: config.contractorCompany || config.contractorName,
      currency: config.currency
    }
  };
}

/**
 * Envía el plano a la cola de impresión del backend (plotter / reprografía).
 * Degrada a descarga local si el servidor no tiene el endpoint habilitado.
 */
export function downloadDxfOnServer(
  scene: CadScene,
  options: { version: DxfVersion; units: CadUnits },
  config: ProjectConfig
): void {
  const request = buildPlotRequest(scene, options, config);
  void fetch('/api/cad/plot', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  })
    .then(async (res) => {
      if (!res.ok) throw new Error(String(res.status));
      const payload = (await res.json()) as { downloadUrl?: string; queued?: boolean };
      if (payload.downloadUrl) {
        window.open(payload.downloadUrl, '_blank', 'noopener');
        return;
      }
      // Sin cola configurada: descarga local del DXF.
      const blob = new Blob([request.dxf], { type: 'application/dxf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = request.filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    })
    .catch(() => {
      const blob = new Blob([request.dxf], { type: 'application/dxf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = request.filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    });
}
