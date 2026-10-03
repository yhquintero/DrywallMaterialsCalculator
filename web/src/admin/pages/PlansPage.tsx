import React from 'react';
import { Package, Pencil, Plus, Trash2, Wallet } from 'lucide-react';
import { api } from '../api';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
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
import { APPS, APP_LIST, CURRENCIES } from '../permissions';
import type { AppId, Plan } from '../types';
import { formatMoney } from './DashboardPage';

export function PlansPage() {
  const { toast, viewport } = useToast();
  const [plans, setPlans] = React.useState<Plan[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [appId, setAppId] = React.useState<AppId>('drywall_calculator');
  const [editing, setEditing] = React.useState<Plan | 'new' | null>(null);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: Plan[] }>(`/api/plans?appId=${appId}`);
      setPlans(res.items);
    } catch (err) {
      toast.error(err, 'No se pudieron cargar los planes');
    } finally {
      setLoading(false);
    }
  }, [appId, toast]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const toggleActive = async (plan: Plan) => {
    try {
      await api.patch(`/api/plans/${plan.id}`, { isActive: !plan.isActive });
      toast.success(plan.isActive ? 'Plan desactivado' : 'Plan activado');
      await load();
    } catch (err) {
      toast.error(err);
    }
  };

  const remove = async (plan: Plan) => {
    if (!window.confirm(`¿Eliminar el plan "${plan.label}"?`)) return;
    try {
      const res = await api.delete<{ deactivated?: boolean; reason?: string }>(`/api/plans/${plan.id}`);
      toast.success(res.deactivated ? (res.reason ?? 'Plan desactivado') : 'Plan eliminado');
      await load();
    } catch (err) {
      toast.error(err);
    }
  };

  return (
    <>
      <PageHeader
        title="Planes y precios"
        description="Catálogo de planes por aplicación. Los valores por defecto replican el enum LicenseTypeBase del keygen Android (1 Día, 1 Semana, 1 Mes, 1 Año y 2 Años Profesional)."
        actions={
          <>
            <Select value={appId} onChange={(e) => setAppId(e.target.value as AppId)} className="w-auto min-w-[190px] py-1.5 text-xs">
              {APP_LIST.map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </Select>
            <Can permission="plans.manage">
              <Button size="sm" variant="primary" icon={<Plus className="h-3.5 w-3.5" />} onClick={() => setEditing('new')}>
                Nuevo plan
              </Button>
            </Can>
          </>
        }
      />

      <Card>
        {loading ? (
          <Spinner label="Cargando planes…" />
        ) : plans.length === 0 ? (
          <EmptyState icon={<Package className="h-8 w-8" />} title={`Sin planes para ${APPS[appId].name}`} description="Crea el primero con «Nuevo plan»." />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Orden</Th>
                <Th>Plan</Th>
                <Th>Código técnico</Th>
                <Th className="text-right">Días</Th>
                <Th className="text-right">Precio</Th>
                <Th>Estado</Th>
                <Th className="text-right">Acciones</Th>
              </tr>
            </thead>
            <tbody>
              {plans.map((p) => (
                <Tr key={p.id} className={cn(!p.isActive && 'opacity-55')}>
                  <Td><span className="tabular-nums text-slate-400">{p.sortOrder}</span></Td>
                  <Td>
                    <span className="block font-medium text-slate-100">{p.label}</span>
                    <span className="block text-[10px] text-slate-500">tipo firmado: {p.typeName}</span>
                  </Td>
                  <Td><code className="font-mono text-[11px] text-slate-300">{p.code}</code></Td>
                  <Td className="text-right tabular-nums">{p.days}</Td>
                  <Td className="text-right font-medium tabular-nums text-slate-100">{formatMoney(p.price, p.currency)}</Td>
                  <Td><Badge tone={p.isActive ? 'green' : 'slate'}>{p.isActive ? 'Activo' : 'Inactivo'}</Badge></Td>
                  <Td>
                    <div className="flex items-center justify-end gap-1">
                      <Can permission="plans.manage">
                        <Button size="sm" variant="ghost" icon={<Pencil className="h-3.5 w-3.5" />} onClick={() => setEditing(p)}>
                          Editar
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => void toggleActive(p)}>
                          {p.isActive ? 'Desactivar' : 'Activar'}
                        </Button>
                        <Button size="sm" variant="ghost" icon={<Trash2 className="h-3.5 w-3.5 text-rose-500" />} onClick={() => void remove(p)}>
                          <span className="sr-only">Eliminar</span>
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

      <Card className="mt-4">
        <CardBody className="flex items-start gap-3 text-xs text-slate-400">
          <Wallet className="mt-0.5 h-4 w-4 shrink-0 text-brand-400" />
          <p>
            El campo <strong className="text-slate-200">«tipo firmado»</strong> es el valor que viaja en{' '}
            <code className="font-mono text-emerald-300">LicenseInfo.type</code> y que la app muestra al usuario
            (por ejemplo <em>1 MES PROFESIONAL</em>). Si lo cambias, las licencias ya emitidas conservan el tipo con el que se firmaron.
          </p>
        </CardBody>
      </Card>

      {editing ? (
        <PlanFormModal
          appId={appId}
          plan={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      ) : null}

      {viewport}
    </>
  );
}

function PlanFormModal({
  appId,
  plan,
  onClose,
  onSaved,
}: {
  appId: AppId;
  plan: Plan | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast, viewport } = useToast();
  const [saving, setSaving] = React.useState(false);
  const [form, setForm] = React.useState({
    code: plan?.code ?? '',
    label: plan?.label ?? '',
    typeName: plan?.typeName ?? '',
    days: plan?.days ?? 30,
    price: plan?.price ?? 50,
    currency: plan?.currency ?? 'USD',
    sortOrder: plan?.sortOrder ?? 0,
    isActive: plan?.isActive ?? true,
  });

  const submit = async () => {
    setSaving(true);
    try {
      const payload = { appId, ...form, code: form.code.toUpperCase() };
      if (plan) await api.patch(`/api/plans/${plan.id}`, payload);
      else await api.post('/api/plans', payload);
      toast.success(plan ? 'Plan actualizado' : 'Plan creado');
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
        title={plan ? `Editar plan ${plan.code}` : 'Nuevo plan'}
        subtitle={APPS[appId].name}
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={onClose}>Cancelar</Button>
            <Button variant="primary" loading={saving} onClick={() => void submit()}>{plan ? 'Guardar' : 'Crear plan'}</Button>
          </>
        }
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Código técnico" required hint="MAYÚSCULAS, p. ej. ONE_MONTH">
            <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} maxLength={40} />
          </Field>
          <Field label="Etiqueta comercial" required hint="Lo que ve el operador al emitir">
            <Input value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} maxLength={80} placeholder="1 Mes Profesional" />
          </Field>
          <Field label="Tipo firmado (LicenseInfo.type)" required hint="Va dentro del JSON firmado">
            <Input value={form.typeName} onChange={(e) => setForm({ ...form, typeName: e.target.value })} maxLength={80} placeholder="1 MES PROFESIONAL" />
          </Field>
          <Field label="Días de vigencia" required>
            <Input type="number" min={1} max={36500} value={form.days} onChange={(e) => setForm({ ...form, days: Number(e.target.value) })} />
          </Field>
          <Field label="Precio">
            <Input type="number" step="0.01" min={0} value={form.price} onChange={(e) => setForm({ ...form, price: Number(e.target.value) })} />
          </Field>
          <Field label="Moneda">
            <Select value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}>
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </Select>
          </Field>
          <Field label="Orden de aparición">
            <Input type="number" min={0} max={999} value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
          </Field>
          <div className="flex items-end pb-2">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-300">
              <input
                type="checkbox"
                checked={form.isActive}
                onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                className="h-4 w-4 rounded border-slate-600 bg-slate-950 text-brand-600 focus:ring-brand-500"
              />
              Plan activo
            </label>
          </div>
        </div>
      </Modal>
      {viewport}
    </>
  );
}
