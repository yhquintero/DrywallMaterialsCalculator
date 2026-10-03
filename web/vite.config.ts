import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'security-headers-plugin',
      configureServer(server) {
        server.middlewares.use((_req, res, next) => {
          // HTTP Strict Transport Security
          res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
          // Anti MIME-type sniffing
          res.setHeader('X-Content-Type-Options', 'nosniff');
          // Cross-Site Scripting filter
          res.setHeader('X-XSS-Protection', '1; mode=block');
          // Strict Referrer policy
          res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
          // Permissions policy
          res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
          next();
        });
      }
    }
  ],
  server: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: true,
  },
  preview: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: true,
  }
});
