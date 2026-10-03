/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  DrywallPro Master — Web + Consola de Licencias
 *
 *  HTTPS SIEMPRE:
 *   • Producción : el build se sirve detrás de nginx/Caddy/Netlify/Vercel con
 *                  TLS 1.2+ y HSTS preload (ver nginx.conf, vercel.json, ...).
 *   • Desarrollo : `npm run dev:https` activa TLS local con un certificado
 *                  autofirmado (web/certs) o el generado por @vitejs/plugin-basic-ssl.
 *
 *  La API se alcanza SIEMPRE por ruta relativa (/api, /.well-known) y Vite la
 *  proxifica al backend, de modo que el navegador nunca hace peticiones
 *  cross-origin ni mezcla contenidos (cero "mixed content").
 * ─────────────────────────────────────────────────────────────────────────────
 */

const API_TARGET = process.env.API_TARGET || 'http://127.0.0.1:8443';

/**
 * El escáner de estancias necesita la cámara y el micrófono del propio origen
 * (WebXR + getUserMedia), por eso se permite `self` en esas directivas. El
 * resto de capacidades sensibles siguen bloqueadas.
 */
const PERMISSIONS_POLICY =
  'camera=(self), microphone=(self), xr-spatial-tracking=(self), fullscreen=(self), geolocation=(), payment=()';

/**
 * `@vitejs/plugin-basic-ssl` es solo el RESPALDO para servir HTTPS cuando no hay
 * certificado local (web/certs/dev.crt+dev.key), que es lo que genera
 * `npm run certs` / `npm run dev:https`.
 *
 * Se carga de forma diferida y con un especificador no literal a propósito: si se
 * importa de manera estática, Vite lo resuelve al empaquetar `vite.config.ts` y un
 * `node_modules` desactualizado (instalado antes de añadir la dependencia) hace
 * que TODA la configuración falle con `ERR_MODULE_NOT_FOUND: Cannot find package
 * '@vitejs/plugin-basic-ssl'`, incluso para `npm run dev` o `npm run build`, que
 * no lo usan. Así el fallo queda acotado al caso que de verdad lo necesita y el
 * mensaje dice cómo arreglarlo.
 */
const BASIC_SSL_PKG = ['@vitejs', 'plugin-basic-ssl'].join('/');

async function loadBasicSslPlugin() {
  try {
    const modulo = await import(/* @vite-ignore */ BASIC_SSL_PKG);
    const factory = (modulo as { default?: unknown }).default ?? modulo;
    return (factory as () => import('vite').Plugin)();
  } catch (err) {
    const detalle = err instanceof Error ? err.message : String(err);
    throw new Error(
      [
        `No se pudo cargar ${BASIC_SSL_PKG}, necesario para HTTPS sin certificado local.`,
        `  Detalle: ${detalle}`,
        '  Solución A: instala las dependencias de la web y reintenta:',
        '              npm --prefix web install   (o `npm run install:all` en la raíz)',
        '  Solución B: genera los certificados del repo (`npm run certs`) para usar',
        '              web/certs/dev.crt + dev.key en lugar de ese plugin.',
      ].join('\n')
    );
  }
}

/** Certificado local firmado por la CA del repo (scripts/gen-certs.sh). */
function localHttpsOptions() {
  const certDir = path.resolve(HERE, 'certs');
  const cert = path.join(certDir, 'dev.crt');
  const key = path.join(certDir, 'dev.key');
  if (fs.existsSync(cert) && fs.existsSync(key)) {
    return { cert: fs.readFileSync(cert), key: fs.readFileSync(key) };
  }
  return undefined; // ⇒ se usa @vitejs/plugin-basic-ssl (autofirmado)
}

/** Cabeceras de seguridad idénticas en dev y en producción. */
function securityHeadersPlugin(enableHsts: boolean) {
  return {
    name: 'security-headers-plugin',
    configureServer(server: import('vite').ViteDevServer) {
      server.middlewares.use((_req, res, next) => {
        if (enableHsts) {
          res.setHeader(
            'Strict-Transport-Security',
            'max-age=31536000; includeSubDomains; preload'
          );
        }
        res.setHeader('X-Content-Type-Options', 'nosniff');
        res.setHeader('X-Frame-Options', 'DENY');
        res.setHeader('X-XSS-Protection', '0');
        res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
        res.setHeader('Permissions-Policy', PERMISSIONS_POLICY);
        res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
        next();
      });
    },
    configurePreviewServer(server: import('vite').PreviewServer) {
      server.middlewares.use((_req, res, next) => {
        if (enableHsts) {
          res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
        }
        res.setHeader('X-Content-Type-Options', 'nosniff');
        res.setHeader('X-Frame-Options', 'DENY');
        res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
        res.setHeader('Permissions-Policy', PERMISSIONS_POLICY);
        next();
      });
    },
  };
}

export default defineConfig(async ({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const wantHttps =
    process.env.VITE_HTTPS === 'true' ||
    process.env.VITE_HTTPS === '1' ||
    env.VITE_HTTPS === 'true' ||
    env.VITE_HTTPS === '1' ||
    process.argv.includes('--https');
  const httpsOptions = wantHttps ? localHttpsOptions() : undefined;
  const apiTarget =
    process.env.API_TARGET ||
    env.API_TARGET ||
    (wantHttps ? 'https://127.0.0.1:8443' : API_TARGET);

  // Solo hace falta si se pide HTTPS y no hay certificado local propio.
  const basicSslPlugin = wantHttps && !httpsOptions ? await loadBasicSslPlugin() : null;

  return {
    plugins: [
      react(),
      ...(basicSslPlugin ? [basicSslPlugin] : []),
      securityHeadersPlugin(wantHttps),
    ],
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT) || 3000,
      allowedHosts: true,
      strictPort: false,
      ...(httpsOptions ? { https: httpsOptions } : {}),
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
          // El backend puede ir en HTTPS con certificado autofirmado en dev.
        },
        '/.well-known': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
    preview: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT) || 3000,
      allowedHosts: true,
      ...(httpsOptions ? { https: httpsOptions } : {}),
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true, secure: false },
        '/.well-known': { target: apiTarget, changeOrigin: true, secure: false },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
      rollupOptions: {
        output: {
          /**
           * `react` se separa para aprovechar la caché del navegador entre
           * despliegues. El generador de PDF (jsPDF + autotable ≈ 135 kB gzip)
           * NO se declara aquí a propósito: Rollup lo agrupa con el módulo que
           * lo importa en diferido (`BudgetEstimator`), de modo que no se
           * descarga ni se precarga en el arranque.
           */
          manualChunks: {
            react: ['react', 'react-dom', 'react-router-dom'],
          },
        },
      },
    },
    test: {
      environment: 'node',
      include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    },
  };
});
