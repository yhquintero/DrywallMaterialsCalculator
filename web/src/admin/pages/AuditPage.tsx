import React from 'react';
import { AlertTriangle, Download, FileDown, Info, ScrollText, Search, ShieldAlert, XCircle } from 'lucide-react';
import { api, downloadAuthenticated, qs } from '../api';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
  EmptyState,
  Input,
  Modal,
  Select,
  Spinner,
  Stat,
  Table,
  Td,
  Th,
  Tr,
  cn,
} from '../components/ui';
import { PageHeader } from '../components/AdminLayout';
import { Can } from '../components/Guard';
import { APPS } from '../permissions';
import type { AuditEntry } from '../types';
import { formatDateTime, formatRelative } from './DashboardPage';

const SEVERITY_META: Record<AuditEntry['severity'], { label: string; tone: 'slate' | 'sky' | 'amber' | 'red'; Icon: typeof Info }> = {
  debug: { label: 'Debug', tone: 'slate', Icon: Info },
  info: { label: 'Info', tone: 'sky', Icon: Info },
  warn: { label: 'Aviso', tone: 'amber', Icon: AlertTriangle },
  critical: { label: 'Crítico', tone: 'red', Icon: ShieldAlert },
};

interface Summary {
  last24h: number;
  critical24h: number;
  failedLogins24h: number;
  bySeverity: { severity: string; c: number }[];
}

const PAGE_SIZE = 50;

export function AuditPage() {
  const { toast, viewport } = useToast();
  const [items, setItems] = React.useState<AuditEntry[]>([]);
  const [total, setTotal] = React.useState(0);
  const [actions, setActions] = React.useState<{ action: string; c: number }[]>([]);
  const [summary, setSummary] = React.useState<Summary | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [page, setPage] = React.useState(0);
  const [detail, setDetail] = React.useState<AuditEntry | null>(null);

  const [filters, setFilters] = React.useState({ action: '', severity: '', appId: '', search: '' });
  const [searchInput, setSearchInput] = React.useState('');

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const params = qs({
        ...filters,
        appId: filters.appId || undefined,
        search: filters.search || undefined,
        limit: PAGE_SIZE,
        offset: page * PAGE_SIZE,
      });
      const [res, sum] = await Promise.all([
        api.get<{ items: AuditEntry[]; total: number; availableActions: { action: string; c: number }[] }>(`/api/audit${params}`),
        api.get<Summary>('/api/audit/summary').catch(() => null),
      ]);
      setItems(res.items);
      setTotal(res.total);
      setActions(res.availableActions);
      if (sum) setSummary(sum);
    } catch (err) {
      toast.error(err, 'No se pudo cargar la auditoría');
    } finally {
      setLoading(false);
    }
  }, [filters, page, toast]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <>
      <PageHeader
        title="Auditoría"
        description="Trazabilidad completa: cada emisión, revocación, cambio de rol, inicio de sesión fallido o exportación de clave queda registrada con actor, IP y detalle."
        actions={
          <Can permission="audit.export">
            <Button
              size="sm"
              variant="secondary"
              icon={<FileDown className="h-3.5 w-3.5" />}
              onClick={async () => {
                try {
                  await downloadAuthenticated(`/api/audit/export.csv${qs({ ...filters, appId: filters.appId || undefined })}`, 'auditoria.csv');
                } catch (err) {
                  toast.error(err);
                }
              }}
            >
              Exportar CSV
            </Button>
          </Can>
        }
      />

      {summary ? (
        <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Stat label="Eventos (24 h)" value={summary.last24h} icon={<ScrollText className="h-4 w-4" />} />
          <Stat label="Críticos (24 h)" value={summary.critical24h} tone={summary.critical24h > 0 ? 'red' : 'green'} icon={<ShieldAlert className="h-4 w-4" />} />
          <Stat label="Accesos fallidos (24 h)" value={summary.failedLogins24h} tone={summary.failedLogins24h > 3 ? 'amber' : 'slate'} icon={<XCircle className="h-4 w-4" />} />
          <Stat
            label="Eventos totales"
            value={total}
            hint={`${pages} página(s) con el filtro actual`}
            icon={<Info className="h-4 w-4" />}
          />
        </div>
      ) : null}

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setPage(0);
                  setFilters((f) => ({ ...f, search: searchInput.trim() }));
                }
              }}
              placeholder="Buscar en acción, detalle, actor o entidad…"
              className="pl-9"
            />
          </div>
          <Select
            value={filters.action}
            onChange={(e) => { setPage(0); setFilters((f) => ({ ...f, action: e.target.value })); }}
            className="w-auto min-w-[210px]"
          >
            <option value="">Todas las acciones</option>
            {actions.map((a) => (
              <option key={a.action} value={a.action}>
                {a.action} ({a.c})
              </option>
            ))}
          </Select>
          <Select
            value={filters.severity}
            onChange={(e) => { setPage(0); setFilters((f) => ({ ...f, severity: e.target.value })); }}
            className="w-auto min-w-[140px]"
          >
            <option value="">Toda severidad</option>
            {Object.entries(SEVERITY_META).map(([k, v]) => (
              <option key={k} value={k}>{v.label}</option>
            ))}
          </Select>
          <Select
            value={filters.appId}
            onChange={(e) => { setPage(0); setFilters((f) => ({ ...f, appId: e.target.value })); }}
            className="w-auto min-w-[170px]"
          >
            <option value="">Ambas apps</option>
            <option value="drywall_calculator">{APPS.drywall_calculator.name}</option>
            <option value="keygen_pro">{APPS.keygen_pro.name}</option>
          </Select>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setPage(0);
              setSearchInput('');
              setFilters({ action: '', severity: '', appId: '', search: '' });
            }}
          >
            Limpiar
          </Button>
        </CardBody>
      </Card>

      <Card>
        {loading ? (
          <Spinner label="Cargando auditoría…" />
        ) : items.length === 0 ? (
          <EmptyState icon={<ScrollText className="h-8 w-8" />} title="Sin eventos con este filtro" />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Severidad</Th>
                  <Th>Acción</Th>
                  <Th>Actor</Th>
                  <Th>Entidad</Th>
                  <Th>App</Th>
                  <Th>IP</Th>
                  <Th className="text-right">Fecha</Th>
                </tr>
              </thead>
              <tbody>
                {items.map((entry) => {
                  const meta = SEVERITY_META[entry.severity] ?? SEVERITY_META.info;
                  return (
                    <Tr key={entry.id} className="cursor-pointer" onClick={() => setDetail(entry)}>
                      <Td>
                        <Badge tone={meta.tone}>
                          <meta.Icon className="h-3 w-3" /> {meta.label}
                        </Badge>
                      </Td>
                      <Td><code className="font-mono text-[11px] text-slate-200">{entry.action}</code></Td>
                      <Td>{entry.actor_name}</Td>
                      <Td>
                        <span className="text-xs text-slate-300">{entry.entity || '—'}</span>
                        {entry.entity_id ? <span className="ml-1 text-[10px] text-slate-600">#{entry.entity_id}</span> : null}
                      </Td>
                      <Td>
                        {entry.app_id ? (
                          <Badge tone={entry.app_id === 'keygen_pro' ? 'sky' : 'green'}>
                            {entry.app_id === 'keygen_pro' ? APPS.keygen_pro.short : APPS.drywall_calculator.short}
                          </Badge>
                        ) : (
                          <span className="text-slate-600">—</span>
                        )}
                      </Td>
                      <Td><span className="font-mono text-[10px] text-slate-500">{entry.ip_address || '—'}</span></Td>
                      <Td className="text-right">
                        <span className="block text-xs text-slate-300">{formatDateTime(entry.created_at)}</span>
                        <span className="block text-[10px] text-slate-600">{formatRelative(entry.created_at)}</span>
                      </Td>
                    </Tr>
                  );
                })}
              </tbody>
            </Table>
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-800 px-4 py-3 text-xs text-slate-400">
              <span>{total} evento(s) · página {page + 1} de {pages}</span>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Anterior</Button>
                <Button size="sm" variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Siguiente</Button>
              </div>
            </div>
          </>
        )}
      </Card>

      <Modal
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        title={detail?.action ?? ''}
        subtitle={detail ? `${detail.actor_name} · ${formatDateTime(detail.created_at)}` : undefined}
        size="md"
        footer={<Button variant="primary" onClick={() => setDetail(null)}>Cerrar</Button>}
      >
        {detail ? (
          <div className="space-y-3">
            <div className="grid gap-2 sm:grid-cols-2">
              <DetailCell label="Severidad" value={SEVERITY_META[detail.severity]?.label ?? detail.severity} />
              <DetailCell label="Entidad" value={`${detail.entity || '—'} ${detail.entity_id ? `#${detail.entity_id}` : ''}`} />
              <DetailCell label="Aplicación" value={detail.app_id ? (APPS[detail.app_id as keyof typeof APPS]?.name ?? detail.app_id) : '—'} />
              <DetailCell label="Actor" value={`${detail.actor_name}${detail.actor_id ? ` (#${detail.actor_id})` : ''}`} />
              <DetailCell label="IP" value={detail.ip_address || '—'} mono />
              <DetailCell label="Fecha" value={new Date(detail.created_at).toLocaleString('es')} />
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-slate-400">User-Agent</p>
              <code className={cn('block break-all rounded-lg border border-slate-800 bg-slate-950 p-2.5 font-mono text-[11px] text-slate-400')}>
                {detail.user_agent || '—'}
              </code>
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-slate-400">Detalle</p>
              <pre className="max-h-64 overflow-auto rounded-lg border border-slate-800 bg-slate-950 p-3 font-mono text-[11px] text-emerald-200">
                {prettyJson(detail.detail)}
              </pre>
            </div>
          </div>
        ) : null}
      </Modal>

      {viewport}
    </>
  );
}

function DetailCell({ label, value, mono = false }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-950/40 p-2.5">
      <p className="text-[10px] uppercase tracking-wider text-slate-500">{label}</p>
      <p className={cn('mt-0.5 break-words text-sm text-slate-200', mono && 'font-mono text-xs')}>{value}</p>
    </div>
  );
}

function prettyJson(raw: string) {
  if (!raw) return '—';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
