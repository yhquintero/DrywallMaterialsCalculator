import { describe, expect, it } from 'vitest';
import {
  APPS,
  APP_LIST,
  CURRENCIES,
  DEFAULT_PLANS,
  PERMISSIONS,
  PERMISSION_GROUPS,
  PERMISSION_IDS,
  STATUS_META,
  can,
  canAny,
  permissionsByGroup,
} from './permissions';

describe('catálogo de permisos (espejo del backend)', () => {
  it('no tiene permisos duplicados ni grupos huérfanos', () => {
    const ids = PERMISSIONS.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(PERMISSION_IDS).toEqual(ids);

    const groupIds = new Set(PERMISSION_GROUPS.map((g) => g.id));
    for (const p of PERMISSIONS) {
      expect(groupIds.has(p.group), `permiso ${p.id} en grupo desconocido ${p.group}`).toBe(true);
    }
    for (const g of PERMISSION_GROUPS) {
      expect(PERMISSIONS.some((p) => p.group === g.id), `grupo ${g.id} sin permisos`).toBe(true);
    }
  });

  it('todos los permisos tienen etiqueta y descripción', () => {
    for (const p of PERMISSIONS) {
      expect(p.label.length).toBeGreaterThan(1);
      expect(p.description.length).toBeGreaterThan(5);
    }
  });

  it('sigue la convención recurso.acción', () => {
    for (const p of PERMISSIONS) {
      expect(p.id).toMatch(/^[a-z_]+\.[a-z_]+$/);
    }
  });

  it('agrupa los permisos para la UI', () => {
    const grouped = permissionsByGroup();
    expect(grouped.length).toBe(PERMISSION_GROUPS.length);
    const total = grouped.reduce((acc, g) => acc + g.items.length, 0);
    expect(total).toBe(PERMISSIONS.length);
  });
});

describe('can() — misma regla que requirePermission del servidor', () => {
  it('deniega sin permisos', () => {
    expect(can([], 'licenses.view')).toBe(false);
    expect(can(undefined, 'licenses.view')).toBe(false);
    expect(can(null, 'licenses.view')).toBe(false);
  });

  it('el comodín * concede todo', () => {
    expect(can(['*'], 'licenses.revoke')).toBe(true);
    expect(can(['*'], 'keys.export_private')).toBe(true);
    expect(can(['*'], ['users.create', 'roles.manage'])).toBe(true);
  });

  it('exige TODOS los permisos cuando se pasa una lista', () => {
    const perms = ['licenses.view', 'licenses.issue'];
    expect(can(perms, 'licenses.view')).toBe(true);
    expect(can(perms, ['licenses.view', 'licenses.issue'])).toBe(true);
    expect(can(perms, ['licenses.view', 'licenses.revoke'])).toBe(false);
  });

  it('canAny concede con uno solo', () => {
    const perms = ['licenses.view'];
    expect(canAny(perms, ['licenses.revoke', 'licenses.view'])).toBe(true);
    expect(canAny(perms, ['licenses.revoke', 'users.view'])).toBe(false);
    expect(canAny([], ['licenses.view'])).toBe(false);
  });

  it('replica los roles por defecto del backend', () => {
    const MANAGER = [
      'dashboard.view',
      'licenses.view', 'licenses.issue', 'licenses.renew', 'licenses.revoke',
      'licenses.mark_paid', 'licenses.import', 'licenses.export',
      'keys.view', 'rates.view', 'rates.manage', 'plans.manage',
    ];
    const OPERATOR = ['dashboard.view', 'licenses.view', 'licenses.issue', 'licenses.mark_paid', 'licenses.export', 'keys.view', 'rates.view'];
    const AUDITOR = ['dashboard.view', 'licenses.view', 'licenses.export', 'keys.view', 'users.view', 'roles.view', 'rates.view', 'audit.view', 'audit.export', 'system.health'];
    const CLIENT = ['licenses.view'];

    expect(can(MANAGER, 'licenses.revoke')).toBe(true);
    expect(can(MANAGER, 'users.view')).toBe(false);
    expect(can(OPERATOR, 'licenses.revoke')).toBe(false);
    expect(can(OPERATOR, 'licenses.issue')).toBe(true);
    expect(can(AUDITOR, 'licenses.issue')).toBe(false);
    expect(can(AUDITOR, 'audit.view')).toBe(true);
    expect(can(CLIENT, 'licenses.view')).toBe(true);
    expect(can(CLIENT, 'licenses.issue')).toBe(false);
    // El comodín es exclusivo de ADMIN.
    expect(can(MANAGER, '*')).toBe(false);
  });
});

describe('dominio compartido con las apps Android', () => {
  it('expone exactamente las dos apps gestionadas', () => {
    expect(APP_LIST.map((a) => a.id)).toEqual(['drywall_calculator', 'keygen_pro']);
    expect(APPS.drywall_calculator.package).toBe('com.drywall.calculator');
    expect(APPS.keygen_pro.package).toBe('com.drywall.keygen');
  });

  it('los planes replican LicenseTypeBase del keygen', () => {
    expect(DEFAULT_PLANS.map((p) => p.code)).toEqual(['ONE_DAY', 'ONE_WEEK', 'ONE_MONTH', 'ONE_YEAR', 'TWO_YEARS']);
    expect(DEFAULT_PLANS.map((p) => p.days)).toEqual([1, 7, 30, 365, 730]);
    expect(DEFAULT_PLANS.map((p) => p.price)).toEqual([5, 20, 50, 300, 500]);
    expect(DEFAULT_PLANS[2].typeName).toBe('1 MES PROFESIONAL');
  });

  it('las monedas coinciden con el enum Currency', () => {
    expect(CURRENCIES).toEqual(['USD', 'EUR', 'MLC', 'CAD', 'MEX', 'ZELLE', 'CLA']);
  });

  it('todos los estados de licencia tienen etiqueta', () => {
    for (const status of ['draft', 'issued', 'paid', 'expired', 'revoked', 'blocked']) {
      expect(STATUS_META[status]?.label).toBeTruthy();
      expect(STATUS_META[status]?.className).toMatch(/ring-/);
    }
  });
});
