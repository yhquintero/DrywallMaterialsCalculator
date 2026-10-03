import React from 'react';
import { BadgeCheck, KeyRound, Wand2 } from 'lucide-react';
import { api, downloadAuthenticated, qs } from '../api';
import { useToast } from '../hooks/useToast';
import { Button, Checkbox, CodeBlock, Field, Input, Modal, Select, Textarea, cn } from './ui';
import { APPS, CURRENCIES, DEFAULT_PLANS } from '../permissions';
import type { AppId, License, Plan } from '../types';
import { formatDateTime, formatMoney } from '../pages/DashboardPage';

type Mode = 'issue' | 'renew' | 'view';

/**
 * Emisión / renovación / detalle de una licencia.
 * El servidor es quien calcula la vigencia y FIRMA con la clave RSA de la app;
 * aquí solo se capturan los datos del cliente.
 */
export function LicenseModal({
  open,
  mode,
  license,
  defaultApp,
  onClose,
  onSaved,
}: {
  open: boolean;
  mode: Mode;
  license?: License | null;
  defaultApp: AppId;
  onClose: () => void;
  onSaved: (license: License) => void;
}) {
  const { toast, viewport } = useToast();
  const [appId, setAppId] = React.useState<AppId>(license?.appId ?? defaultApp);
  const [plans, setPlans] = React.useState<Plan[]>([]);
  const [saving, setSaving] = React.useState(false);
  const [issued, setIssued] = React.useState<License | null>(null);

  const [form, setForm] = React.useState({
    userName: '',
    deviceId: '',
    deviceLabel: '',
    planCode: 'ONE_MONTH',
    days: '' as string | number,
    price: '' as string | number,
    currency: 'USD',
    email: '',
    phone: '',
    notes: '',
    paymentMethod: 'Efectivo',
    markPaid: true,
  });

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  // Carga los planes de la app seleccionada (los del keygen Android por defecto).
  React.useEffect(() => {
    if (!open) return;
    let alive = true;
    api
      .get<{ items: Plan[] }>(`/api/plans/active/${appId}`)
      .then((res) => {
        if (!alive) return;
        const items = res.items.length ? res.items : (DEFAULT_PLANS.map((p, i) => ({ ...p, id: i, appId, isActive: true, currency: 'USD', sortOrder: i })) as Plan[]);
        setPlans(items);
        setForm((prev) => {
          const exists = items.some((p) => p.code === prev.planCode);
          const chosen = exists ? prev.planCode : items[0]?.code ?? 'ONE_MONTH';
          const plan = items.find((p) => p.code === chosen);
          return {
            ...prev,
            planCode: chosen,
            price: mode === 'issue' ? plan?.price ?? prev.price : prev.price,
            days: plan?.days ?? prev.days,
            currency: plan?.currency ?? prev.currency,
          };
        });
      })
      .catch(() => {
        if (alive) setPlans([]);
      });
    return () => {
      alive = false;
    };
  }, [appId, open, mode]);

  // Precarga al renovar o reabrir.
  React.useEffect(() => {
    if (!open) {
      setIssued(null);
      return;
    }
    if (license) {
      setAppId(license.appId);
      setForm({
        userName: license.userName,
        deviceId: license.deviceId,
        deviceLabel: license.deviceLabel ?? '',
        planCode: license.planCode || 'ONE_MONTH',
        days: '',
        price: license.price,
        currency: license.currency,
        email: license.email ?? '',
        phone: license.phone ?? '',
        notes: license.notes ?? '',
        paymentMethod: license.paymentMethod ?? 'Efectivo',
        markPaid: license.isPaid,
      });
    } else {
      setAppId(defaultApp);
      setForm({
        userName: '',
        deviceId: '',
        deviceLabel: '',
        planCode: 'ONE_MONTH',
        days: '',
        price: DEFAULT_PLANS[2].price,
        currency: 'USD',
        email: '',
        phone: '',
        notes: '',
        paymentMethod: 'Efectivo',
        markPaid: true,
      });
    }
  }, [open, license, defaultApp]);

  const selectedPlan = plans.find((p) => p.code === form.planCode);
  const effectiveDays = Number(form.days) || selectedPlan?.days || 30;
  const previewExpiry = React.useMemo(() => {
    const d = new Date(license && mode === 'renew' ? Math.max(license.expiryDate, Date.now()) : Date.now());
    d.setDate(d.getDate() + effectiveDays);
    d.setHours(23, 59, 59, 0);
    return d;
  }, [effectiveDays, license, mode]);

  const submit = async () => {
    setSaving(true);
    try {
      const payload = {
        appId,
        userName: form.userName.trim(),
        deviceId: form.deviceId.trim(),
        deviceLabel: form.deviceLabel.trim() || undefined,
        planCode: form.planCode,
        days: form.days ? Number(form.days) : undefined,
        price: Number(form.price) || 0,
        currency: form.currency,
        email: form.email.trim() || undefined,
        phone: form.phone.trim() || undefined,
        notes: form.notes.trim() || undefined,
        paymentMethod: form.paymentMethod,
        markPaid: form.markPaid,
      };
      const saved =
        mode === 'renew' && license
          ? await api.post<License>(`/api/licenses/${license.id}/renew`, payload)
          : await api.post<License>('/api/licenses', payload);
      setIssued(saved);
      toast.success(mode === 'renew' ? `Licencia renovada hasta ${formatDateTime(saved.expiryDate)}` : 'Licencia emitida y firmada correctamente');
      onSaved(saved);
    } catch (err) {
      toast.error(err, 'No se pudo guardar la licencia');
    } finally {
      setSaving(false);
    }
  };

  const readOnly = mode === 'view';
  const title =
    mode === 'issue' ? 'Emitir licencia' : mode === 'renew' ? 'Renovar licencia' : `Licencia ${license?.licenseKey ?? ''}`;

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        title={title}
        subtitle={
          mode === 'issue'
            ? 'El servidor firma con la clave RSA-4096 activa de la app elegida.'
            : mode === 'renew'
              ? 'Se encadena sobre el vencimiento actual y se genera una firma nueva.'
              : 'Detalle completo del registro y su JSON firmado.'
        }
        size="lg"
        footer={
          issued ? (
            <>
              <Button variant="ghost" onClick={onClose}>Cerrar</Button>
              <Button
                variant="primary"
                icon={<KeyRound className="h-4 w-4" />}
                onClick={async () => {
                  try {
                    const name = await downloadAuthenticated(`/api/licenses/${issued.id}/download`, `Licencia_${issued.licenseKey}.json`);
                    toast.success(`Descargado ${name}`);
                  } catch (err) {
                    toast.error(err, 'No se pudo descargar el JSON');
                  }
                }}
              >
                Descargar JSON
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" onClick={onClose}>Cancelar</Button>
              {!readOnly ? (
                <Button variant="primary" loading={saving} icon={<Wand2 className="h-4 w-4" />} onClick={() => void submit()}>
                  {mode === 'renew' ? 'Renovar y firmar' : 'Emitir y firmar'}
                </Button>
              ) : null}
            </>
          )
        }
      >
        {issued ? (
          <IssuedPanel license={issued} />
        ) : (
          <div className="space-y-5">
            {/* Selector de app */}
            <div>
              <p className="mb-1.5 text-xs font-medium text-slate-300">Aplicación destino</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {(Object.values(APPS) as { id: AppId; name: string; description: string; package: string }[]).map((app) => (
                  <button
                    key={app.id}
                    type="button"
                    disabled={readOnly || (mode === 'renew' && license?.appId !== app.id)}
                    onClick={() => setAppId(app.id)}
                    className={cn(
                      'rounded-lg border p-3 text-left transition disabled:cursor-not-allowed disabled:opacity-50',
                      appId === app.id
                        ? 'border-brand-500/60 bg-brand-600/10 ring-1 ring-brand-500/30'
                        : 'border-slate-800 bg-slate-950/40 hover:border-slate-700'
                    )}
                  >
                    <p className="flex items-center gap-2 text-sm font-medium text-slate-100">
                      <BadgeCheck className={cn('h-4 w-4', appId === app.id ? 'text-brand-400' : 'text-slate-500')} />
                      {app.name}
                    </p>
                    <p className="mt-0.5 font-mono text-[10px] text-slate-500">{app.package}</p>
                    <p className="mt-1 text-[11px] leading-snug text-slate-400">{app.description}</p>
                  </button>
                ))}
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Nombre del cliente" required error={form.userName && form.userName.length > 100 ? 'Máximo 100 caracteres' : undefined}>
                <Input
                  value={form.userName}
                  onChange={(e) => set('userName', e.target.value)}
                  placeholder="Juan Pérez"
                  maxLength={100}
                  disabled={readOnly}
                />
              </Field>
              <Field label="Etiqueta del dispositivo" hint="Opcional: modelo o referencia interna">
                <Input
                  value={form.deviceLabel}
                  onChange={(e) => set('deviceLabel', e.target.value)}
                  placeholder="Samsung A54 · obra Habana"
                  maxLength={120}
                  disabled={readOnly}
                />
              </Field>
            </div>

            <Field
              label="ID de dispositivo (Base64 del SHA-256 del ANDROID_ID)"
              required
              hint="Es el ID que muestra la app en su pantalla de activación. Se normaliza automáticamente."
            >
              <Textarea
                value={form.deviceId}
                onChange={(e) => set('deviceId', e.target.value)}
                placeholder="RQVDRVNUREVWSUNFSUQwMTIzNDU2Nzg5QUJDREVG=="
                className="min-h-[64px] font-mono text-xs"
                disabled={readOnly}
              />
            </Field>

            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Plan">
                <Select
                  value={form.planCode}
                  onChange={(e) => {
                    const code = e.target.value;
                    const plan = plans.find((p) => p.code === code);
                    set('planCode', code);
                    if (plan) {
                      set('days', plan.days);
                      if (mode === 'issue') set('price', plan.price);
                    }
                  }}
                  disabled={readOnly}
                >
                  {(plans.length ? plans : (DEFAULT_PLANS as unknown as Plan[])).map((p) => (
                    <option key={p.code} value={p.code}>
                      {p.label} ({p.days} d)
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Días de vigencia" hint={`Vence ${formatDateTime(previewExpiry.getTime())}`}>
                <Input
                  type="number"
                  min={1}
                  max={36500}
                  value={form.days}
                  onChange={(e) => set('days', e.target.value)}
                  disabled={readOnly}
                />
              </Field>
              <Field label="Precio">
                <div className="flex gap-2">
                  <Input
                    type="number"
                    step="0.01"
                    min={0}
                    value={form.price}
                    onChange={(e) => set('price', e.target.value)}
                    disabled={readOnly}
                    className="flex-1"
                  />
                  <Select value={form.currency} onChange={(e) => set('currency', e.target.value)} disabled={readOnly} className="w-24">
                    {CURRENCIES.map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </Select>
                </div>
              </Field>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Correo">
                <Input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} maxLength={160} disabled={readOnly} />
              </Field>
              <Field label="Teléfono / WhatsApp">
                <Input value={form.phone} onChange={(e) => set('phone', e.target.value)} maxLength={40} disabled={readOnly} />
              </Field>
              <Field label="Método de pago">
                <Select value={form.paymentMethod} onChange={(e) => set('paymentMethod', e.target.value)} disabled={readOnly}>
                  {['Efectivo', 'Transferencia', 'Zelle', 'MLC', 'Tarjeta', 'Otro'].map((m) => (
                    <option key={m} value={m}>{m}</option>
                  ))}
                </Select>
              </Field>
            </div>

            <Field label="Notas internas">
              <Textarea value={form.notes} onChange={(e) => set('notes', e.target.value)} maxLength={1000} disabled={readOnly} />
            </Field>

            <Checkbox
              checked={form.markPaid}
              onChange={(e) => set('markPaid', e.target.checked)}
              disabled={readOnly}
              label="Marcar como cobrada (estado «paid»)"
            />

            {license && mode !== 'issue' ? (
              <div className="rounded-lg border border-slate-800 bg-slate-950/50 p-3 text-xs">
                <p className="font-semibold text-slate-300">Licencia actual</p>
                <dl className="mt-2 grid gap-1 sm:grid-cols-2">
                  <Info label="Clave" value={license.licenseKey} mono />
                  <Info label="Estado" value={license.status} />
                  <Info label="Emitida" value={formatDateTime(license.creationDate)} />
                  <Info label="Vence" value={formatDateTime(license.expiryDate)} />
                  <Info label="Importe" value={`${formatMoney(license.price, license.currency)} · ${license.isPaid ? 'cobrada' : 'pendiente'}`} />
                  <Info label="Origen" value={license.source} />
                </dl>
              </div>
            ) : null}
          </div>
        )}
      </Modal>
      {viewport}
    </>
  );
}

function Info({ label, value, mono = false }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div className="flex gap-2">
      <dt className="w-20 shrink-0 text-slate-500">{label}</dt>
      <dd className={cn('min-w-0 flex-1 break-all text-slate-200', mono && 'font-mono')}>{value}</dd>
    </div>
  );
}

function IssuedPanel({ license }: { license: License }) {
  const [copied, setCopied] = React.useState<string | null>(null);

  const copy = async (label: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(label);
      setTimeout(() => setCopied(null), 1800);
    } catch {
      /* portapapeles no disponible */
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3 rounded-lg border border-emerald-700/40 bg-emerald-950/40 p-3">
        <BadgeCheck className="mt-0.5 h-5 w-5 shrink-0 text-emerald-400" />
        <div className="min-w-0">
          <p className="text-sm font-semibold text-emerald-200">Licencia firmada correctamente</p>
          <p className="mt-0.5 text-xs text-emerald-300/80">
            Envía el JSON al cliente. La app verificará la firma con la clave pública de {license.appName}.
          </p>
        </div>
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <Info label="Clave" value={license.licenseKey} mono />
        <Info label="Cliente" value={license.userName} />
        <Info label="Plan" value={license.planLabel} />
        <Info label="Importe" value={formatMoney(license.price, license.currency)} />
        <Info label="Emitida" value={formatDateTime(license.creationDate)} />
        <Info label="Vence" value={formatDateTime(license.expiryDate)} />
      </div>

      <div>
        <div className="mb-1.5 flex items-center justify-between">
          <p className="text-xs font-medium text-slate-300">JSON de licencia (LicenseInfo)</p>
          <Button size="sm" variant="ghost" onClick={() => void copy('json', JSON.stringify(license.licenseJson))}>
            {copied === 'json' ? 'Copiado ✓' : 'Copiar'}
          </Button>
        </div>
        <CodeBlock>{JSON.stringify(license.licenseJson, null, 2)}</CodeBlock>
      </div>

      <div>
        <div className="mb-1.5 flex items-center justify-between">
          <p className="text-xs font-medium text-slate-300">Firma RSA-SHA256 (Base64)</p>
          <Button size="sm" variant="ghost" onClick={() => void copy('sig', license.signature)}>
            {copied === 'sig' ? 'Copiado ✓' : 'Copiar'}
          </Button>
        </div>
        <CodeBlock className="max-h-24 break-all">{license.signature}</CodeBlock>
      </div>
    </div>
  );
}

/** Mensaje de WhatsApp listo para pegar, igual que el del keygen Android. */
export function buildRenewalMessage(license: License) {
  const days = license.daysRemaining;
  if (days > 0) {
    return `Hola ${license.userName}, le informamos que su licencia de ${license.appName} vencerá en ${days} días (${formatDateTime(license.expiryDate)}). ¡Recuerde renovar para no perder sus funciones!`;
  }
  return `Hola ${license.userName}, su licencia de ${license.appName} ha vencido. Contacte con nosotros para renovar su suscripción.`;
}

/** Helper para consultas reutilizado por varias pantallas. */
export async function fetchLicenses(params: Record<string, unknown>) {
  return api.get<{ items: License[]; total: number }>(`/api/licenses${qs(params)}`);
}
