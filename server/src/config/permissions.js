/**
 * Catálogo maestro de PERMISOS del sistema (fuente única de verdad).
 * Se comparte conceptualmente con el frontend (web/src/admin/permissions.js),
 * que replica el mismo catálogo para pintar la UI.
 */

export const APPS = {
  DRYWALL: 'drywall_calculator',
  KEYGEN: 'keygen_pro',
};

export const APP_META = {
  [APPS.DRYWALL]: {
    id: APPS.DRYWALL,
    name: 'DrywallPro Master',
    package: 'com.drywall.calculator',
    description: 'Calculadora de materiales, presupuestos y visualizador 2D/3D.',
  },
  [APPS.KEYGEN]: {
    id: APPS.KEYGEN,
    name: 'Keygen Pro',
    package: 'com.drywall.keygen',
    description: 'Emisor móvil de licencias, tasas de cambio y facturación.',
  },
};

export const APP_IDS = Object.values(APPS);

/** Grupos de permisos para la UI. */
export const PERMISSION_GROUPS = [
  { id: 'dashboard', label: 'Panel' },
  { id: 'licenses', label: 'Licencias / Keygen' },
  { id: 'keys', label: 'Claves de firma' },
  { id: 'identity', label: 'Usuarios y Roles' },
  { id: 'rates', label: 'Tasas de cambio' },
  { id: 'audit', label: 'Auditoría' },
  { id: 'system', label: 'Sistema' },
];

/**
 * Cada permiso: { id, group, label, description }
 * Convención: <recurso>.<acción>
 */
export const PERMISSIONS = [
  // Panel
  { id: 'dashboard.view', group: 'dashboard', label: 'Ver panel', description: 'Accede al dashboard con KPIs de licencias e ingresos.' },

  // Licencias
  { id: 'licenses.view', group: 'licenses', label: 'Ver licencias', description: 'Lista y detalle de licencias emitidas de ambas apps.' },
  { id: 'licenses.issue', group: 'licenses', label: 'Emitir licencias', description: 'Genera y firma una licencia nueva (RSA-4096).' },
  { id: 'licenses.renew', group: 'licenses', label: 'Renovar licencias', description: 'Extiende la vigencia de una licencia existente.' },
  { id: 'licenses.revoke', group: 'licenses', label: 'Revocar licencias', description: 'Revoca y agrega la firma a la lista negra.' },
  { id: 'licenses.delete', group: 'licenses', label: 'Eliminar licencias', description: 'Borrado definitivo del registro.' },
  { id: 'licenses.mark_paid', group: 'licenses', label: 'Marcar cobrada', description: 'Confirma el pago y activa la entrega de la licencia.' },
  { id: 'licenses.import', group: 'licenses', label: 'Importar / sincronizar', description: 'Importa licencias emitidas por las apps Android.' },
  { id: 'licenses.export', group: 'licenses', label: 'Exportar', description: 'Exporta licencias a JSON/CSV y descarga el archivo .json del cliente.' },

  // Claves de firma
  { id: 'keys.view', group: 'keys', label: 'Ver claves públicas', description: 'Consulta el estado y la clave pública (PEM/Base64) de cada app.' },
  { id: 'keys.rotate', group: 'keys', label: 'Rotar claves', description: 'Genera un nuevo par RSA-4096 y lo marca activo.' },
  { id: 'keys.export_private', group: 'keys', label: 'Exportar clave privada', description: 'PELIGROSO: descarga la llave privada PKCS#8 cifrada. Solo Admin.' },

  // Identidad
  { id: 'users.view', group: 'identity', label: 'Ver usuarios', description: 'Lista usuarios, sus roles y estado.' },
  { id: 'users.create', group: 'identity', label: 'Crear usuarios', description: 'Alta de usuarios con rol asignado.' },
  { id: 'users.update', group: 'identity', label: 'Editar usuarios', description: 'Cambia datos, rol, estado activo/bloqueado.' },
  { id: 'users.delete', group: 'identity', label: 'Eliminar usuarios', description: 'Baja lógica o física de usuarios.' },
  { id: 'users.reset_password', group: 'identity', label: 'Resetear contraseñas', description: 'Genera una contraseña temporal para otro usuario.' },
  { id: 'roles.view', group: 'identity', label: 'Ver roles', description: 'Lista roles y sus permisos.' },
  { id: 'roles.manage', group: 'identity', label: 'Gestionar roles', description: 'Crea/edita roles y asigna permisos. Solo Admin.' },

  // Tasas
  { id: 'rates.view', group: 'rates', label: 'Ver tasas', description: 'Consulta tasas de cambio actuales e histórico.' },
  { id: 'rates.manage', group: 'rates', label: 'Gestionar tasas', description: 'Publica/actualiza tasas usadas por las apps.' },

  // Auditoría
  { id: 'audit.view', group: 'audit', label: 'Ver auditoría', description: 'Lee el log de auditoría con filtros.' },
  { id: 'audit.export', group: 'audit', label: 'Exportar auditoría', description: 'Descarga el log de auditoría en CSV/JSON.' },

  // Sistema
  { id: 'plans.manage', group: 'system', label: 'Gestionar planes', description: 'Alta/edición de planes de licencia y precios.' },
  { id: 'settings.manage', group: 'system', label: 'Configuración del sistema', description: 'Parámetros globales, políticas de seguridad y mantenimiento.' },
  { id: 'system.health', group: 'system', label: 'Ver salud del sistema', description: 'Estado del servicio, driver de BD y TLS.' },
];

export const PERMISSION_IDS = PERMISSIONS.map((p) => p.id);
export const PERMISSION_MAP = Object.fromEntries(PERMISSIONS.map((p) => [p.id, p]));

/**
 * Roles por defecto (sembrados en la migración).
 * `isSystem` impide borrarlos; ADMIN no puede perder `*`.
 */
export const DEFAULT_ROLES = [
  {
    code: 'ADMIN',
    name: 'Administrador',
    description: 'Control total: usuarios, roles, claves privadas, licencias y auditoría.',
    level: 100,
    isSystem: 1,
    permissions: ['*'],
  },
  {
    code: 'MANAGER',
    name: 'Gestor de Licencias',
    description: 'Emite, renueva, cobra y revoca licencias de ambas apps. No administra usuarios.',
    level: 70,
    isSystem: 1,
    permissions: [
      'dashboard.view',
      'licenses.view', 'licenses.issue', 'licenses.renew', 'licenses.revoke',
      'licenses.mark_paid', 'licenses.import', 'licenses.export',
      'keys.view',
      'rates.view', 'rates.manage',
      'plans.manage',
    ],
  },
  {
    code: 'OPERATOR',
    name: 'Operador',
    description: 'Registra solicitudes y cobra. No puede revocar ni eliminar.',
    level: 40,
    isSystem: 1,
    permissions: [
      'dashboard.view',
      'licenses.view', 'licenses.issue', 'licenses.mark_paid', 'licenses.export',
      'keys.view',
      'rates.view',
    ],
  },
  {
    code: 'AUDITOR',
    name: 'Auditor',
    description: 'Solo lectura con acceso completo a la trazabilidad y auditoría.',
    level: 30,
    isSystem: 1,
    permissions: [
      'dashboard.view',
      'licenses.view', 'licenses.export',
      'keys.view',
      'users.view', 'roles.view',
      'rates.view',
      'audit.view', 'audit.export',
      'system.health',
    ],
  },
  {
    code: 'CLIENT',
    name: 'Cliente',
    description: 'Portal de autoconsulta: ve únicamente sus propias licencias y su renovación.',
    level: 10,
    isSystem: 1,
    permissions: ['licenses.view'],
  },
];

export const ROLE_CODES = DEFAULT_ROLES.map((r) => r.code);
