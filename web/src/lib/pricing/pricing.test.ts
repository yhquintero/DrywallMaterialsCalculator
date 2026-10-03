import { describe, it, expect, beforeEach } from 'vitest';
import {
  convertCurrency,
  convertToPackage,
  findFirstArray,
  getByPath,
  inferPackage,
  matchMaterial,
  normalizeText,
  parsePrice,
  parseUnit,
  similarity
} from './normalize';
import { csvToObjects, parseCsv, pickColumn } from './csv';
import { createMockProvider } from './providers/mock';
import { renderTemplate } from './providers/rest';
import { csvProvider } from './providers/csv';
import {
  applyReportToPrices,
  normalizeQuote,
  syncPrices,
  validateEndpoint
} from './engine';
import { clearCache, readCache, writeCache } from './cache';
import { buildMaterialQueries, packageSizeFromFormat, DEFAULT_SUPPLIERS, loadSettings, SUPPLIER_TEMPLATES, validateSupplier } from './client';
import { MaterialQuery, SupplierConfig, SyncReport } from './types';
import { calculateProjectMaterials } from '../calculator';
import { DEFAULT_CONFIG, DEFAULT_ROOMS } from '../storage';

const MATERIALS: MaterialQuery[] = [
  { name: 'Placa Drywall ST 12.5mm', category: 'boards', unit: 'planchas', currentPrice: 12.5, packageSize: 2.88 },
  { name: 'Perfil Primario Metálico', category: 'profiles', unit: 'm lineales', currentPrice: 1.35, packageSize: 3 },
  { name: 'Tornillo T2 Placa (Punta Aguja)', category: 'fasteners', unit: 'piezas', currentPrice: 0.018, packageSize: 1000 },
  { name: 'Masilla Compuesto de Juntas', category: 'compounds', unit: 'kg', currentPrice: 0.85, packageSize: 28 }
];

const SUPPLIER: SupplierConfig = {
  id: 'test-1',
  name: 'Proveedor de prueba',
  kind: 'rest-json',
  enabled: true,
  currency: 'USD',
  endpoint: 'https://api.proveedor.com/catalogo',
  method: 'GET',
  timeoutMs: 5000,
  ttlMinutes: 60,
  priceAdjustmentPct: 0,
  matchThreshold: 0.35
};

beforeEach(() => {
  clearCache();
});

describe('Normalización de catálogos', () => {
  it('normaliza acentos, sinónimos y puntuación', () => {
    expect(normalizeText('Placa Drywall ST 12,5mm')).toBe('placa placa st 12.5mm');
    expect(normalizeText('Perfil Montante 48')).toBe('perfil parante 48');
    expect(normalizeText('GYPSUM BOARD 1/2')).toContain('placa');
  });

  it('empareja nombres equivalentes entre proveedor y presupuesto', () => {
    const match = matchMaterial('Placa de Yeso Standard 12.5 mm', MATERIALS, 0.3);
    expect(match?.material.name).toBe('Placa Drywall ST 12.5mm');
    expect(match!.score).toBeGreaterThan(0.3);
  });

  it('no empareja productos sin relación', () => {
    expect(matchMaterial('Cemento Portland 50 kg', MATERIALS, 0.5)).toBeNull();
  });

  it('rechaza coincidencias ambiguas por nombre muy corto', () => {
    expect(matchMaterial('Perfil', MATERIALS, 0.4)).toBeNull();
  });

  it('la similitud es simétrica y acotada', () => {
    const a = similarity('Placa Yeso ST 12.5', 'Placa Drywall ST 12,5mm');
    const b = similarity('Placa Drywall ST 12,5mm', 'Placa Yeso ST 12.5');
    expect(a).toBeCloseTo(b, 6);
    expect(a).toBeLessThanOrEqual(1);
    expect(a).toBeGreaterThan(0.4);
  });

  it('parsea precios con formato europeo y anglosajón', () => {
    expect(parsePrice('1.234,56')).toBeCloseTo(1234.56, 2);
    expect(parsePrice('$1,234.56')).toBeCloseTo(1234.56, 2);
    expect(parsePrice('12,50 €')).toBeCloseTo(12.5, 2);
    expect(parsePrice(4.5)).toBe(4.5);
    expect(parsePrice('n/d')).toBe(0);
  });

  it('reconoce unidades canónicas', () => {
    expect(parseUnit('m²')).toBe('m2');
    expect(parseUnit('METROS LINEALES')).toBe('m');
    expect(parseUnit('und')).toBe('u');
    expect(parseUnit('unidades')).toBe('u');
    expect(parseUnit('')).toBe('desconocido');
  });

  it('convierte precios al embalaje comercial', () => {
    // Placa cotizada por m² → precio por plancha de 2.88 m²
    expect(convertToPackage(4.2, 'm2', 2.88, 'm2').price).toBeCloseTo(12.096, 3);
    // Perfil por metro → tira de 3 m
    expect(convertToPackage(1.35, 'm', 3, 'm').price).toBeCloseTo(4.05, 4);
    // Tornillo por unidad → caja de 1000
    expect(convertToPackage(0.018, 'u', 1000, 'u').price).toBeCloseTo(18, 4);
    // Precio ya publicado por embalaje: no se toca
    expect(convertToPackage(12.1, 'placa', 2.88, 'm2')).toEqual({ price: 12.1, converted: false });
    // Sin conversión cuando la unidad no se reconoce
    expect(convertToPackage(10, 'desconocido', 5, 'u').converted).toBe(false);
  });

  it('deduce el embalaje por categoría', () => {
    expect(inferPackage(MATERIALS[0])).toEqual({ size: 2.88, content: 'm2', unit: 'placa' });
    expect(inferPackage(MATERIALS[1])).toEqual({ size: 3, content: 'm', unit: 'tira' });
    // La categoría manda aunque el nombre contenga "Placa"
    expect(inferPackage(MATERIALS[2])).toEqual({ size: 1000, content: 'u', unit: 'caja' });
    expect(inferPackage(MATERIALS[3])).toEqual({ size: 28, content: 'kg', unit: 'balde' });
  });

  it('convierte divisas con las tasas del proyecto', () => {
    expect(convertCurrency(100, 'USD', 'USD')).toBe(100);
    expect(convertCurrency(100, 'USD', 'EUR')).toBeCloseTo(92, 1);
    expect(convertCurrency(92, 'EUR', 'USD')).toBeCloseTo(100, 1);
    expect(convertCurrency(100, 'XXX', 'USD')).toBe(100);
  });

  it('lee valores anidados por ruta', () => {
    const payload = { data: { items: [{ price: 9.99 }] } };
    expect(getByPath(payload, 'data.items[0].price')).toBe(9.99);
    expect(getByPath(payload, 'data.missing.deep')).toBeUndefined();
    expect(findFirstArray(payload)).toEqual([{ price: 9.99 }]);
    expect(findFirstArray({ a: { b: [1, 2] } })).toEqual([1, 2]);
    expect(findFirstArray(null)).toBeNull();
  });
});

describe('CSV de proveedor', () => {
  const csv = [
    'Referencia;Descripcion;Precio;Unidad;Stock',
    'PL-125;Placa Drywall ST 12,5mm;4,25;m2;350',
    'PF-300;Perfil Primario Metálico 3m;4,10;tira;120',
    '"TO-1000";"Tornillo T2 Punta Aguja, caja 1000";17,90;caja;40'
  ].join('\n');

  it('detecta el delimitador y parsea comillas', () => {
    const withQuotes = 'a,b\n"x, y",2';
    const comma = parseCsv(withQuotes, ',');
    expect(comma.rows[1]).toEqual(['x, y', '2']);
    const semicolon = parseCsv(csv, ';');
    expect(semicolon.rows).toHaveLength(4);
    expect(semicolon.rows[3][0]).toBe('TO-1000');
  });

  it('localiza las columnas por alias', () => {
    const header = parseCsv(csv, ';').rows[0];
    expect(pickColumn(header, undefined, ['precio', 'price'])).toBe(2);
    expect(pickColumn(header, undefined, ['descripcion', 'nombre'])).toBe(1);
    expect(pickColumn(header, undefined, ['inexistente'])).toBe(-1);
  });

  it('convierte a objetos', () => {
    const objects = csvToObjects(csv, ';');
    expect(objects[0].Referencia).toBe('PL-125');
    expect(objects).toHaveLength(3);
  });

  it('el proveedor CSV normaliza filas con unidades reales', async () => {
    const quotes = await csvProvider.fetchQuotes({
      supplier: { ...SUPPLIER, kind: 'csv', csvOptions: { delimiter: ';', headerRow: 1, priceColumn: 'Precio' } },
      transport: async () => ({ status: 200, text: csv }),
      queries: MATERIALS
    });
    expect(quotes).toHaveLength(3);
    const placa = quotes.find((q) => q.sku === 'PL-125')!;
    expect(placa.price).toBeCloseTo(4.25, 2);
    expect(placa.unit).toBe('m2');
    expect(placa.stock).toBe(350);
  });

  it('falla con un CSV sin ninguna columna de precio reconocible', async () => {
    const sinPrecio = 'EAN;Marca;Color\n1;Marca X;Blanco';
    await expect(
      csvProvider.fetchQuotes({
        supplier: { ...SUPPLIER, kind: 'csv', csvOptions: { delimiter: ';', headerRow: 1, priceColumn: 'Nope' } },
        transport: async () => ({ status: 200, text: sinPrecio }),
        queries: MATERIALS
      })
    ).rejects.toThrow('columna de precio');
  });
});

describe('Plantillas y conversión de peticiones', () => {
  it('renderiza plantillas de URL codificando los parámetros', () => {
    expect(renderTemplate('/api/products?q={query}&limit={limit}', { query: 'placa 12,5', limit: '50' })).toBe(
      '/api/products?q=placa%2012%2C5&limit=50'
    );
    expect(renderTemplate('{desconocido}', {})).toBe('');
  });
});

describe('Seguridad de endpoints (anti-SSRF)', () => {
  it('rechaza endpoints internos o no cifrados', () => {
    expect(validateEndpoint('http://localhost:3000/catalogo').ok).toBe(false);
    expect(validateEndpoint('https://127.0.0.1/catalogo').ok).toBe(false);
    expect(validateEndpoint('https://192.168.1.10/api').ok).toBe(false);
    expect(validateEndpoint('https://169.254.169.254/latest/meta-data').ok).toBe(false);
    expect(validateEndpoint('http://api.proveedor.com/catalogo').ok).toBe(false); // sin TLS
    expect(validateEndpoint('https://api.proveedor.com/catalogo').ok).toBe(true);
    expect(validateEndpoint('file:///etc/passwd').ok).toBe(false);
    expect(validateEndpoint('https://user:pass@api.proveedor.com').ok).toBe(false);
    expect(validateEndpoint('').ok).toBe(false);
  });
});

describe('Motor de sincronización', () => {
  it('normaliza una cotización cruda al embalaje del proyecto', () => {
    const quote = normalizeQuote(
      { name: 'Placa de Yeso 12.5', price: 4.2, unit: 'm2', currency: 'USD', stock: 100 },
      SUPPLIER,
      MATERIALS[0],
      'USD',
      0.8,
      'live'
    );
    expect(quote).not.toBeNull();
    expect(quote!.price).toBeCloseTo(12.096, 2);
    expect(quote!.confidence).toBe(0.8);
    expect(quote!.source).toBe('live');
  });

  it('aplica descuentos negociados y convierte a la divisa del proyecto', () => {
    const quote = normalizeQuote(
      { name: 'Placa de Yeso 12.5', price: 4.2, unit: 'm2', currency: 'EUR' },
      { ...SUPPLIER, priceAdjustmentPct: -10, currency: 'EUR' },
      MATERIALS[0],
      'USD',
      0.9,
      'live'
    );
    // 4.2 €/m² × 2.88 m² = 12.096 € ; −10 % = 10.8864 € ; ×(1/0.92) ≈ 11.83 $
    expect(quote!.price).toBeCloseTo(11.83, 1);
  });

  it('sincroniza con un proveedor simulado y produce informe', async () => {
    let calls = 0;
    const report = await syncPrices({
      suppliers: [{ ...DEFAULT_MOCK, id: 'mock-a' }],
      materials: MATERIALS,
      currency: 'USD',
      transport: async () => {
        calls += 1;
        throw new Error('no debería usar transporte');
      },
      retries: 0
    });
    expect(calls).toBe(0);
    expect(report.suppliers[0].status).toBe('ok');
    expect(report.totals.matched).toBe(MATERIALS.length);
    expect(report.items.every((i) => i.best)).toBe(true);
    expect(report.totals.potentialSavings).toBeGreaterThanOrEqual(0);
  });

  it('usa la caché cuando el proveedor falla', async () => {
    const supplier: SupplierConfig = { ...SUPPLIER, id: 'cache-1' };
    writeCache('cache-1', [
      {
        materialName: MATERIALS[0].name,
        supplierId: 'cache-1',
        supplierName: 'Proveedor de prueba',
        matchedName: 'Placa de Yeso 12.5',
        price: 11.9,
        rawPrice: 4.13,
        currency: 'USD',
        fetchedAt: new Date().toISOString(),
        confidence: 0.9,
        source: 'live'
      }
    ]);

    const report = await syncPrices({
      suppliers: [{ ...supplier, ttlMinutes: 0 }],
      materials: [MATERIALS[0]],
      currency: 'USD',
      transport: async () => {
        throw new Error('red caída');
      },
      retries: 0
    });

    expect(report.suppliers[0].status).toBe('cached');
    expect(report.items[0].best!.price).toBeCloseTo(11.9, 2);
    expect(report.items[0].best!.source).toBe('cache');
  });

  it('informa de los errores sin romper la sincronización', async () => {
    const report = await syncPrices({
      suppliers: [{ ...SUPPLIER, id: 'err-1' }],
      materials: MATERIALS,
      currency: 'USD',
      transport: async () => {
        throw new Error('timeout del proveedor');
      },
      retries: 0
    });
    expect(report.totals.errors).toBe(1);
    expect(report.errors[0].message).toContain('timeout');
    expect(report.items.every((i) => i.status === 'error')).toBe(true);
  });

  it('respeta la caché fresca sin llamar al proveedor', async () => {
    const supplier: SupplierConfig = { ...SUPPLIER, id: 'fresh-1' };
    writeCache('fresh-1', [
      {
        materialName: MATERIALS[0].name,
        supplierId: 'fresh-1',
        supplierName: 'Proveedor de prueba',
        matchedName: 'Placa',
        price: 10,
        rawPrice: 10,
        currency: 'USD',
        fetchedAt: new Date().toISOString(),
        confidence: 0.9,
        source: 'live'
      }
    ]);
    let used = false;
    const report = await syncPrices({
      suppliers: [{ ...supplier, ttlMinutes: 120 }],
      materials: [MATERIALS[0]],
      currency: 'USD',
      transport: async () => {
        used = true;
        return { status: 200, text: '{}' };
      }
    });
    expect(used).toBe(false);
    expect(report.suppliers[0].status).toBe('cached');
    expect(readCache('fresh-1', 120).fresh).toBe(true);
  });

  it('aplica los mejores precios al mapa del proyecto', () => {
    const report = {
      items: [
        { materialName: 'A', best: { price: 9.5 } },
        { materialName: 'B', best: null }
      ]
    } as unknown as SyncReport;
    const prices = applyReportToPrices(report, { A: 12, B: 3 });
    expect(prices.A).toBe(9.5);
    expect(prices.B).toBe(3);
  });

  it('el proveedor REST mapea campos y deduplica por SKU', async () => {
    const provider = (await import('./providers/rest')).restJsonProvider;
    const payload = {
      data: {
        items: [
          { sku: 'X1', name: 'Placa Drywall ST 12,5mm', price: '4,25', unit: 'm2', stock: 10, currency: 'USD' },
          { sku: 'X1', name: 'Placa Drywall ST 12,5mm', price: '4,20', unit: 'm2', stock: 10, currency: 'USD' },
          { sku: 'X2', name: 'Cemento', price: '9', unit: 'kg' }
        ]
      }
    };
    const quotes = await provider.fetchQuotes({
      supplier: { ...SUPPLIER, responsePath: 'data.items' },
      transport: async () => ({ status: 200, text: JSON.stringify(payload) }),
      queries: MATERIALS
    });
    expect(quotes).toHaveLength(2);
    expect(quotes[0].price).toBeCloseTo(4.2, 2); // se conserva el más barato
  });

  it('propaga el error HTTP del proveedor REST', async () => {
    const provider = (await import('./providers/rest')).restJsonProvider;
    await expect(
      provider.fetchQuotes({
        supplier: SUPPLIER,
        transport: async () => ({ status: 503, text: 'servicio no disponible' }),
        queries: MATERIALS
      })
    ).rejects.toThrow('503');
  });
});

const DEFAULT_MOCK: SupplierConfig = {
  id: 'mock',
  name: 'Demo',
  kind: 'mock',
  enabled: true,
  currency: 'USD',
  method: 'GET',
  timeoutMs: 4000,
  ttlMinutes: 30,
  priceAdjustmentPct: 0,
  matchThreshold: 0.35
};

describe('Proveedor simulado', () => {
  it('genera precios deterministas dentro del mismo día', async () => {
    const provider = createMockProvider();
    const ctx = { supplier: DEFAULT_MOCK, transport: async () => ({ status: 200, text: '{}' }), queries: MATERIALS };
    const a = await provider.fetchQuotes(ctx);
    const b = await provider.fetchQuotes(ctx);
    expect(a.map((r) => r.price)).toEqual(b.map((r) => r.price));
    expect(a.every((r) => r.price > 0)).toBe(true);
  });

  it('puede simular fallos', async () => {
    const provider = createMockProvider({ failureRate: 1 });
    await expect(
      provider.fetchQuotes({ supplier: DEFAULT_MOCK, transport: async () => ({ status: 200, text: '{}' }), queries: MATERIALS })
    ).rejects.toThrow();
  });
});


describe('Construcción de consultas desde el cómputo', () => {
  it('deduce el tamaño del embalaje desde el formato comercial', () => {
    expect(packageSizeFromFormat('Caja 1,000 u.', 'fasteners', 1)).toBe(1000);
    expect(packageSizeFromFormat('Caja 100 juegos', 'fasteners', 1)).toBe(100);
    expect(packageSizeFromFormat('Balde 28 kg (o Caja)', 'compounds', 1)).toBe(28);
    expect(packageSizeFromFormat('Rollo 150 m', 'tapes', 1)).toBe(150);
    expect(packageSizeFromFormat('Tira de 3.00 m', 'profiles', 1)).toBeCloseTo(3, 4);
    expect(packageSizeFromFormat('Placa 1.20x2.40m (2.88 m²)', 'boards', 2.88)).toBeCloseTo(2.88, 2);
    // Sin superficie explícita se usa el formato configurado en el proyecto.
    expect(packageSizeFromFormat('Placa 1.20 x 2.40 m', 'boards', 2.88)).toBe(2.88);
    expect(packageSizeFromFormat('', 'accessories', 7)).toBe(7);
  });

  it('genera una consulta por material del presupuesto', () => {
    const summary = calculateProjectMaterials(DEFAULT_ROOMS, DEFAULT_CONFIG);
    const queries = buildMaterialQueries(summary, DEFAULT_CONFIG);
    expect(queries).toHaveLength(summary.requirements.length);
    queries.forEach((query) => {
      expect(query.name.length).toBeGreaterThan(0);
      expect(query.packageSize).toBeGreaterThan(0);
      expect(query.currentPrice).toBeGreaterThan(0);
    });
    const boards = queries.filter((q) => q.category === 'boards');
    boards.forEach((q) => expect(q.packageSize).toBeCloseTo(2.88, 2));
    const fasteners = queries.filter((q) => q.category === 'fasteners');
    fasteners.forEach((q) => expect(q.packageSize).toBe(1000));
  });
});

describe('Configuración de proveedores', () => {
  it('el proveedor de demostración viene listo para usar', () => {
    expect(DEFAULT_SUPPLIERS.length).toBeGreaterThan(0);
    expect(DEFAULT_SUPPLIERS[0].kind).toBe('mock');
    expect(DEFAULT_SUPPLIERS[0].enabled).toBe(true);
    expect(validateSupplier(DEFAULT_SUPPLIERS[0])).toHaveLength(0);
  });

  it('valida los campos obligatorios y los rangos', () => {
    const base = DEFAULT_SUPPLIERS[0];
    expect(validateSupplier({ ...base, name: '' })).toContain('El proveedor necesita un nombre.');
    expect(validateSupplier({ ...base, kind: 'rest-json', endpoint: '' }).join(' ')).toMatch(/endpoint/i);
    expect(validateSupplier({ ...base, kind: 'rest-json', endpoint: 'https://10.0.0.1/api' }).join(' ')).toMatch(
      /interna|bloqueado/i
    );
    expect(validateSupplier({ ...base, timeoutMs: 999 })).toContain(
      'El tiempo de espera debe estar entre 1 y 60 segundos.'
    );
    expect(validateSupplier({ ...base, matchThreshold: 2 }).length).toBeGreaterThan(0);
  });

  it('las plantillas son configuraciones válidas de partida', () => {
    expect(SUPPLIER_TEMPLATES.length).toBeGreaterThanOrEqual(3);
    SUPPLIER_TEMPLATES.forEach((template) => {
      expect(template.config.kind).toMatch(/rest-json|csv/);
      expect(template.description.length).toBeGreaterThan(10);
    });
  });

  it('la configuración por defecto es segura', () => {
    const settings = loadSettings();
    expect(settings.useServerProxy).toBe(true);
    expect(settings.retries).toBeLessThanOrEqual(3);
  });
});
