import { ConstructionType, MaterialCategory } from '../types';

export interface ConstructionTypeDefinition {
  id: ConstructionType;
  name: string;
  nameEn: string;
  description: string;
  descriptionEn: string;
  category: 'ceiling' | 'wall' | 'special' | 'structural';
  icon: string;
  accentColor: string;
  standardPlate: string;
  framingPattern: string;
  consumptionRates: Record<string, {
    rate: number;
    unit: string;
    category: MaterialCategory;
    commercialPackage: {
      name: string;
      unitSize: number;
      unitName: string;
    };
    defaultPriceUSD: number;
  }>;
  technicalTips: string[];
  technicalTipsEn: string[];
}

export const CONSTRUCTION_TYPES: Record<ConstructionType, ConstructionTypeDefinition> = {
  techo_st: {
    id: 'techo_st',
    name: 'Techo ST (Estándar)',
    nameEn: 'Standard Drywall Ceiling (ST)',
    description: 'Cielo raso continuo con junta invisible y placas estándar de yeso 12.5mm.',
    descriptionEn: 'Continuous drywall ceiling with concealed joints and standard 12.5mm plasterboards.',
    category: 'ceiling',
    icon: 'Layers',
    accentColor: '#38bdf8',
    standardPlate: 'Placa Yeso ST 12.5mm (1.20 x 2.40m)',
    framingPattern: 'Primarios a 1.20m, Omegas a 40.7cm',
    consumptionRates: {
      'Placa Drywall ST 12.5mm': {
        rate: 0.35,
        unit: 'planchas',
        category: 'boards',
        commercialPackage: { name: 'Placa 1.20x2.40m (2.88m²)', unitSize: 1, unitName: 'placas' },
        defaultPriceUSD: 12.50
      },
      'Perfil Primario Metálico': {
        rate: 0.85,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.35
      },
      'Perfil Omega / Furring': {
        rate: 1.10,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.15
      },
      'Tornillo T1 Estructura (Comecocos)': {
        rate: 18.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.015
      },
      'Tornillo T2 Placa (Punta Aguja)': {
        rate: 24.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.018
      },
      'Cinta de Papel Microperforada': {
        rate: 1.50,
        unit: 'm lineales',
        category: 'tapes',
        commercialPackage: { name: 'Rollo 150 m', unitSize: 150, unitName: 'rollos' },
        defaultPriceUSD: 0.08
      },
      'Masilla Compuesto de Juntas': {
        rate: 0.90,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Balde 28 kg (o Caja)', unitSize: 28, unitName: 'baldes' },
        defaultPriceUSD: 0.85
      },
      'Fijaciones y Clavos de Impacto': {
        rate: 1.50,
        unit: 'juegos',
        category: 'accessories',
        commercialPackage: { name: 'Caja 100 juegos', unitSize: 100, unitName: 'cajas' },
        defaultPriceUSD: 0.25
      }
    },
    technicalTips: [
      'Asegure nivelación láser previa en todo el perímetro antes de colocar los ángulos.',
      'Los perfiles primarios deben estar suspendidos a 1.20m máximo con varilla roscada o alambre galvanizado #12.',
      'Perfiles Omega modulados cada 40.7cm transversalmente para evitar pandeo.',
      'Dejar 10mm de holgura perimetral para absorción de dilataciones térmicas.'
    ],
    technicalTipsEn: [
      'Ensure laser leveling around the perimeter before installing track angles.',
      'Primary carrying channels should be spaced at max 1.20m using #12 wire or threaded rods.',
      'Furring channels spaced @ 16" (40.7cm) perpendicular to framing to prevent sagging.',
      'Leave a 10mm gap at perimeters for thermal expansion absorption.'
    ]
  },

  techo_rh: {
    id: 'techo_rh',
    name: 'Techo RH (Resistente a Humedad)',
    nameEn: 'Moisture Resistant Ceiling (Green Board)',
    description: 'Cielo raso para baños, lavanderías, cocinas y áreas con humedad constante (Placa Verde).',
    descriptionEn: 'Suspended ceiling for bathrooms, kitchens, and high-humidity areas (Green Board).',
    category: 'ceiling',
    icon: 'Droplets',
    accentColor: '#10b981',
    standardPlate: 'Placa Yeso RH Verde 12.5mm (1.20 x 2.40m)',
    framingPattern: 'Primarios a 1.00m, Omegas a 40.7cm',
    consumptionRates: {
      'Placa Drywall RH Verde 12.5mm': {
        rate: 0.35,
        unit: 'planchas',
        category: 'boards',
        commercialPackage: { name: 'Placa 1.20x2.40m (2.88m²)', unitSize: 1, unitName: 'placas' },
        defaultPriceUSD: 16.20
      },
      'Perfil Primario Galvanizado': {
        rate: 0.85,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.45
      },
      'Perfil Omega Galvanizado': {
        rate: 1.10,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.25
      },
      'Tornillo T1 Zincado Anticorrosión': {
        rate: 18.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.018
      },
      'Tornillo T2 Zincado Anticorrosión': {
        rate: 24.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.022
      },
      'Cinta de Malla Fibra de Vidrio Anti-Humedad': {
        rate: 1.50,
        unit: 'm lineales',
        category: 'tapes',
        commercialPackage: { name: 'Rollo 90 m', unitSize: 90, unitName: 'rollos' },
        defaultPriceUSD: 0.12
      },
      'Masilla Hidrófuga Especial RH': {
        rate: 1.00,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Balde 20 kg', unitSize: 20, unitName: 'baldes' },
        defaultPriceUSD: 1.10
      },
      'Fulminantes y Clavos Tratados': {
        rate: 1.50,
        unit: 'juegos',
        category: 'accessories',
        commercialPackage: { name: 'Caja 100 juegos', unitSize: 100, unitName: 'cajas' },
        defaultPriceUSD: 0.28
      }
    },
    technicalTips: [
      'Utilice tornillos zincados anticorrosión para evitar manchas de óxido por vapor de agua.',
      'Se recomienda imprimación hidrófuga y pintura impermeable de acabado.',
      'Sellar juntas perimetrales con cordón de silicona neutra anti-hongos.',
      'Mantener ventilación activa o pasiva para evitar condensación prolongada.'
    ],
    technicalTipsEn: [
      'Always use zinc-coated anti-corrosion screws to prevent rust marks from moisture.',
      'Apply moisture-resistant primer and waterproof top coat.',
      'Seal perimeter gaps with anti-mold silicone sealant.',
      'Ensure adequate ventilation to prevent persistent vapor condensation.'
    ]
  },

  muro_sencillo: {
    id: 'muro_sencillo',
    name: 'Muro Sencillo (Una cara)',
    nameEn: 'Single-Sided Drywall Lining / Furring Wall',
    description: 'Revestimiento interior o trasdosado sobre pared existente de ladrillo u hormigón.',
    descriptionEn: 'Wall lining / furring system attached or spaced from an existing masonry wall.',
    category: 'wall',
    icon: 'Maximize2',
    accentColor: '#fb923c',
    standardPlate: 'Placa Yeso ST 12.5mm (1.20 x 2.40m)',
    framingPattern: 'Canal U 64mm + Parante C @ 40.7cm',
    consumptionRates: {
      'Placa Drywall ST 12.5mm': {
        rate: 0.35,
        unit: 'planchas',
        category: 'boards',
        commercialPackage: { name: 'Placa 1.20x2.40m (2.88m²)', unitSize: 1, unitName: 'placas' },
        defaultPriceUSD: 12.50
      },
      'Canal Guía Metálica (U) 64mm': {
        rate: 0.80,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.20
      },
      'Parante Montante (C) 64mm': {
        rate: 1.20,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.40
      },
      'Tornillo T1 Estructura': {
        rate: 12.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.015
      },
      'Tornillo T2 Fijación Placa': {
        rate: 20.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.018
      },
      'Cinta de Papel Reforzada': {
        rate: 1.50,
        unit: 'm lineales',
        category: 'tapes',
        commercialPackage: { name: 'Rollo 150 m', unitSize: 150, unitName: 'rollos' },
        defaultPriceUSD: 0.08
      },
      'Masilla de Juntas Drywall': {
        rate: 0.80,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Balde 28 kg', unitSize: 28, unitName: 'baldes' },
        defaultPriceUSD: 0.85
      },
      'Tacos y Tornillos de Fijación a Muro': {
        rate: 1.20,
        unit: 'juegos',
        category: 'accessories',
        commercialPackage: { name: 'Bolsa 100 u.', unitSize: 100, unitName: 'bolsas' },
        defaultPriceUSD: 0.20
      }
    },
    technicalTips: [
      'Coloque banda acústica / estanca bajo el canal inferior para desolidarización térmica y sonora.',
      'Parantes modulados cada 40.7cm (o 61cm en alturas menores a 2.60m sin carga).',
      'Atornillar placas cada 25cm en parantes intermedios y 20cm en bordes perimetrales.',
      'Mantener la placa 1cm despegada del suelo para evitar succión de humedad capilar.'
    ],
    technicalTipsEn: [
      'Install acoustic isolation foam tape beneath the bottom track.',
      'Space studs @ 16" (40.7cm) on center (or 24" for lower walls without loads).',
      'Fasten screws every 25cm along intermediate studs and 20cm on perimeter edges.',
      'Keep drywall panels 1cm above the finished floor to prevent water wicking.'
    ]
  },

  tabique_divisor: {
    id: 'tabique_divisor',
    name: 'Tabique Divisor (Dos caras)',
    nameEn: 'Partition Wall (Double-Sided Acoustic)',
    description: 'Pared divisoria autoportante con placas a ambos lados y aislamiento termo-acústico central.',
    descriptionEn: 'Self-supporting partition wall with drywall on both sides and internal acoustic wool.',
    category: 'wall',
    icon: 'SplitSquareVertical',
    accentColor: '#a855f7',
    standardPlate: 'Placa Yeso ST 12.5mm x 2 Caras',
    framingPattern: 'Canal U + Parantes C @ 40.7cm + Lana de Vidrio',
    consumptionRates: {
      'Placa Drywall ST 12.5mm (Doble Cara)': {
        rate: 0.70,
        unit: 'planchas',
        category: 'boards',
        commercialPackage: { name: 'Placa 1.20x2.40m (2.88m²)', unitSize: 1, unitName: 'placas' },
        defaultPriceUSD: 12.50
      },
      'Canal Guía Metálica (U) 64mm/90mm': {
        rate: 0.80,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.30
      },
      'Parante Montante (C) 64mm/90mm': {
        rate: 1.80,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.50
      },
      'Tornillo T1 Estructura': {
        rate: 14.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.015
      },
      'Tornillo T2 Fijación Placas': {
        rate: 42.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.018
      },
      'Cinta de Papel Reforzada': {
        rate: 3.00,
        unit: 'm lineales',
        category: 'tapes',
        commercialPackage: { name: 'Rollo 150 m', unitSize: 150, unitName: 'rollos' },
        defaultPriceUSD: 0.08
      },
      'Masilla de Juntas Drywall': {
        rate: 1.60,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Balde 28 kg', unitSize: 28, unitName: 'baldes' },
        defaultPriceUSD: 0.85
      },
      'Lana de Vidrio Acústica 50mm': {
        rate: 1.05,
        unit: 'm²',
        category: 'insulation',
        commercialPackage: { name: 'Rollo 14.4 m²', unitSize: 14.4, unitName: 'rollos' },
        defaultPriceUSD: 3.20
      },
      'Fijaciones Suelo y Techo': {
        rate: 1.60,
        unit: 'juegos',
        category: 'accessories',
        commercialPackage: { name: 'Caja 100 juegos', unitSize: 100, unitName: 'cajas' },
        defaultPriceUSD: 0.25
      }
    },
    technicalTips: [
      'Alterne las juntas de las placas entre ambas caras: nunca haga coincidir una junta en la misma posición de ambas caras.',
      'En puertas y ventanas, refuerce los parantes con canal insertado o montante doble espaldado.',
      'La lana de vidrio debe cubrir completamente la cavidad sin comprimirse excesivamente.',
      'Pase las instalaciones eléctricas e hidrosanitarias por los orificios troquelados antes de cerrar la 2ª cara.'
    ],
    technicalTipsEn: [
      'Stagger board joints between both sides: never align seams on opposite sides of the wall.',
      'For door and window openings, box or double studs for structural rigidity.',
      'Glass wool insulation must fill the cavity continuously without crushing.',
      'Complete electrical and plumbing rough-ins through stud knockouts before boarding the second face.'
    ]
  },

  muro_rf: {
    id: 'muro_rf',
    name: 'Muro RF (Resistente al Fuego)',
    nameEn: 'Fire-Rated Partition Wall (RF / Type X)',
    description: 'Muro corta-fuego homologado (RF 60/120) para pasillos de evacuación, calderas y cocheras.',
    descriptionEn: 'Certified fire barrier wall (FRL 60/120) for egress corridors, mechanical rooms and garages.',
    category: 'wall',
    icon: 'Flame',
    accentColor: '#ef4444',
    standardPlate: 'Placa Yeso RF Rosa/Roja 15mm Doble',
    framingPattern: 'Canal U 90mm + Parantes C 90mm @ 40.7cm',
    consumptionRates: {
      'Placa Drywall RF Roja 15mm': {
        rate: 0.70,
        unit: 'planchas',
        category: 'boards',
        commercialPackage: { name: 'Placa 1.20x2.40m (2.88m²)', unitSize: 1, unitName: 'placas' },
        defaultPriceUSD: 18.50
      },
      'Canal Guía Metálica 90mm Reforzado': {
        rate: 0.80,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.75
      },
      'Parante Montante 90mm Calibre 20': {
        rate: 2.40,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 2.10
      },
      'Tornillo T2 Largo Fosfatado Especial RF': {
        rate: 48.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.024
      },
      'Cinta de Fibra de Vidrio Ignífuga': {
        rate: 3.00,
        unit: 'm lineales',
        category: 'tapes',
        commercialPackage: { name: 'Rollo 90 m', unitSize: 90, unitName: 'rollos' },
        defaultPriceUSD: 0.14
      },
      'Masilla Cortafuego Homologada': {
        rate: 1.80,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Balde 25 kg', unitSize: 25, unitName: 'baldes' },
        defaultPriceUSD: 1.45
      },
      'Lana de Roca Mineral Ignífuga 70kg/m³': {
        rate: 1.05,
        unit: 'm²',
        category: 'insulation',
        commercialPackage: { name: 'Paquete 7.2 m²', unitSize: 7.2, unitName: 'paquetes' },
        defaultPriceUSD: 5.80
      },
      'Sellador Intumescente Cortafuego': {
        rate: 0.10,
        unit: 'cartuchos',
        category: 'accessories',
        commercialPackage: { name: 'Cartucho 310 ml', unitSize: 1, unitName: 'cartuchos' },
        defaultPriceUSD: 8.50
      }
    },
    technicalTips: [
      'Cumplir estrictamente con la normativa NFPA / EN 1364 para sellos cortafuego.',
      'Sellar todos los perímetros y pases de tubos con masilla intumescente contra humo caliente.',
      'Utilice lana de roca de alta densidad (≥70 kg/m³) de punto de fusión > 1000°C.',
      'No taladrar ni hacer perforaciones sin collarines o almohadillas intumescentes de protección.'
    ],
    technicalTipsEn: [
      'Strictly comply with NFPA / EN fire rating specifications.',
      'Seal all perimeters and penetrations with intumescent firestop mastic.',
      'Use high density rockwool (≥70 kg/m³) with melting point over 1000°C.',
      'Never leave unsealed electrical boxes; protect with intumescent putty pads.'
    ]
  },

  plafon_reticulado: {
    id: 'plafon_reticulado',
    name: 'Plafón Reticulado (60x60)',
    nameEn: 'Acoustic Suspended Grid Ceiling (2x2 ft / 60x60 cm)',
    description: 'Cielo raso registrable con perfilería vista T24 y baldosas de fibra mineral o metálicas.',
    descriptionEn: 'Lay-in suspended grid ceiling with exposed T-bar grid and 60x60 acoustic tiles.',
    category: 'ceiling',
    icon: 'Grid',
    accentColor: '#06b6d4',
    standardPlate: 'Baldosas Acústicas Minerales 60x60 cm',
    framingPattern: 'Rejilla T24: Principal 3.66m + Sec 1.22m + Ter 0.61m',
    consumptionRates: {
      'Baldosa Cielo Raso 60x60cm': {
        rate: 2.80,
        unit: 'piezas',
        category: 'boards',
        commercialPackage: { name: 'Caja 16 baldosas (5.76 m²)', unitSize: 16, unitName: 'cajas' },
        defaultPriceUSD: 2.40
      },
      'Perfil T Principal 3.66m (T24)': {
        rate: 0.23,
        unit: 'piezas',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.66 m', unitSize: 1, unitName: 'piezas' },
        defaultPriceUSD: 5.60
      },
      'Perfil T Secundario 1.22m (T24)': {
        rate: 1.40,
        unit: 'piezas',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 1.22 m', unitSize: 1, unitName: 'piezas' },
        defaultPriceUSD: 2.20
      },
      'Perfil T Terciario 0.61m (T24)': {
        rate: 0.70,
        unit: 'piezas',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 0.61 m', unitSize: 1, unitName: 'piezas' },
        defaultPriceUSD: 1.15
      },
      'Ángulo Perimetral L Blanco 3.00m': {
        rate: 0.60,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.10
      },
      'Alambre Galvanizado #12 de Suspensión': {
        rate: 0.80,
        unit: 'm lineales',
        category: 'accessories',
        commercialPackage: { name: 'Rollo 100 m', unitSize: 100, unitName: 'rollos' },
        defaultPriceUSD: 0.20
      },
      'Clavos o Anclajes con Ojal': {
        rate: 1.20,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 100 u.', unitSize: 100, unitName: 'cajas' },
        defaultPriceUSD: 0.30
      }
    },
    technicalTips: [
      'Haga un replanteo previo centrado en la habitación para que las baldosas de borde sean simétricas (>30cm).',
      'Suspender el perfil principal cada 1.20m mediante alambre tensado en mariposa o varilla.',
      'Ideal para oficinas y locales comerciales por su facilidad de acceso a instalaciones eléctricas y de aire acondicionado.',
      'Manipule las baldosas minerales con guantes limpios de algodón para evitar marcas.'
    ],
    technicalTipsEn: [
      'Center the grid layout so border tiles on opposite walls are equal and greater than half a tile (>30cm).',
      'Suspend main runners every 1.20m using tensioned wire or hanger clips.',
      'Allows 100% plenum accessibility for HVAC and electrical maintenance.',
      'Wear clean cotton gloves when installing mineral tiles to prevent grease stains.'
    ]
  },

  cajillo_viga: {
    id: 'cajillo_viga',
    name: 'Cajillo / Viga Drywall',
    nameEn: 'Bulkhead / Soffit / Drywall Beam',
    description: 'Desniveles de techo, vigas decorativas falsas, cortineros e iluminación indirecta LED.',
    descriptionEn: 'Architectural soffits, recessed light coves, curtain pockets, and drywall bulkheads.',
    category: 'special',
    icon: 'Box',
    accentColor: '#eab308',
    standardPlate: 'Placa Yeso ST 12.5mm con Esquineros',
    framingPattern: 'Doble Canal U + Parantes cortos + Esquineros metálicos',
    consumptionRates: {
      'Placa Drywall ST 12.5mm': {
        rate: 0.45,
        unit: 'planchas',
        category: 'boards',
        commercialPackage: { name: 'Placa 1.20x2.40m (2.88m²)', unitSize: 1, unitName: 'placas' },
        defaultPriceUSD: 12.50
      },
      'Canal Metálico Guía (U)': {
        rate: 1.50,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.30
      },
      'Parante Montante (C)': {
        rate: 2.00,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 1.50
      },
      'Esquinero Metálico Perforado': {
        rate: 1.20,
        unit: 'm lineales',
        category: 'accessories',
        commercialPackage: { name: 'Tira de 2.60 m', unitSize: 2.6, unitName: 'tiras' },
        defaultPriceUSD: 0.95
      },
      'Tornillo T1 Estructura': {
        rate: 25.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.015
      },
      'Tornillo T2 Fijación Placas': {
        rate: 30.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 1,000 u.', unitSize: 1000, unitName: 'cajas' },
        defaultPriceUSD: 0.018
      },
      'Masilla de Juntas y Acabado': {
        rate: 1.20,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Balde 28 kg', unitSize: 28, unitName: 'baldes' },
        defaultPriceUSD: 0.85
      }
    },
    technicalTips: [
      'Aplique esquineros metálicos o cinta con refuerzo metálico en todas las esquinas expuestas.',
      'Prevea cableado y espacio libre para tiras LED de iluminación indirecta.',
      'Refuerce las aristas con escuadras metálicas triangulares cada 60cm.'
    ],
    technicalTipsEn: [
      'Apply metal corner beads on all external 90-degree corners.',
      'Plan wiring and clearance for hidden LED strip cove lighting.',
      'Add internal gusset diagonal braces every 60cm for cantilever stability.'
    ]
  },

  fachada_eifs: {
    id: 'fachada_eifs',
    name: 'Fachada EIFS / Exterior',
    nameEn: 'EIFS Exterior Facade System',
    description: 'Sistema de aislamiento térmico exterior con placas OSB/Cementicias, EPS y base coat.',
    descriptionEn: 'Exterior Insulation and Finish System with weather barrier, EPS insulation, and base coat.',
    category: 'special',
    icon: 'Building',
    accentColor: '#14b8a6',
    standardPlate: 'Placa OSB 11.1mm / Cemento + EPS',
    framingPattern: 'Estructura Steel / Tyvek + Malla de Fibra',
    consumptionRates: {
      'Placa OSB Estructural 11.1mm (o Cementicia)': {
        rate: 0.35,
        unit: 'planchas',
        category: 'boards',
        commercialPackage: { name: 'Placa 1.22x2.44m (2.98m²)', unitSize: 1, unitName: 'placas' },
        defaultPriceUSD: 19.50
      },
      'Barrera Hidrófuga / Membrana Tyvek': {
        rate: 1.10,
        unit: 'm²',
        category: 'accessories',
        commercialPackage: { name: 'Rollo 30 m²', unitSize: 30, unitName: 'rollos' },
        defaultPriceUSD: 1.60
      },
      'Planchas de EPS Poliestireno 50mm': {
        rate: 1.05,
        unit: 'm²',
        category: 'insulation',
        commercialPackage: { name: 'Paquete 6 m²', unitSize: 6, unitName: 'paquetes' },
        defaultPriceUSD: 4.80
      },
      'Malla de Fibra de Vidrio Antialcalina 160g': {
        rate: 1.20,
        unit: 'm²',
        category: 'tapes',
        commercialPackage: { name: 'Rollo 50 m²', unitSize: 50, unitName: 'rollos' },
        defaultPriceUSD: 1.10
      },
      'Mortero Base Coat Adhesivo': {
        rate: 5.00,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Saco 25 kg', unitSize: 25, unitName: 'sacos' },
        defaultPriceUSD: 0.65
      },
      'Fijaciones Mecánicas con Arandela': {
        rate: 8.00,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 250 u.', unitSize: 250, unitName: 'cajas' },
        defaultPriceUSD: 0.18
      },
      'Revestimiento Texturizado Acrílico': {
        rate: 2.50,
        unit: 'kg',
        category: 'compounds',
        commercialPackage: { name: 'Balde 25 kg', unitSize: 25, unitName: 'baldes' },
        defaultPriceUSD: 1.80
      }
    },
    technicalTips: [
      'Garantice traslapes mínimos de 10cm en la membrana hidrófuga y en la malla de fibra.',
      'Embeber la malla completamente dentro de la primera capa de base coat mientras está fresca.',
      'Trabajar a temperaturas entre 5°C y 30°C sin lluvia ni insolación directa extrema.'
    ],
    technicalTipsEn: [
      'Overlap weather barrier and fiberglass mesh by at least 10cm (4 inches).',
      'Fully embed fiberglass mesh into the first wet pass of base coat mortar.',
      'Apply between 5°C and 30°C; avoid direct scorching sunlight or rain during cure.'
    ]
  },

  steel_framing: {
    id: 'steel_framing',
    name: 'Steel Framing (Estructural)',
    nameEn: 'Light Gauge Steel Framing (Structural)',
    description: 'Estructura portante ligera con perfiles PGC (Montante) y PGU (Solera) de acero galvanizado.',
    descriptionEn: 'Structural cold-formed steel framing with load-bearing PGC studs and PGU tracks.',
    category: 'structural',
    icon: 'ShieldCheck',
    accentColor: '#6366f1',
    standardPlate: 'Estructura Portante C100/U100',
    framingPattern: 'PGC 100x0.9 @ 40cm + Cruces de San Andrés',
    consumptionRates: {
      'Perfil Montante PGC 100x0.9mm': {
        rate: 1.50,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 4.20
      },
      'Perfil Solera PGU 100x0.9mm': {
        rate: 0.80,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Tira de 3.00 m', unitSize: 3.0, unitName: 'tiras' },
        defaultPriceUSD: 3.80
      },
      'Tornillo Hexagonal Estructural 14x3/4 Punta Broca': {
        rate: 15.0,
        unit: 'piezas',
        category: 'fasteners',
        commercialPackage: { name: 'Caja 500 u.', unitSize: 500, unitName: 'cajas' },
        defaultPriceUSD: 0.05
      },
      'Fleje de San Andrés (Cruz de Arriostre)': {
        rate: 0.50,
        unit: 'm lineales',
        category: 'profiles',
        commercialPackage: { name: 'Rollo 30 m', unitSize: 30, unitName: 'rollos' },
        defaultPriceUSD: 1.40
      },
      'Anclaje Químico / Varilla Roscada a Fundación': {
        rate: 0.08,
        unit: 'juegos',
        category: 'fasteners',
        commercialPackage: { name: 'Juego Anclaje', unitSize: 1, unitName: 'juegos' },
        defaultPriceUSD: 3.50
      }
    },
    technicalTips: [
      'Arriostrar los paneles mediante cruces de San Andrés tensadas para resistir vientos y sismos.',
      'Alinear las cargas axiales directamente stud sobre stud o colocar dintel de repartición.',
      'Aislar térmicamente el exterior para cortar el puente térmico del perfil de acero.'
    ],
    technicalTipsEn: [
      'Install tensioned strap X-bracing to resist lateral wind and seismic forces.',
      'Align inline framing studs directly under floor joists or rafters.',
      'Provide continuous exterior insulation to prevent thermal bridging across steel flanges.'
    ]
  },

  multi_partes: {
    id: 'multi_partes',
    name: 'Multi-Partes / Obra Completa',
    nameEn: 'Multi-Room / Full Project Takeoff',
    description: 'Gestione múltiples habitaciones, techos, tabiques y aberturas en un solo cómputo global.',
    descriptionEn: 'Manage multiple rooms, partitions, ceilings and openings in one unified project estimate.',
    category: 'special',
    icon: 'FolderKanban',
    accentColor: '#3b82f6',
    standardPlate: 'Personalizable por estancia',
    framingPattern: 'Dinámico según estancia',
    consumptionRates: {},
    technicalTips: [
      'Organice su obra por estancias (Salón, Baño, Habitación, etc.) para control exacto del material.',
      'Las puertas y ventanas se descuentan del metrado neto de placa automáticamente.',
      'La estructura añade refuerzos perimetrales por cada abertura ingresada.'
    ],
    technicalTipsEn: [
      'Organize takeoff by rooms (Living room, Bathroom, Bedrooms) for precise distribution.',
      'Doors and windows are automatically deducted from square footage.',
      'Framing calculations add trimmer and header reinforcements for every opening.'
    ]
  }
};

export const CURRENCY_SYMBOLS: Record<string, { symbol: string, rateToUSD: number, label: string }> = {
  USD: { symbol: '$', rateToUSD: 1.0, label: 'Dólar USA (USD $)' },
  EUR: { symbol: '€', rateToUSD: 0.92, label: 'Euro (EUR €)' },
  MXN: { symbol: '$', rateToUSD: 18.50, label: 'Peso Mexicano (MXN $)' },
  COP: { symbol: '$', rateToUSD: 4100.0, label: 'Peso Colombiano (COP $)' },
  ARS: { symbol: '$', rateToUSD: 1180.0, label: 'Peso Argentino (ARS $)' },
  CLP: { symbol: '$', rateToUSD: 940.0, label: 'Peso Chileno (CLP $)' },
  PEN: { symbol: 'S/.', rateToUSD: 3.75, label: 'Sol Peruano (PEN S/.)' }
};

export const QUICK_PRESETS = [
  {
    id: 'preset_bedroom_partition',
    name: 'Habitación - Muro Divisorio Acústico',
    nameEn: 'Bedroom - Acoustic Partition Wall',
    type: 'tabique_divisor' as ConstructionType,
    length: 4.5,
    width: 2.7,
    openings: [
      { id: 'p1_door', type: 'door' as const, name: 'Puerta Paso', width: 0.82, height: 2.05, count: 1 }
    ]
  },
  {
    id: 'preset_bathroom_ceiling',
    name: 'Baño - Techo Antihumedad RH',
    nameEn: 'Bathroom - Moisture Resistant Ceiling',
    type: 'techo_rh' as ConstructionType,
    length: 2.6,
    width: 2.2,
    openings: []
  },
  {
    id: 'preset_living_ceiling',
    name: 'Salón - Cielo Raso Continuo ST',
    nameEn: 'Living Room - Continuous Ceiling ST',
    type: 'techo_st' as ConstructionType,
    length: 6.0,
    width: 4.2,
    openings: []
  },
  {
    id: 'preset_office_grid',
    name: 'Oficina - Plafón Reticulado 60x60',
    nameEn: 'Office - Acoustic Grid 60x60',
    type: 'plafon_reticulado' as ConstructionType,
    length: 8.0,
    width: 5.5,
    openings: []
  },
  {
    id: 'preset_fire_wall',
    name: 'Pasillo - Muro Cortafuego RF 120',
    nameEn: 'Corridor - Fire Barrier Wall RF 120',
    type: 'muro_rf' as ConstructionType,
    length: 7.2,
    width: 2.8,
    openings: [
      { id: 'p2_fire_door', type: 'door' as const, name: 'Puerta Cortafuego', width: 0.90, height: 2.10, count: 1 }
    ]
  }
];
