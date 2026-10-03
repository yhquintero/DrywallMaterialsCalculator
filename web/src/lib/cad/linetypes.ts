/**
 * Catálogo de capas, tipos de línea y estilos de texto normalizados para los
 * planos de drywall exportados a DXF/DWG.
 *
 * La nomenclatura sigue la convención "DISCIPLINA-ELEMENTO" para que un
 * proyectista pueda congelar/descongelar capas rápidamente en AutoCAD.
 */

export interface CadLayerDef {
  name: string;
  /** AutoCad Color Index (ACI). */
  color: number;
  linetype: string;
  description: string;
}

export const CAD_LAYERS: Record<string, CadLayerDef> = {
  ARQ_MURO: { name: 'ARQ-MURO', color: 7, linetype: 'CONTINUOUS', description: 'Contorno de muros y tabiques' },
  DW_PERFIL: { name: 'DW-PERFIL', color: 4, linetype: 'CONTINUOUS', description: 'Parantes / montantes metálicos' },
  DW_CANAL: { name: 'DW-CANAL', color: 5, linetype: 'CONTINUOUS', description: 'Canales, soleras y ángulos perimetrales' },
  DW_OMEGA: { name: 'DW-OMEGA', color: 140, linetype: 'CONTINUOUS', description: 'Perfiles omega / furring de cielo raso' },
  DW_PRIMARIO: { name: 'DW-PRIMARIO', color: 30, linetype: 'CONTINUOUS', description: 'Perfiles primarios y suspensión' },
  DW_PLACA: { name: 'DW-PLACA', color: 3, linetype: 'CONTINUOUS', description: 'Modulación de placas de yeso' },
  DW_AISLAMIENTO: { name: 'DW-AISLANTE', color: 40, linetype: 'CONTINUOUS', description: 'Lana mineral / relleno (trama)' },
  DW_ABERTURAS: { name: 'DW-ABERTURA', color: 2, linetype: 'CONTINUOUS', description: 'Puertas, ventanas y refuerzos' },
  DW_FIJACION: { name: 'DW-FIJACION', color: 8, linetype: 'CONTINUOUS', description: 'Patrón de tornillería y fijaciones' },
  DW_EJE: { name: 'DW-EJE', color: 1, linetype: 'CENTER', description: 'Ejes de replanteo y modulación' },
  DW_COTA: { name: 'DW-COTA', color: 6, linetype: 'CONTINUOUS', description: 'Cotas y líneas de acotación' },
  DW_TEXTO: { name: 'DW-TEXTO', color: 7, linetype: 'CONTINUOUS', description: 'Rotulación técnica' },
  DW_SIMBOLO: { name: 'DW-SIMBOLO', color: 7, linetype: 'CONTINUOUS', description: 'Símbolos de bloque (tornillo, anclaje)' },
  PLANO_MARCO: { name: 'PLANO-MARCO', color: 7, linetype: 'CONTINUOUS', description: 'Marco de lámina y cajetín' },
  PLANO_TABLA: { name: 'PLANO-TABLA', color: 7, linetype: 'CONTINUOUS', description: 'Cuadro de cómputo de materiales' }
};

export const LAYER_ORDER = Object.values(CAD_LAYERS).map((l) => l.name);

/** Tipos de línea (patrones en unidades de dibujo relativas). */
export interface CadLinetypeDef {
  name: string;
  description: string;
  /** Longitudes de los elementos: >0 trazo, <0 hueco, 0 punto. */
  pattern: number[];
  totalLength: number;
}

export const CAD_LINETYPES: CadLinetypeDef[] = [
  { name: 'CONTINUOUS', description: 'Línea continua ______', pattern: [], totalLength: 0 },
  { name: 'DASHED', description: 'Línea discontinua _ _ _ _', pattern: [0.5, -0.25], totalLength: 0.75 },
  { name: 'CENTER', description: 'Eje __ _ __ _ __', pattern: [1.25, -0.25, 0.25, -0.25], totalLength: 2.0 },
  { name: 'HIDDEN', description: 'Oculta __ __ __', pattern: [0.25, -0.125], totalLength: 0.375 },
  { name: 'PHANTOM', description: 'Fantasma _____ _ _____', pattern: [1.25, -0.25, 0.25, -0.25, 0.25, -0.25], totalLength: 2.5 },
  { name: 'DOTTED', description: 'Punteada . . . .', pattern: [0.0, -0.25], totalLength: 0.25 }
];

/** Estilos de texto SHX estándar de AutoCAD (con respaldo a TTF). */
export const CAD_TEXT_STYLES = [
  { name: 'STANDARD', font: 'txt.shx', widthFactor: 1.0, oblique: 0 },
  { name: 'ROMANS', font: 'romans.shx', widthFactor: 0.85, oblique: 0 },
  { name: 'ISOCP', font: 'isocp.shx', widthFactor: 1.0, oblique: 0 },
  { name: 'TECHNICAL', font: 'monotxt.shx', widthFactor: 1.0, oblique: 0 }
];

/** Devuelve el nombre de capa seguro (sin caracteres inválidos en DXF). */
export function safeLayerName(name: string): string {
  const cleaned = String(name || '0')
    .toUpperCase()
    .replace(/[^A-Z0-9_\-]/g, '-')
    .slice(0, 31);
  return cleaned || '0';
}
