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

const STORAGE_KEY = 'drywall_calculator_projects_v2';
const ACTIVE_PROJECT_KEY = 'drywall_calculator_active_id';

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

export function getSavedProjects(): SavedProject[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    // Descontaminar prototipo
    return parsed.map((item) => sanitizeAgainstPrototypePollution(item));
  } catch (e) {
    console.error('Error loading projects from storage:', e);
    return [];
  }
}

export async function saveProjectToStorage(project: SavedProject): Promise<void> {
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

    // Calcular checksum criptográfico
    const checksum = await calculateSha256({
      config: cleanProject.config,
      rooms: cleanProject.rooms,
      customPrices: cleanProject.customPrices,
      customStock: cleanProject.customStock
    });

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

    localStorage.setItem(STORAGE_KEY, JSON.stringify(existing));
    localStorage.setItem(ACTIVE_PROJECT_KEY, project.id);
  } catch (e) {
    console.error('Error saving project to storage:', e);
  }
}

export function getActiveProjectId(): string | null {
  return localStorage.getItem(ACTIVE_PROJECT_KEY);
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

  const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
  const encodedUri = encodeURI(csvContent);
  const link = document.createElement('a');
  link.setAttribute('href', encodedUri);
  link.setAttribute('download', `computo_materiales_drywall_${Date.now()}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
