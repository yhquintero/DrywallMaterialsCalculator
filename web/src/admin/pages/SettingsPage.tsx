import React from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Database,
  Globe,
  KeyRound,
  Lock,
  LogOut,
  MonitorSmartphone,
  Save,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import { api } from '../api';
import { useAuth } from '../AuthContext';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  Field,
  Input,
  KeyValue,
  cn,
} from '../components/ui';
import { PageHeader } from '../components/AdminLayout';
import { Can } from '../components/Guard';
import { formatDateTime } from './DashboardPage';

interface SystemInfo {
  env: string;
  driver: string;
  dbFile: string;
  https: boolean;
  secureCookies: boolean;
  counts: Record<string, number>;
  policy: {
    accessTtlMinutes: number;
    refreshTtlDays: number;
    maxFailedLogins: number;
    lockMinutes: number;
    rsaKeySize: number;
  };
  time: string;
}

interface ActiveSession {
  id: number;
  ip_address: string;
  user_agent: string;
  issued_at: number;
  last_used_at: number | null;
  expires_at: number;
}

export function SettingsPage() {
  const { user, session, secureConnection, hasPermission, logout } = useAuth();
  const { toast, viewport } = useToast();
  const [params] = useSearchParams();
  const [system, setSystem] = React.useState<SystemInfo | null>(null);
  const [sessions, setSessions] = React.useState<ActiveSession[]>([]);
  const [passwordForm, setPasswordForm] = React.useState({ currentPassword: '', newPassword: '', repeat: '' });
  const [savingPassword, setSavingPassword] = React.useState(false);

  const mustChange = params.get('cambiar') === '1' || Boolean(user?.mustChangePassword);

  React.useEffect(() => {
    api
      .get<{ user: typeof user; permissions: string[]; activeSessions: ActiveSession[] }>('/api/auth/me')
      .then((res) => setSessions(res.activeSessions ?? []))
      .catch(() => undefined);
    if (hasPermission('system.health')) {
      api
        .get<SystemInfo>('/api/system')
        .then(setSystem)
        .catch(() => undefined);
    }
  }, [hasPermission]);

  const changePassword = async () => {
    if (passwordForm.newPassword !== passwordForm.repeat) {
      toast.warning('Las contraseñas nuevas no coinciden');
      return;
    }
    setSavingPassword(true);
    try {
      await api.post('/api/auth/change-password', {
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      });
      toast.success('Contraseña actualizada. Vuelve a iniciar sesión.');
      setPasswordForm({ currentPassword: '', newPassword: '', repeat: '' });
      setTimeout(() => void logout(), 1200);
    } catch (err) {
      toast.error(err, 'No se pudo cambiar la contraseña');
    } finally {
      setSavingPassword(false);
    }
  };

  const revokeAll = async () => {
    if (!window.confirm('¿Cerrar todas tus sesiones activas en otros dispositivos?')) return;
    try {
      const res = await api.post<{ revoked: number }>('/api/auth/revoke-sessions');
      toast.success(`${res.revoked} sesión(es) cerrada(s)`);
      setSessions([]);
    } catch (err) {
      toast.error(err);
    }
  };

  return (
    <>
      <PageHeader
        title="Configuración y seguridad"
        description="Tu cuenta, la política de sesiones del sistema y el estado del servicio."
      />

      {mustChange ? (
        <Card className="mb-4 border-amber-700/50 bg-amber-950/30">
          <CardBody className="flex items-start gap-3 text-sm text-amber-100">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-400" />
            <div>
              <p className="font-semibold">Debes cambiar tu contraseña</p>
              <p className="mt-0.5 text-xs text-amber-200/80">
                Estás usando una contraseña temporal generada por el sistema. Cámbiala ahora para poder seguir operando con normalidad.
              </p>
            </div>
          </CardBody>
        </Card>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-2">
        {/* Cuenta */}
        <Card>
          <CardHeader title="Tu cuenta" icon={<ShieldCheck className="h-4 w-4" />} subtitle={`Sesión iniciada como @${user?.username}`} />
          <CardBody>
            <dl className="mb-4 rounded-lg border border-slate-800 bg-slate-950/40 p-3">
              <KeyValue label="Nombre" value={user?.fullName || '—'} />
              <KeyValue label="Usuario" value={<span className="font-mono text-xs">{user?.username}</span>} />
              <KeyValue label="Correo" value={user?.email || '—'} />
              <KeyValue label="Rol" value={<Badge tone={user?.roleCode === 'ADMIN' ? 'violet' : 'sky'}>{user?.roleName} ({user?.roleCode})</Badge>} />
              <KeyValue label="Permisos efectivos" value={`${user?.permissions.includes('*') ? 'todos (*)' : user?.permissions.length}`} />
              <KeyValue label="Último acceso" value={formatDateTime(user?.lastLoginAt ?? null)} />
              <KeyValue label="Alta" value={formatDateTime(user?.createdAt ?? null)} />
            </dl>

            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" icon={<LogOut className="h-4 w-4" />} onClick={() => void revokeAll()}>
                Cerrar todas mis sesiones
              </Button>
            </div>
          </CardBody>
        </Card>

        {/* Contraseña */}
        <Card>
          <CardHeader title="Cambiar contraseña" icon={<KeyRound className="h-4 w-4" />} subtitle="Al guardar se cerrarán todas tus sesiones activas" />
          <CardBody className="space-y-4">
            <Field label="Contraseña actual" required>
              <Input
                type="password"
                autoComplete="current-password"
                value={passwordForm.currentPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
                maxLength={200}
              />
            </Field>
            <Field label="Contraseña nueva" required hint="Mínimo 10 caracteres con mayúscula, minúscula, número y símbolo">
              <Input
                type="password"
                autoComplete="new-password"
                value={passwordForm.newPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                maxLength={200}
              />
            </Field>
            <Field label="Repite la contraseña nueva" required>
              <Input
                type="password"
                autoComplete="new-password"
                value={passwordForm.repeat}
                onChange={(e) => setPasswordForm({ ...passwordForm, repeat: e.target.value })}
                maxLength={200}
              />
            </Field>
            <Button variant="primary" icon={<Save className="h-4 w-4" />} loading={savingPassword} onClick={() => void changePassword()}>
              Actualizar contraseña
            </Button>
          </CardBody>
        </Card>

        {/* Estado HTTPS / sesión */}
        <Card>
          <CardHeader title="Estado de la conexión" icon={secureConnection ? <Lock className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />} />
          <CardBody>
            <div
              className={cn(
                'mb-4 flex items-start gap-3 rounded-lg border p-3',
                secureConnection ? 'border-emerald-700/50 bg-emerald-950/30 text-emerald-100' : 'border-amber-700/50 bg-amber-950/30 text-amber-100'
              )}
            >
              {secureConnection ? <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-400" /> : <XCircle className="mt-0.5 h-5 w-5 shrink-0 text-amber-400" />}
              <div className="text-xs">
                <p className="font-semibold">{secureConnection ? 'Sirviéndose por HTTPS' : 'Sirviéndose por HTTP (sin cifrar)'}</p>
                <p className="mt-1 leading-relaxed opacity-85">
                  {secureConnection
                    ? 'HSTS activo (max-age 1 año, includeSubDomains, preload). Las credenciales y las llaves viajan cifradas.'
                    : 'En local es aceptable para pruebas; en producción la consola redirige todo el tráfico a HTTPS y exige certificado TLS.'}
                </p>
              </div>
            </div>

            <dl className="rounded-lg border border-slate-800 bg-slate-950/40 p-3">
              <KeyValue label="Duración del access token" value={`${session?.accessTtlMinutes ?? '—'} min`} />
              <KeyValue label="Refresh token" value={`${session?.refreshTtlDays ?? '—'} días · cookie httpOnly${session?.secureCookies ? ' + Secure' : ''}`} />
              <KeyValue label="Bloqueo por fallos" value={`${session?.maxFailedLogins ?? '—'} intentos / ${session?.lockMinutes ?? '—'} min`} />
              <KeyValue label="Origen actual" value={<span className="font-mono text-xs">{typeof window !== 'undefined' ? window.location.origin : '—'}</span>} />
            </dl>
          </CardBody>
        </Card>

        {/* Sesiones activas */}
        <Card>
          <CardHeader title="Sesiones activas" icon={<MonitorSmartphone className="h-4 w-4" />} subtitle={`${sessions.length} dispositivo(s) con sesión abierta`} />
          <CardBody className="p-0">
            {sessions.length === 0 ? (
              <div className="px-5 py-8 text-center text-xs text-slate-500">Sin sesiones registradas.</div>
            ) : (
              <ul className="divide-y divide-slate-800/70">
                {sessions.map((s) => (
                  <li key={s.id} className="px-5 py-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-[11px] text-slate-300">{s.ip_address || 'IP desconocida'}</span>
                      <Badge tone="green">activa</Badge>
                    </div>
                    <p className="mt-0.5 truncate text-[10px] text-slate-500" title={s.user_agent}>{s.user_agent || '—'}</p>
                    <p className="mt-0.5 text-[10px] text-slate-600">
                      abierta {formatDateTime(s.issued_at)} · expira {formatDateTime(s.expires_at)}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>

      {/* Estado del servicio (solo ADMIN/AUDITOR) */}
      <Can permission="system.health">
        <Card className="mt-4">
          <CardHeader
            title="Estado del servicio"
            icon={<Activity className="h-4 w-4" />}
            subtitle={system ? `Entorno ${system.env} · driver ${system.driver}` : 'Consultando…'}
            actions={
              <Button size="sm" variant="secondary" onClick={() => api.get<SystemInfo>('/api/system').then(setSystem).catch(() => undefined)}>
                Refrescar
              </Button>
            }
          />
          <CardBody>
            {!system ? (
              <p className="text-xs text-slate-500">Sin datos.</p>
            ) : (
              <>
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                  {Object.entries(system.counts).map(([key, value]) => (
                    <div key={key} className="rounded-lg border border-slate-800 bg-slate-950/40 p-3">
                      <p className="text-[10px] uppercase tracking-wider text-slate-500">{labelFor(key)}</p>
                      <p className="mt-0.5 text-lg font-bold tabular-nums text-slate-100">{value}</p>
                    </div>
                  ))}
                </div>
                <dl className="mt-4 grid gap-x-6 rounded-lg border border-slate-800 bg-slate-950/40 p-3 sm:grid-cols-2">
                  <KeyValue label="Base de datos" value={<span className="font-mono text-xs">{system.dbFile}</span>} mono />
                  <KeyValue label="Driver SQLite" value={<Badge tone="sky"><Database className="h-3 w-3" /> {system.driver}</Badge>} />
                  <KeyValue label="TLS en el servidor" value={system.https ? <Badge tone="green">activado</Badge> : <Badge tone="amber">desactivado</Badge>} />
                  <KeyValue label="Cookies Secure" value={system.secureCookies ? <Badge tone="green">sí</Badge> : <Badge tone="amber">no</Badge>} />
                  <KeyValue label="Tamaño de clave RSA" value={`${system.policy.rsaKeySize} bits`} />
                  <KeyValue label="Hora del servidor" value={<span className="font-mono text-xs">{system.time}</span>} mono />
                </dl>
                <p className="mt-3 flex items-center gap-2 text-[11px] text-slate-500">
                  <Globe className="h-3.5 w-3.5" />
                  Los endpoints públicos que consumen las apps están en <code className="font-mono text-emerald-300">/.well-known/</code> y{' '}
                  <code className="font-mono text-emerald-300">/api/public/</code>.
                </p>
              </>
            )}
          </CardBody>
        </Card>
      </Can>

      {viewport}
    </>
  );
}

function labelFor(key: string) {
  const map: Record<string, string> = {
    users: 'Usuarios',
    activeUsers: 'Usuarios activos',
    roles: 'Roles',
    permissions: 'Permisos',
    licenses: 'Licencias',
    signingKeys: 'Claves de firma',
    devices: 'Dispositivos',
    blacklist: 'Lista negra',
    auditEntries: 'Eventos auditados',
    activeSessions: 'Sesiones activas',
  };
  return map[key] ?? key;
}
