import {
  CalculationSummary,
  ConstructionType,
  MaterialRequirement,
  ProjectConfig,
  Room
} from '../types';
import {
  CONSTRUCTION_TYPES,
  CURRENCY_SYMBOLS
} from '../data/materials';

/**
 * Precio de catálogo de un material expresado **por embalaje comercial**.
 *
 * En `CONSTRUCTION_TYPES` el valor `defaultPriceUSD` se publica por unidad base
 * (m lineal, kg, pieza, m²…), mientras que el cómputo comercial y la interfaz
 * trabajan siempre con embalajes completos (tira de 3 m, caja de 1.000 u.,
 * balde de 28 kg, rollo de 150 m…). Es exactamente la misma conversión que
 * aplica el módulo de precios de distribuidores en `convertToPackage()`.
 */
export function packagePriceFromBase(
  defaultPricePerBaseUnitUSD: number,
  packageSize: number
): number {
  const size = Number.isFinite(packageSize) && packageSize > 0 ? packageSize : 1;
  return defaultPricePerBaseUnitUSD * size;
}

export function calculateProjectMaterials(
  rooms: Room[],
  config: ProjectConfig,
  customPrices: Record<string, number> = {},
  customStock: Record<string, number> = {}
): CalculationSummary {
  const isMetric = config.unitSystem === 'metric';
  const currencyInfo = CURRENCY_SYMBOLS[config.currency] || CURRENCY_SYMBOLS.USD;
  const currencyRate = currencyInfo.rateToUSD;

  let totalGrossAreaM2 = 0;
  let totalOpeningsAreaM2 = 0;
  let totalNetAreaM2 = 0;

  // Map of accumulated requirements across all rooms
  // Key: material name
  const accumulatedReqs: Record<
    string,
    {
      name: string;
      category: MaterialRequirement['category'];
      unit: string;
      commercialFormat: string;
      quantityPerM2: number;
      rawQuantity: number;
      wastePercentage: number;
      unitSize: number;
      unitName: string;
      defaultPriceUSD: number;
      notes: string[];
    }
  > = {};

  const allTips = new Set<string>();

  // Process each room
  rooms.forEach((room) => {
    const constType = CONSTRUCTION_TYPES[room.type] || CONSTRUCTION_TYPES.techo_st;
    
    // Add technical tips for this room type
    const tipsList = config.unitSystem === 'imperial' ? constType.technicalTipsEn : constType.technicalTips;
    tipsList.forEach((t) => allTips.add(t));

    room.segments.forEach((segment) => {
      // Dimensions in metric base (convert if imperial)
      // If imperial: length in feet -> m = ft * 0.3048
      const segLengthM = isMetric ? segment.length : segment.length * 0.3048;
      const segWidthM = isMetric ? segment.width : segment.width * 0.3048;
      const reps = Math.max(1, segment.repetitions || 1);

      const grossM2 = segLengthM * segWidthM * reps;
      totalGrossAreaM2 += grossM2;

      // Subtraction of openings (doors, windows)
      let openingsM2 = 0;
      let totalOpeningPerimeterM = 0;

      if (segment.openings && segment.openings.length > 0) {
        segment.openings.forEach((op) => {
          const opW = isMetric ? op.width : op.width * 0.3048;
          const opH = isMetric ? op.height : op.height * 0.3048;
          const opCount = Math.max(1, op.count || 1);
          const opArea = opW * opH * opCount;

          openingsM2 += opArea;
          totalOpeningPerimeterM += (2 * opH + opW) * opCount; // Stud trimmers + header
        });
      }

      totalOpeningsAreaM2 += openingsM2;
      const netM2 = Math.max(0, grossM2 - openingsM2);
      totalNetAreaM2 += netM2;

      // Calculate consumption for this segment
      Object.entries(constType.consumptionRates).forEach(([matName, def]) => {
        let baseRate = def.rate;

        // Custom plate adjustment: default standard plate is 1.20 x 2.40 = 2.88 m²
        if (def.category === 'boards' && config.sheetWidth > 0 && config.sheetLength > 0) {
          const standardPlateArea = 2.88;
          const customPlateArea = config.sheetWidth * config.sheetLength;
          if (customPlateArea > 0) {
            baseRate = baseRate * (standardPlateArea / customPlateArea);
          }
        }

        // Stud spacing adjustment: default is 0.407m (16"). If user selected 0.61m (24") or 0.488m (19.2")
        if (def.category === 'profiles' && (matName.includes('Parante') || matName.includes('PGC') || matName.includes('Montante'))) {
          const defaultSpacing = 0.407;
          if (config.studSpacing > 0) {
            baseRate = baseRate * (defaultSpacing / config.studSpacing);
          }
        }

        // Raw quantity for this segment
        let segMatQty = netM2 * baseRate;

        // Extra reinforcement for openings in wall framing
        if (def.category === 'profiles' && (matName.includes('Parante') || matName.includes('Canal') || matName.includes('PGC'))) {
          segMatQty += totalOpeningPerimeterM * 0.5; // Additional framing trimmers/headers
        }

        if (!accumulatedReqs[matName]) {
          accumulatedReqs[matName] = {
            name: matName,
            category: def.category,
            unit: def.unit,
            commercialFormat: def.commercialPackage.name,
            quantityPerM2: baseRate,
            rawQuantity: 0,
            wastePercentage: config.wastePercentage,
            unitSize: def.commercialPackage.unitSize,
            unitName: def.commercialPackage.unitName,
            defaultPriceUSD: def.defaultPriceUSD,
            notes: []
          };
        }

        accumulatedReqs[matName].rawQuantity += segMatQty;
      });
    });
  });

  // Convert to array of MaterialRequirement
  const requirements: MaterialRequirement[] = Object.values(accumulatedReqs).map((item, idx) => {
    const rawQty = item.rawQuantity;
    const wasteFactor = 1 + config.wastePercentage / 100;
    const finalQty = rawQty * wasteFactor;

    // Commercial units (e.g. integer sheets, strips, boxes, buckets)
    const commercialUnits = Math.ceil(finalQty / item.unitSize);

    // Determinación del precio:
    //   1. Precio personalizado / de distribuidor (ya expresado por embalaje).
    //   2. Precio de catálogo: se pasa de unidad base a embalaje comercial
    //      (tira de 3 m, caja de 1.000 u., balde de 28 kg…) y se convierte a
    //      la divisa del proyecto.
    const catalogPackagePriceUSD = packagePriceFromBase(item.defaultPriceUSD, item.unitSize);
    const unitPrice = customPrices[item.name] !== undefined
      ? customPrices[item.name]
      : catalogPackagePriceUSD * currencyRate;

    // `unitPrice` es SIEMPRE el precio de un embalaje comercial completo, que
    // es lo que se multiplica por el número de embalajes a comprar.
    const totalPrice = commercialUnits * unitPrice;

    // Stock check
    const stockAvailable = customStock[item.name] || 0;
    const toBuyQty = Math.max(0, commercialUnits - stockAvailable);

    return {
      id: `req_${idx + 1}`,
      name: item.name,
      category: item.category,
      unit: item.unit,
      commercialFormat: item.commercialFormat,
      quantityPerM2: Number((item.rawQuantity / Math.max(1, totalNetAreaM2)).toFixed(3)),
      rawQuantity: Number(rawQty.toFixed(2)),
      wastePercentage: config.wastePercentage,
      finalQuantity: Number(finalQty.toFixed(2)),
      commercialUnits,
      unitPrice: Number(unitPrice.toFixed(2)),
      totalPrice: Number(totalPrice.toFixed(2)),
      availableStock: stockAvailable,
      toBuyQuantity: toBuyQty,
      toBuyPackages: toBuyQty,
      notes: `${item.commercialFormat} • (${item.unitName})`
    };
  });

  // Calculate Totals
  const materialsCost = requirements.reduce((sum, r) => sum + r.totalPrice, 0);

  // Labor Cost
  let laborCost = 0;
  if (config.laborCalculationMode === 'per_area') {
    const unitArea = isMetric ? totalNetAreaM2 : totalNetAreaM2 * 10.7639;
    laborCost = unitArea * config.laborCostPerUnit;
  } else if (config.laborCalculationMode === 'fixed') {
    laborCost = config.fixedLaborCost;
  }

  const subtotal = materialsCost + laborCost;
  const profitAmount = subtotal * (config.profitPercentage / 100);
  const subtotalWithProfit = subtotal + profitAmount;
  const taxAmount = subtotalWithProfit * (config.taxPercentage / 100);
  const grandTotal = subtotalWithProfit + taxAmount;

  const displayArea = isMetric ? totalNetAreaM2 : totalNetAreaM2 * 10.7639;
  const costPerUnitArea = displayArea > 0 ? grandTotal / displayArea : 0;

  // Custom advisory notes based on size
  if (totalNetAreaM2 > 100) {
    allTips.add('Proyecto de gran escala (>100 m²): Se recomienda prever juntas de dilatación estructural cada 10 metros lineales de paño continuo.');
  }

  return {
    totalGrossArea: Number((isMetric ? totalGrossAreaM2 : totalGrossAreaM2 * 10.7639).toFixed(2)),
    totalNetArea: Number(displayArea.toFixed(2)),
    totalOpeningsArea: Number((isMetric ? totalOpeningsAreaM2 : totalOpeningsAreaM2 * 10.7639).toFixed(2)),
    requirements,
    materialsCost: Number(materialsCost.toFixed(2)),
    laborCost: Number(laborCost.toFixed(2)),
    subtotal: Number(subtotal.toFixed(2)),
    profitAmount: Number(profitAmount.toFixed(2)),
    taxAmount: Number(taxAmount.toFixed(2)),
    grandTotal: Number(grandTotal.toFixed(2)),
    costPerUnitArea: Number(costPerUnitArea.toFixed(2)),
    advisoryTips: Array.from(allTips)
  };
}

export function formatCurrency(amount: number, currency: string = 'USD'): string {
  const sym = CURRENCY_SYMBOLS[currency]?.symbol || '$';
  return `${sym} ${amount.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}
