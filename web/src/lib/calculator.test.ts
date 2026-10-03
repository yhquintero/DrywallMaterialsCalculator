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

  it('supports imperial units (sq ft and feet)', () => {    const imperialConfig: ProjectConfig = {
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

  /**
   * Regresión del defecto crítico detectado en la revisión técnica (2026-10):
   * `defaultPriceUSD` se publica por unidad base (m, kg, pieza…) pero se
   * aplicaba directamente al número de embalajes comerciales, dejando el
   * presupuesto infravalorado (≈ -60 % en materiales).
   */
  describe('precio de catálogo por embalaje comercial', () => {
    const room: Room = {
      id: 'rp',
      name: 'Techo 20 m²',
      type: 'techo_st',
      segments: [{ id: 'sp', name: 'Paño', length: 5, width: 4, repetitions: 1, openings: [] }]
    };

    it('multiplica el precio unitario por el contenido del embalaje', () => {
      const summary = calculateProjectMaterials([room], baseConfig);

      // Perfil primario: 17 m → 18.7 m con merma → 7 tiras de 3 m a 1.35 USD/m
      const primary = summary.requirements.find((r) => r.name.includes('Perfil Primario'))!;
      expect(primary.commercialUnits).toBe(7);
      expect(primary.unitPrice).toBeCloseTo(4.05, 2); // 1.35 × 3 m
      expect(primary.totalPrice).toBeCloseTo(28.35, 2);

      // Tornillo T2: caja de 1.000 u. a 0.018 USD/pieza ⇒ 18 USD la caja
      const screws = summary.requirements.find((r) => r.name.includes('Tornillo T2 Placa'))!;
      expect(screws.unitPrice).toBeCloseTo(18, 2);
      expect(screws.totalPrice).toBeCloseTo(screws.commercialUnits * 18, 2);

      // Cinta: rollo de 150 m a 0.08 USD/m ⇒ 12 USD el rollo
      const tape = summary.requirements.find((r) => r.name.includes('Cinta de Papel Micro'))!;
      expect(tape.unitPrice).toBeCloseTo(12, 2);

      // Masilla: balde de 28 kg a 0.85 USD/kg ⇒ 23.80 USD el balde
      const compound = summary.requirements.find((r) => r.name.includes('Masilla'))!;
      expect(compound.unitPrice).toBeCloseTo(23.8, 2);
    });

    it('mantiene el total de materiales coherente con la suma de sus líneas', () => {
      const summary = calculateProjectMaterials([room], baseConfig);
      const lineSum = summary.requirements.reduce((sum, r) => sum + r.totalPrice, 0);
      expect(summary.materialsCost).toBeCloseTo(lineSum, 2);
      // Antes del arreglo estas mismas 8 líneas sumaban ~120 USD (sólo la
      // partida de placas estaba bien cotizada porque su embalaje es 1).
      expect(summary.materialsCost).toBeCloseTo(253.2, 1);
      expect(summary.materialsCost).toBeGreaterThan(240);
    });

    it('respeta el precio personalizado por embalaje por encima del catálogo', () => {
      const summary = calculateProjectMaterials([room], baseConfig, {
        'Placa Drywall ST 12.5mm': 30
      });
      const boards = summary.requirements.find((r) => r.name === 'Placa Drywall ST 12.5mm')!;
      expect(boards.unitPrice).toBe(30);
      expect(boards.totalPrice).toBeCloseTo(boards.commercialUnits * 30, 2);
    });

    it('convierte el precio de catálogo a la divisa del proyecto', () => {
      const eurConfig: ProjectConfig = { ...baseConfig, currency: 'EUR' };
      const usd = calculateProjectMaterials([room], baseConfig);
      const eur = calculateProjectMaterials([room], eurConfig);
      const usdBoards = usd.requirements.find((r) => r.name === 'Placa Drywall ST 12.5mm')!;
      const eurBoards = eur.requirements.find((r) => r.name === 'Placa Drywall ST 12.5mm')!;
      expect(eurBoards.unitPrice).toBeCloseTo(usdBoards.unitPrice * 0.92, 2);
    });
  });
});
