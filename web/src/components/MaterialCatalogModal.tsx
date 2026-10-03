import React, { useState } from 'react';
import { X, Search, Package, Layers, ShieldCheck, Tag } from 'lucide-react';
import { CONSTRUCTION_TYPES } from '../data/materials';

interface MaterialCatalogModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface CatalogItem {
  id: string;
  name: string;
  category: string;
  specs: string;
  unit: string;
  packaging: string;
  notes: string;
  tags: string[];
}

const CATALOG_ITEMS: CatalogItem[] = [
  {
    id: 'PLY-001',
    name: 'Placa de Yeso Estándar (ST) 12.5mm',
    category: 'Placas',
    specs: '1200 × 2400 × 12.5 mm • Peso aprox. 9.5 kg/m²',
    unit: 'Placa',
    packaging: 'Palet de 40 a 50 unidades (2.88 m² por placa)',
    notes: 'Apta para tabiques, revestimientos y techos continuos en ambientes secos interiores.',
    tags: ['st', 'yeso', 'pladur', 'estandar', '12.5']
  },
  {
    id: 'PLY-002',
    name: 'Placa de Yeso Antihumedad (RH) 12.5mm Verde',
    category: 'Placas',
    specs: '1200 × 2400 × 12.5 mm • Absorción de agua < 5%',
    unit: 'Placa',
    packaging: 'Palet de 40 a 50 unidades',
    notes: 'Tratamiento hidrófugo con silicona. Recomendada para cuartos de baño, lavaderos y cocinas.',
    tags: ['rh', 'verde', 'humedad', 'bano', 'cocina']
  },
  {
    id: 'PLY-003',
    name: 'Placa Ignífuga Resistente al Fuego (RF) 15mm Rosa',
    category: 'Placas',
    specs: '1200 × 2400 × 15.0 mm • Fibra de vidrio incorporada',
    unit: 'Placa',
    packaging: 'Palet de 36 unidades',
    notes: 'Clasificación A2-s1,d0. Mantiene cohesión del núcleo bajo altas temperaturas hasta 120 min.',
    tags: ['rf', 'fuego', 'ignifuga', 'rosa', '15mm']
  },
  {
    id: 'PLY-004',
    name: 'Placa de Fibrocemento / Cemento Exterior 10mm',
    category: 'Placas',
    specs: '1220 × 2440 × 10.0 mm • Cemento portland + malla',
    unit: 'Placa',
    packaging: 'Palet de 30 unidades',
    notes: 'Máxima resistencia a intemperie, agua directa, moho e impacto para fachadas y zonas húmedas.',
    tags: ['cemento', 'superboard', 'durock', 'exterior', 'eifs']
  },
  {
    id: 'PER-001',
    name: 'Canal Guía U 64mm Galvanizado',
    category: 'Perfiles',
    specs: 'Base 64mm, alas 30mm, espesor 0.50mm • Largo 3.00m',
    unit: 'Tira 3m',
    packaging: 'Atado de 10 tiras',
    notes: 'Perfil perimetral horizontal fijado a suelo y techo como guía del tabique.',
    tags: ['canal', 'guia', 'u', '64mm', 'galvanizado']
  },
  {
    id: 'PER-002',
    name: 'Parante Montante C 64mm Galvanizado',
    category: 'Perfiles',
    specs: 'Base 63mm, alas 34mm, con pestañas y troqueles • 3.00m',
    unit: 'Tira 3m',
    packaging: 'Atado de 10 tiras',
    notes: 'Estructura vertical portante que recibe las placas atornilladas. Modulación a 40.7cm.',
    tags: ['parante', 'montante', 'c', '64mm', 'estructura']
  },
  {
    id: 'PER-003',
    name: 'Perfil Omega / Furring Channel',
    category: 'Perfiles',
    specs: 'Sección sombrero 80×20mm, espesor 0.50mm • Largo 3.00m',
    unit: 'Tira 3m',
    packaging: 'Atado de 10 tiras',
    notes: 'Perfil soporte para atornillado de techos suspendidos y trasdosados directos.',
    tags: ['omega', 'furring', 'techo', 'cielo raso']
  },
  {
    id: 'TOR-001',
    name: 'Tornillo T1 Estructura (Comecocos 7×7/16")',
    category: 'Tornillos',
    specs: 'Punta broca o punta fina aguja, cabeza extraplana',
    unit: 'Caja 1000 u.',
    packaging: 'Caja de 1,000 unidades',
    notes: 'Unión metal con metal entre parantes y canales o perfiles primarios.',
    tags: ['t1', 'comecocos', 'chapa', 'tornillo']
  },
  {
    id: 'TOR-002',
    name: 'Tornillo T2 para Placa (6×1" / 6×1 1/4")',
    category: 'Tornillos',
    specs: 'Cabeza trompeta fosfatada negra, punta aguja fina',
    unit: 'Caja 1000 u.',
    packaging: 'Caja de 1,000 unidades',
    notes: 'Fijación directa de placas de yeso a perfiles de espesor hasta 0.70mm.',
    tags: ['t2', 'trompeta', 'placa', 'drywall', 'fosfatado']
  },
  {
    id: 'MAS-001',
    name: 'Masilla para Juntas Pasta Preparada (Balde 28 kg)',
    category: 'Masillas',
    specs: 'Compuesto multiuso listo para aplicar sin mezcla previa',
    unit: 'Balde 28kg',
    packaging: 'Balde hermético de 28 kg / 5 galones',
    notes: 'Para embeber cinta de papel y aplicar capas 2 y 3 de acabado fino.',
    tags: ['masilla', 'pasta', 'juntas', 'balde', 'ready-mix']
  },
  {
    id: 'CIN-001',
    name: 'Cinta de Papel Microperforada Kraft',
    category: 'Cintas',
    specs: 'Ancho 50 mm • Plegado longitudinal central para esquinas',
    unit: 'Rollo 150m',
    packaging: 'Rollos de 150 metros (o 75m)',
    notes: 'Máxima resistencia a tracción y prevención de fisuras en uniones de placas.',
    tags: ['cinta', 'papel', 'kraft', 'junta']
  },
  {
    id: 'AIS-001',
    name: 'Lana de Vidrio con Velo Hidrófugo 50mm',
    category: 'Aislamientos',
    specs: 'Espesor 50 mm • Resistencia térmica R=1.25 m²·K/W',
    unit: 'Rollo 14.4 m²',
    packaging: 'Rollo comprimido 14.40 m²',
    notes: 'Absorción acústica superior para cavidades internas de tabiques y techos.',
    tags: ['lana', 'vidrio', 'aislamiento', 'acustico', 'termico']
  }
];

export const MaterialCatalogModal: React.FC<MaterialCatalogModalProps> = ({
  isOpen,
  onClose
}) => {
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  if (!isOpen) return null;

  const categories = ['all', 'Placas', 'Perfiles', 'Tornillos', 'Masillas', 'Cintas', 'Aislamientos'];

  const filtered = CATALOG_ITEMS.filter((item) => {
    const matchesCat = selectedCategory === 'all' || item.category === selectedCategory;
    const matchesSearch =
      item.name.toLowerCase().includes(search.toLowerCase()) ||
      item.specs.toLowerCase().includes(search.toLowerCase()) ||
      item.notes.toLowerCase().includes(search.toLowerCase()) ||
      item.tags.some((t) => t.toLowerCase().includes(search.toLowerCase()));
    return matchesCat && matchesSearch;
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl max-h-[88vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="bg-slate-850 px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 bg-emerald-500/10 rounded-xl text-emerald-400">
              <Package className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">
                Catálogo Técnico de Materiales Drywall
              </h2>
              <p className="text-xs text-slate-400">
                Fichas técnicas, dimensiones normadas y presentaciones comerciales de suministros.
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

        {/* Filters */}
        <div className="p-4 bg-slate-950/70 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-400" />
            <input
              type="text"
              placeholder="Buscar por placa, perfil, medida, tornillo..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-9 pr-3 py-1.5 text-xs text-slate-100 placeholder-slate-500 focus:border-brand-500 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                className={`text-xs px-3 py-1 rounded-lg border transition-all ${
                  selectedCategory === cat
                    ? 'bg-brand-500/15 border-brand-500 text-brand-400 font-medium'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                {cat === 'all' ? 'Todos' : cat}
              </button>
            ))}
          </div>
        </div>

        {/* Material Cards Grid */}
        <div className="p-5 overflow-y-auto grid grid-cols-1 md:grid-cols-2 gap-4 flex-1 text-xs">
          {filtered.map((item) => (
            <div
              key={item.id}
              className="bg-slate-950 border border-slate-800 hover:border-slate-700 rounded-xl p-4 flex flex-col justify-between space-y-3 transition-colors"
            >
              <div>
                <div className="flex items-center justify-between gap-2 mb-1.5">
                  <span className="text-[10px] font-mono font-semibold px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                    {item.id}
                  </span>
                  <span className="text-[10px] bg-brand-500/10 text-brand-400 border border-brand-500/20 px-2 py-0.5 rounded-full font-medium">
                    {item.category}
                  </span>
                </div>
                <h3 className="font-bold text-sm text-slate-100 mb-1">{item.name}</h3>
                <p className="text-slate-400 font-mono text-[11px] mb-2">{item.specs}</p>
                <p className="text-slate-300 leading-relaxed text-xs">{item.notes}</p>
              </div>

              <div className="pt-3 border-t border-slate-900 flex flex-wrap items-center justify-between text-[11px] text-slate-400 gap-2">
                <span>
                  <strong className="text-slate-300">Embalaje:</strong> {item.packaging}
                </span>
                <span className="bg-slate-900 px-2 py-0.5 rounded text-slate-300 font-mono">
                  {item.unit}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
