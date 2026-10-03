import React from 'react';
import { KeyRound, Plus, Search, ShieldCheck, Trash2, Unlock, UserCog, UserPlus } from 'lucide-react';
import { api } from '../api';
import { useAuth } from '../AuthContext';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
  Checkbox,
  EmptyState,
  Field,
  Input,
  Modal,
  Select,
  Spinner,
  Table,
  Td,
  Th,
  Tr,
  cn,
} from '../components/ui';
import { PageHeader } from '../components/AdminLayout';
import { Can } from '../components/Guard';
import { formatDateTime } from './DashboardPage';
import type { Role, UserRecord } from '../types';

interface RoleOption {
  id: number;
  code: string;
  name: string;
  level: number;
}

export function UsersPage() {
  const { user: currentUser, isAdmin } = useAuth();
  const { toast, viewport } = useToast();

  const [users, setUsers] = React.useState<UserRecord[]>([]);
  const [roles, setRoles] = React.useState<RoleOption[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [search, setSearch] = React.useState('');

  const [createOpen, setCreateOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<UserRecord | null>(null);
  const [tempPassword, setTempPassword] = React.useState<{ username: string; password: string } | null>(null);
  const [overridesFor, setOverridesFor] = React.useState<UserRecord | null>(null);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const [u, r] = await Promise.all([
        api.get<{ items: UserRecord[] }>(`/api/users${search ? `?search=${encodeURIComponent(search)}` : ''}`),
        api.get<{ items: RoleOption[] }>('/api/users/roles'),
      ]);
      setUsers(u.items);
      setRoles(r.items);
    } catch (err) {
      toast.error(err, 'No se pudieron cargar los usuarios');
    } finally {
      setLoading(false);
    }
  }, [search, toast]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const resetPassword = async (u: UserRecord) => {
    if (!window.confirm(`¿Generar una contraseña temporal para ${u.username}? Se cerrarán todas sus sesiones.`)) return;
    try {
      const res = await api.post<{ temporaryPassword: string }>(`/api/users/${u.id}/reset-password`);
      setTempPassword({ username: u.username, password: res.temporaryPassword });
      toast.success('Contraseña temporal generada');
    } catch (err) {
      toast.error(err);
    }
  };

  const toggleActive = async (u: UserRecord) => {
    try {
      await api.patch(`/api/users/${u.id}`, { isActive: !u.isActive });
      toast.success(u.isActive ? 'Usuario desactivado' : 'Usuario activado');
      await load();
    } catch (err) {
      toast.error(err);
    }
  };

  const unlock = async (u: UserRecord) => {
    try {
      await api.patch(`/api/users/${u.id}`, { unlock: true });
      toast.success(`${u.username} desbloqueado`);
      await load();
    } catch (err) {
      toast.error(err);
    }
  };

  return (
    <>
      <PageHeader
        title="Usuarios"
        description="Altas, roles, bloqueos y restablecimiento de contraseñas. Los cambios de rol o estado cierran las sesiones activas del usuario."
        actions={
          <Can permission="users.create">
            <Button size="sm" variant="primary" icon={<UserPlus className="h-3.5 w-3.5" />} onClick={() => setCreateOpen(true)}>
              Nuevo usuario
            </Button>
          </Can>
        }
      />

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por usuario, nombre o correo…"
              className="pl-9"
            />
          </div>
          <Badge tone="slate">{users.length} usuario(s)</Badge>
        </CardBody>
      </Card>

      <Card>
        {loading ? (
          <Spinner label="Cargando usuarios…" />
        ) : users.length === 0 ? (
          <EmptyState icon={<UserCog className="h-8 w-8" />} title="Sin usuarios" description="Crea el primero con el botón «Nuevo usuario»." />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Usuario</Th>
                <Th>Rol</Th>
                <Th>Estado</Th>
                <Th>Último acceso</Th>
                <Th className="text-right">Acciones</Th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const locked = Boolean(u.lockedUntil && u.lockedUntil > Date.now());
                const isSelf = u.id === currentUser?.id;
                return (
                  <Tr key={u.id}>
                    <Td>
                      <div className="flex items-center gap-2.5">
                        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-slate-800 text-xs font-bold text-slate-200">
                          {(u.fullName || u.username).slice(0, 2).toUpperCase()}
                        </span>
                        <span className="min-w-0">
                          <span className="block truncate font-medium text-slate-100">
                            {u.fullName || u.username}
                            {isSelf ? <span className="ml-2 text-[10px] text-brand-400">(tú)</span> : null}
                          </span>
                          <span className="block truncate font-mono text-[11px] text-slate-500">@{u.username}</span>
                        </span>
                      </div>
                    </Td>
                    <Td>
                      <Badge tone={u.roleCode === 'ADMIN' ? 'violet' : u.roleCode === 'CLIENT' ? 'slate' : 'sky'}>{u.roleName}</Badge>
                      <span className="mt-0.5 block text-[10px] text-slate-500">
                        nivel {roles.find((r) => r.id === u.roleId)?.level ?? 0}
                      </span>
                    </Td>
                    <Td>
                      <div className="flex flex-wrap gap-1">
                        {!u.isActive ? <Badge tone="slate">Inactivo</Badge> : null}
                        {locked ? <Badge tone="red">Bloqueado</Badge> : null}
                        {u.mustChangePassword ? <Badge tone="amber">Cambiar contraseña</Badge> : null}
                        {u.failedAttempts > 0 && !locked ? <Badge tone="amber">{u.failedAttempts} fallo(s)</Badge> : null}
                        {u.isActive && !locked && !u.mustChangePassword ? <Badge tone="green">Activo</Badge> : null}
                      </div>
                    </Td>
                    <Td>
                      <span className="block text-xs text-slate-300">{formatDateTime(u.lastLoginAt)}</span>
                      <span className="block text-[10px] text-slate-600">{u.lastLoginIp || '—'}</span>
                    </Td>
                    <Td>
                      <div className="flex items-center justify-end gap-1">
                        <Can permission="roles.manage">
                          <Action title="Permisos finos" onClick={() => setOverridesFor(u)}>
                            <ShieldCheck className="h-4 w-4 text-violet-400" />
                          </Action>
                        </Can>
                        <Can permission="users.reset_password">
                          <Action title="Generar contraseña temporal" onClick={() => void resetPassword(u)}>
                            <KeyRound className="h-4 w-4 text-sky-400" />
                          </Action>
                        </Can>
                        <Can permission="users.update">
                          <Action title={locked ? 'Desbloquear cuenta' : 'Editar'} disabled={!locked && isSelf} onClick={() => (locked ? void unlock(u) : setEditing(u))}>
                            {locked ? <Unlock className="h-4 w-4 text-amber-400" /> : <UserCog className="h-4 w-4" />}
                          </Action>
                          <Action title={u.isActive ? 'Desactivar' : 'Activar'} disabled={isSelf} onClick={() => void toggleActive(u)}>
                            <span className={cn('text-xs font-semibold', u.isActive ? 'text-amber-400' : 'text-emerald-400')}>
                              {u.isActive ? 'OFF' : 'ON'}
                            </span>
                          </Action>
                        </Can>
                        <Can permission="users.delete">
                          <Action
                            title="Eliminar"
                            disabled={isSelf || u.roleCode === 'ADMIN'}
                            onClick={async () => {
                              if (!window.confirm(`¿Eliminar a ${u.username}?`)) return;
                              try {
                                await api.delete(`/api/users/${u.id}`);
                                toast.success('Usuario eliminado');
                                await load();
                              } catch (err) {
                                toast.error(err);
                              }
                            }}
                          >
                            <Trash2 className="h-4 w-4 text-rose-500" />
                          </Action>
                        </Can>
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Card>

      {createOpen ? (
        <CreateUserModal
          roles={roles}
          isAdmin={isAdmin}
          onClose={() => setCreateOpen(false)}
          onCreated={(username, password) => {
            setCreateOpen(false);
            void load();
            if (password) setTempPassword({ username, password });
          }}
        />
      ) : null}

      {editing ? (
        <EditUserModal
          user={editing}
          roles={roles}
          isAdmin={isAdmin}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      ) : null}

      {overridesFor ? (
        <PermissionOverridesModal user={overridesFor} onClose={() => setOverridesFor(null)} onSaved={() => void load()} />
      ) : null}

      <Modal
        open={Boolean(tempPassword)}
        onClose={() => setTempPassword(null)}
        title="Contraseña temporal generada"
        subtitle="Cópiala ahora: no se vuelve a mostrar."
        size="sm"
        danger
        footer={<Button variant="primary" onClick={() => setTempPassword(null)}>Entendido</Button>}
      >
        {tempPassword ? (
          <div className="space-y-3">
            <p className="text-sm text-slate-300">
              Usuario <strong className="text-slate-100">{tempPassword.username}</strong>. Deberá cambiarla en su primer
              inicio de sesión y todas sus sesiones anteriores fueron cerradas.
            </p>
            <div className="flex items-center gap-2">
              <code className="flex-1 break-all rounded-lg border border-slate-800 bg-slate-950 p-3 font-mono text-sm text-emerald-300">
                {tempPassword.password}
              </code>
              <Button
                size="sm"
                variant="secondary"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(tempPassword.password);
                    toast.success('Copiada al portapapeles');
                  } catch {
                    toast.warning('El navegador bloqueó el portapapeles');
                  }
                }}
              >
                Copiar
              </Button>
            </div>
          </div>
        ) : null}
      </Modal>

      {viewport}
    </>
  );
}

function Action({
  title,
  onClick,
  children,
  disabled,
}: {
  title: string;
  onClick: () => void;
  children: React.ReactNode;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      disabled={disabled}
      onClick={onClick}
      className="rounded-md p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-white disabled:cursor-not-allowed disabled:opacity-30"
    >
      {children}
    </button>
  );
}

function CreateUserModal({
  roles,
  isAdmin,
  onClose,
  onCreated,
}: {
  roles: RoleOption[];
  isAdmin: boolean;
  onClose: () => void;
  onCreated: (username: string, password?: string) => void;
}) {
  const { toast, viewport } = useToast();
  const [saving, setSaving] = React.useState(false);
  const [autoPassword, setAutoPassword] = React.useState(true);
  const [form, setForm] = React.useState({
    username: '',
    fullName: '',
    email: '',
    roleCode: 'OPERATOR',
    password: '',
    isActive: true,
  });

  const availableRoles = roles.filter((r) => r.code !== 'ADMIN' || isAdmin);

  const submit = async () => {
    setSaving(true);
    try {
      const res = await api.post<{ id: number; username: string; temporaryPassword?: string }>('/api/users', {
        ...form,
        email: form.email.trim() || undefined,
        password: autoPassword ? undefined : form.password,
      });
      toast.success(`Usuario ${res.username} creado`);
      onCreated(res.username, res.temporaryPassword);
    } catch (err) {
      toast.error(err, 'No se pudo crear el usuario');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title="Nuevo usuario"
        subtitle="El rol define los permisos; luego puedes conceder o denegar permisos concretos."
        footer={
          <>
            <Button variant="ghost" onClick={onClose}>Cancelar</Button>
            <Button variant="primary" loading={saving} icon={<Plus className="h-4 w-4" />} onClick={() => void submit()}>
              Crear usuario
            </Button>
          </>
        }
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Usuario" required hint="Letras, números, punto, guion y guion bajo">
            <Input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} maxLength={40} autoFocus />
          </Field>
          <Field label="Nombre completo">
            <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} maxLength={120} />
          </Field>
          <Field label="Correo">
            <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} maxLength={160} />
          </Field>
          <Field label="Rol" required>
            <Select value={form.roleCode} onChange={(e) => setForm({ ...form, roleCode: e.target.value })}>
              {availableRoles.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.name} ({r.code})
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <div className="mt-4 space-y-3 rounded-lg border border-slate-800 bg-slate-950/40 p-3">
          <Checkbox
            checked={autoPassword}
            onChange={(e) => setAutoPassword(e.target.checked)}
            label="Generar contraseña temporal fuerte (recomendado)"
          />
          {!autoPassword ? (
            <Field label="Contraseña" required hint="Mínimo 10 caracteres con mayúscula, minúscula, número y símbolo">
              <Input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} maxLength={200} />
            </Field>
          ) : null}
          <Checkbox
            checked={form.isActive}
            onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
            label="Cuenta activa desde el alta"
          />
        </div>
      </Modal>
      {viewport}
    </>
  );
}

function EditUserModal({
  user,
  roles,
  isAdmin,
  onClose,
  onSaved,
}: {
  user: UserRecord;
  roles: RoleOption[];
  isAdmin: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast, viewport } = useToast();
  const [saving, setSaving] = React.useState(false);
  const [form, setForm] = React.useState({
    fullName: user.fullName,
    email: user.email ?? '',
    roleCode: user.roleCode,
    isActive: user.isActive,
  });
  const availableRoles = roles.filter((r) => isAdmin || r.code !== 'ADMIN');

  const submit = async () => {
    setSaving(true);
    try {
      await api.patch(`/api/users/${user.id}`, { ...form, email: form.email.trim() || null });
      toast.success('Usuario actualizado');
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
        title={`Editar @${user.username}`}
        subtitle="Cambiar el rol o desactivar la cuenta cierra todas sus sesiones activas."
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={onClose}>Cancelar</Button>
            <Button variant="primary" loading={saving} onClick={() => void submit()}>Guardar cambios</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field label="Nombre completo">
            <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} maxLength={120} />
          </Field>
          <Field label="Correo">
            <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} maxLength={160} />
          </Field>
          <Field label="Rol">
            <Select value={form.roleCode} onChange={(e) => setForm({ ...form, roleCode: e.target.value })}>
              {availableRoles.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.name} ({r.code})
                </option>
              ))}
            </Select>
          </Field>
          <Checkbox checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} label="Cuenta activa" />
        </div>
      </Modal>
      {viewport}
    </>
  );
}

function PermissionOverridesModal({
  user,
  onClose,
  onSaved,
}: {
  user: UserRecord;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast, viewport } = useToast();
  const [saving, setSaving] = React.useState(false);
  const [overrides, setOverrides] = React.useState<Record<string, 'allow' | 'deny'>>({});
  const [detail, setDetail] = React.useState<{ permissions: string[]; overrides: { code: string; effect: string }[] } | null>(null);

  React.useEffect(() => {
    api
      .get<{ permissions: string[]; overrides: { code: string; effect: string }[] }>(`/api/users/${user.id}`)
      .then((res) => {
        setDetail(res);
        setOverrides(Object.fromEntries(res.overrides.map((o) => [o.code, o.effect as 'allow' | 'deny'])));
      })
      .catch((err) => toast.error(err));
  }, [user.id, toast]);

  const submit = async () => {
    setSaving(true);
    try {
      await api.put(`/api/users/${user.id}/permissions`, {
        overrides: Object.entries(overrides).map(([code, effect]) => ({ code, effect })),
      });
      toast.success('Permisos actualizados; se cerraron las sesiones del usuario');
      onSaved();
      onClose();
    } catch (err) {
      toast.error(err);
    } finally {
      setSaving(false);
    }
  };

  const grouped = React.useMemo(() => {
    const groups: Record<string, string[]> = {};
    for (const code of detail?.permissions ?? []) {
      const g = code.split('.')[0];
      groups[g] = groups[g] || [];
      groups[g].push(code);
    }
    return groups;
  }, [detail]);

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title={`Permisos de @${user.username}`}
        subtitle="Los permisos heredados del rol se muestran marcados. Aquí puedes conceder (allow) o denegar (deny) permisos concretos."
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={onClose}>Cancelar</Button>
            <Button variant="primary" loading={saving} onClick={() => void submit()}>Guardar permisos</Button>
          </>
        }
      >
        {!detail ? (
          <Spinner />
        ) : (
          <div className="space-y-4">
            <div className="rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-400">
              Rol <strong className="text-slate-200">{user.roleName}</strong> · {detail.permissions.length} permiso(s) efectivo(s)
            </div>
            {Object.entries(grouped).map(([group, codes]) => (
              <div key={group}>
                <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500">{group}</p>
                <div className="grid gap-1.5 sm:grid-cols-2">
                  {codes.map((code) => (
                    <div key={code} className="flex items-center justify-between gap-2 rounded-lg border border-slate-800 bg-slate-950/40 px-2.5 py-1.5">
                      <code className="truncate font-mono text-[11px] text-slate-300">{code}</code>
                      <div className="flex shrink-0 gap-1">
                        {(['allow', 'deny'] as const).map((effect) => (
                          <button
                            key={effect}
                            type="button"
                            onClick={() =>
                              setOverrides((prev) => {
                                const next = { ...prev };
                                if (next[code] === effect) delete next[code];
                                else next[code] = effect;
                                return next;
                              })
                            }
                            className={cn(
                              'rounded px-1.5 py-0.5 text-[10px] font-medium transition',
                              overrides[code] === effect
                                ? effect === 'allow'
                                  ? 'bg-emerald-600 text-white'
                                  : 'bg-rose-600 text-white'
                                : 'bg-slate-800 text-slate-400 hover:text-slate-200'
                            )}
                          >
                            {effect === 'allow' ? '+' : '−'}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </Modal>
      {viewport}
    </>
  );
}

/** Reexportado para la pantalla de roles. */
export type { Role };
