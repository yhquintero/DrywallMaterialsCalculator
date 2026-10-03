#!/usr/bin/env node
/**
 * Presupuesto de rendimiento del bundle.
 *
 * Comprueba el peso real (gzip) de los recursos que el navegador descarga en la
 * primera visita: los chunks enlazados desde `dist/index.html` más la hoja de
 * estilos. El resto de módulos (AR, CAD, precios, PDF) se cargan en diferido y
 * no cuentan para el arranque.
 *
 * Uso:
 *   node scripts/check-bundle.mjs           → informa y falla si se supera el tope
 *   BUDGET_KB=280 node scripts/check-bundle.mjs
 */
import { gzipSync } from 'node:zlib';
import { readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.resolve(HERE, '..', 'dist');
const BUDGET_KB = Number(process.env.BUDGET_KB || 260);

const kb = (bytes) => bytes / 1024;
const fmt = (bytes) => `${kb(bytes).toFixed(2)} kB`;

function gzipSize(filePath) {
  return gzipSync(readFileSync(filePath)).length;
}

let html;
try {
  html = readFileSync(path.join(DIST, 'index.html'), 'utf8');
} catch {
  console.error(`✖ No se encontró ${path.join(DIST, 'index.html')}. Ejecuta «npm run build» antes.`);
  process.exit(1);
}

const assets = [...html.matchAll(/(?:src|href)="\/assets\/([^"]+)"/g)].map((m) => m[1]);
if (!assets.length) {
  console.error('✖ No se pudo extraer ningún recurso de dist/index.html.');
  process.exit(1);
}

const rows = assets.map((asset) => {
  const file = path.join(DIST, 'assets', asset);
  const raw = statSync(file).size;
  const gz = gzipSize(file);
  return { asset, raw, gz };
});

const totalGzip = rows.reduce((sum, row) => sum + row.gz, 0);

console.log('Presupuesto de arranque (primer pintado):');
for (const row of rows.sort((a, b) => b.gz - a.gz)) {
  console.log(`  ${row.asset.padEnd(34)} ${fmt(row.raw).padStart(10)} → ${fmt(row.gz).padStart(10)} gzip`);
}
console.log(`  ${'TOTAL'.padEnd(34)} ${' '.repeat(10)}   ${fmt(totalGzip).padStart(10)} gzip`);
console.log(`  Tope configurado: ${BUDGET_KB} kB gzip`);

if (kb(totalGzip) > BUDGET_KB) {
  console.error(`\n✖ Presupuesto superado: ${fmt(totalGzip)} > ${BUDGET_KB} kB.`);
  console.error('  Revisa los imports estáticos, aplica carga diferida o sube el tope con criterio.');
  process.exit(1);
}

console.log(`\n✔ Dentro del presupuesto (${fmt(BUDGET_KB * 1024 - totalGzip)} de margen).`);
