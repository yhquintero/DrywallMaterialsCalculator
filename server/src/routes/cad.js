/**
 * Endpoints del módulo CAD (planos vectoriales de drywall).
 *
 *   GET  /api/cad/status   → ¿hay conversor DXF→DWG disponible en el servidor?
 *   POST /api/cad/convert  → convierte un DXF a DWG (o PDF) con ODA File Converter
 *   POST /api/cad/plot     → encola un plano en la carpeta del plotter/reprografía
 *
 * Nota técnica: el formato .dwg es propietario de Autodesk. La vía profesional
 * y legal para generarlo es DXF + ODA File Converter (gratuito) o la
 * suscripción a la RealDWG SDK. Este módulo integra el conversor si está
 * instalado y, si no, indica al usuario cómo obtenerlo.
 *
 * Seguridad: los nombres de archivo se sanean, el DXF se valida como texto con
 * cabecera DXF y nunca se ejecuta nada con el contenido del usuario; el
 * conversor se invoca por argumentos separados (sin shell).
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { Router } from 'express';
import { spawn } from 'node:child_process';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import config from '../config/index.js';
import { HttpError } from '../utils/errors.js';
import { sameOriginGuard } from '../security/httpsMiddleware.js';

const router = Router();

const cadLimiter = rateLimit({
  windowMs: 60_000,
  limit: 30,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Demasiadas operaciones CAD. Espera un minuto.' }
});

router.use(sameOriginGuard);
router.use(cadLimiter);

const SAFE_NAME = /^[A-Za-z0-9._-]{1,120}$/;

/** Sanea un nombre de archivo recibido del cliente. */
function safeFilename(name, fallback = 'plano.dxf') {
  const base = path.basename(String(name || '')).replace(/[^A-Za-z0-9._-]+/g, '_');
  if (!SAFE_NAME.test(base)) return fallback;
  if (!/\.dxf$/i.test(base)) return `${base}.dxf`;
  return base;
}

/** Comprueba que el contenido parece un DXF legible (y no un binario cualquiera). */
function looksLikeDxf(content) {
  if (typeof content !== 'string' || content.length < 64) return false;
  if (content.indexOf('SECTION') === -1 || content.indexOf('ENTITIES') === -1) return false;
  if (content.indexOf('EOF') === -1) return false;
  // Un DXF ASCII nunca contiene bytes nulos.
  return content.indexOf('\u0000') === -1;
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
  return dir;
}

/** Ejecuta un comando sin shell y devuelve { stdout, stderr, code }. */
function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { shell: false, ...options });
    let stdout = '';
    let stderr = '';
    child.stdout?.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr?.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    child.on('error', reject);
    child.on('close', (code) => resolve({ stdout, stderr, code }));
  });
}

/**
 * GET /api/cad/status
 * Informa a la web de qué puede ofrecer el servidor: conversión a DWG y plotter.
 */
router.get('/status', async (_req, res) => {
  const formats = ['dxf', 'svg'];
  let provider = null;
  let message =
    'Conversión a DWG no configurada. Exporta el DXF y en AutoCAD usa GUARDAR COMO → DWG (o instala ODA File Converter).';

  if (config.cad.converterCommand) {
    try {
      await fs.access(config.cad.converterCommand);
      provider = 'ODA File Converter';
      formats.push('dwg', 'pdf');
      message = 'Conversión DXF→DWG disponible en el servidor (ODA File Converter).';
    } catch {
      message = `El conversor configurado (${config.cad.converterCommand}) no existe en el servidor.`;
    }
  }

  res.json({
    available: Boolean(provider),
    provider,
    formats,
    plotter: Boolean(config.cad.plotDir),
    message
  });
});

const convertSchema = z.object({
  dxf: z.string().min(64).max(config.cad.maxDxfBytes),
  filename: z.string().max(120).optional(),
  target: z.enum(['dwg', 'pdf']).default('dwg'),
  version: z.enum(['R12', 'R2000', 'R2007']).default('R2000')
});

/**
 * POST /api/cad/convert
 * Convierte el DXF recibido usando el conversor configurado.
 */
router.post('/convert', async (req, res, next) => {
  try {
    const parsed = convertSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Petición de conversión inválida.', parsed.error.issues);
    const { dxf, target } = parsed.data;

    if (!looksLikeDxf(dxf)) {
      throw new HttpError(400, 'El contenido recibido no es un DXF válido.');
    }
    if (!config.cad.converterCommand) {
      return res.status(501).json({
        error: 'El servidor no tiene configurado un conversor DXF→DWG.',
        hint:
          'Instala ODA File Converter y define CAD_CONVERTER_CMD en el .env del servidor. ' +
          'Mientras tanto puedes abrir el DXF en AutoCAD y guardarlo como DWG.',
        formats: ['dxf', 'svg']
      });
    }

    const workDir = await ensureDir(config.cad.converterDir);
    const jobId = crypto.randomBytes(8).toString('hex');
    const base = safeFilename(parsed.data.filename || 'plano').replace(/\.dxf$/i, '');
    const inDir = path.join(workDir, `in-${jobId}`);
    const outDir = path.join(workDir, `out-${jobId}`);
    await ensureDir(inDir);
    await ensureDir(outDir);

    const inputPath = path.join(inDir, `${base}.dxf`);
    await fs.writeFile(inputPath, dxf, 'utf8');

    // ODA File Converter: <in> <out> <outVer> <outFileType> <recurse> <audit>
    const outVer = parsed.data.version === 'R12' ? 'ACAD12' : parsed.data.version === 'R2007' ? 'ACAD2007' : 'ACAD2000';
    const outType = target === 'pdf' ? 'PDF' : 'DWG';
    const result = await run(config.cad.converterCommand, [inDir, outDir, outVer, outType, '0', '1']);

    if (result.code !== 0) {
      throw new HttpError(500, `El conversor devolvió el código ${result.code}.`, {
        stderr: result.stderr.slice(0, 500)
      });
    }

    const produced = (await fs.readdir(outDir)).find((file) => new RegExp(`\\.${target}$`, 'i').test(file));
    if (!produced) throw new HttpError(500, 'El conversor no generó ningún archivo de salida.');

    const outputPath = path.join(outDir, produced);
    const buffer = await fs.readFile(outputPath);
    res.setHeader('Content-Type', 'application/octet-stream');
    res.setHeader('Content-Disposition', `attachment; filename="${base}.${target}"`);
    res.setHeader('Cache-Control', 'no-store');
    res.send(buffer);

    // Limpieza de los archivos temporales de trabajo.
    await fs.rm(inDir, { recursive: true, force: true }).catch(() => undefined);
    await fs.rm(outDir, { recursive: true, force: true }).catch(() => undefined);
  } catch (error) {
    next(error);
  }
});

const plotSchema = z.object({
  filename: z.string().max(120),
  dxf: z.string().min(64),
  units: z.enum(['mm', 'cm', 'm', 'in', 'ft']).default('mm'),
  version: z.enum(['R12', 'R2000', 'R2007']).default('R2000'),
  scaleDenominator: z.number().min(1).max(1000).default(50),
  paper: z.string().max(16).default('A3'),
  project: z
    .object({
      name: z.string().max(160).default(''),
      client: z.string().max(160).default(''),
      contractor: z.string().max(160).default(''),
      currency: z.string().max(8).default('USD')
    })
    .default({})
});

/**
 * POST /api/cad/plot
 * Deja el plano en la carpeta del plotter/reprografía (o devuelve la ruta de
 * descarga si no hay carpeta configurada).
 */
router.post('/plot', async (req, res, next) => {
  try {
    const parsed = plotSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Petición de impresión inválida.', parsed.error.issues);
    const { dxf, filename } = parsed.data;
    if (!looksLikeDxf(dxf)) throw new HttpError(400, 'El contenido recibido no es un DXF válido.');
    if (dxf.length > config.cad.maxDxfBytes) throw new HttpError(413, 'El plano es demasiado grande.');

    if (!config.cad.plotDir) {
      return res.json({
        queued: false,
        message:
          'No hay cola de impresión configurada (CAD_PLOT_DIR). Descarga el DXF y envíalo al plotter manualmente.'
      });
    }

    const dir = await ensureDir(config.cad.plotDir);
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const name = `${stamp}_${safeFilename(filename)}`;
    const target = path.join(dir, name);
    await fs.writeFile(target, dxf, 'utf8');
    await fs.writeFile(
      target.replace(/\.dxf$/i, '.json'),
      JSON.stringify({ ...parsed.data, dxf: undefined, queuedAt: new Date().toISOString() }, null, 2),
      'utf8'
    );

    res.json({ queued: true, file: name, directory: dir, message: `Plano encolado como ${name}.` });
  } catch (error) {
    next(error);
  }
});

export default router;
