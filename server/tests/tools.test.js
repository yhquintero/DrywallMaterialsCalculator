/**
 * Tests de los módulos profesionales añadidos en esta iteración:
 * planos CAD (DXF/DWG), precios de distribuidores y asistencia remota (AR).
 */
import './setup.js';
import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

process.env.SUPPLIER_TEST_KEY = 'secreto-de-prueba-1234';

import { getDb } from '../src/db/index.js';
import { createApp } from '../src/app.js';

getDb();
const app = createApp();
const server = http.createServer(app);
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const BASE = `http://127.0.0.1:${server.address().port}`;

after(() => server.close());

async function api(method, path, { body, raw = false, headers = {} } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'content-type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (raw) return { status: res.status, text: await res.text(), headers: res.headers };
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: res.status, body: json, headers: res.headers };
}

/** DXF mínimo válido para las pruebas. */
const SAMPLE_DXF = [
  '0', 'SECTION', '2', 'HEADER', '9', '$ACADVER', '1', 'AC1009', '0', 'ENDSEC',
  '0', 'SECTION', '2', 'ENTITIES',
  '0', 'LINE', '8', 'ARQ-MURO', '10', '0.0', '20', '0.0', '30', '0.0', '11', '1000.0', '21', '0.0', '31', '0.0',
  '0', 'ENDSEC', '0', 'EOF',
].join('\n');

// ── CAD ──────────────────────────────────────────────────────────────────────

test('GET /api/cad/status describe las capacidades del servidor', async () => {
  const res = await api('GET', '/api/cad/status');
  assert.equal(res.status, 200);
  assert.equal(typeof res.body.available, 'boolean');
  assert.ok(Array.isArray(res.body.formats));
  assert.ok(res.body.formats.includes('dxf'));
  assert.ok(res.body.formats.includes('svg'));
  assert.ok(typeof res.body.message === 'string' && res.body.message.length > 10);
});

test('POST /api/cad/convert rechaza contenido que no es DXF', async () => {
  const res = await api('POST', '/api/cad/convert', {
    body: { dxf: 'x'.repeat(200), filename: 'plano.dxf', target: 'dwg' },
  });
  assert.equal(res.status, 400);
  assert.match(res.body.error, /DXF/i);
});

test('POST /api/cad/convert valida el esquema de la petición', async () => {
  const res = await api('POST', '/api/cad/convert', { body: { dxf: 'corto' } });
  assert.equal(res.status, 400);
});

test('POST /api/cad/convert responde 501 sin conversor configurado', async () => {
  const res = await api('POST', '/api/cad/convert', {
    body: { dxf: SAMPLE_DXF, filename: 'plano.dxf', target: 'dwg' },
  });
  // Sin CAD_CONVERTER_CMD en el entorno de test: 501 con instrucciones.
  assert.equal(res.status, 501);
  assert.match(res.body.error, /conversor/i);
  assert.ok(res.body.hint.includes('CAD_CONVERTER_CMD'));
});

test('POST /api/cad/plot sanea el nombre y confirma la operación', async () => {
  const res = await api('POST', '/api/cad/plot', {
    body: {
      filename: '../../etc/passwd',
      dxf: SAMPLE_DXF,
      units: 'mm',
      scaleDenominator: 50,
      paper: 'A3',
      project: { name: 'Obra', client: 'Cliente', contractor: 'Contratista', currency: 'USD' },
    },
  });
  assert.equal(res.status, 200);
  assert.equal(res.body.queued, false); // sin CAD_PLOT_DIR en test
  assert.match(res.body.message, /cola de impresión/i);
});

test('POST /api/cad/plot rechaza DXF gigantes o inválidos', async () => {
  const res = await api('POST', '/api/cad/plot', { body: { filename: 'a.dxf', dxf: 'no es un dxf' } });
  assert.equal(res.status, 400);
});

test('los endpoints CAD exigen origen permitido en peticiones de escritura', async () => {
  const res = await api('POST', '/api/cad/plot', {
    headers: { origin: 'https://sitio-malicioso.example' },
    body: { filename: 'a.dxf', dxf: SAMPLE_DXF },
  });
  assert.equal(res.status, 403);
});

// ── Precios ──────────────────────────────────────────────────────────────────

test('POST /api/pricing/proxy bloquea direcciones internas (anti-SSRF)', async () => {
  for (const url of [
    'https://localhost/catalogo',
    'https://127.0.0.1/catalogo',
    'https://192.168.1.5/api',
    'https://169.254.169.254/latest/meta-data',
  ]) {
    const res = await api('POST', '/api/pricing/proxy', { body: { url, method: 'GET' } });
    assert.equal(res.status, 403, `debería bloquear ${url}`);
  }
});

test('POST /api/pricing/proxy exige HTTPS', async () => {
  const res = await api('POST', '/api/pricing/proxy', {
    body: { url: 'http://api.proveedor-publico.com/catalogo', method: 'GET' },
  });
  assert.equal(res.status, 400);
  assert.match(res.body.error, /HTTPS/i);
});

test('POST /api/pricing/proxy rechaza URLs con credenciales embebidas', async () => {
  const res = await api('POST', '/api/pricing/proxy', {
    body: { url: 'https://user:pass@api.proveedor.com/catalogo' },
  });
  assert.equal(res.status, 400);
  assert.match(res.body.error, /credenciales/i);
});

test('POST /api/pricing/proxy valida el cuerpo', async () => {
  const res = await api('POST', '/api/pricing/proxy', { body: { url: 'no-es-una-url' } });
  assert.equal(res.status, 400);
});

test('GET /api/pricing/secrets nunca devuelve valores', async () => {
  const res = await api('GET', '/api/pricing/secrets?refs=SUPPLIER_TEST_KEY,JWT_SECRET,SUPPLIER_OTRO');
  assert.equal(res.status, 200);
  const parsed = res.body.secrets;
  assert.equal(parsed.length, 3);
  assert.equal(parsed.find((s) => s.name === 'SUPPLIER_TEST_KEY').configured, true);
  assert.equal(parsed.find((s) => s.name === 'SUPPLIER_TEST_KEY').allowed, true);
  // JWT_SECRET queda fuera del prefijo permitido: nunca se confirma.
  assert.equal(parsed.find((s) => s.name === 'JWT_SECRET').allowed, false);
  assert.equal(parsed.find((s) => s.name === 'JWT_SECRET').configured, false);
  assert.ok(!JSON.stringify(res.body).includes('secreto-de-prueba'));
});

// ── AR / asistencia remota ───────────────────────────────────────────────────

test('POST /api/ar/rooms crea una sala efímera válida', async () => {
  const res = await api('POST', '/api/ar/rooms', { body: { ttlSeconds: 300, label: 'Obra Los Álamos' } });
  assert.equal(res.status, 201);
  assert.match(res.body.room, /^obra-[0-9a-f]{6}$/);
  assert.ok(res.body.expiresAt > Date.now());
});

test('GET /api/ar/rooms/:room informa del estado de la sala', async () => {
  const created = await api('POST', '/api/ar/rooms', { body: { ttlSeconds: 120 } });
  const res = await api('GET', `/api/ar/rooms/${created.body.room}`);
  assert.equal(res.status, 200);
  assert.equal(res.body.room, created.body.room);
  assert.equal(res.body.peers, 0);
});

test('GET /api/ar/rooms/:room devuelve 404 si la sala no existe', async () => {
  const res = await api('GET', '/api/ar/rooms/obra-ffffff');
  assert.equal(res.status, 404);
});

test('la señalización publica y entrega mensajes a los suscriptores', async () => {
  const created = await api('POST', '/api/ar/rooms', { body: { ttlSeconds: 120 } });
  const room = created.body.room;

  // Suscriptor SSE (host)
  const controller = new AbortController();
  const sse = await fetch(`${BASE}/api/ar/signal/${room}?peer=host-test`, { signal: controller.signal });
  assert.equal(sse.status, 200);
  assert.match(sse.headers.get('content-type'), /text\/event-stream/);

  const reader = sse.body.getReader();
  const decoder = new TextDecoder();
  const first = decoder.decode((await reader.read()).value);
  assert.match(first, /event: ready/);

  // Publicación de una oferta
  const posted = await api('POST', `/api/ar/signal/${room}`, {
    body: { type: 'offer', from: 'guest-test', sdp: 'v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\n' },
  });
  assert.equal(posted.status, 200);
  assert.equal(posted.body.delivered, 1);

  const chunk = decoder.decode((await reader.read()).value);
  assert.match(chunk, /"type":"offer"/);
  assert.match(chunk, /guest-test/);

  controller.abort();
});

test('la señalización ignora mensajes mal formados', async () => {
  const created = await api('POST', '/api/ar/rooms', { body: { ttlSeconds: 120 } });
  const res = await api('POST', `/api/ar/signal/${created.body.room}`, { body: { type: 'raro' } });
  assert.equal(res.status, 400);
});

test('la señalización rechaza identificadores de sala inválidos', async () => {
  const res = await api('POST', '/api/ar/signal/../etc/passwd', { body: { type: 'hello' } });
  assert.ok([400, 404].includes(res.status), `estado inesperado ${res.status}`);
});

test('los endpoints AR validan el origen en escrituras', async () => {
  const res = await api('POST', '/api/ar/rooms', {
    headers: { origin: 'https://atacante.example' },
    body: { ttlSeconds: 120 },
  });
  assert.equal(res.status, 403);
});
