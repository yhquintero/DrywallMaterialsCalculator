import React, { useState } from 'react';
import {
  Lightbulb,
  ShieldCheck,
  Ruler,
  AlertTriangle,
  HelpCircle,
  FileCheck,
  Check,
  Hammer,
  BookOpen
} from 'lucide-react';
import { UnitSystem } from '../types';

interface AdvisoryPanelProps {
  unitSystem: UnitSystem;
  tips: string[];
}

export const AdvisoryPanel: React.FC<AdvisoryPanelProps> = ({ unitSystem, tips }) => {
  const [activeTab, setActiveTab] = useState<'tips' | 'levels' | 'deflection' | 'faq'>('tips');

  const finishingLevels = [
    {
      level: 'Nivel 0',
      title: 'Solo Placas Fijadas',
      desc: 'Placas atornilladas a la estructura sin tratamiento de cinta ni masilla. Solo para construcciones provisionales o prueba.',
      suitability: 'Estructuras temporales'
    },
    {
      level: 'Nivel 1',
      title: 'Cinta Embebida (Básico)',
      desc: 'Cinta embebida en masilla en todas las juntas y ángulos. No requiere eliminación de exceso de masilla ni lijado.',
      suitability: 'Plenums sobre falsos techos y áreas no visibles'
    },
    {
      level: 'Nivel 2',
      title: 'Capa Fina Sobre Fijaciones',
      desc: 'Cinta embebida más una fina capa de masilla sobre las cabezas de tornillos y esquineros. Sin lijar.',
      suitability: 'Zonas de sustrato bajo azulejos o cerámicas'
    },
    {
      level: 'Nivel 3',
      title: 'Segunda Capa de Masilla',
      desc: 'Dos capas sobre juntas y fijaciones. Bordes afinados y lijado suave para eliminar rebabas.',
      suitability: 'Bajo revestimientos vinílicos o pinturas texturizadas pesadas'
    },
    {
      level: 'Nivel 4',
      title: 'Estándar Comercial (Tres Capas)',
      desc: 'Tres capas de masilla con lija fina. La superficie queda lisa y continua.',
      suitability: 'Pinturas mates o satinadas estándar, empapelados decorativos'
    },
    {
      level: 'Nivel 5',
      title: 'Acabado de Máxima Calidad (Skim Coat)',
      desc: 'Nivel 4 + enlucido total con capa ultrafina de masilla en toda la placa para igualar la absorción.',
      suitability: 'Iluminación rasante crítica, pinturas brillantes o lacadas'
    }
  ];

  const deflectionData = [
    { stud: 'Montante 38mm (Calibre 25 / 0.45mm)', spacing: '40.7 cm (16")', maxH: '2.50 m (8.2 ft)', use: 'Trasdosados y tabiques bajos sin carga' },
    { stud: 'Montante 64mm (Calibre 25 / 0.50mm)', spacing: '40.7 cm (16")', maxH: '3.10 m (10.2 ft)', use: 'Tabiques estándar residenciales y oficinas' },
    { stud: 'Montante 64mm (Calibre 20 / 0.90mm)', spacing: '40.7 cm (16")', maxH: '3.80 m (12.5 ft)', use: 'Altura media y soporte para alicatado cerámico' },
    { stud: 'Montante 90mm (Calibre 20 / 0.90mm)', spacing: '40.7 cm (16")', maxH: '4.70 m (15.4 ft)', use: 'Alturas elevadas, pasillos y naves industriales' },
    { stud: 'Doble Montante Cajón 90mm', spacing: '40.7 cm (16")', maxH: '6.20 m (20.3 ft)', use: 'Muros cortafuego altos y divisiones de naves' }
  ];

  const faqs = [
    {
      q: '¿Por qué se fisuran las juntas del drywall y cómo evitarlo?',
      a: 'Las fisuras suelen ocurrir por: 1) No dejar la separación de 1cm con el piso (absorción de humedad o flexión de losa); 2) No trabar las placas (las juntas deben colocarse a matajunta / tresbolillo); 3) Modulación excesiva de los parantes (>60cm); 4) Secado forzado de la masilla con corrientes de aire calientes.'
    },
    {
      q: '¿A qué distancia del borde de la placa deben colocarse los tornillos?',
      a: 'Nunca atornille a menos de 10mm (1 cm) de los bordes biselados ni a menos de 15mm de los bordes cortados transversalmente para no descascarillar el núcleo de yeso.'
    },
    {
      q: '¿Cómo debe entrar el tornillo en la placa de yeso?',
      a: 'La cabeza del tornillo debe quedar avellanada aproximadamente 0.5mm a 1mm bajo la superficie de la celulosa (formando un hoyuelo), sin rasgar ni romper el papel exterior.'
    },
    {
      q: '¿Cuándo usar placa verde RH versus placa de cemento?',
      a: 'La placa verde RH es apta para ambientes húmedos intermitentes (baños residenciales, cocinas). Para duchas directas o zonas exteriores expuestas a agua líquida, es obligatorio usar placas de cemento (Aquapanel, Durock o Superboard) o placas con velo de fibra de vidrio.'
    }
  ];

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
      {/* Panel Navigation Tabs */}
      <div className="bg-slate-850 px-4 py-3 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center space-x-2">
          <BookOpen className="w-4 h-4 text-brand-400" />
          <h3 className="font-semibold text-sm text-slate-100">
            Manual Técnico & Asesoría de Ingeniería Drywall
          </h3>
        </div>

        <div className="flex items-center space-x-1 bg-slate-950 p-1 rounded-lg border border-slate-800 text-xs font-medium">
          <button
            onClick={() => setActiveTab('tips')}
            className={`px-3 py-1 rounded-md transition-all ${
              activeTab === 'tips' ? 'bg-brand-600 text-white' : 'text-slate-400 hover:text-white'
            }`}
          >
            Recomendaciones de Obra
          </button>
          <button
            onClick={() => setActiveTab('levels')}
            className={`px-3 py-1 rounded-md transition-all ${
              activeTab === 'levels' ? 'bg-brand-600 text-white' : 'text-slate-400 hover:text-white'
            }`}
          >
            Niveles de Acabado (0-5)
          </button>
          <button
            onClick={() => setActiveTab('deflection')}
            className={`px-3 py-1 rounded-md transition-all ${
              activeTab === 'deflection' ? 'bg-brand-600 text-white' : 'text-slate-400 hover:text-white'
            }`}
          >
            Alturas Máximas
          </button>
          <button
            onClick={() => setActiveTab('faq')}
            className={`px-3 py-1 rounded-md transition-all ${
              activeTab === 'faq' ? 'bg-brand-600 text-white' : 'text-slate-400 hover:text-white'
            }`}
          >
            Preguntas Frecuentes
          </button>
        </div>
      </div>

      {/* Tab Content */}
      <div className="p-5">
        {activeTab === 'tips' && (
          <div className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {tips.map((tip, idx) => (
                <div
                  key={idx}
                  className="bg-slate-950/70 border border-slate-800 rounded-xl p-3.5 flex items-start space-x-3 text-xs"
                >
                  <div className="p-1.5 rounded-lg bg-brand-500/10 text-brand-400 shrink-0 mt-0.5">
                    <Check className="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <h4 className="font-semibold text-slate-200 mb-1">
                      Criterio Técnico #{idx + 1}
                    </h4>
                    <p className="text-slate-400 leading-relaxed">{tip}</p>
                  </div>
                </div>
              ))}
            </div>

            {/* General Best Practices Summary */}
            <div className="mt-4 p-4 rounded-xl bg-blue-500/10 border border-blue-500/20 text-xs text-blue-200 flex items-start space-x-3">
              <ShieldCheck className="w-5 h-5 text-blue-400 shrink-0 mt-0.5" />
              <div>
                <h4 className="font-semibold text-blue-300 mb-1">
                  Estándares Internacionales Aplicados
                </h4>
                <p className="text-blue-200/90 leading-relaxed">
                  Los cómputos de esta plataforma están calibrados según las directrices de la
                  Asociación de Drywall y Pladur (USG Gypsum Construction Handbook, Knauf Drywall Systems y directivas EN 520 / ASTM C840).
                </p>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'levels' && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {finishingLevels.map((lvl, i) => (
              <div
                key={i}
                className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-col justify-between hover:border-slate-700 transition-colors"
              >
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-brand-400 font-mono bg-brand-500/10 px-2 py-0.5 rounded">
                      {lvl.level}
                    </span>
                    <FileCheck className="w-4 h-4 text-slate-500" />
                  </div>
                  <h4 className="font-semibold text-sm text-slate-200 mb-1.5">{lvl.title}</h4>
                  <p className="text-xs text-slate-400 leading-relaxed mb-3">{lvl.desc}</p>
                </div>
                <div className="pt-2 border-t border-slate-900 text-[11px] text-slate-500">
                  <span className="font-medium text-slate-400">Uso idóneo:</span> {lvl.suitability}
                </div>
              </div>
            ))}
          </div>
        )}

        {activeTab === 'deflection' && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="bg-slate-950 text-slate-400 uppercase tracking-wider border-b border-slate-800">
                  <th className="py-2.5 px-3">Perfil Montante (Parante C)</th>
                  <th className="py-2.5 px-3">Modulación Entre Ejes</th>
                  <th className="py-2.5 px-3 text-brand-400 font-bold">Altura Límite (L/240)</th>
                  <th className="py-2.5 px-3">Aplicación Típica</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 font-mono">
                {deflectionData.map((row, i) => (
                  <tr key={i} className="hover:bg-slate-850/50">
                    <td className="py-3 px-3 font-sans font-medium text-slate-200">{row.stud}</td>
                    <td className="py-3 px-3 text-slate-300">{row.spacing}</td>
                    <td className="py-3 px-3 font-bold text-brand-400">{row.maxH}</td>
                    <td className="py-3 px-3 font-sans text-slate-400">{row.use}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === 'faq' && (
          <div className="space-y-3">
            {faqs.map((faq, i) => (
              <div key={i} className="bg-slate-950 border border-slate-800 rounded-xl p-4">
                <h4 className="text-xs font-semibold text-brand-400 flex items-center gap-2 mb-2">
                  <HelpCircle className="w-4 h-4 shrink-0" />
                  <span>{faq.q}</span>
                </h4>
                <p className="text-xs text-slate-300 leading-relaxed pl-6">{faq.a}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
