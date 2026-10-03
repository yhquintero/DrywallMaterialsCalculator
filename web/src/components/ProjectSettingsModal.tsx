import React from 'react';
import { X, Settings, Building2, User, Wrench, Shield, Check } from 'lucide-react';
import { Currency, ProjectConfig, UnitSystem } from '../types';
import { CURRENCY_SYMBOLS } from '../data/materials';
import { Dialog } from './ui/Dialog';

interface ProjectSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  config: ProjectConfig;
  onSaveConfig: (newConfig: ProjectConfig) => void;
}

export const ProjectSettingsModal: React.FC<ProjectSettingsModalProps> = ({
  isOpen,
  onClose,
  config,
  onSaveConfig
}) => {
  const [formData, setFormData] = React.useState<ProjectConfig>(config);

  React.useEffect(() => {
    setFormData(config);
  }, [config, isOpen]);

  if (!isOpen) return null;

  const handleChange = (field: keyof ProjectConfig, value: any) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSaveConfig(formData);
    onClose();
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      label="Configuración del proyecto y parámetros técnicos"
      backdropClassName="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200"
      panelClassName="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-3xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden"
    >
        {/* Header */}
        <div className="bg-slate-850 px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 bg-brand-500/10 rounded-xl text-brand-400">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">
                Configuración del Proyecto & Parámetros Técnicos
              </h2>
              <p className="text-xs text-slate-400">
                Personalice datos de la cotización, moneda, márgenes y normas constructivas.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body form */}
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto space-y-6 text-xs">
          {/* Section 1: Project & Client */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <User className="w-3.5 h-3.5 text-brand-400" />
              Datos de la Obra y Cliente
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-slate-400 mb-1">Nombre del Proyecto / Obra</label>
                <input
                  type="text"
                  value={formData.projectName}
                  onChange={(e) => handleChange('projectName', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                  required
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Dirección de la Obra</label>
                <input
                  type="text"
                  value={formData.projectAddress}
                  onChange={(e) => handleChange('projectAddress', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Nombre del Cliente / Empresa</label>
                <input
                  type="text"
                  value={formData.clientName}
                  onChange={(e) => handleChange('clientName', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Teléfono / Email del Cliente</label>
                <input
                  type="text"
                  value={formData.clientPhone}
                  onChange={(e) => handleChange('clientPhone', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
            </div>
          </div>

          {/* Section 2: Contractor Header */}
          <div className="space-y-3 pt-3 border-t border-slate-800">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Building2 className="w-3.5 h-3.5 text-blue-400" />
              Datos de Su Empresa / Contratista (Para el PDF)
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-slate-400 mb-1">Razón Social / Empresa</label>
                <input
                  type="text"
                  value={formData.contractorCompany}
                  onChange={(e) => handleChange('contractorCompany', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Responsable Técnico</label>
                <input
                  type="text"
                  value={formData.contractorName}
                  onChange={(e) => handleChange('contractorName', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Contacto / Email</label>
                <input
                  type="text"
                  value={formData.contractorEmail}
                  onChange={(e) => handleChange('contractorEmail', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
            </div>
          </div>

          {/* Section 3: Engineering and Construction standards */}
          <div className="space-y-3 pt-3 border-t border-slate-800">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Wrench className="w-3.5 h-3.5 text-amber-400" />
              Parámetros Constructivos & Unidades
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-slate-400 mb-1">Sistema de Medidas</label>
                <select
                  value={formData.unitSystem}
                  onChange={(e) => handleChange('unitSystem', e.target.value as UnitSystem)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                >
                  <option value="metric">Métrico Internacional (m, cm, m²)</option>
                  <option value="imperial">Imperial USA (ft, in, sq ft)</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Moneda del Presupuesto</label>
                <select
                  value={formData.currency}
                  onChange={(e) => handleChange('currency', e.target.value as Currency)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                >
                  {Object.entries(CURRENCY_SYMBOLS).map(([code, info]) => (
                    <option key={code} value={code}>
                      {info.label}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Merma / Desperdicio Técnico</label>
                <select
                  value={formData.wastePercentage}
                  onChange={(e) => handleChange('wastePercentage', Number(e.target.value))}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                >
                  <option value={0}>0% (Teórico exacto)</option>
                  <option value={5}>5% (Cortes limpios y regulares)</option>
                  <option value={8}>8% (Recomendado estándar)</option>
                  <option value={10}>10% (Áreas con muchas aberturas)</option>
                  <option value={15}>15% (Geometrías complejas / curvas)</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Modulación de Parantes (Distanciamiento)</label>
                <select
                  value={formData.studSpacing}
                  onChange={(e) => handleChange('studSpacing', Number(e.target.value))}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                >
                  <option value={0.407}>40.7 cm / 16 pulgadas (Estándar alta rigidez)</option>
                  <option value={0.488}>48.8 cm / 19.2 pulgadas (Intermedio)</option>
                  <option value={0.61}>61.0 cm / 24 pulgadas (Ligero sin cargas)</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Largo Comercial de Perfiles</label>
                <select
                  value={formData.profileLength}
                  onChange={(e) => handleChange('profileLength', Number(e.target.value))}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                >
                  <option value={2.44}>2.44 metros (8 ft)</option>
                  <option value={3.0}>3.00 metros (Estándar)</option>
                  <option value={3.66}>3.66 metros (12 ft)</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Dimensiones de Placa</label>
                <select
                  value={`${formData.sheetWidth}x${formData.sheetLength}`}
                  onChange={(e) => {
                    const [w, l] = e.target.value.split('x').map(Number);
                    handleChange('sheetWidth', w);
                    handleChange('sheetLength', l);
                  }}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none"
                >
                  <option value="1.2x2.4">1.20 m × 2.40 m (4x8 ft - Estándar)</option>
                  <option value="1.2x2.6">1.20 m × 2.60 m (Para techos altos)</option>
                  <option value="1.2x3.0">1.20 m × 3.00 m (4x10 ft - Menos juntas)</option>
                </select>
              </div>
            </div>
          </div>

          {/* Section 4: Commercial Terms & Pricing */}
          <div className="space-y-3 pt-3 border-t border-slate-800">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-emerald-400" />
              Tasa de Mano de Obra, Margen & Finanzas
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-slate-400 mb-1">
                  Mano de Obra por {formData.unitSystem === 'metric' ? 'm²' : 'sq ft'}
                </label>
                <input
                  type="number"
                  step="0.5"
                  min="0"
                  value={formData.laborCostPerUnit}
                  onChange={(e) => handleChange('laborCostPerUnit', parseFloat(e.target.value) || 0)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 font-mono focus:border-brand-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Margen de Beneficio (%)</label>
                <input
                  type="number"
                  step="1"
                  min="0"
                  max="100"
                  value={formData.profitPercentage}
                  onChange={(e) => handleChange('profitPercentage', parseFloat(e.target.value) || 0)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 font-mono focus:border-brand-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Impuesto / IVA (%)</label>
                <input
                  type="number"
                  step="1"
                  min="0"
                  max="50"
                  value={formData.taxPercentage}
                  onChange={(e) => handleChange('taxPercentage', parseFloat(e.target.value) || 0)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 font-mono focus:border-brand-500 focus:outline-none"
                />
              </div>
            </div>

            <div>
              <label className="block text-slate-400 mb-1">
                Términos, Garantía & Condiciones para el Presupuesto
              </label>
              <textarea
                rows={2}
                value={formData.notes}
                onChange={(e) => handleChange('notes', e.target.value)}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:border-brand-500 focus:outline-none text-xs"
              />
            </div>
          </div>

          {/* Footer buttons */}
          <div className="pt-4 border-t border-slate-800 flex items-center justify-end space-x-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="px-5 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl font-semibold shadow-lg shadow-brand-600/20 transition-all flex items-center gap-1.5"
            >
              <Check className="w-4 h-4" />
              <span>Guardar Configuración</span>
            </button>
          </div>
        </form>
    </Dialog>
  );
};
