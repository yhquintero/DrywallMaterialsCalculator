# Consola de Licencias — Backend (`server/`)

API profesional, **HTTPS por diseño**, que centraliza el control del Keygen de las dos apps:

| App | `appId` | Paquete |
| :-- | :-- | :-- |
| **DrywallPro Master** (calculadora) | `drywall_calculator` | `com.drywall.calculator` |
| **Keygen Pro** (emisor móvil) | `keygen_pro` | `com.drywall.keygen` |

La web (calculadora + consola en `/admin`) y esta API forman una sola plataforma. La UI vive en
[`web/src/admin/`](../web/src/admin).

---

## 1. Arranque rápido

```bash
# Desde la raíz del repositorio
npm run install:all          # instala web/ y server/
cp server/.env.example server/.env   # y completa JWT_SECRET y MASTER_KEY

npm run dev:api              # API en http://0.0.0.0:8443
npm run dev                  # web en http://localhost:3000 (proxifica /api)
npm run dev:https            # TODO en HTTPS local con certificados propios
```

Al primer arranque el servidor:

1. Crea el esquema SQLite y siembra permisos, roles, planes y monedas.
2. Crea el **usuario administrador** (`BOOTSTRAP_ADMIN_*` del `.env`, o una contraseña
   aleatoria que imprime **una sola vez** en el log).
3. Genera un **par RSA-4096 por app** y lo marca activo, para que `/.well-known/*`
   nunca devuelva 503 a las apps Android.

Requisitos: **Node.js ≥ 22.5** (se usa el driver nativo `node:sqlite`; no hay módulos
compilados con `node-gyp`).

---

## 2. Compatibilidad criptográfica con las apps Android

La consola emite licencias **idénticas** a las del keygen móvil, así que una licencia
generada en el navegador se activa en la app sin cambios:

```text
dataToSign = "${user}|${deviceId}|${creationDate}|${expiryDate}"
signature  = Base64( RSASSA-PKCS1-v1_5 + SHA-256 )   // "SHA256withRSA"
issuerKey  = Base64( DER X.509 SubjectPublicKeyInfo ) // X509EncodedKeySpec
```

```json
{
  "user": "Cliente Demo",
  "deviceId": "RQVDRVNUREVWSUNFSUQwMTIzNDU2Nzg5QUJDREVG",
  "creationDate": 1790992241786,
  "expiryDate": 1793663999000,
  "signature": "…Base64…",
  "type": "1 MES PROFESIONAL",
  "issuerKey": "…Base64 SPKI…"
}
```

Detalles que replica del código Kotlin:

* **Vigencia**: vence a las **23:59:59** del último día (`MainActivity.kt` → `Calendar`).
* **Planes**: `1 Día / 1 Semana / 1 Mes / 1 Año / 2 Años Profesional` con los precios
  del enum `LicenseTypeBase` (5 / 20 / 50 / 300 / 500 USD).
* **`deviceId`**: se normaliza con el mismo algoritmo que `LicensingManager.normalizeBase64`
  (url-safe → estándar + re-padding), de modo que la firma coincide con la verificación.
* **Algoritmo**: Android prueba `SHA256withRSAandMGF1` y luego `SHA256withRSA`; al firmar en
  PKCS#1 v1.5 la verificación triunfa en el segundo intento.
* **Llave privada**: se guarda cifrada con **AES-256-GCM** derivado de `MASTER_KEY`.
  Si cambias `MASTER_KEY` después de generar claves, **no podrás descifrarlas**.

### ¿Ya tenías claves generadas en el móvil?

Entra en **Claves de firma → Importar llave del keygen Android** y pega la llave privada
PKCS#8 (la que guarda `KeyGenSecurity` en `software_private_key`). Así la web firma con la
**misma identidad** que ya tienen instalada tus clientes y no hay que reactivar nada.

---

## 3. Roles y permisos (RBAC)

Roles sembrados (`server/src/config/permissions.js`, espejo en `web/src/admin/permissions.ts`):

| Rol | Nivel | Qué puede hacer |
| :-- | :-: | :-- |
| **ADMIN** | 100 | Todo (`*`): usuarios, roles, claves privadas, licencias, auditoría, sistema |
| **MANAGER** | 70 | Emitir, renovar, cobrar, **revocar**, importar/exportar licencias; claves públicas; tasas; planes |
| **OPERATOR** | 40 | Emitir y cobrar licencias, ver claves públicas y tasas. **No** revoca ni elimina |
| **AUDITOR** | 30 | Solo lectura + auditoría completa y exportación |
| **CLIENT** | 10 | Portal de autoconsulta: únicamente sus propias licencias |

**27 permisos granulares** agrupados en `dashboard`, `licenses`, `keys`, `identity`, `rates`,
`audit` y `system`. Además puedes conceder (`allow`) o denegar (`deny`) permisos concretos a
un usuario por encima de su rol — la regla es:

```text
permisos efectivos = permisos del rol + allow del usuario − deny del usuario
```

Reglas duras que aplica el servidor (no solo la UI):

* El comodín `*` es **exclusivo** del rol ADMIN; no se puede conceder a otro rol.
* Solo un ADMIN crea/edita/elimina cuentas con rol ADMIN.
* Nadie puede desactivar su propia cuenta ni degradar su propio rol.
* Siempre debe quedar **al menos un ADMIN activo**.
* Cambiar rol, permisos, contraseña o desactivar ⇒ se **revocan las sesiones** del usuario.
* Exportar la llave privada exige `keys.export_private` **+** rol ADMIN **+** confirmación
  explícita **+** motivo, y se audita como evento **crítico**.

---

## 4. Autenticación

| Elemento | Decisión |
| :-- | :-- |
| Access token | JWT (30 min por defecto), **solo en memoria** del navegador |
| Refresh token | Opaco y aleatorio, cookie `httpOnly` + `Secure` + `SameSite=Strict`, ruta `/api/auth` |
| Almacenamiento | Solo el **hash SHA-256** del refresh token; rotación en cada refresco |
| Contraseñas | bcrypt, coste 12; política mínima de 10 caracteres con mayúscula/minúscula/número/símbolo |
| Bloqueo | 5 intentos fallidos ⇒ 15 min de bloqueo; el ADMIN puede desbloquear |
| Rate limiting | 300 req/min por IP en la API; 30 intentos/15 min en login |
| CSRF | Guard de origen en todas las mutaciones |

---

## 5. API

### Públicos (los consumen las apps Android, sin login)

| Método | Ruta | Uso |
| :-- | :-- | :-- |
| `GET` | `/.well-known/licensing-public-key-v2.pem?app=` | Clave pública PEM (ruta que usa `RemoteKeyProvider`) |
| `GET` | `/.well-known/licensing-public-key.json?app=` | Clave en Base64 + huella + contrato de firma |
| `GET` | `/.well-known/apps.json` | Las dos apps, su clave vigente y sus planes |
| `GET` | `/api/public/health` | Salud del servicio |
| `GET` | `/api/public/rates` | Tasas publicadas (mismo contrato que el scraper) |
| `GET` | `/api/public/revocation-status?signature=&app=` | ¿Está revocada esta firma? |
| `POST` | `/api/public/validate` | Veredicto completo: firma registrada, vigencia, revocación y dispositivo |

### Autenticados

| Recurso | Rutas |
| :-- | :-- |
| Auth | `POST /api/auth/login` · `refresh` · `logout` · `change-password` · `revoke-sessions` · `GET /api/auth/me` · `session-policy` |
| Usuarios | `GET/POST /api/users` · `GET/PATCH/DELETE /api/users/:id` · `POST /api/users/:id/reset-password` · `PUT /api/users/:id/permissions` · `GET /api/users/roles` |
| Roles | `GET/POST /api/roles` · `GET/PATCH/DELETE /api/roles/:id` · `GET /api/roles/permissions` |
| Licencias | `GET/POST /api/licenses` · `GET/PATCH/DELETE /api/licenses/:id` · `:id/renew` · `:id/revoke` · `:id/mark-paid` · `:id/download` · `import` · `stats` · `blacklist` · `export.csv` · `export.json` |
| Dispositivos | `GET /api/devices` · `PATCH /api/devices/:id` (bloquear/desbloquear) |
| Claves | `GET /api/keys` · `GET /api/keys/active/:appId` · `GET /api/keys/:appId/public.pem` · `POST :appId/rotate` · `POST :appId/import` · `POST :appId/export-private` · `DELETE /api/keys/:id` · `GET /api/keys/meta/apps` |
| Planes | `GET/POST /api/plans` · `GET /api/plans/active/:appId` · `PATCH/DELETE /api/plans/:id` |
| Tasas | `GET /api/rates` · `GET /api/rates/history` · `PUT /api/rates` |
| Auditoría | `GET /api/audit` · `GET /api/audit/summary` · `GET /api/audit/export.csv` |
| Sistema | `GET /api/system` (ADMIN/AUDITOR) |

Todos los errores devuelven `{ "error": "mensaje", "details": … }` con el código HTTP adecuado.

---

## 6. Auditoría

Cada acción sensible queda registrada con actor, acción, entidad, app, severidad, IP,
User-Agent y detalle JSON:

`auth.login` · `auth.login_failed` · `auth.account_locked` · `auth.logout` ·
`auth.password_changed` · `rbac.denied` · `users.create/update/delete/reset_password/permissions_update` ·
`roles.create/update/delete` · `licenses.issue/renew/revoke/mark_paid/delete/import/download/update` ·
`keys.rotate/import/export_private/export_public/delete` · `devices.block` ·
`rates.update` · `plans.create/update/delete/deactivate` · `users.bootstrap_admin`

---

## 7. Tests

```bash
npm --prefix server test       # 40 pruebas con node:test
npm run test                   # server + web desde la raíz
```

Cubren: compatibilidad de la firma con Android, vigencia a las 23:59:59, claves por app,
renovación encadenada, revocación y lista negra, importación verificada, RBAC rol por rol,
overrides `allow`/`deny`, bloqueo por intentos fallidos, restricciones de ADMIN, endpoints
públicos, cabeceras de seguridad, guard CSRF y exportaciones CSV/JSON con BOM.

---

## 8. Variables de entorno

Ver [`.env.example`](.env.example). Las críticas:

| Variable | Por qué importa |
| :-- | :-- |
| `JWT_SECRET` | Firma los access tokens. **Obligatoria en producción** (≥ 16 caracteres) |
| `MASTER_KEY` | Cifra las llaves privadas RSA en la BD. **Si la cambias, pierdes las claves** |
| `ENABLE_HTTPS` / `TLS_CERT_FILE` / `TLS_KEY_FILE` | TLS propio del proceso Node |
| `PUBLIC_URL` / `PUBLIC_HOST` | Dominio canónico HTTPS (redirects y `.well-known`) |
| `SECURE_COOKIES` | `true` en producción ⇒ cookie de refresco solo por HTTPS |
| `DB_FILE` | Ruta del SQLite (monta un volumen persistente en Docker) |
| `BOOTSTRAP_ADMIN_*` | Usuario administrador inicial |
| `RSA_KEY_SIZE` | `4096` para coincidir con `KeyGenSecurity.kt` |

Genera secretos fuertes con `openssl rand -base64 48`.

---

## 9. Estructura

```text
server/
├── src/
│   ├── index.js              # arranque HTTP/HTTPS + bootstrap
│   ├── app.js                # Express: helmet, CORS, TLS, rutas, errores
│   ├── config/
│   │   ├── index.js          # .env + validación de secretos
│   │   └── permissions.js    # catálogo de permisos, roles y apps (fuente de verdad)
│   ├── db/
│   │   ├── driver.js         # node:sqlite (fallback better-sqlite3) + transacciones
│   │   ├── schema.js         # 16 tablas, idempotente
│   │   └── index.js          # singleton + siembra
│   ├── security/
│   │   ├── auth.js           # JWT, bcrypt, sesiones, RBAC, bloqueo
│   │   └── httpsMiddleware.js# HSTS, redirect a HTTPS, guard CSRF
│   ├── services/             # cryptoService, keyService, licenseService, auditService
│   └── routes/               # auth, users, roles, licenses, devices, keys, plans, rates, audit, wellKnown, public
├── tools/bootstrap.js        # admin inicial + claves de firma
└── tests/                    # 40 pruebas (node:test)
```
