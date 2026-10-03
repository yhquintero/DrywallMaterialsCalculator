import React, { useState } from 'react';
import {
  DollarSign,
  FileSpreadsheet,
  FileText,
  Printer,
  TrendingUp,
  Percent,
  Warehouse,
  ShoppingCart,
  Sliders,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import { CalculationSummary, ProjectConfig, Room } from '../types';
import { CURRENCY_SYMBOLS } from '../data/materials';
import { formatCurrency } from '../lib/calculator';
import { generatePurchaseOrderPDF, generateQuotePDF } from '../lib/pdfGenerator';
import { exportMaterialsToCSV } from '../lib/storage';

interface BudgetEstimatorProps {
  summary: CalculationSummary;
  config: ProjectConfig;
  rooms: Room[];
  customPrices: Record<string, number>;
  customStock: Record<string, number>;
  onUpdateConfig: (config: ProjectConfig) => void;
  onUpdatePrice: (materialName: string, price: number) => void;
  onUpdateStock: (materialName: string, stock: number) => void;
}

export const BudgetEstimator: React.FC<BudgetEstimatorProps> = ({
  summary,
  config,
  rooms,
  customPrices,
  customStock,
  onUpdateConfig,
  onUpdatePrice,
  onUpdateStock
}) => {
  const [filterCategory, setFilterCategory] = useState<string>('all');
  const isMetric = config.unitSystem === 'metric';
  const unitAreaLabel = isMetric ? 'm²' : 'sq ft';
  const currencyInfo = CURRENCY_SYMBOLS[config.currency] || CURRENCY_SYMBOLS.USD;

  const filteredRequirements = summary.requirements.filter((r) => {
    if (filterCategory === 'all') return true;
    return r.category === filterCategory;
  });

  const categoryLabels: Record<string, string> = {
    all: 'Todos los Rubros',
    boards: 'Placas de Yeso',
    profiles: 'Perfilería Metálica',
    fasteners: 'Tornillos & Fijaciones',
    compounds: 'Masillas & Pastas',
    tapes: 'Cintas & Mallas',
    insulation: 'Aislamiento Térmico/Acústico',
    accessories: 'Accesorios & Sellos'
  };

  const handleExportQuote = () => {
    generateQuotePDF(summary, config, rooms);
  };

  const handleExportPurchaseOrder = () => {
    generatePurchaseOrderPDF(summary, config);
  };

  const handleExportCSV = () => {
    exportMaterialsToCSV(summary.requirements, config.currency);
  };

  return (
    <div className="space-y-6">
      {/* Top Financial Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Card 1: Total Project Budget */}
        <div className="bg-gradient-to-br from-brand-950/60 to-slate-900 border border-brand-500/30 rounded-2xl p-4 shadow-xl relative overflow-hidden group">
          <div className="absolute top-0 right-0 w-24 h-24 bg-brand-500/10 rounded-full blur-2xl group-hover:bg-brand-500/20 transition-all pointer-events-none" />
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-brand-400 mb-1">
            <span>Presupuesto Total</span>
            <DollarSign className="w-4 h-4 text-brand-400" />
          </div>
          <div className="text-2xl font-black text-white font-mono tracking-tight">
            {formatCurrency(summary.grandTotal, config.currency)}
          </div>
          <div className="flex items-center justify-between text-xs text-slate-400 mt-2 font-mono">
            <span>Costo Unitario:</span>
            <span className="text-brand-300 font-semibold">
              {formatCurrency(summary.costPerUnitArea, config.currency)} / {unitAreaLabel}
            </span>
          </div>
        </div>

        {/* Card 2: Materials Total */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg">
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-blue-400 mb-1">
            <span>Suministro Materiales</span>
            <Warehouse className="w-4 h-4 text-blue-400" />
          </div>
          <div className="text-2xl font-black text-slate-100 font-mono tracking-tight">
            {formatCurrency(summary.materialsCost, config.currency)}
          </div>
          <div className="text-xs text-slate-400 mt-2">
            <span>{summary.requirements.length} materiales calculados con merma</span>
          </div>
        </div>

        {/* Card 3: Labor Cost */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg">
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-amber-400 mb-1">
            <span>Mano de Obra</span>
            <TrendingUp className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-2xl font-black text-slate-100 font-mono tracking-tight">
            {formatCurrency(summary.laborCost, config.currency)}
          </div>
          <div className="text-xs text-slate-400 mt-2 flex items-center justify-between">
            <span>Tasa de Mano de Obra:</span>
            <span className="font-mono text-amber-300">
              {formatCurrency(config.laborCostPerUnit, config.currency)} / {unitAreaLabel}
            </span>
          </div>
        </div>

        {/* Card 4: Profit & Tax */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg">
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-purple-400 mb-1">
            <span>Margen & Impuestos</span>
            <Percent className="w-4 h-4 text-purple-400" />
          </div>
          <div className="text-xl font-bold text-slate-100 font-mono tracking-tight">
            + {formatCurrency(summary.profitAmount + summary.taxAmount, config.currency)}
          </div>
          <div className="text-xs text-slate-400 mt-2 flex items-center justify-between font-mono">
            <span>Beneficio: {config.profitPercentage}%</span>
            <span>IVA: {config.taxPercentage}%</span>
          </div>
        </div>
      </div>

      {/* Action Toolbar */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 flex flex-wrap items-center justify-between gap-3 shadow-md">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-slate-400 mr-1">Filtrar por:</span>
          {Object.entries(categoryLabels).map(([key, label]) => (
            <button
              key={key}
              onClick={() => setFilterCategory(key)}
              className={`text-xs px-2.5 py-1 rounded-lg border transition-all ${
                filterCategory === key
                  ? 'bg-brand-500/15 border-brand-500 text-brand-400 font-medium'
                  : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={handleExportQuote}
            className="px-3.5 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold shadow-lg shadow-brand-600/20 transition-all flex items-center gap-2"
          >
            <FileText className="w-4 h-4" />
            <span>Exportar Cotización PDF</span>
          </button>
          <button
            onClick={handleExportPurchaseOrder}
            className="px-3 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold shadow-md transition-all flex items-center gap-1.5"
            title="Generar PDF para proveedor"
          >
            <ShoppingCart className="w-4 h-4" />
            <span>Orden Compra PDF</span>
          </button>
          <button
            onClick={handleExportCSV}
            className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-xl text-xs font-semibold transition-all flex items-center gap-1.5"
            title="Descargar tabla en CSV / Excel"
          >
            <FileSpreadsheet className="w-4 h-4 text-emerald-400" />
            <span>Excel / CSV</span>
          </button>
          <button
            onClick={() => window.print()}
            className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white border border-slate-700 rounded-xl transition-all"
            title="Imprimir pantalla"
          >
            <Printer className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Financial & Material Items Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="px-5 py-4 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="font-semibold text-slate-100 text-sm">
              Desglose Técnico de Materiales & Lista de Compras
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Ajuste precios unitarios o stock existente en almacén para calcular la orden de compra exacta.
            </p>
          </div>
          <span className="text-xs bg-brand-500/10 text-brand-400 px-3 py-1 rounded-full border border-brand-500/20 font-medium">
            Precios en {config.currency} ({currencyInfo.symbol})
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <th className="py-3 px-4">Material / Insumo</th>
                <th className="py-3 px-3">Formato Comercial</th>
                <th className="py-3 px-3 text-right">Cant. Neta</th>
                <th className="py-3 px-3 text-right">Cant. Merma (+{config.wastePercentage}%)</th>
                <th className="py-3 px-3 text-center">Unid. Comercial</th>
                <th className="py-3 px-3 text-center">Stock Almacén</th>
                <th className="py-3 px-3 text-center font-bold text-blue-400">A Comprar</th>
                <th className="py-3 px-3 text-right">Precio Unit. ({currencyInfo.symbol})</th>
                <th className="py-3 px-4 text-right">Subtotal</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono">
              {filteredRequirements.map((req) => {
                const isCustomPriced = customPrices[req.name] !== undefined;
                return (
                  <tr
                    key={req.id}
                    className="hover:bg-slate-850/60 transition-colors text-slate-200"
                  >
                    <td className="py-3 px-4 font-sans font-medium text-slate-100">
                      <div className="flex items-center space-x-2">
                        <span className="text-xs">{req.name}</span>
                      </div>
                    </td>

                    <td className="py-3 px-3 font-sans text-slate-400 text-[11px]">
                      {req.commercialFormat}
                    </td>

                    <td className="py-3 px-3 text-right text-slate-400">
                      {req.rawQuantity.toFixed(1)} {req.unit}
                    </td>

                    <td className="py-3 px-3 text-right text-slate-300">
                      {req.finalQuantity.toFixed(1)} {req.unit}
                    </td>

                    <td className="py-3 px-3 text-center">
                      <span className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 font-bold text-slate-200">
                        {req.commercialUnits}
                      </span>
                    </td>

                    {/* Stock Input */}
                    <td className="py-3 px-3 text-center">
                      <input
                        type="number"
                        min="0"
                        step="1"
                        value={req.availableStock}
                        onChange={(e) => onUpdateStock(req.name, parseInt(e.target.value) || 0)}
                        className="w-16 bg-slate-950 border border-slate-700 hover:border-slate-500 focus:border-brand-500 rounded px-1.5 py-0.5 text-center text-xs font-mono text-slate-200 focus:outline-none"
                      />
                    </td>

                    {/* To Buy */}
                    <td className="py-3 px-3 text-center">
                      <span
                        className={`px-2 py-0.5 rounded font-bold ${
                          req.toBuyQuantity > 0
                            ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
                            : 'bg-emerald-500/10 text-emerald-400'
                        }`}
                      >
                        {req.toBuyQuantity}
                      </span>
                    </td>

                    {/* Unit Price Input */}
                    <td className="py-3 px-3 text-right">
                      <div className="flex items-center justify-end space-x-1">
                        <span className="text-slate-500 text-[11px]">{currencyInfo.symbol}</span>
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          value={req.unitPrice}
                          onChange={(e) => onUpdatePrice(req.name, parseFloat(e.target.value) || 0)}
                          className={`w-20 bg-slate-950 border ${
                            isCustomPriced ? 'border-brand-500/60 text-brand-300' : 'border-slate-700 text-slate-200'
                          } rounded px-1.5 py-0.5 text-right text-xs font-mono focus:border-brand-500 focus:outline-none`}
                        />
                      </div>
                    </td>

                    <td className="py-3 px-4 text-right font-bold text-slate-100">
                      {formatCurrency(req.totalPrice, config.currency)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Totals Summary Footer */}
        <div className="bg-slate-950 px-6 py-5 border-t border-slate-800 flex flex-col md:flex-row items-end md:items-center justify-between gap-4">
          <div className="text-xs text-slate-400 space-y-1">
            <p className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-brand-400" />
              Cálculo de embalajes comerciales redondeados hacia arriba automáticamente.
            </p>
            <p className="flex items-center gap-1.5 text-slate-500">
              <AlertCircle className="w-3.5 h-3.5 text-amber-400" />
              El stock ingresado deduce la cantidad a comprar pero mantiene el presupuesto total íntegro.
            </p>
          </div>

          <div className="w-full md:w-80 space-y-2 text-xs font-mono">
            <div className="flex justify-between text-slate-400">
              <span>Subtotal Materiales:</span>
              <span>{formatCurrency(summary.materialsCost, config.currency)}</span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>Mano de Obra ({config.laborCostPerUnit} {currencyInfo.symbol}/{unitAreaLabel}):</span>
              <span>{formatCurrency(summary.laborCost, config.currency)}</span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>Margen Beneficio ({config.profitPercentage}%):</span>
              <span>{formatCurrency(summary.profitAmount, config.currency)}</span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>Impuesto IVA ({config.taxPercentage}%):</span>
              <span>{formatCurrency(summary.taxAmount, config.currency)}</span>
            </div>
            <div className="border-t border-slate-800 pt-2 flex justify-between text-sm font-bold text-brand-400">
              <span>TOTAL PROYECTO:</span>
              <span className="text-base text-white">{formatCurrency(summary.grandTotal, config.currency)}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
