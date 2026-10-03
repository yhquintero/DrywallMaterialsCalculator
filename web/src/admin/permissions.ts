/**
 * Catálogo de permisos del lado cliente.
 *
 * Es el ESPEJO de `server/src/config/permissions.js`: la fuente de verdad es el
 * backend (que devuelve `GET /api/roles/permissions`), pero replicamos el
 * catálogo aquí para poder pintar la UI sin depender de una petición extra y
 * para tipar las comprobaciones.
 *
 * `can()` evalúa exactamente con la misma regla que el middleware `requirePermission`
 * del servidor: el comodín `*` concede todo; el resto se comprueba por inclusión.
 */
import type { PermissionDef, PermissionGroup } from './types';

export const PERMISSION_GROUPS: PermissionGroup[] = [
  { id: 'dashboard', label: 'Panel' },
  { id: 'licenses', label: 'Licencias / Keygen' },
  { id: 'keys', label: 'Claves de firma' },
  { id: 'identity', label: 'Usuarios y Roles' },
  { id: 'rates', label: 'Tasas de cambio' },
  { id: 'audit', label: 'Auditoría' },
  { id: 'system', label: 'Sistema' },
];

export const PERMISSIONS: PermissionDef[] = [
  { id: 'dashboard.view', group: 'dashboard', label: 'Ver panel', description: 'Accede al dashboard con KPIs de licencias e ingresos.' },

  { id: 'licenses.view', group: 'licenses', label: 'Ver licencias', description: 'Lista y detalle de licencias emitidas de ambas apps.' },
  { id: 'licenses.issue', group: 'licenses', label: 'Emitir licencias', description: 'Genera y firma una licencia nueva (RSA-4096).' },
  { id: 'licenses.renew', group: 'licenses', label: 'Renovar licencias', description: 'Extiende la vigencia de una licencia existente.' },
  { id: 'licenses.revoke', group: 'licenses', label: 'Revocar licencias', description: 'Revoca y agrega la firma a la lista negra.' },
  { id: 'licenses.delete', group: 'licenses', label: 'Eliminar licencias', description: 'Borrado definitivo del registro.' },
  { id: 'licenses.mark_paid', group: 'licenses', label: 'Marcar cobrada', description: 'Confirma el pago y activa la entrega de la licencia.' },
  { id: 'licenses.import', group: 'licenses', label: 'Importar / sincronizar', description: 'Importa licencias emitidas por las apps Android.' },
  { id: 'licenses.export', group: 'licenses', label: 'Exportar', description: 'Exporta licencias a JSON/CSV y descarga el archivo del cliente.' },

  { id: 'keys.view', group: 'keys', label: 'Ver claves públicas', description: 'Consulta el estado y la clave pública de cada app.' },
  { id: 'keys.rotate', group: 'keys', label: 'Rotar claves', description: 'Genera un nuevo par RSA-4096 y lo marca activo.' },
  { id: 'keys.export_private', group: 'keys', label: 'Exportar clave privada', description: 'PELIGROSO: descarga la llave privada PKCS#8. Solo Admin.' },

  { id: 'users.view', group: 'identity', label: 'Ver usuarios', description: 'Lista usuarios, sus roles y estado.' },
  { id: 'users.create', group: 'identity', label: 'Crear usuarios', description: 'Alta de usuarios con rol asignado.' },
  { id: 'users.update', group: 'identity', label: 'Editar usuarios', description: 'Cambia datos, rol, estado activo/bloqueado.' },
  { id: 'users.delete', group: 'identity', label: 'Eliminar usuarios', description: 'Baja lógica o física de usuarios.' },
  { id: 'users.reset_password', group: 'identity', label: 'Resetear contraseñas', description: 'Genera una contraseña temporal para otro usuario.' },
  { id: 'roles.view', group: 'identity', label: 'Ver roles', description: 'Lista roles y sus permisos.' },
  { id: 'roles.manage', group: 'identity', label: 'Gestionar roles', description: 'Crea/edita roles y asigna permisos. Solo Admin.' },

  { id: 'rates.view', group: 'rates', label: 'Ver tasas', description: 'Consulta tasas de cambio actuales e histórico.' },
  { id: 'rates.manage', group: 'rates', label: 'Gestionar tasas', description: 'Publica/actualiza tasas usadas por las apps.' },

  { id: 'audit.view', group: 'audit', label: 'Ver auditoría', description: 'Lee el log de auditoría con filtros.' },
  { id: 'audit.export', group: 'audit', label: 'Exportar auditoría', description: 'Descarga el log en CSV/JSON.' },

  { id: 'plans.manage', group: 'system', label: 'Gestionar planes', description: 'Alta/edición de planes de licencia y precios.' },
  { id: 'settings.manage', group: 'system', label: 'Configuración del sistema', description: 'Parámetros globales y políticas de seguridad.' },
  { id: 'system.health', group: 'system', label: 'Ver salud del sistema', description: 'Estado del servicio, driver de BD y TLS.' },
];

export const PERMISSION_IDS = PERMISSIONS.map((p) => p.id);

/** Regla idéntica al middleware del servidor. */
export function can(permissions: string[] | undefined | null, required: string | string[]): boolean {
  if (!permissions || permissions.length === 0) return false;
  if (permissions.includes('*')) return true;
  const list = Array.isArray(required) ? required : [required];
  return list.every((p) => permissions.includes(p));
}

export const canAny = (permissions: string[] | undefined | null, list: string[]): boolean =>
  list.some((p) => can(permissions, p));

export function permissionsByGroup(): { group: PermissionGroup; items: PermissionDef[] }[] {
  return PERMISSION_GROUPS.map((group) => ({
    group,
    items: PERMISSIONS.filter((p) => p.group === group.id),
  }));
}

/** Metadatos de las dos apps gestionadas (espejo de APP_META del backend). */
export const APPS = {
  drywall_calculator: {
    id: 'drywall_calculator' as const,
    name: 'DrywallPro Master',
    package: 'com.drywall.calculator',
    short: 'Calculadora',
    description: 'Calculadora de materiales, presupuestos y visualizador 2D/3D.',
  },
  keygen_pro: {
    id: 'keygen_pro' as const,
    name: 'Keygen Pro',
    package: 'com.drywall.keygen',
    short: 'Keygen',
    description: 'Emisor móvil de licencias, tasas de cambio y facturación.',
  },
};

export const APP_LIST = [APPS.drywall_calculator, APPS.keygen_pro];

export const CURRENCIES = ['USD', 'EUR', 'MLC', 'CAD', 'MEX', 'ZELLE', 'CLA'] as const;

/** Planes por defecto (enum LicenseTypeBase del keygen Android). */
export const DEFAULT_PLANS = [
  { code: 'ONE_DAY', label: '1 Día Profesional', typeName: '1 DÍA PROFESIONAL', days: 1, price: 5 },
  { code: 'ONE_WEEK', label: '1 Semana Profesional', typeName: '1 SEMANA PROFESIONAL', days: 7, price: 20 },
  { code: 'ONE_MONTH', label: '1 Mes Profesional', typeName: '1 MES PROFESIONAL', days: 30, price: 50 },
  { code: 'ONE_YEAR', label: '1 Año Profesional', typeName: '1 AÑO PROFESIONAL', days: 365, price: 300 },
  { code: 'TWO_YEARS', label: '2 Años Profesional', typeName: '2 AÑOS PROFESIONAL', days: 730, price: 500 },
];

export const STATUS_META: Record<string, { label: string; className: string }> = {
  paid: { label: 'Cobrada', className: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30' },
  issued: { label: 'Emitida', className: 'bg-sky-500/15 text-sky-300 ring-sky-500/30' },
  draft: { label: 'Borrador', className: 'bg-slate-500/15 text-slate-300 ring-slate-500/30' },
  expired: { label: 'Vencida', className: 'bg-amber-500/15 text-amber-300 ring-amber-500/30' },
  revoked: { label: 'Revocada', className: 'bg-rose-500/15 text-rose-300 ring-rose-500/30' },
  blocked: { label: 'Bloqueada', className: 'bg-red-600/15 text-red-300 ring-red-600/30' },
};
