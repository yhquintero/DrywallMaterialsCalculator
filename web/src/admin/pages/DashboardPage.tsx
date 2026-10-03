import React from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  BadgeCheck,
  Banknote,
  Ban,
  Clock,
  FileKey2,
  KeyRound,
  RefreshCw,
  ScrollText,
  Smartphone,
  TrendingUp,
} from 'lucide-react';
import { api, qs } from '../api';
import { useAuth } from '../AuthContext';
import { Badge, Button, Card, CardBody, CardHeader, EmptyState, Spinner, Stat, cn } from '../components/ui';
import { PageHeader } from '../components/AdminLayout';
import { APPS } from '../permissions';
import type { AppId, AuditEntry, License, LicenseStats } from '../types';

const APP_FILTERS: { id: AppId | 'all'; label: string }[] = [
  { id: 'all', label: 'Ambas apps' },
  { id: 'drywall_calculator', label: APPS.drywall_calculator.short },
  { id: 'keygen_pro', label: APPS.keygen_pro.short },
];

export function DashboardPage() {
  const { user } = useAuth();
  const [appId, setAppId] = React.useState<AppId | 'all'>('all');
  const [stats, setStats] = React.useState<LicenseStats | null>(null);
  const [expiring, setExpiring] = React.useState<License[]>([]);
  const [pending, setPending] = React.useState<License[]>([]);
  const [audit, setAudit] = React.useState<AuditEntry[]>([]);
  const [loading, setLoading] = React.useState(true);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const filter = appId === 'all' ? {} : { appId };
      const [s, exp, pend, aud] = await Promise.all([
        api.get<LicenseStats>(`/api/licenses/stats${qs(filter)}`),
        api.get<{ items: License[] }>(`/api/licenses${qs({ ...filter, expiringInDays: 7, limit: 8 })}`),
        api.get<{ items: License[] }>(`/api/licenses${qs({ ...filter, paid: false, limit: 8 })}`),
        api
          .get<{ items: AuditEntry[] }>('/api/audit?limit=8')
          .catch(() => ({ items: [] as AuditEntry[] })),
      ]);
      setStats(s);
      setExpiring(exp.items);
      setPending(pend.items);
      setAudit(aud.items);
    } catch {
      /* los errores se muestran con el estado vacío */
    } finally {
      setLoading(false);
    }
  }, [appId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const maxMonthly = Math.max(1, ...(stats?.monthly.map((m) => Number(m.revenue) || 0) ?? [1]));

  return (
    <>
      <PageHeader
        title={`Panel de control`}
        description={`Hola ${user?.fullName || user?.username}. Estado global de las licencias emitidas para las dos apps.`}
        actions={
          <>
            <div className="flex rounded-lg border border-slate-800 bg-slate-900 p-0.5">
              {APP_FILTERS.map((f) => (
                <button
                  key={f.id}
                  type="button"
                  onClick={() => setAppId(f.id)}
                  className={cn(
                    'rounded-md px-2.5 py-1 text-xs font-medium transition',
                    appId === f.id ? 'bg-brand-600 text-white' : 'text-slate-400 hover:text-slate-100'
                  )}
                >
                  {f.label}
                </button>
              ))}
            </div>
            <Button size="sm" variant="secondary" icon={<RefreshCw className={cn('h-3.5 w-3.5', loading && 'animate-spin')} />} onClick={() => void load()}>
              Actualizar
            </Button>
          </>
        }
      />

      {loading && !stats ? (
        <Spinner label="Cargando métricas…" />
      ) : !stats ? (
        <Card>
          <EmptyState
            icon={<AlertTriangle className="h-8 w-8" />}
            title="No se pudieron cargar las métricas"
            description="Comprueba que el servidor de la consola esté en marcha."
          />
        </Card>
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Stat label="Licencias activas" value={stats.active} hint={`${stats.total} emitidas en total`} icon={<BadgeCheck className="h-4 w-4" />} tone="green" />
            <Stat label="Ingresos cobrados" value={formatMoney(stats.revenue)} hint={`${stats.pending} pendiente(s) de cobro`} icon={<Banknote className="h-4 w-4" />} tone="sky" />
            <Stat label="Vencen en 7 días" value={stats.expiring7} hint={`${stats.expiring30} en los próximos 30 días`} icon={<Clock className="h-4 w-4" />} tone="amber" />
            <Stat label="Revocadas" value={stats.revoked} hint={`${stats.blockedDevices} dispositivo(s) bloqueado(s)`} icon={<Ban className="h-4 w-4" />} tone="red" />
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-3">
            {/* Por app */}
            <Card className="lg:col-span-1">
              <CardHeader title="Licencias por aplicación" icon={<FileKey2 className="h-4 w-4" />} />
              <CardBody className="space-y-3">
                {stats.byApp.map((app) => (
                  <div key={app.appId} className="rounded-lg border border-slate-800 bg-slate-950/40 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="flex items-center gap-2 text-sm font-medium text-slate-200">
                        {app.appId === 'keygen_pro' ? (
                          <KeyRound className="h-4 w-4 text-sky-400" />
                        ) : (
                          <FileKey2 className="h-4 w-4 text-brand-400" />
                        )}
                        {app.appName}
                      </p>
                      <Badge tone={app.active > 0 ? 'green' : 'slate'}>{app.active} activas</Badge>
                    </div>
                    <dl className="mt-2 grid grid-cols-3 gap-2 text-center">
                      <Metric label="Total" value={String(app.total)} />
                      <Metric label="Cobradas" value={String(app.paid)} />
                      <Metric label="Ingresos" value={formatMoney(app.revenue)} />
                    </dl>
                  </div>
                ))}
                <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/40 px-3 py-2 text-xs text-slate-400">
                  <span className="flex items-center gap-1.5">
                    <Smartphone className="h-3.5 w-3.5" /> {stats.devices} dispositivos registrados
                  </span>
                  <Link to="/admin/devices" className="font-medium text-brand-400 hover:text-brand-300">
                    Ver
                  </Link>
                </div>
              </CardBody>
            </Card>

            {/* Ingresos por mes */}
            <Card className="lg:col-span-2">
              <CardHeader
                title="Ingresos y emisiones (últimos 12 meses)"
                icon={<TrendingUp className="h-4 w-4" />}
                subtitle="Cobros confirmados por mes natural"
              />
              <CardBody>
                {stats.monthly.length === 0 ? (
                  <EmptyState title="Sin datos todavía" description="Emite la primera licencia para ver la evolución." />
                ) : (
                  <div className="flex h-52 items-end gap-2">
                    {stats.monthly.slice(-12).map((m) => {
                      const revenue = Number(m.revenue) || 0;
                      const height = Math.max(4, Math.round((revenue / maxMonthly) * 100));
                      return (
                        <div key={m.month} className="group flex flex-1 flex-col items-center gap-1.5">
                          <span className="text-[10px] font-medium text-slate-500 opacity-0 transition group-hover:opacity-100">
                            {formatMoney(revenue)}
                          </span>
                          <div
                            className="w-full rounded-t-md bg-gradient-to-t from-brand-700 to-brand-400 transition group-hover:from-brand-600 group-hover:to-brand-300"
                            style={{ height: `${height}%` }}
                            title={`${m.month}: ${formatMoney(revenue)} · ${m.total} licencias`}
                          />
                          <span className="text-[10px] text-slate-500">{m.month.slice(5)}/{m.month.slice(2, 4)}</span>
                        </div>
                      );
                    })}
                  </div>
                )}

                {stats.byPlan.length > 0 ? (
                  <div className="mt-5 border-t border-slate-800 pt-4">
                    <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-500">Planes más emitidos</p>
                    <div className="flex flex-wrap gap-2">
                      {stats.byPlan.slice(0, 6).map((p) => (
                        <span key={p.plan_label} className="rounded-lg border border-slate-800 bg-slate-950/60 px-2.5 py-1.5 text-xs">
                          <span className="text-slate-200">{p.plan_label}</span>
                          <span className="ml-2 text-slate-500">{p.total}</span>
                        </span>
                      ))}
                    </div>
                  </div>
                ) : null}
              </CardBody>
            </Card>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <LicenseListCard
              title="Vencen en 7 días"
              icon={<Clock className="h-4 w-4" />}
              items={expiring}
              emptyText="Ninguna licencia vence esta semana"
              tone="amber"
            />
            <LicenseListCard
              title="Pendientes de cobro"
              icon={<Banknote className="h-4 w-4" />}
              items={pending}
              emptyText="No hay cobros pendientes"
              tone="sky"
            />
          </div>

          {audit.length > 0 ? (
            <Card className="mt-4">
              <CardHeader
                title="Actividad reciente"
                icon={<ScrollText className="h-4 w-4" />}
                actions={
                  <Link to="/admin/audit">
                    <Button size="sm" variant="ghost">Ver auditoría completa</Button>
                  </Link>
                }
              />
              <CardBody className="p-0">
                <ul className="divide-y divide-slate-800/70">
                  {audit.map((entry) => (
                    <li key={entry.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-5 py-2.5 text-xs">
                      <SeverityDot severity={entry.severity} />
                      <code className="font-mono text-slate-300">{entry.action}</code>
                      <span className="text-slate-500">{entry.actor_name}</span>
                      {entry.entity_id ? <span className="text-slate-600">#{entry.entity_id}</span> : null}
                      <span className="ml-auto text-slate-600">{formatRelative(entry.created_at)}</span>
                    </li>
                  ))}
                </ul>
              </CardBody>
            </Card>
          ) : null}
        </>
      )}
    </>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-slate-900/70 px-2 py-1.5">
      <dt className="text-[10px] uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="text-sm font-semibold tabular-nums text-slate-100">{value}</dd>
    </div>
  );
}

function LicenseListCard({
  title,
  icon,
  items,
  emptyText,
  tone,
}: {
  title: string;
  icon: React.ReactNode;
  items: License[];
  emptyText: string;
  tone: 'amber' | 'sky';
}) {
  return (
    <Card>
      <CardHeader title={title} icon={icon} subtitle={`${items.length} registro(s)`} />
      <CardBody className="p-0">
        {items.length === 0 ? (
          <EmptyState title={emptyText} />
        ) : (
          <ul className="divide-y divide-slate-800/70">
            {items.map((l) => (
              <li key={l.id} className="flex items-center gap-3 px-5 py-2.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-200">{l.userName}</p>
                  <p className="truncate text-[11px] text-slate-500">
                    {l.appName} · {l.planLabel} · {l.licenseKey}
                  </p>
                </div>
                <div className="shrink-0 text-right">
                  <p className={cn('text-xs font-semibold tabular-nums', tone === 'amber' ? 'text-amber-300' : 'text-sky-300')}>
                    {tone === 'amber' ? `${l.daysRemaining} d` : formatMoney(l.price)}
                  </p>
                  <p className="text-[10px] text-slate-500">{new Date(l.expiryDate).toLocaleDateString('es')}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

function SeverityDot({ severity }: { severity: AuditEntry['severity'] }) {
  const colors: Record<string, string> = {
    debug: 'bg-slate-500',
    info: 'bg-sky-400',
    warn: 'bg-amber-400',
    critical: 'bg-rose-500',
  };
  return <span className={cn('h-2 w-2 shrink-0 rounded-full', colors[severity] ?? 'bg-slate-500')} title={severity} />;
}

export function formatMoney(value: number, currency = 'USD') {
  const symbols: Record<string, string> = { USD: '$', EUR: '€', MLC: 'MLC ', CAD: 'C$', MEX: '$', ZELLE: 'Z$', CLA: 'CL$' };
  const n = Number(value) || 0;
  return `${symbols[currency] ?? ''}${n.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function formatRelative(ts: number) {
  const diff = Date.now() - ts;
  const min = Math.round(diff / 60000);
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `hace ${h} h`;
  const d = Math.round(h / 24);
  if (d < 30) return `hace ${d} d`;
  return new Date(ts).toLocaleDateString('es');
}

export function formatDateTime(ts: number | null | undefined) {
  if (!ts) return '—';
  return new Date(ts).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' });
}
