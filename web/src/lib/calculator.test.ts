import { describe, it, expect } from 'vitest';
import { calculateProjectMaterials } from './calculator';
import { ProjectConfig, Room } from '../types';
import { DEFAULT_CONFIG } from './storage';

describe('DrywallPro Materials Calculation Engine', () => {
  const baseConfig: ProjectConfig = {
    ...DEFAULT_CONFIG,
    wastePercentage: 10,
    unitSystem: 'metric',
    currency: 'USD',
    taxPercentage: 16,
    profitPercentage: 25,
    laborCostPerUnit: 10
  };

  it('calculates standard ceiling materials accurately for a single room', () => {
    const room: Room = {
      id: 'r1',
      name: 'Habitación 1',
      type: 'techo_st',
      segments: [
        {
          id: 's1',
          name: 'Techo',
          length: 5.0,
          width: 4.0, // 20 m²
          repetitions: 1,
          openings: []
        }
      ]
    };

    const summary = calculateProjectMaterials([room], baseConfig);

    expect(summary.totalGrossArea).toBe(20);
    expect(summary.totalNetArea).toBe(20);
    expect(summary.totalOpeningsArea).toBe(0);

    // Boards: 20 m² * 0.35 = 7 raw. With 10% waste = 7.7. Ceil to 8 commercial sheets.
    const boards = summary.requirements.find((r) => r.category === 'boards');
    expect(boards).toBeDefined();
    expect(boards?.rawQuantity).toBe(7);
    expect(boards?.finalQuantity).toBe(7.7);
    expect(boards?.commercialUnits).toBe(8);

    // Primary profiles: 20 * 0.85 = 17 raw. With 10% waste = 18.7. 18.7 / 3m = 6.23 -> 7 tiras
    const primary = summary.requirements.find((r) => r.name.includes('Perfil Primario'));
    expect(primary).toBeDefined();
    expect(primary?.rawQuantity).toBe(17);
    expect(primary?.commercialUnits).toBe(7);

    // Labor cost: 20 m² * $10 = $200
    expect(summary.laborCost).toBe(200);

    // Subtotal and Grand Total checks
    expect(summary.subtotal).toBe(summary.materialsCost + summary.laborCost);
    expect(summary.grandTotal).toBeGreaterThan(summary.subtotal);
  });

  it('correctly deducts door and window openings from partition walls', () => {
    const room: Room = {
      id: 'r2',
      name: 'Tabique Separador',
      type: 'tabique_divisor',
      segments: [
        {
          id: 's2',
          name: 'Pared con Puerta',
          length: 6.0,
          width: 3.0, // 18 m² gross
          repetitions: 1,
          openings: [
            {
              id: 'op1',
              type: 'door',
              name: 'Puerta Principal',
              width: 1.0,
              height: 2.0, // 2 m² opening
              count: 1
            }
          ]
        }
      ]
    };

    const summary = calculateProjectMaterials([room], baseConfig);

    expect(summary.totalGrossArea).toBe(18);
    expect(summary.totalOpeningsArea).toBe(2);
    expect(summary.totalNetArea).toBe(16); // 18 - 2 = 16 m² net
  });

  it('handles available stock deduction and to-buy quantity computation', () => {
    const room: Room = {
      id: 'r3',
      name: 'Salón',
      type: 'techo_st',
      segments: [
        {
          id: 's3',
          name: 'Techo',
          length: 5.0,
          width: 4.0, // 20 m² -> 8 boards
          repetitions: 1,
          openings: []
        }
      ]
    };

    const customStock = {
      'Placa Drywall ST 12.5mm': 5 // User has 5 boards in stock
    };

    const summary = calculateProjectMaterials([room], baseConfig, {}, customStock);
    const boards = summary.requirements.find((r) => r.name === 'Placa Drywall ST 12.5mm');

    expect(boards).toBeDefined();
    expect(boards?.commercialUnits).toBe(8);
    expect(boards?.availableStock).toBe(5);
    expect(boards?.toBuyQuantity).toBe(3); // 8 - 5 = 3
  });

  it('supports imperial units (sq ft and feet)', () => {
    const imperialConfig: ProjectConfig = {
      ...baseConfig,
      unitSystem: 'imperial'
    };

    const room: Room = {
      id: 'r4',
      name: 'Room 1',
      type: 'techo_st',
      segments: [
        {
          id: 's4',
          name: 'Ceiling',
          length: 10.0, // 10 ft
          width: 10.0,  // 10 ft = 100 sq ft
          repetitions: 1,
          openings: []
        }
      ]
    };

    const summary = calculateProjectMaterials([room], imperialConfig);
    expect(summary.totalNetArea).toBeCloseTo(100, 0);
  });
});

describe('Precios del cómputo (por unidad comercial)', () => {
  const config: ProjectConfig = {
    ...DEFAULT_CONFIG,
    unitSystem: 'metric',
    currency: 'USD',
    wastePercentage: 0,
    taxPercentage: 0,
    profitPercentage: 0,
    laborCostPerUnit: 0
  };

  const room: Room = {
    id: 'r_price',
    name: 'Sala',
    type: 'techo_st',
    segments: [
      { id: 's_price', name: 'Techo', length: 5, width: 4, repetitions: 1, openings: [] }
    ]
  };

  it('un material con embalaje múltiple se cotiza por unidad comercial', () => {
    const summary = calculateProjectMaterials([room], config);
    const screwsBox = summary.requirements.find((r) => r.name.includes('Tornillo T1'));
    expect(screwsBox).toBeDefined();
    // 0,015 $/tornillo × 1.000 tornillos por caja = 15 $ por caja
    expect(screwsBox!.unitPrice).toBeCloseTo(15, 2);
    expect(screwsBox!.totalPrice).toBeCloseTo(screwsBox!.commercialUnits * 15, 2);

    const profile = summary.requirements.find((r) => r.name.includes('Perfil Primario'));
    // 1,35 $/m lineal × 3 m por tira = 4,05 $ por tira
    expect(profile!.unitPrice).toBeCloseTo(4.05, 2);

    const compound = summary.requirements.find((r) => r.name.includes('Masilla'));
    // 0,85 $/kg × 28 kg por balde = 23,80 $ por balde
    expect(compound!.unitPrice).toBeCloseTo(23.8, 2);

    const board = summary.requirements.find((r) => r.name.includes('Placa Drywall ST'));
    // La plancha se vende por unidad: el precio no se multiplica.
    expect(board!.unitPrice).toBeCloseTo(12.5, 2);

    expect(summary.materialsCost).toBeCloseTo(
      summary.requirements.reduce((sum, r) => sum + r.totalPrice, 0),
      2
    );
  });

  it('un precio personalizado se interpreta por unidad comercial', () => {
    const summary = calculateProjectMaterials([room], config, { 'Tornillo T1 Estructura (Comecocos)': 19.9 });
    const screwsBox = summary.requirements.find((r) => r.name.includes('Tornillo T1'));
    expect(screwsBox!.unitPrice).toBeCloseTo(19.9, 2);
    expect(screwsBox!.totalPrice).toBeCloseTo(screwsBox!.commercialUnits * 19.9, 2);
  });

  it('respeta la divisa del proyecto en los precios por defecto', () => {
    const eur = calculateProjectMaterials([room], { ...config, currency: 'EUR' });
    const board = eur.requirements.find((r) => r.name.includes('Placa Drywall ST'));
    // 12,50 USD × 0,92 = 11,50 EUR
    expect(board!.unitPrice).toBeCloseTo(11.5, 2);
  });
});
