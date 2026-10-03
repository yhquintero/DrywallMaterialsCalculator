import React from 'react';
import { Check, Minus, Plus, ShieldCheck, Trash2, Users } from 'lucide-react';
import { api } from '../api';
import { useAuth } from '../AuthContext';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  Checkbox,
  EmptyState,
  Field,
  Input,
  Modal,
  Spinner,
  Table,
  Td,
  Textarea,
  Th,
  Tr,
  cn,
} from '../components/ui';
import { PageHeader } from '../components/AdminLayout';
import { Can } from '../components/Guard';
import { PERMISSIONS, PERMISSION_GROUPS, can } from '../permissions';
import type { PermissionDef, Role } from '../types';

export function RolesPage() {
  const { hasPermission, isAdmin } = useAuth();
  const { toast, viewport } = useToast();
  const canManage = hasPermission('roles.manage');

  const [roles, setRoles] = React.useState<Role[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [selected, setSelected] = React.useState<Role | null>(null);
  const [editing, setEditing] = React.useState<Role | 'new' | null>(null);
  const [deleting, setDeleting] = React.useState<Role | null>(null);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: Role[] }>('/api/roles');
      setRoles(res.items);
      setSelected((prev) => (prev ? res.items.find((r) => r.id === prev.id) ?? res.items[0] ?? null : res.items[0] ?? null));
    } catch (err) {
      toast.error(err, 'No se pudieron cargar los roles');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <Spinner label="Cargando roles…" />;

  return (
    <>
      <PageHeader
        title="Roles y permisos"
        description="Matriz RBAC del sistema. Cada rol agrupa permisos granulares; además puedes conceder o denegar permisos concretos a un usuario desde su ficha."
        actions={
          <Can permission="roles.manage">
            <Button size="sm" variant="primary" icon={<Plus className="h-3.5 w-3.5" />} onClick={() => setEditing('new')}>
              Nuevo rol
            </Button>
          </Can>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,340px)_minmax(0,1fr)]">
        {/* Lista de roles */}
        <Card>
          <CardHeader title="Roles definidos" icon={<ShieldCheck className="h-4 w-4" />} subtitle={`${roles.length} rol(es)`} />
          <CardBody className="space-y-2 p-3">
            {roles.length === 0 ? (
              <EmptyState title="Sin roles" />
            ) : (
              roles.map((role) => (
                <button
                  key={role.id}
                  type="button"
                  onClick={() => setSelected(role)}
                  className={cn(
                    'w-full rounded-lg border p-3 text-left transition',
                    selected?.id === role.id
                      ? 'border-brand-500/60 bg-brand-600/10 ring-1 ring-brand-500/30'
                      : 'border-slate-800 bg-slate-950/40 hover:border-slate-700'
                  )}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-100">{role.name}</p>
                      <p className="font-mono text-[10px] text-slate-500">{role.code} · nivel {role.level}</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      {role.isSystem ? <Badge tone="violet">sistema</Badge> : null}
                      <Badge tone="slate">
                        <Users className="h-3 w-3" /> {role.userCount ?? 0}
                      </Badge>
                    </div>
                  </div>
                  <p className="mt-1.5 line-clamp-2 text-[11px] leading-snug text-slate-400">{role.description}</p>
                  <p className="mt-1.5 text-[10px] text-slate-500">
                    {role.isWildcard ? 'Todos los permisos (*)' : `${role.permissions.length} permiso(s)`}
                  </p>
                </button>
              ))
            )}
          </CardBody>
        </Card>

        {/* Matriz de permisos */}
        {selected ? (
          <Card>
            <CardHeader
              title={`${selected.name} — matriz de permisos`}
              icon={<ShieldCheck className="h-4 w-4" />}
              subtitle={selected.description}
              actions={
                <>
                  <Can permission="roles.manage">
                    <Button size="sm" variant="secondary" onClick={() => setEditing(selected)}>Editar rol</Button>
                    <Button
                      size="sm"
                      variant="danger"
                      icon={<Trash2 className="h-3.5 w-3.5" />}
                      disabled={selected.isSystem}
                      onClick={() => setDeleting(selected)}
                    >
                      Eliminar
                    </Button>
                  </Can>
                </>
              }
            />
            <CardBody className="p-0">
              <PermissionMatrix role={selected} roles={roles} onChanged={load} readOnly={!canManage} />
            </CardBody>
          </Card>
        ) : null}
      </div>

      {/* Comparativa global */}
      <Card className="mt-4">
        <CardHeader title="Comparativa de roles" icon={<Users className="h-4 w-4" />} subtitle="Vista rápida de qué puede hacer cada rol" />
        <CardBody className="p-0">
          <Table>
            <thead>
              <tr>
                <Th>Permiso</Th>
                {roles.map((r) => (
                  <Th key={r.id} className="text-center">{r.code}</Th>
                ))}
              </tr>
            </thead>
            <tbody>
              {PERMISSIONS.map((p) => (
                <Tr key={p.id}>
                  <Td>
                    <span className="block font-mono text-[11px] text-slate-200">{p.id}</span>
                    <span className="block text-[10px] text-slate-500">{p.label}</span>
                  </Td>
                  {roles.map((r) => {
                    const granted = can(r.permissions, p.id);
                    return (
                      <Td key={r.id} className="text-center">
                        {granted ? (
                          <Check className="mx-auto h-4 w-4 text-emerald-400" />
                        ) : (
                          <Minus className="mx-auto h-4 w-4 text-slate-700" />
                        )}
                      </Td>
                    );
                  })}
                </Tr>
              ))}
            </tbody>
          </Table>
        </CardBody>
      </Card>

      {editing ? (
        <RoleFormModal
          role={editing === 'new' ? null : editing}
          isAdmin={isAdmin}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      ) : null}

      <Modal
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        title="Eliminar rol"
        danger
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)}>Cancelar</Button>
            <Button
              variant="danger"
              onClick={async () => {
                if (!deleting) return;
                try {
                  await api.delete(`/api/roles/${deleting.id}`);
                  toast.success('Rol eliminado');
                  setDeleting(null);
                  void load();
                } catch (err) {
                  toast.error(err);
                }
              }}
            >
              Eliminar
            </Button>
          </>
        }
      >
        <p className="text-sm text-slate-300">
          Se eliminará el rol <strong>{deleting?.name}</strong>. Solo es posible si no tiene usuarios asignados; en caso
          contrario reasigna primero esos usuarios a otro rol.
        </p>
      </Modal>

      {viewport}
    </>
  );
}

/** Matriz editable: concede/revoca permisos del rol con un clic. */
function PermissionMatrix({
  role,
  roles,
  onChanged,
  readOnly,
}: {
  role: Role;
  roles: Role[];
  onChanged: () => void;
  readOnly: boolean;
}) {
  const { toast, viewport } = useToast();
  const [pending, setPending] = React.useState<string | null>(null);
  void roles;

  const toggle = async (perm: PermissionDef, granted: boolean) => {
    if (readOnly || role.isWildcard) return;
    setPending(perm.id);
    const next = granted ? role.permissions.filter((p) => p !== perm.id) : [...role.permissions, perm.id];
    try {
      await api.patch(`/api/roles/${role.id}`, { permissions: next });
      onChanged();
    } catch (err) {
      toast.error(err);
    } finally {
      setPending(null);
    }
  };

  if (role.isWildcard) {
    return (
      <div className="px-5 py-8 text-center">
        <ShieldCheck className="mx-auto h-8 w-8 text-violet-400" />
        <p className="mt-2 text-sm font-medium text-slate-200">Este rol tiene el permiso comodín (*)</p>
        <p className="mt-1 text-xs text-slate-500">
          Concede automáticamente todos los permisos, incluidos los que se añadan en el futuro. Solo el rol ADMIN puede tenerlo.
        </p>
      </div>
    );
  }

  return (
    <>
      {PERMISSION_GROUPS.map((group) => {
        const items = PERMISSIONS.filter((p) => p.group === group.id);
        if (items.length === 0) return null;
        const grantedCount = items.filter((p) => can(role.permissions, p.id)).length;
        return (
          <div key={group.id} className="border-b border-slate-800/70 last:border-0">
            <div className="flex items-center justify-between gap-2 bg-slate-900/70 px-5 py-2">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">{group.label}</p>
              <Badge tone={grantedCount === items.length ? 'green' : grantedCount === 0 ? 'slate' : 'amber'}>
                {grantedCount}/{items.length}
              </Badge>
            </div>
            <ul className="divide-y divide-slate-800/50">
              {items.map((perm) => {
                const granted = can(role.permissions, perm.id);
                return (
                  <li key={perm.id} className="flex items-start gap-3 px-5 py-2.5">
                    <button
                      type="button"
                      disabled={readOnly}
                      onClick={() => void toggle(perm, granted)}
                      aria-pressed={granted}
                      className={cn(
                        'mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded border transition',
                        granted
                          ? 'border-emerald-500 bg-emerald-600/30 text-emerald-300'
                          : 'border-slate-700 bg-slate-950 text-transparent hover:border-slate-500',
                        readOnly && 'cursor-not-allowed opacity-60',
                        pending === perm.id && 'animate-pulse'
                      )}
                    >
                      <Check className="h-3.5 w-3.5" />
                    </button>
                    <div className="min-w-0 flex-1">
                      <p className="font-mono text-[11px] text-slate-200">{perm.id}</p>
                      <p className="text-xs font-medium text-slate-300">{perm.label}</p>
                      <p className="mt-0.5 text-[11px] leading-snug text-slate-500">{perm.description}</p>
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
      {readOnly ? (
        <p className="border-t border-slate-800 px-5 py-2.5 text-[11px] text-slate-500">
          Modo lectura: tu rol no incluye <code className="font-mono">roles.manage</code>.
        </p>
      ) : null}
      {viewport}
    </>
  );
}

function RoleFormModal({
  role,
  isAdmin,
  onClose,
  onSaved,
}: {
  role: Role | null;
  isAdmin: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast, viewport } = useToast();
  const [saving, setSaving] = React.useState(false);
  const [form, setForm] = React.useState({
    code: role?.code ?? '',
    name: role?.name ?? '',
    description: role?.description ?? '',
    level: role?.level ?? 20,
    isActive: role?.isActive ?? true,
  });
  const [perms, setPerms] = React.useState<string[]>(role?.permissions.filter((p) => p !== '*') ?? []);

  const togglePerm = (id: string) =>
    setPerms((prev) => (prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]));

  const toggleGroup = (groupId: string) => {
    const ids = PERMISSIONS.filter((p) => p.group === groupId).map((p) => p.id);
    const allOn = ids.every((id) => perms.includes(id));
    setPerms((prev) => (allOn ? prev.filter((p) => !ids.includes(p)) : [...new Set([...prev, ...ids])]));
  };

  const submit = async () => {
    setSaving(true);
    try {
      const payload = { ...form, code: form.code.toUpperCase(), permissions: perms, isActive: form.isActive };
      if (role) await api.patch(`/api/roles/${role.id}`, payload);
      else await api.post('/api/roles', payload);
      toast.success(role ? 'Rol actualizado' : 'Rol creado');
      onSaved();
    } catch (err) {
      toast.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title={role ? `Editar rol ${role.code}` : 'Nuevo rol'}
        subtitle="Selecciona los permisos granulares que tendrá este rol."
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={onClose}>Cancelar</Button>
            <Button variant="primary" loading={saving} onClick={() => void submit()}>
              {role ? 'Guardar rol' : 'Crear rol'}
            </Button>
          </>
        }
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Código" required hint="MAYÚSCULAS sin espacios, p. ej. SOPORTE">
            <Input
              value={form.code}
              onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
              disabled={Boolean(role?.isSystem)}
              maxLength={30}
            />
          </Field>
          <Field label="Nombre" required>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={80} />
          </Field>
          <Field label="Nivel jerárquico" hint="Mayor nivel = más autoridad (ADMIN = 100)">
            <Input type="number" min={0} max={999} value={form.level} onChange={(e) => setForm({ ...form, level: Number(e.target.value) })} />
          </Field>
          <div className="flex items-end pb-2">
            <Checkbox checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} label="Rol activo" />
          </div>
          <Field label="Descripción" className="sm:col-span-2">
            <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} maxLength={300} />
          </Field>
        </div>

        {!isAdmin ? (
          <p className="mt-3 rounded-lg border border-amber-800/40 bg-amber-950/30 p-2.5 text-[11px] text-amber-200">
            El permiso comodín (*) es exclusivo del rol ADMIN y no está disponible aquí.
          </p>
        ) : null}

        <div className="mt-5 space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-slate-300">Permisos ({perms.length}/{PERMISSIONS.length})</p>
            <div className="flex gap-2">
              <Button size="sm" variant="ghost" onClick={() => setPerms(PERMISSIONS.map((p) => p.id))}>Marcar todos</Button>
              <Button size="sm" variant="ghost" onClick={() => setPerms([])}>Ninguno</Button>
            </div>
          </div>
          {PERMISSION_GROUPS.map((group) => {
            const items = PERMISSIONS.filter((p) => p.group === group.id);
            if (!items.length) return null;
            const allOn = items.every((p) => perms.includes(p.id));
            return (
              <div key={group.id} className="rounded-lg border border-slate-800 bg-slate-950/40 p-3">
                <div className="mb-2 flex items-center justify-between">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">{group.label}</p>
                  <Button size="sm" variant="ghost" onClick={() => toggleGroup(group.id)}>
                    {allOn ? 'Quitar grupo' : 'Todo el grupo'}
                  </Button>
                </div>
                <div className="grid gap-1.5 sm:grid-cols-2">
                  {items.map((p) => (
                    <Checkbox
                      key={p.id}
                      checked={perms.includes(p.id)}
                      onChange={() => togglePerm(p.id)}
                      label={
                        <span className="min-w-0">
                          <span className="block truncate font-mono text-[11px] text-slate-200">{p.id}</span>
                          <span className="block truncate text-[10px] text-slate-500">{p.label}</span>
                        </span>
                      }
                      className="items-start rounded-md border border-slate-800/70 px-2 py-1.5 hover:border-slate-700"
                    />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </Modal>
      {viewport}
    </>
  );
}
