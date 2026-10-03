import React from 'react';
import { LineChart, Radio, RefreshCw, Save, TrendingUp } from 'lucide-react';
import { api } from '../api';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  Field,
  Input,
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
import { CURRENCIES } from '../permissions';
import type { Rate } from '../types';
import { formatDateTime, formatRelative } from './DashboardPage';

interface HistoryRow {
  id: number;
  code: string;
  rate: number;
  source: string;
  timestamp: number;
}

const CURRENCY_NAMES: Record<string, string> = {
  USD: 'Dólar estadounidense',
  EUR: 'Euro',
  MLC: 'MLC (Cuba)',
  CAD: 'Dólar canadiense',
  MEX: 'Peso mexicano',
  ZELLE: 'Zelle',
  CLA: 'CLA',
};

export function RatesPage() {
  const { toast, viewport } = useToast();
  const [rates, setRates] = React.useState<Rate[]>([]);
  const [draft, setDraft] = React.useState<Record<string, string>>({});
  const [history, setHistory] = React.useState<HistoryRow[]>([]);
  const [code, setCode] = React.useState('USD');
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);

  const loadRates = React.useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: Rate[] }>('/api/rates');
      setRates(res.items);
      setDraft(Object.fromEntries(res.items.map((r) => [r.code, r.rate ? String(r.rate) : ''])));
    } catch (err) {
      toast.error(err, 'No se pudieron cargar las tasas');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  const loadHistory = React.useCallback(async () => {
    try {
      const res = await api.get<{ items: HistoryRow[] }>(`/api/rates/history?code=${code}&limit=120`);
      setHistory(res.items);
    } catch {
      setHistory([]);
    }
  }, [code]);

  React.useEffect(() => {
    void loadRates();
  }, [loadRates]);

  React.useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  const saveAll = async () => {
    const payload: Record<string, number> = {};
    for (const [k, v] of Object.entries(draft)) {
      const n = Number(String(v).replace(',', '.'));
      if (Number.isFinite(n) && n > 0) payload[k] = n;
    }
    if (Object.keys(payload).length === 0) {
      toast.warning('Introduce al menos una tasa válida mayor que 0');
      return;
    }
    setSaving(true);
    try {
      await api.put('/api/rates', { rates: payload, source: 'web-console' });
      toast.success(`${Object.keys(payload).length} tasa(s) publicada(s)`);
      await Promise.all([loadRates(), loadHistory()]);
    } catch (err) {
      toast.error(err);
    } finally {
      setSaving(false);
    }
  };

  const published = rates.filter((r) => r.rate > 0);
  const current = rates.find((r) => r.code === code);
  const maxRate = Math.max(1, ...history.map((h) => h.rate));
  const minRate = Math.min(...history.map((h) => h.rate), maxRate);

  return (
    <>
      <PageHeader
        title="Tasas de cambio"
        description="Las mismas monedas que gestiona el keygen Android (USD, EUR, MLC, CAD, MEX, ZELLE, CLA). Lo que publiques aquí se expone sin autenticación en /api/public/rates para que las apps lo consuman."
        actions={
          <>
            <Button size="sm" variant="secondary" icon={<RefreshCw className={cn('h-3.5 w-3.5', loading && 'animate-spin')} />} onClick={() => void loadRates()}>
              Recargar
            </Button>
            <Can permission="rates.manage">
              <Button size="sm" variant="primary" icon={<Save className="h-3.5 w-3.5" />} loading={saving} onClick={() => void saveAll()}>
                Publicar tasas
              </Button>
            </Can>
          </>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,420px)]">
        <Card>
          <CardHeader
            title="Valores actuales"
            icon={<Radio className="h-4 w-4" />}
            subtitle={`${published.length} de ${rates.length} monedas publicadas`}
          />
          <CardBody className="p-0">
            {loading ? (
              <Spinner />
            ) : (
              <Table>
                <thead>
                  <tr>
                    <Th>Moneda</Th>
                    <Th className="text-right">Tasa</Th>
                    <Th>Fuente</Th>
                    <Th>Actualizada</Th>
                  </tr>
                </thead>
                <tbody>
                  {rates.map((r) => (
                    <Tr key={r.code}>
                      <Td>
                        <button type="button" className="text-left" onClick={() => setCode(r.code)}>
                          <span className={cn('block font-semibold', code === r.code ? 'text-brand-300' : 'text-slate-100')}>
                            {r.code} <span className="text-slate-500">{r.symbol}</span>
                          </span>
                          <span className="block text-[10px] text-slate-500">{CURRENCY_NAMES[r.code] ?? ''}</span>
                        </button>
                      </Td>
                      <Td className="text-right">
                        <Can
                          permission="rates.manage"
                          else={<span className="tabular-nums text-slate-200">{r.rate > 0 ? r.rate.toFixed(2) : '—'}</span>}
                        >
                          <Input
                            type="number"
                            step="0.01"
                            min={0}
                            value={draft[r.code] ?? ''}
                            onChange={(e) => setDraft((prev) => ({ ...prev, [r.code]: e.target.value }))}
                            className="ml-auto w-32 text-right tabular-nums"
                            placeholder="0.00"
                          />
                        </Can>
                      </Td>
                      <Td>
                        <Badge tone={r.source === 'manual' || r.source === 'web-console' ? 'slate' : 'sky'}>{r.source}</Badge>
                      </Td>
                      <Td>
                        <span className="block text-xs text-slate-300">{r.rate > 0 ? formatDateTime(r.updatedAt) : 'sin publicar'}</span>
                        {r.rate > 0 ? <span className="block text-[10px] text-slate-600">{formatRelative(r.updatedAt)}</span> : null}
                      </Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}
          </CardBody>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader
              title={`Evolución de ${code}`}
              icon={<TrendingUp className="h-4 w-4" />}
              subtitle={current && current.rate > 0 ? `Valor actual: ${current.rate.toFixed(2)}` : 'Sin publicar'}
              actions={
                <Select value={code} onChange={(e) => setCode(e.target.value)} className="w-auto py-1 text-xs">
                  {CURRENCIES.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </Select>
              }
            />
            <CardBody>
              {history.length < 2 ? (
                <EmptyState icon={<LineChart className="h-8 w-8" />} title="Historial insuficiente" description="Publica tasas para empezar a registrar la evolución." />
              ) : (
                <>
                  <div className="flex h-40 items-end gap-1">
                    {[...history].reverse().slice(-60).map((h) => {
                      const span = maxRate - minRate || 1;
                      const height = 12 + Math.round(((h.rate - minRate) / span) * 88);
                      return (
                        <div
                          key={h.id}
                          className="group relative flex-1 rounded-t bg-gradient-to-t from-sky-700 to-sky-400 transition hover:from-brand-600 hover:to-brand-300"
                          style={{ height: `${height}%` }}
                          title={`${h.rate.toFixed(2)} · ${formatDateTime(h.timestamp)}`}
                        />
                      );
                    })}
                  </div>
                  <div className="mt-2 flex justify-between text-[10px] text-slate-500">
                    <span>mín {minRate.toFixed(2)}</span>
                    <span>{history.length} registro(s)</span>
                    <span>máx {maxRate.toFixed(2)}</span>
                  </div>
                </>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Endpoint público" icon={<Radio className="h-4 w-4" />} />
            <CardBody className="space-y-2 text-xs text-slate-400">
              <p>Las apps Android pueden leer las tasas sin autenticarse:</p>
              <code className="block break-all rounded-lg border border-slate-800 bg-slate-950 p-2.5 font-mono text-[11px] text-emerald-300">
                GET /api/public/rates
              </code>
              <p className="text-[11px]">
                Respuesta: <code className="font-mono">{`{ success, rates: { USD, EUR, … }, symbols, timestamp }`}</code> — el
                mismo contrato que devuelve el scraper de eltoque.com, así que <code className="font-mono">CurrencyScraper</code>{' '}
                puede cambiar de fuente sin tocar el modelo de datos.
              </p>
            </CardBody>
          </Card>
        </div>
      </div>

      {viewport}
    </>
  );
}

/** Campo reutilizable para ediciones puntuales. */
export function RateField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <Field label={label}>
      <Input value={value} onChange={(e) => onChange(e.target.value)} />
    </Field>
  );
}
