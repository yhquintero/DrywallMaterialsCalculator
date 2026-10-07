import React from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Ban,
  Download,
  Eye,
  FileDown,
  KeyRound,
  MessageCircle,
  RefreshCw,
  RotateCcw,
  Search,
  Trash2,
  Upload,
  Wallet,
  Wand2,
} from 'lucide-react';
import { api, downloadAuthenticated, qs } from '../api';
import { useAuth } from '../AuthContext';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
  EmptyState,
  Input,
  KeyValue,
  Modal,
  Select,
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
import { LicenseModal, buildRenewalMessage } from '../components/LicenseModal';
import { APPS, STATUS_META } from '../permissions';
import type { AppId, License } from '../types';
import { formatDateTime, formatMoney } from './DashboardPage';

const STATUS_OPTIONS = [
  { value: '', label: 'Todos los estados' },
  { value: 'active', label: 'Activas' },
  { value: 'expired', label: 'Vencidas' },
  { value: 'paid', label: 'Cobradas' },
  { value: 'issued', label: 'Pendientes de cobro' },
  { value: 'revoked', label: 'Revocadas' },
  { value: 'blocked', label: 'Bloqueadas' },
];

const PAGE_SIZE = 25;

export function LicensesPage() {
  const { hasPermission } = useAuth();
  const { toast, viewport } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const [items, setItems] = React.useState<License[]>([]);
  const [total, setTotal] = React.useState(0);
  const [loading, setLoading] = React.useState(true);
  const [page, setPage] = React.useState(0);

  const [appId, setAppId] = React.useState<AppId | ''>('');
  const [status, setStatus] = React.useState('');
  const [search, setSearch] = React.useState('');
  const [searchInput, setSearchInput] = React.useState('');
  const [expiringSoon, setExpiringSoon] = React.useState(false);

  const [modal, setModal] = React.useState<{ open: boolean; mode: 'issue' | 'renew' | 'view'; license?: License | null }>({
    open: false,
    mode: 'issue',
  });

  // Abre automáticamente el Keygen cuando se entra por `/admin/licenses?emitir=1`.
  React.useEffect(() => {
    const shouldIssue = searchParams.get('emitir') === '1' || searchParams.get('new') === '1';
    const requestedApp = searchParams.get('app');
    if (requestedApp === 'drywall_calculator' || requestedApp === 'keygen_pro') {
      setAppId(requestedApp);
    }
    if (shouldIssue && hasPermission('licenses.issue')) {
      setModal({ open: true, mode: 'issue' });
      const next = new URLSearchParams(searchParams);
      next.delete('emitir');
      next.delete('new');
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams, hasPermission]);
  const [revoking, setRevoking] = React.useState<License | null>(null);
  const [revokeReason, setRevokeReason] = React.useState('');
  const [detail, setDetail] = React.useState<License | null>(null);
  const [importOpen, setImportOpen] = React.useState(false);
  const [importText, setImportText] = React.useState('');
  const [importing, setImporting] = React.useState(false);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: License[]; total: number }>(
        `/api/licenses${qs({
          appId: appId || undefined,
          status: status || undefined,
          search: search || undefined,
          expiringInDays: expiringSoon ? 15 : undefined,
          limit: PAGE_SIZE,
          offset: page * PAGE_SIZE,
        })}`
      );
      setItems(res.items);
      setTotal(res.total);
    } catch (err) {
      toast.error(err, 'No se pudieron cargar las licencias');
    } finally {
      setLoading(false);
    }
  }, [appId, status, search, expiringSoon, page, toast]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const act = async (fn: () => Promise<unknown>, okMessage: string) => {
    try {
      await fn();
      toast.success(okMessage);
      await load();
    } catch (err) {
      toast.error(err, 'La operación falló');
    }
  };

  const doRevoke = async () => {
    if (!revoking) return;
    await act(
      () => api.post(`/api/licenses/${revoking.id}/revoke`, { reason: revokeReason }),
      'Licencia revocada y añadida a la lista negra'
    );
    setRevoking(null);
    setRevokeReason('');
  };

  const runImport = async () => {
    setImporting(true);
    try {
      const parsed = JSON.parse(importText) as unknown;
      const entries = Array.isArray(parsed)
        ? parsed
        : ((parsed as { licenses?: unknown[]; items?: unknown[] }).licenses ??
          (parsed as { licenses?: unknown[]; items?: unknown[] }).items ??
          [parsed]);
      const res = await api.post<{ imported: number; updated: number; invalidSignature: number; skipped: number }>(
        '/api/licenses/import',
        { licenses: entries }
      );
      toast.success(`Importación: ${res.imported} nuevas, ${res.updated} actualizadas, ${res.invalidSignature} con firma inválida`);
      setImportOpen(false);
      setImportText('');
      await load();
    } catch (err) {
      toast.error(err, 'Importación fallida: revisa que sea JSON válido');
    } finally {
      setImporting(false);
    }
  };

  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <>
      <PageHeader
        title="Licencias / Keygen"
        description="Emite, renueva, cobra y revoca las licencias firmadas de las dos apps. Cada emisión queda registrada en la auditoría."
        actions={
          <>
            <Can permission="licenses.import">
              <Button size="sm" variant="secondary" icon={<Upload className="h-3.5 w-3.5" />} onClick={() => setImportOpen(true)}>
                Importar de Android
              </Button>
            </Can>
            <Can permission="licenses.export">
              <Button
                size="sm"
                variant="secondary"
                icon={<FileDown className="h-3.5 w-3.5" />}
                onClick={async () => {
                  try {
                    await downloadAuthenticated(`/api/licenses/export.csv${qs({ appId: appId || undefined })}`, 'licencias.csv');
                  } catch (err) {
                    toast.error(err);
                  }
                }}
              >
                CSV
              </Button>
            </Can>
            <Can permission="licenses.issue">
              <Button size="sm" variant="primary" icon={<Wand2 className="h-3.5 w-3.5" />} onClick={() => setModal({ open: true, mode: 'issue' })}>
                Emitir licencia
              </Button>
            </Can>
          </>
        }
      />

      {/* Filtros */}
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
                  setSearch(searchInput.trim());
                }
              }}
              placeholder="Buscar por cliente, dispositivo, clave o correo…"
              className="pl-9"
            />
          </div>
          <Button size="sm" variant="ghost" onClick={() => { setPage(0); setSearch(searchInput.trim()); }}>
            Buscar
          </Button>
          <Select
            value={appId}
            onChange={(e) => { setPage(0); setAppId(e.target.value as AppId | ''); }}
            className="w-auto min-w-[170px]"
          >
            <option value="">Ambas aplicaciones</option>
            <option value="drywall_calculator">{APPS.drywall_calculator.name}</option>
            <option value="keygen_pro">{APPS.keygen_pro.name}</option>
          </Select>
          <Select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }} className="w-auto min-w-[180px]">
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Select>
          <Button
            size="sm"
            variant={expiringSoon ? 'warning' : 'secondary'}
            onClick={() => { setPage(0); setExpiringSoon((v) => !v); }}
          >
            {expiringSoon ? 'Vencen pronto ✓' : 'Vencen en 15 días'}
          </Button>
          <Button size="sm" variant="ghost" icon={<RefreshCw className={cn('h-3.5 w-3.5', loading && 'animate-spin')} />} onClick={() => void load()}>
            Refrescar
          </Button>
        </CardBody>
      </Card>

      <Card>
        {loading ? (
          <Spinner label="Cargando licencias…" />
        ) : items.length === 0 ? (
          <EmptyState
            icon={<KeyRound className="h-8 w-8" />}
            title="No hay licencias con estos filtros"
            description={hasPermission('licenses.issue') ? 'Emite la primera con el botón «Emitir licencia».' : 'Consulta con otro criterio o pide permisos de emisión.'}
            action={
              <Can permission="licenses.issue">
                <Button variant="primary" size="sm" icon={<Wand2 className="h-3.5 w-3.5" />} onClick={() => setModal({ open: true, mode: 'issue' })}>
                  Emitir licencia
                </Button>
              </Can>
            }
          />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Cliente</Th>
                  <Th>App</Th>
                  <Th>Plan</Th>
                  <Th className="text-right">Importe</Th>
                  <Th>Vence</Th>
                  <Th>Estado</Th>
                  <Th className="text-right">Acciones</Th>
                </tr>
              </thead>
              <tbody>
                {items.map((l) => {
                  const meta = STATUS_META[l.status] ?? STATUS_META.issued;
                  return (
                    <Tr key={l.id}>
                      <Td>
                        <button type="button" className="text-left" onClick={() => setDetail(l)}>
                          <span className="block font-medium text-slate-100 hover:text-brand-300">{l.userName}</span>
                          <span className="block font-mono text-[10px] text-slate-500">{l.licenseKey}</span>
                        </button>
                      </Td>
                      <Td>
                        <Badge tone={l.appId === 'keygen_pro' ? 'sky' : 'green'}>
                          {l.appId === 'keygen_pro' ? APPS.keygen_pro.short : APPS.drywall_calculator.short}
                        </Badge>
                      </Td>
                      <Td>
                        <span className="block text-slate-200">{l.planLabel}</span>
                        <span className="block text-[10px] text-slate-500">emitida {formatDateTime(l.creationDate)}</span>
                      </Td>
                      <Td className="text-right tabular-nums">
                        <span className="block font-medium text-slate-100">{formatMoney(l.price, l.currency)}</span>
                        <span className={cn('block text-[10px]', l.isPaid ? 'text-emerald-400' : 'text-amber-400')}>
                          {l.isPaid ? `cobrada${l.paymentMethod ? ` · ${l.paymentMethod}` : ''}` : 'pendiente'}
                        </span>
                      </Td>
                      <Td>
                        <span className="block text-slate-200">{formatDateTime(l.expiryDate)}</span>
                        <span className={cn('block text-[10px]', l.daysRemaining <= 7 ? 'text-amber-400' : 'text-slate-500')}>
                          {l.daysRemaining > 0 ? `${l.daysRemaining} días restantes` : 'sin vigencia'}
                        </span>
                      </Td>
                      <Td>
                        <span className={cn('inline-flex rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset', meta.className)}>
                          {meta.label}
                        </span>
                      </Td>
                      <Td>
                        <div className="flex items-center justify-end gap-1">
                          <IconAction title="Ver detalle" onClick={() => setDetail(l)}>
                            <Eye className="h-4 w-4" />
                          </IconAction>
                          {!l.isPaid && l.status !== 'revoked' ? (
                            <Can permission="licenses.mark_paid">
                              <IconAction
                                title="Marcar cobrada"
                                onClick={() => void act(() => api.post(`/api/licenses/${l.id}/mark-paid`, {}), 'Licencia marcada como cobrada')}
                              >
                                <Wallet className="h-4 w-4 text-emerald-400" />
                              </IconAction>
                            </Can>
                          ) : null}
                          <Can permission="licenses.renew">
                            <IconAction
                              title="Renovar"
                              disabled={l.status === 'revoked' || l.status === 'blocked'}
                              onClick={() => setModal({ open: true, mode: 'renew', license: l })}
                            >
                              <RotateCcw className="h-4 w-4 text-sky-400" />
                            </IconAction>
                          </Can>
                          <Can permission="licenses.export">
                            <IconAction
                              title="Descargar JSON firmado"
                              onClick={async () => {
                                try {
                                  await downloadAuthenticated(`/api/licenses/${l.id}/download`, `Licencia_${l.licenseKey}.json`);
                                } catch (err) {
                                  toast.error(err);
                                }
                              }}
                            >
                              <Download className="h-4 w-4 text-brand-400" />
                            </IconAction>
                          </Can>
                          <Can permission="licenses.revoke">
                            <IconAction
                              title="Revocar"
                              disabled={l.status === 'revoked'}
                              onClick={() => { setRevoking(l); setRevokeReason(''); }}
                            >
                              <Ban className="h-4 w-4 text-rose-400" />
                            </IconAction>
                          </Can>
                          <Can permission="licenses.delete">
                            <IconAction
                              title="Eliminar"
                              onClick={async () => {
                                if (!window.confirm(`¿Eliminar definitivamente la licencia de ${l.userName}?`)) return;
                                await act(() => api.delete(`/api/licenses/${l.id}`), 'Licencia eliminada');
                              }}
                            >
                              <Trash2 className="h-4 w-4 text-red-500" />
                            </IconAction>
                          </Can>
                        </div>
                      </Td>
                    </Tr>
                  );
                })}
              </tbody>
            </Table>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-800 px-4 py-3 text-xs text-slate-400">
              <span>
                {total} licencia(s) · página {page + 1} de {pages}
              </span>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  Anterior
                </Button>
                <Button size="sm" variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>
                  Siguiente
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>

      {/* Emisión / renovación */}
      <LicenseModal
        open={modal.open}
        mode={modal.mode}
        license={modal.license}
        defaultApp={(appId as AppId) || 'drywall_calculator'}
        onClose={() => setModal({ open: false, mode: 'issue' })}
        onSaved={() => void load()}
      />

      {/* Detalle */}
      <Modal
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        title={`Licencia ${detail?.licenseKey ?? ''}`}
        subtitle={detail ? `${detail.appName} · ${detail.planLabel}` : undefined}
        size="lg"
        footer={
          detail ? (
            <>
              <Button variant="ghost" onClick={() => setDetail(null)}>Cerrar</Button>
              <Button
                variant="secondary"
                icon={<MessageCircle className="h-4 w-4" />}
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(buildRenewalMessage(detail));
                    toast.success('Mensaje de renovación copiado al portapapeles');
                  } catch {
                    toast.warning('El navegador bloqueó el portapapeles');
                  }
                }}
              >
                Copiar aviso de renovación
              </Button>
            </>
          ) : null
        }
      >
        {detail ? (
          <div className="space-y-4">
            <div className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
              <dl className="grid gap-x-6 sm:grid-cols-2">
                <KeyValue label="Cliente" value={detail.userName} />
                <KeyValue label="Estado" value={<span className={cn('rounded px-1.5 py-0.5 text-[11px] ring-1 ring-inset', (STATUS_META[detail.status] ?? STATUS_META.issued).className)}>{(STATUS_META[detail.status] ?? STATUS_META.issued).label}</span>} />
                <KeyValue label="Aplicación" value={`${detail.appName}`} />
                <KeyValue label="Plan" value={detail.planLabel} />
                <KeyValue label="Emitida" value={formatDateTime(detail.creationDate)} />
                <KeyValue label="Vence" value={`${formatDateTime(detail.expiryDate)} (${detail.daysRemaining} días)`} />
                <KeyValue label="Importe" value={`${formatMoney(detail.price, detail.currency)} · ${detail.isPaid ? 'cobrada' : 'pendiente'}`} />
                <KeyValue label="Método de pago" value={detail.paymentMethod || '—'} />
                <KeyValue label="Correo" value={detail.email || '—'} />
                <KeyValue label="Teléfono" value={detail.phone || '—'} />
                <KeyValue label="Origen" value={detail.source} />
                <KeyValue label="Dispositivo" value={detail.deviceLabel || '—'} />
              </dl>
            </div>
            <div>
              <p className="mb-1.5 text-xs font-medium text-slate-300">ID de dispositivo</p>
              <code className="block break-all rounded-lg border border-slate-800 bg-slate-950 p-2.5 font-mono text-[11px] text-slate-300">
                {detail.deviceId}
              </code>
            </div>
            <div>
              <p className="mb-1.5 text-xs font-medium text-slate-300">JSON firmado (LicenseInfo)</p>
              <pre className="max-h-60 overflow-auto rounded-lg border border-slate-800 bg-slate-950 p-3 font-mono text-[11px] text-emerald-200">
                {JSON.stringify(detail.licenseJson, null, 2)}
              </pre>
            </div>
            {detail.notes ? (
              <div>
                <p className="mb-1.5 text-xs font-medium text-slate-300">Notas</p>
                <p className="rounded-lg border border-slate-800 bg-slate-950/50 p-2.5 text-xs text-slate-300">{detail.notes}</p>
              </div>
            ) : null}
            {detail.revokeReason ? (
              <div className="rounded-lg border border-rose-800/50 bg-rose-950/40 p-3 text-xs text-rose-200">
                <strong>Revocada</strong> el {formatDateTime(detail.revokedAt)} — motivo: {detail.revokeReason}
              </div>
            ) : null}
          </div>
        ) : null}
      </Modal>

      {/* Revocación */}
      <Modal
        open={Boolean(revoking)}
        onClose={() => setRevoking(null)}
        title="Revocar licencia"
        subtitle={revoking ? `${revoking.userName} · ${revoking.licenseKey}` : undefined}
        danger
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRevoking(null)}>Cancelar</Button>
            <Button variant="danger" icon={<Ban className="h-4 w-4" />} onClick={() => void doRevoke()}>
              Revocar definitivamente
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <p className="text-sm text-slate-300">
            La licencia dejará de ser válida y su firma se añadirá a la <strong>lista negra</strong> que consultan las apps.
            Esta acción queda registrada en la auditoría como evento crítico.
          </p>
          <Textarea
            value={revokeReason}
            onChange={(e) => setRevokeReason(e.target.value)}
            placeholder="Motivo de la revocación (obligatorio para la trazabilidad)"
            maxLength={500}
          />
        </div>
      </Modal>

      {/* Importación desde Android */}
      <Modal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        title="Importar licencias emitidas en Android"
        subtitle="Pega el contenido de un archivo de Licencias_Generadas o un array con varios JSON."
        size="lg"
        footer={
          <>
            <Button variant="ghost" onClick={() => setImportOpen(false)}>Cancelar</Button>
            <Button variant="primary" loading={importing} icon={<Upload className="h-4 w-4" />} onClick={() => void runImport()}>
              Verificar firmas e importar
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <div className="rounded-lg border border-sky-800/40 bg-sky-950/30 p-3 text-xs text-sky-200">
            Cada licencia importada se <strong>verifica con la clave pública activa</strong> de su app. Las que no
            coincidan se rechazan y se listan en el resultado (no se guarda nada sin firma válida).
          </div>
          <Textarea
            value={importText}
            onChange={(e) => setImportText(e.target.value)}
            placeholder='{ "usuario": "…", "dispositivo": "…", "plan": "1 Mes Profesional", "precio": "50.00 USD", "codigoLicencia": { … } }'
            className="min-h-[240px] font-mono text-[11px]"
          />
          <input
            type="file"
            accept="application/json,.json"
            className="block w-full text-xs text-slate-400 file:mr-3 file:rounded-lg file:border-0 file:bg-slate-800 file:px-3 file:py-2 file:text-xs file:text-slate-100 hover:file:bg-slate-700"
            onChange={async (e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              setImportText(await file.text());
            }}
          />
        </div>
      </Modal>

      {viewport}
    </>
  );
}

function IconAction({
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
