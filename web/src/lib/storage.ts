import { ProjectConfig, Room } from '../types';
import { sanitizeAgainstPrototypePollution, calculateSha256 } from '../security/cryptoStorage';
import { sanitizeCsvCell } from '../security/csvSanitizer';
import { detectSqlInjection, sanitizeAlphaNumericSafe } from '../security/sqlSanitizer';
import { detectXss } from '../security/xssDefense';

export interface SavedProject {
  id: string;
  updatedAt: string;
  checksum?: string;
  config: ProjectConfig;
  rooms: Room[];
  customPrices: Record<string, number>;
  customStock: Record<string, number>;
}

/** Resultado de la verificación SHA-256 de un proyecto guardado. */
export type IntegrityStatus = 'verified' | 'mismatch' | 'unverified';

export interface ProjectIntegrity {
  status: IntegrityStatus;
  expected?: string;
  current?: string;
}

export type SavedProjectWithIntegrity = SavedProject & { integrity: ProjectIntegrity };

export type SaveResult = { ok: true; checksum: string } | { ok: false; error: string };

const STORAGE_KEY = 'drywall_calculator_projects_v2';
const ACTIVE_PROJECT_KEY = 'drywall_calculator_active_id';
/** Tope de proyectos locales: evita llenar la cuota de LocalStorage. */
const MAX_STORED_PROJECTS = 50;

export const DEFAULT_CONFIG: ProjectConfig = {
  projectName: 'Residencial Los Álamos - Proyecto Reforma',
  clientName: 'Ing. Carlos Mendoza',
  clientPhone: '+34 612 345 678',
  clientEmail: 'cmendoza@constructora.com',
  projectAddress: 'Av. Libertador 450, Piso 3',
  contractorName: 'DrywallPro Soluciones Técnicas',
  contractorCompany: 'Drywall & Steel Master SL',
  contractorPhone: '+34 910 000 111',
  contractorEmail: 'contacto@drywallmaster.pro',
  quoteDate: new Date().toISOString().split('T')[0],
  validUntil: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
  unitSystem: 'metric',
  currency: 'USD',
  wastePercentage: 8,
  studSpacing: 0.407,
  profileLength: 3.0,
  sheetWidth: 1.20,
  sheetLength: 2.40,
  laborCalculationMode: 'per_area',
  laborCostPerUnit: 14.50,
  fixedLaborCost: 0,
  taxPercentage: 16,
  profitPercentage: 25,
  notes: 'Presupuesto incluye suministro, acarreo interno, instalación de perfilería conforme a normativa ASTM C840 y tratamiento de juntas hasta Nivel 4 listo para pintar.'
};

export const DEFAULT_ROOMS: Room[] = [
  {
    id: 'room_1',
    name: 'Salón Principal - Techo Continuo ST',
    type: 'techo_st',
    segments: [
      {
        id: 'seg_1',
        name: 'Paño Central',
        length: 6.5,
        width: 4.8,
        repetitions: 1,
        openings: []
      }
    ],
    notes: 'Cielo raso suspendido a 2.70m con perfilería primario y omega.'
  },
  {
    id: 'room_2',
    name: 'Dormitorio - Tabique Divisor Acústico',
    type: 'tabique_divisor',
    segments: [
      {
        id: 'seg_2',
        name: 'Pared Divisoria',
        length: 5.2,
        width: 2.8,
        repetitions: 1,
        openings: [
          {
            id: 'op_1',
            type: 'door',
            name: 'Puerta Paso',
            width: 0.82,
            height: 2.05,
            count: 1
          }
        ]
      }
    ],
    notes: 'Tabique doble cara con lana de vidrio 50mm acústica.'
  },
  {
    id: 'room_3',
    name: 'Baño Principal - Techo RH Placa Verde',
    type: 'techo_rh',
    segments: [
      {
        id: 'seg_3',
        name: 'Techo Baño',
        length: 2.8,
        width: 2.2,
        repetitions: 1,
        openings: []
      }
    ],
    notes: 'Placa resistente a la humedad con tornillería zincada.'
  }
];

// ── Integridad criptográfica ─────────────────────────────────────────────────

/**
 * Payload EXACTO que se firma con SHA-256.
 *
 * Guardado y verificación deben serializar el mismo objeto y en el mismo orden,
 * de lo contrario el checksum nunca coincidiría (defecto detectado en la
 * revisión técnica: `verifyProjectIntegrity()` firmaba el proyecto completo,
 * incluido el propio campo `checksum`).
 */
function checksumPayload(project: SavedProject): Record<string, unknown> {
  return {
    config: project.config,
    rooms: project.rooms,
    customPrices: project.customPrices || {},
    customStock: project.customStock || {}
  };
}

/** Checksum SHA-256 del contenido funcional del proyecto (sin metadatos). */
export async function calculateProjectChecksum(project: SavedProject): Promise<string> {
  return calculateSha256(checksumPayload(project));
}

/** Verifica que un proyecto guardado no haya sido manipulado en disco. */
export async function verifyProjectChecksum(project: SavedProject): Promise<ProjectIntegrity> {
  if (!project?.checksum) return { status: 'unverified' };
  try {
    const current = await calculateProjectChecksum(project);
    return {
      status: current === project.checksum ? 'verified' : 'mismatch',
      expected: project.checksum,
      current
    };
  } catch {
    return { status: 'unverified' };
  }
}

/** Normaliza un registro crudo de LocalStorage y descarta entradas corruptas. */
function normalizeStoredProject(raw: unknown): SavedProject | null {
  if (!raw || typeof raw !== 'object') return null;
  const clean = sanitizeAgainstPrototypePollution(raw) as Partial<SavedProject>;
  if (!clean.id || !clean.config || !Array.isArray(clean.rooms)) return null;
  return {
    ...(clean as SavedProject),
    updatedAt: typeof clean.updatedAt === 'string' ? clean.updatedAt : new Date().toISOString(),
    customPrices: clean.customPrices || {},
    customStock: clean.customStock || {}
  };
}

export function getSavedProjects(): SavedProject[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((item) => normalizeStoredProject(item))
      .filter((item): item is SavedProject => item !== null);
  } catch (e) {
    console.error('Error loading projects from storage:', e);
    return [];
  }
}

/**
 * Carga los proyectos y verifica su integridad SHA-256, de modo que la interfaz
 * pueda avisar si un proyecto fue modificado fuera de la aplicación.
 */
export async function loadProjectsWithIntegrity(): Promise<SavedProjectWithIntegrity[]> {
  const projects = getSavedProjects();
  return Promise.all(
    projects.map(async (project) => ({ ...project, integrity: await verifyProjectChecksum(project) }))
  );
}

export async function saveProjectToStorage(project: SavedProject): Promise<SaveResult> {
  try {
    const cleanProject = sanitizeAgainstPrototypePollution(project);

    // Sanitizar nombres de texto contra inyección SQL y XSS
    if (cleanProject.config) {
      const sqlCheck = detectSqlInjection(cleanProject.config.projectName);
      const xssCheck = detectXss(cleanProject.config.projectName);
      if (sqlCheck.isSuspicious || xssCheck.hasThreat) {
        cleanProject.config.projectName = sanitizeAlphaNumericSafe(cleanProject.config.projectName, 100);
      }
    }

    const checksum = await calculateProjectChecksum(cleanProject);

    const projectWithChecksum: SavedProject = {
      ...cleanProject,
      checksum,
      updatedAt: new Date().toISOString()
    };

    const existing = getSavedProjects();
    const index = existing.findIndex((p) => p.id === project.id);
    if (index >= 0) {
      existing[index] = projectWithChecksum;
    } else {
      existing.unshift(projectWithChecksum);
    }

    // Cuota: se conservan los proyectos más recientes para no perder el guardado.
    const trimmed = existing.slice(0, MAX_STORED_PROJECTS);

    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
    } catch (quotaError) {
      // Segundo intento con un único proyecto (el actual) antes de rendirse.
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([projectWithChecksum]));
      } catch {
        return {
          ok: false,
          error: 'No hay espacio en el almacenamiento local del navegador para guardar la obra.'
        };
      }
    }

    localStorage.setItem(ACTIVE_PROJECT_KEY, project.id);
    return { ok: true, checksum };
  } catch (e) {
    console.error('Error saving project to storage:', e);
    return { ok: false, error: 'No se pudo guardar la obra en el almacenamiento local.' };
  }
}

export function getActiveProjectId(): string | null {
  try {
    return localStorage.getItem(ACTIVE_PROJECT_KEY);
  } catch {
    return null;
  }
}

/**
 * Elimina un proyecto y repara el puntero de proyecto activo si apuntaba a él.
 * Devuelve la lista restante para que la interfaz se refresque sin recargar.
 */
export function deleteProjectFromStorage(id: string): SavedProject[] {
  const remaining = getSavedProjects().filter((p) => p.id !== id);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(remaining));
    if (getActiveProjectId() === id) {
      if (remaining[0]) {
        localStorage.setItem(ACTIVE_PROJECT_KEY, remaining[0].id);
      } else {
        localStorage.removeItem(ACTIVE_PROJECT_KEY);
      }
    }
  } catch (e) {
    console.error('Error deleting project from storage:', e);
  }
  return remaining;
}

export function exportProjectToJson(project: SavedProject): void {
  const cleanProject = sanitizeAgainstPrototypePollution(project);
  const jsonStr = JSON.stringify(cleanProject, null, 2);
  const blob = new Blob([jsonStr], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  const safeName = (project.config.projectName || 'proyecto').replace(/[^a-zA-Z0-9_-]/g, '_').toLowerCase();
  a.download = `drywall_proyecto_${safeName}.json`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function exportMaterialsToCSV(
  requirements: Array<{
    name: string;
    category: string;
    commercialUnits: number;
    commercialFormat: string;
    unitPrice: number;
    totalPrice: number;
    availableStock: number;
    toBuyQuantity: number;
  }>,
  currency: string = 'USD'
): void {
  // Headers protegidos
  const headers = [
    sanitizeCsvCell('Material'),
    sanitizeCsvCell('Categoria'),
    sanitizeCsvCell('Cant. Requerida'),
    sanitizeCsvCell('Formato Comercial'),
    sanitizeCsvCell('Stock Actual'),
    sanitizeCsvCell('A Comprar'),
    sanitizeCsvCell(`Precio Unit (${currency})`),
    sanitizeCsvCell(`Total (${currency})`)
  ];

  // Cada celda es rigurosamente sanitizada contra Formula Injection (OWASP)
  const rows = requirements.map((r) => [
    sanitizeCsvCell(r.name),
    sanitizeCsvCell(r.category),
    sanitizeCsvCell(r.commercialUnits),
    sanitizeCsvCell(r.commercialFormat),
    sanitizeCsvCell(r.availableStock),
    sanitizeCsvCell(r.toBuyQuantity),
    sanitizeCsvCell(r.unitPrice.toFixed(2)),
    sanitizeCsvCell(r.totalPrice.toFixed(2))
  ]);

  // Blob + BOM UTF-8 + CRLF: compatible con Excel y sin el límite de longitud
  // de las URLs `data:` (que truncaba cómputos grandes).
  const csvBody = [headers.join(','), ...rows.map((e) => e.join(','))].join('\r\n');
  const blob = new Blob(['\uFEFF' + csvBody], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', `computo_materiales_drywall_${Date.now()}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
