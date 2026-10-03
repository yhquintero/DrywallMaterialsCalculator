/**
 * Cliente HTTP de la Consola de Licencias.
 *
 * Decisiones de seguridad:
 *  • Todas las URLs son RELATIVAS (`/api/...`): en desarrollo las proxifica Vite
 *    y en producción las sirve el mismo origen. Así nunca hay contenido mixto
 *    (mixed content) ni CORS, y la web funciona 100% sobre HTTPS.
 *  • El access token vive SOLO en memoria (nunca en localStorage ⇒ inaccesible
 *    para XSS persistente). El refresh token va en una cookie httpOnly que el
 *    navegador gestiona solo.
 *  • Ante un 401 se intenta UN refresco; si falla, se fuerza el re-login.
 */

export class ApiError extends Error {
  status: number;
  details: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

let accessToken: string | null = null;
let onUnauthorized: (() => void) | null = null;
let refreshPromise: Promise<boolean> | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}
export function getAccessToken() {
  return accessToken;
}
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

/** Intenta renovar la sesión con la cookie httpOnly. Devuelve true si hay token nuevo. */
export async function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch('/api/auth/refresh', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'content-type': 'application/json' },
        });
        if (!res.ok) return false;
        const data = await res.json();
        if (data?.accessToken) {
          accessToken = data.accessToken;
          return true;
        }
        return false;
      } catch {
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
}

async function buildHeaders(extra?: HeadersInit, withAuth = true): Promise<HeadersInit> {
  const headers = new Headers(extra);
  if (!headers.has('content-type')) headers.set('content-type', 'application/json');
  if (withAuth && accessToken) headers.set('authorization', `Bearer ${accessToken}`);
  return headers;
}

async function parse<T>(res: Response): Promise<T> {
  const text = await res.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  if (!res.ok) {
    const message =
      (body && typeof body === 'object' && 'error' in body ? String((body as { error: unknown }).error) : null) ||
      `Error ${res.status}`;
    const details = body && typeof body === 'object' && 'details' in body ? (body as { details: unknown }).details : undefined;
    throw new ApiError(res.status, message, details);
  }
  return body as T;
}

async function request<T>(method: string, path: string, body?: unknown, opts: { raw?: boolean } = {}): Promise<T> {
  const doFetch = async (withAuth: boolean) =>
    fetch(path, {
      method,
      credentials: 'same-origin',
      headers: await buildHeaders(undefined, withAuth),
      body: body === undefined ? undefined : JSON.stringify(body),
    });

  let res = await doFetch(Boolean(accessToken));

  if (res.status === 401 && accessToken !== null) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      res = await doFetch(true);
    } else {
      accessToken = null;
      onUnauthorized?.();
    }
  } else if (res.status === 401 && accessToken === null) {
    // Sin token: puede que la cookie siga viva (p. ej. tras recargar la página).
    const refreshed = await refreshAccessToken();
    if (refreshed) res = await doFetch(true);
    else onUnauthorized?.();
  }

  if (opts.raw) return res as unknown as T;
  return parse<T>(res);
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
  delete: <T>(path: string) => request<T>('DELETE', path),
  raw: (path: string) => request<Response>('GET', path, undefined, { raw: true }),
};

/** Descarga un archivo autenticado (CSV/JSON/PEM) vía blob. */
export async function downloadAuthenticated(path: string, fallbackName: string) {
  const res = await api.raw(path);
  if (!res.ok) throw new ApiError(res.status, `No se pudo descargar ${path}`);
  const disposition = res.headers.get('content-disposition') || '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const filename = match?.[1] ?? fallbackName;
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
  return filename;
}

/** Construye una query string ignorando valores vacíos. */
export function qs(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    search.set(key, String(value));
  }
  const s = search.toString();
  return s ? `?${s}` : '';
}
