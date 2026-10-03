/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import basicSsl from '@vitejs/plugin-basic-ssl';
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

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const wantHttps =
    env.VITE_HTTPS === 'true' || env.VITE_HTTPS === '1' || process.argv.includes('--https');
  const httpsOptions = wantHttps ? localHttpsOptions() : undefined;

  return {
    plugins: [
      react(),
      // Solo si no hay certificado local propio.
      ...(wantHttps && !httpsOptions ? [basicSsl()] : []),
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
          target: API_TARGET,
          changeOrigin: true,
          secure: false,
          // El backend puede ir en HTTPS con certificado autofirmado en dev.
        },
        '/.well-known': {
          target: API_TARGET,
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
        '/api': { target: API_TARGET, changeOrigin: true, secure: false },
        '/.well-known': { target: API_TARGET, changeOrigin: true, secure: false },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
      rollupOptions: {
        output: {
          manualChunks: {
            react: ['react', 'react-dom', 'react-router-dom'],
            pdf: ['jspdf', 'jspdf-autotable'],
          },
        },
      },
    },
    test: {
      environment: 'node',
      include: ['src/**/*.test.ts'],
    },
  };
});
