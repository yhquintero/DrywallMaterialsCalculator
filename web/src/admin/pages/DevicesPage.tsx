import React from 'react';
import { Ban, Search, ShieldCheck, Smartphone, Unlock } from 'lucide-react';
import { api, qs } from '../api';
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
  Table,
  Td,
  Th,
  Tr,
} from '../components/ui';
import { PageHeader } from '../components/AdminLayout';
import { Can } from '../components/Guard';
import { APPS } from '../permissions';
import type { AppId, DeviceRecord } from '../types';
import { formatDateTime, formatRelative } from './DashboardPage';

export function DevicesPage() {
  const { toast, viewport } = useToast();
  const [items, setItems] = React.useState<DeviceRecord[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [appId, setAppId] = React.useState<AppId | ''>('');
  const [search, setSearch] = React.useState('');
  const [searchInput, setSearchInput] = React.useState('');
  const [blocking, setBlocking] = React.useState<DeviceRecord | null>(null);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: DeviceRecord[] }>(`/api/devices${qs({ appId: appId || undefined, search: search || undefined })}`);
      setItems(res.items);
    } catch (err) {
      toast.error(err, 'No se pudieron cargar los dispositivos');
    } finally {
      setLoading(false);
    }
  }, [appId, search, toast]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const setBlocked = async (device: DeviceRecord, isBlocked: boolean) => {
    try {
      await api.patch(`/api/devices/${device.id}`, { isBlocked });
      toast.success(isBlocked ? 'Dispositivo bloqueado: no podrá recibir licencias nuevas' : 'Dispositivo desbloqueado');
      setBlocking(null);
      await load();
    } catch (err) {
      toast.error(err);
    }
  };

  const blockedCount = items.filter((d) => d.isBlocked).length;

  return (
    <>
      <PageHeader
        title="Dispositivos"
        description="Huella de activaciones por aplicación. El ID es el SHA-256 del ANDROID_ID en Base64, el mismo que la app envía en su pantalla de activación."
      />

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') setSearch(searchInput.trim());
              }}
              placeholder="Buscar por ID de dispositivo, cliente o etiqueta…"
              className="pl-9 font-mono text-xs"
            />
          </div>
          <Button size="sm" variant="ghost" onClick={() => setSearch(searchInput.trim())}>Buscar</Button>
          <Select value={appId} onChange={(e) => setAppId(e.target.value as AppId | '')} className="w-auto min-w-[180px]">
            <option value="">Ambas aplicaciones</option>
            <option value="drywall_calculator">{APPS.drywall_calculator.name}</option>
            <option value="keygen_pro">{APPS.keygen_pro.name}</option>
          </Select>
          <Badge tone="slate">{items.length} dispositivo(s)</Badge>
          {blockedCount > 0 ? <Badge tone="red">{blockedCount} bloqueado(s)</Badge> : null}
        </CardBody>
      </Card>

      <Card>
        {loading ? (
          <Spinner label="Cargando dispositivos…" />
        ) : items.length === 0 ? (
          <EmptyState
            icon={<Smartphone className="h-8 w-8" />}
            title="Sin dispositivos registrados"
            description="Aparecen automáticamente al emitir la primera licencia de cada dispositivo."
          />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Dispositivo</Th>
                <Th>App</Th>
                <Th>Titular</Th>
                <Th className="text-right">Licencias</Th>
                <Th>Primera vez</Th>
                <Th>Última actividad</Th>
                <Th>Estado</Th>
                <Th className="text-right">Acciones</Th>
              </tr>
            </thead>
            <tbody>
              {items.map((d) => (
                <Tr key={d.id}>
                  <Td>
                    <code className="block max-w-[280px] break-all font-mono text-[10px] text-slate-300">{d.deviceId}</code>
                    {d.deviceLabel ? <span className="mt-0.5 block text-[10px] text-slate-500">{d.deviceLabel}</span> : null}
                  </Td>
                  <Td>
                    <Badge tone={d.appId === 'keygen_pro' ? 'sky' : 'green'}>
                      {d.appId === 'keygen_pro' ? APPS.keygen_pro.short : APPS.drywall_calculator.short}
                    </Badge>
                  </Td>
                  <Td>{d.ownerName || '—'}</Td>
                  <Td className="text-right tabular-nums">{d.licenseCount}</Td>
                  <Td>
                    <span className="block text-xs text-slate-300">{formatDateTime(d.firstSeenAt)}</span>
                  </Td>
                  <Td>
                    <span className="block text-xs text-slate-300">{formatDateTime(d.lastSeenAt)}</span>
                    <span className="block text-[10px] text-slate-600">{formatRelative(d.lastSeenAt)}</span>
                  </Td>
                  <Td>{d.isBlocked ? <Badge tone="red">Bloqueado</Badge> : <Badge tone="green">Operativo</Badge>}</Td>
                  <Td>
                    <div className="flex justify-end">
                      <Can permission="licenses.revoke">
                        <Button
                          size="sm"
                          variant={d.isBlocked ? 'success' : 'danger'}
                          icon={d.isBlocked ? <Unlock className="h-3.5 w-3.5" /> : <Ban className="h-3.5 w-3.5" />}
                          onClick={() => (d.isBlocked ? void setBlocked(d, false) : setBlocking(d))}
                        >
                          {d.isBlocked ? 'Desbloquear' : 'Bloquear'}
                        </Button>
                      </Can>
                    </div>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      <Modal
        open={Boolean(blocking)}
        onClose={() => setBlocking(null)}
        title="Bloquear dispositivo"
        subtitle={blocking ? blocking.deviceId.slice(0, 48) + '…' : undefined}
        danger
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setBlocking(null)}>Cancelar</Button>
            <Button variant="danger" icon={<Ban className="h-4 w-4" />} onClick={() => blocking && void setBlocked(blocking, true)}>
              Bloquear
            </Button>
          </>
        }
      >
        <div className="space-y-3 text-sm text-slate-300">
          <p className="flex items-start gap-2 rounded-lg border border-rose-800/50 bg-rose-950/40 p-3 text-xs text-rose-100">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />
            El dispositivo no podrá recibir licencias nuevas y sus licencias vigentes pasarán a estado «bloqueada».
          </p>
          <p className="text-xs text-slate-400">
            Titular: <strong className="text-slate-200">{blocking?.ownerName || 'desconocido'}</strong> · {blocking?.licenseCount} licencia(s) asociada(s).
            La acción queda registrada en la auditoría como evento crítico.
          </p>
        </div>
      </Modal>

      {viewport}
    </>
  );
}
