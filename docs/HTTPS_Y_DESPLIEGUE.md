# HTTPS y despliegue de la plataforma de licencias

Cómo llevar la calculadora web y la consola de administración a **HTTPS real** en desarrollo
y en producción. Todo el tráfico de licencias contiene claves criptográficas y datos de
clientes: nunca debe viajar en claro.

---

## 1. Resumen de la arquitectura

```text
                  HTTPS (TLS)                      HTTP interno
  Navegador ──────────────────────► Caddy/nginx ──────────────► server/ (Node, :8443)
  App Android ─────────────────────►   :443   │                     │
                                             └─► web/ (estática o nginx :80)
                                                                     │
                                                          SQLite en /data (volumen)
```

* **Un solo origen público**: la web y la API comparten dominio ⇒ sin CORS y sin cookies
  de terceros.
* El proxy termina TLS y reenvía por HTTP **dentro de la red interna del contenedor**.
* `SECURE_COOKIES=true` + `TRUST_PROXY=true` en el backend.
* La web llama siempre a rutas **relativas** (`/api/...`), nunca a `localhost`.

---

## 2. Desarrollo local con HTTPS

```bash
npm run certs        # crea CA + certificados para localhost y 127.0.0.1 (openssl)
npm run dev:https    # API https://localhost:8443 + web https://localhost:3000
```

`scripts/dev-https.mjs` genera los certificados si no existen, arranca la API con TLS y la
web con el plugin HTTPS de Vite (proxy de `/api` y `/.well-known` hacia la API).

**Confiar en el certificado en el navegador**: instala la CA raíz
(`web/certs/dev-rootCA.pem`) en el almacén de autoridades de tu sistema. En Chrome/Edge:
*Configuración → Privacidad y seguridad → Seguridad → Gestionar certificados → Autoridades →
Importar → marcar "Confiar en esta CA para identificar sitios"*.

Si prefieres [mkcert](https://github.com/FiloSottile/mkcert):

```bash
mkcert -install
mkcert -key-file server/certs/server.key -cert-file server/certs/server.crt localhost 127.0.0.1
mkcert -key-file web/certs/dev.key -cert-file web/certs/dev.crt localhost 127.0.0.1
npm run dev:https
```

> Los certificados son para **desarrollo**. `web/certs` y `server/certs` están en `.gitignore`.

### Sin TLS (solo para probar)

```bash
npm run dev:api      # http://localhost:8443
npm run dev          # http://localhost:3000
```

Funciona porque `enforceHttps` exime al bucle local (`localhost`, `127.0.0.1`) y a los
dominios `.e2b.app` en entornos no productivos. En producción **no hay excepciones**: todo
HTTP recibe `301` hacia HTTPS.

---

## 3. Producción con Docker + Caddy (recomendado)

Caddy obtiene y renueva certificados de Let's Encrypt automáticamente.

```bash
cd deploy
cp .env.example .env
#  DOMAIN=licencias.tudominio.com   ← tu dominio apuntando al servidor
#  ACME_EMAIL=tu@correo.com         ← avisos de renovación
#  JWT_SECRET=$(openssl rand -base64 48)
#  MASTER_KEY=$(openssl rand -base64 48)
#  BOOTSTRAP_ADMIN_USERNAME / EMAIL / PASSWORD
docker compose up -d --build
```

Servicios:

| Contenedor | Imagen | Puerto |
| :-- | :-- | :-- |
| `caddy` | `caddy:2-alpine` | `443:443`, `80:80` (redirect) |
| `console-web` | nginx 1.27 con la build de Vite | interno `:80` |
| `console-api` | Node 22 (no root) | interno `:8443` |

Los datos persisten en el volumen `console_data` → `/data/drywall_console.db`.
Los certificados de Caddy viven en `caddy_data` / `caddy_config`.

Comprobaciones:

```bash
curl -sI https://licencias.tudominio.com/api/public/health | head
curl -s https://licencias.tudominio.com/api/public/health
```

---

## 4. Producción con nginx propio

Si ya tienes nginx (o un panel tipo CloudPanel/Plesk):

```bash
# 1. Build de la web
npm --prefix web run build      # genera web/dist

# 2. Copia web/nginx.conf a /etc/nginx/sites-available/console
#    y ajusta: dominio, rutas de certificado y upstream (console-api:8443)
sudo nginx -t && sudo systemctl reload nginx

# 3. API detrás del proxy
cd server && cp .env.example .env   # SECURE_COOKIES=true TRUST_PROXY=true PUBLIC_URL=https://tudominio.com
ENABLE_HTTPS=false PORT=8443 node src/index.js   # o con systemd/pm2/docker
```

`web/nginx.conf` ya incluye: TLS 1.2/1.3, HSTS `preload`, `80 → 443`, cabeceras de
seguridad, `limit_req` en login y API, proxy con cabeceras correctas y **bloqueo de**
`*.db`, `.env`, `.pem`, `.key`.

---

## 5. Hosting gestionado (Vercel / Netlify)

Ambos sirven HTTPS automáticamente. La web está preparada para **proxificar** la API y así
mantener un único origen:

**Vercel** (`web/vercel.json`): la SPA + una serverless function que reenvía `/api/*` y
`/.well-known/*` a `API_ORIGIN`.

```bash
# Variables del proyecto en Vercel
API_ORIGIN=https://licencias.tudominio.com
```

**Netlify** (`web/netlify.toml`):

```toml
API_ORIGIN = "https://licencias.tudominio.com"
```

Netlify también respeta `web/public/_headers` para las cabeceras de seguridad.

En el backend define:

```env
PUBLIC_URL=https://tu-front.vercel.app
CORS_ORIGINS=https://tu-front.vercel.app,https://otro-dominio.com
SECURE_COOKIES=true
TRUST_PROXY=true
```

> **Importante**: la cookie de refresco es `SameSite=Strict`. Si web y API están en dominios
> distintos, el navegador **no la enviará** en la navegación cruzada y el refresco fallará.
> Para ese caso añade un proxy inverso en el frontend (como arriba) o mueve ambos al mismo
> dominio. Es la única configuración recomendada.

---

## 6. Cabeceras de seguridad aplicadas

| Cabecera | Valor | Dónde |
| :-- | :-- | :-- |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Caddy, nginx, Express (siempre que hay TLS) |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'` | Caddy, nginx, `index.html`, plugin de Vite |
| `X-Content-Type-Options` | `nosniff` | en todas las capas |
| `X-Frame-Options` | `DENY` | en todas las capas |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | en todas las capas |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` | Caddy, nginx, Vite |
| `Cross-Origin-Opener-Policy` | `same-origin` | Caddy, nginx |

Y en el backend: `helmet`, validación de `Origin` contra `CORS_ORIGINS`, guard CSRF en todas
las mutaciones y **rate limiting** (300 req/min API, 30 login/15 min, 120/min públicos).

---

## 7. Integración con las apps Android

Añade al archivo `local.properties` (no se versiona) o al entorno de tu CI:

```properties
# Consola de licencias (obligatorio https://)
LICENSE_CONSOLE_URL=https://licencias.tudominio.com

# Solo el módulo keygen: token de un usuario con licenses.import / keys.rotate
LICENSE_CONSOLE_TOKEN=eyJhbGciOi...

# Opcional: fija la huella SHA-256 de la clave pública esperada (pinning)
LICENSE_CONSOLE_KEY_SHA256=a459c77a7abd2d8893776aaebba43981c052d2e53f704d8c02f6407f13509d15
```

Gradle los convierte en `BuildConfig.LICENSE_CONSOLE_URL`, `LICENSE_CONSOLE_TOKEN` y
`LICENSE_CONSOLE_KEY_SHA256`.

| Módulo | Qué usa la consola |
| :-- | :-- |
| `:app` | `RemoteKeyProvider` descarga la clave pública desde `LICENSE_CONSOLE_URL/.well-known/licensing-public-key-v2.pem`, valida que sea un PEM RSA correcto, la cachea cifrada (AES-256/GCM) y opcionalmente comprueba la huella fijada |
| `:keygen` | `ConsoleSync` sube las licencias emitidas a `/api/licenses/import` y compara su llave pública con la activa en la consola |
| `:common` | `ConsoleEndpoints` centraliza las URLs y rechaza cualquier base que no sea `https://` |

Si `LICENSE_CONSOLE_URL` está vacío, las apps siguen funcionando **100 % offline** con la
clave empaquetada, exactamente como antes.

### Dónde leer la huella para el pinning

Consola → **Claves de firma** → tarjeta de la clave activa → campo `publicKeySha256`
(también en `GET /.well-known/licensing-public-key.json?app=drywall_calculator`).

> El pinning protege contra una consola comprometida, pero exige recompilar la app cada vez
> que rotes la clave. Déjalo vacío si prefieres rotación sin recompilar; el riesgo ya está
> cubierto por HTTPS + HSTS + verificación de firma.

---

## 8. Lista de verificación antes de ir a producción

- [ ] `JWT_SECRET` y `MASTER_KEY` generados con `openssl rand -base64 48` y **distintos**.
- [ ] `BOOTSTRAP_ADMIN_PASSWORD` temporal; se cambia al primer inicio de sesión.
- [ ] `NODE_ENV=production` (activa redirect HTTPS, cookies `Secure` y secretos obligatorios).
- [ ] `PUBLIC_URL` y `CORS_ORIGINS` con el dominio real.
- [ ] `SECURE_COOKIES=true` y `TRUST_PROXY=true`.
- [ ] Volumen persistente montado para `DB_FILE` + **copia de seguridad** diaria.
- [ ] Certificado válido (Let's Encrypt) y HSTS activo: comprueba en
      [SSL Labs](https://www.ssllabs.com/ssltest/) y en [securityheaders.com](https://securityheaders.com).
- [ ] `npm run test` (40 pruebas de API + 30 de web) en verde.
- [ ] Llave privada importada desde el keygen Android si ya tenías clientes activados.
- [ ] Las claves RSA-4096 generadas están **respaldadas**: sin `MASTER_KEY` no se recuperan.
- [ ] Registro de auditoría revisado tras el primer día de operación.

---

## 9. Solución de problemas

| Síntoma | Causa probable | Solución |
| :-- | :-- | :-- |
| `El navegador bloquea la página por redirección HTTPS` | Accedes por `http://` con `NODE_ENV=production` | Entra por `https://` o pon `ENABLE_HTTPS=false` en desarrollo |
| La sesión no se mantiene al recargar | Cookie `Secure` servida por HTTP, o web y API en dominios distintos con `SameSite=Strict` | Sirve todo por HTTPS y bajo el mismo dominio |
| `403` al entrar en una pestaña de la consola | El rol no tiene ese permiso | Consola → Roles → ajusta el permiso, o usa un rol superior |
| `/.well-known/...` devuelve 503 | No hay clave activa para esa app | Consola → Claves de firma → Rotar clave, o reinicia el servidor (las genera solo) |
| Las licencias web no se activan en la app | La app usa otra clave pública | Importa la llave privada del keygen, o actualiza `LICENSE_CONSOLE_URL` y reinstala la app |
| La clave privada exportada no se puede leer | `MASTER_KEY` cambió después de generarla | Irrecuperable: rota la clave y vuelve a emitir |
| `Failed to decrypt private key` | Igual que arriba | Rota y reemite |
