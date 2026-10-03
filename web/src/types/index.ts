export type ConstructionType =
  | 'techo_st'
  | 'techo_rh'
  | 'muro_sencillo'
  | 'tabique_divisor'
  | 'muro_rf'
  | 'plafon_reticulado'
  | 'cajillo_viga'
  | 'fachada_eifs'
  | 'steel_framing'
  | 'multi_partes';

export type UnitSystem = 'metric' | 'imperial';
export type Language = 'es' | 'en';
export type Currency = 'USD' | 'EUR' | 'MXN' | 'COP' | 'ARS' | 'CLP' | 'PEN';

export type MaterialCategory =
  | 'boards'
  | 'profiles'
  | 'fasteners'
  | 'compounds'
  | 'tapes'
  | 'insulation'
  | 'accessories';

export interface Opening {
  id: string;
  type: 'door' | 'window';
  name: string;
  width: number;
  height: number;
  count: number;
}

export interface Segment {
  id: string;
  name: string;
  length: number; // in current unit (m or ft)
  width: number;  // in current unit (m or ft) (or height for walls)
  repetitions: number;
  openings: Opening[];
}

export interface Room {
  id: string;
  name: string;
  type: ConstructionType;
  segments: Segment[];
  notes?: string;
}

export interface MaterialRequirement {
  id: string;
  name: string;
  category: MaterialCategory;
  unit: string;
  commercialFormat: string;
  quantityPerM2: number;
  rawQuantity: number;
  wastePercentage: number;
  finalQuantity: number;
  commercialUnits: number;
  unitPrice: number;
  totalPrice: number;
  availableStock: number;
  toBuyQuantity: number;
  toBuyPackages: number;
  notes: string;
}

export interface ProjectConfig {
  projectName: string;
  clientName: string;
  clientPhone: string;
  clientEmail: string;
  projectAddress: string;
  contractorName: string;
  contractorCompany: string;
  contractorPhone: string;
  contractorEmail: string;
  quoteDate: string;
  validUntil: string;
  unitSystem: UnitSystem;
  currency: Currency;
  wastePercentage: number; // default 5 - 10%
  studSpacing: number;     // in m (0.407, 0.488, 0.61) or inches (16, 24)
  profileLength: number;   // standard length in m (2.44, 3.0, 3.66)
  sheetWidth: number;      // 1.20m (4ft)
  sheetLength: number;     // 2.40m (8ft)
  laborCalculationMode: 'per_area' | 'hourly' | 'fixed';
  laborCostPerUnit: number; // cost per m² or sqft
  fixedLaborCost: number;
  taxPercentage: number;   // 0%, 16%, 19%, 21%
  profitPercentage: number;// markup 20-35%
  notes: string;
}

export interface CalculationSummary {
  totalGrossArea: number; // m² or sqft
  totalNetArea: number;   // m² or sqft after opening subtractions
  totalOpeningsArea: number;
  requirements: MaterialRequirement[];
  materialsCost: number;
  laborCost: number;
  subtotal: number;
  profitAmount: number;
  taxAmount: number;
  grandTotal: number;
  costPerUnitArea: number;
  advisoryTips: string[];
}
