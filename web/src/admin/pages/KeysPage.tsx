import React from 'react';
import {
  AlertTriangle,
  BadgeCheck,
  Copy,
  Download,
  FileKey2,
  Fingerprint,
  KeyRound,
  RefreshCw,
  Upload,
} from 'lucide-react';
import { api, downloadAuthenticated } from '../api';
import { useAuth } from '../AuthContext';
import { useToast } from '../hooks/useToast';
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  CodeBlock,
  Field,
  KeyValue,
  Modal,
  Spinner,
  Textarea,
  cn,
} from '../components/ui';
import { PageHeader } from '../components/AdminLayout';
import { Can } from '../components/Guard';
import { APPS, APP_LIST } from '../permissions';
import type { AppId, SigningKey } from '../types';
import { formatDateTime } from './DashboardPage';

export function KeysPage() {
  const { toast, viewport } = useToast();
  const [keys, setKeys] = React.useState<SigningKey[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [rotating, setRotating] = React.useState<AppId | null>(null);
  const [importing, setImporting] = React.useState<AppId | null>(null);
  const [exporting, setExporting] = React.useState<AppId | null>(null);
  const [viewing, setViewing] = React.useState<SigningKey | null>(null);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: SigningKey[] }>('/api/keys');
      setKeys(res.items);
    } catch (err) {
      toast.error(err, 'No se pudieron cargar las claves');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const activeFor = (appId: AppId) => keys.find((k) => k.appId === appId && k.isActive) ?? null;
  const historyFor = (appId: AppId) => keys.filter((k) => k.appId === appId && !k.isActive);

  return (
    <>
      <PageHeader
        title="Claves de firma"
        description="Cada app tiene su propio par RSA. La llave privada se guarda cifrada con AES-256-GCM derivado del MASTER_KEY del servidor; nunca se expone al navegador salvo exportación explícita de un administrador."
        actions={
          <Button size="sm" variant="secondary" icon={<RefreshCw className={cn('h-3.5 w-3.5', loading && 'animate-spin')} />} onClick={() => void load()}>
            Recargar
          </Button>
        }
      />

      {loading ? (
        <Spinner label="Cargando claves de firma…" />
      ) : (
        <div className="grid gap-4 xl:grid-cols-2">
          {APP_LIST.map((app) => {
            const active = activeFor(app.id);
            const history = historyFor(app.id);
            const Icon = app.id === 'keygen_pro' ? KeyRound : FileKey2;
            return (
              <Card key={app.id}>
                <CardHeader
                  title={app.name}
                  icon={<Icon className="h-4 w-4" />}
                  subtitle={app.description}
                  actions={
                    <>
                      <Can permission="keys.view">
                        <Button
                          size="sm"
                          variant="secondary"
                          icon={<Download className="h-3.5 w-3.5" />}
                          onClick={async () => {
                            try {
                              await downloadAuthenticated(`/api/keys/${app.id}/public.pem`, `licensing-public-key-${app.id}.pem`);
                              toast.success('Clave pública descargada');
                            } catch (err) {
                              toast.error(err);
                            }
                          }}
                        >
                          PEM
                        </Button>
                      </Can>
                      <Can permission="keys.rotate">
                        <Button size="sm" variant="warning" icon={<RefreshCw className="h-3.5 w-3.5" />} onClick={() => setRotating(app.id)}>
                          Rotar
                        </Button>
                      </Can>
                    </>
                  }
                />
                <CardBody>
                  {!active ? (
                    <div className="rounded-lg border border-amber-800/40 bg-amber-950/30 p-4 text-xs text-amber-200">
                      <p className="flex items-center gap-2 font-semibold">
                        <AlertTriangle className="h-4 w-4" /> Sin clave activa
                      </p>
                      <p className="mt-1">
                        La primera emisión de licencia generará automáticamente un par RSA. También puedes rotar ahora o
                        importar la llave privada que ya usa el keygen Android.
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge tone="green">
                          <BadgeCheck className="h-3 w-3" /> Activa
                        </Badge>
                        <Badge tone="sky">{active.kid}</Badge>
                        <Badge tone="slate">RSA-{active.modulusBits}</Badge>
                        <Badge tone="slate">{active.algorithm}</Badge>
                        <span className="ml-auto text-[11px] text-slate-500">creada {formatDateTime(active.createdAt)}</span>
                      </div>

                      <dl className="rounded-lg border border-slate-800 bg-slate-950/40 p-3">
                        <KeyValue label="App / paquete" value={<span className="font-mono text-xs">{app.package}</span>} />
                        <KeyValue label="Algoritmo de firma" value={<span className="font-mono text-xs">SHA256withRSA (PKCS#1 v1.5)</span>} />
                        <KeyValue label="Cadena firmada" value={<span className="font-mono text-xs">user|deviceId|creationDate|expiryDate</span>} />
                        <KeyValue label="SHA-256 de la pública" value={<span className="font-mono text-xs">{active.publicKeySha256.slice(0, 32)}…</span>} />
                        {active.notes ? <KeyValue label="Notas" value={active.notes} /> : null}
                      </dl>

                      <div>
                        <div className="mb-1.5 flex items-center justify-between">
                          <p className="flex items-center gap-1.5 text-xs font-medium text-slate-300">
                            <Fingerprint className="h-3.5 w-3.5 text-brand-400" /> Clave pública (Base64 SPKI)
                          </p>
                          <div className="flex gap-1.5">
                            <Button
                              size="sm"
                              variant="ghost"
                              icon={<Copy className="h-3 w-3" />}
                              onClick={async () => {
                                try {
                                  await navigator.clipboard.writeText(active.publicKeyB64);
                                  toast.success('Clave pública copiada');
                                } catch {
                                  toast.warning('El navegador bloqueó el portapapeles');
                                }
                              }}
                            >
                              Copiar
                            </Button>
                            <Button size="sm" variant="ghost" onClick={() => setViewing(active)}>Ver PEM</Button>
                          </div>
                        </div>
                        <CodeBlock className="max-h-28 break-all">{active.publicKeyB64}</CodeBlock>
                      </div>

                      <div className="flex flex-wrap gap-2 border-t border-slate-800 pt-3">
                        <Can permission="keys.rotate">
                          <Button size="sm" variant="secondary" icon={<Upload className="h-3.5 w-3.5" />} onClick={() => setImporting(app.id)}>
                            Importar llave del keygen Android
                          </Button>
                        </Can>
                        <Can permission="keys.export_private">
                          <Button size="sm" variant="danger" icon={<KeyRound className="h-3.5 w-3.5" />} onClick={() => setExporting(app.id)}>
                            Exportar llave privada
                          </Button>
                        </Can>
                      </div>

                      <div className="rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-[11px] text-slate-400">
                        <p className="font-semibold text-slate-300">Endpoint público para las apps</p>
                        <code className="mt-1 block break-all font-mono text-emerald-300">
                          GET /.well-known/licensing-public-key-v2.pem?app={app.id}
                        </code>
                        <p className="mt-1.5">
                          Apunta <code className="font-mono">RemoteKeyProvider.KEY_SERVER_URL</code> a esta URL para que la
                          app descargue la clave vigente sin actualizar la APK.
                        </p>
                      </div>

                      {history.length > 0 ? (
                        <div className="border-t border-slate-800 pt-3">
                          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                            Claves anteriores ({history.length})
                          </p>
                          <ul className="space-y-1">
                            {history.map((k) => (
                              <li key={k.id} className="flex items-center gap-2 text-[11px] text-slate-400">
                                <Badge tone="slate">{k.kid}</Badge>
                                <span className="font-mono">{k.publicKeySha256.slice(0, 16)}…</span>
                                <span className="ml-auto">{formatDateTime(k.createdAt)}</span>
                              </li>
                            ))}
                          </ul>
                          <p className="mt-1.5 text-[10px] text-slate-600">
                            Se conservan para poder verificar licencias antiguas ya emitidas.
                          </p>
                        </div>
                      ) : null}
                    </div>
                  )}
                </CardBody>
              </Card>
            );
          })}
        </div>
      )}

      {/* Rotación */}
      <Modal
        open={Boolean(rotating)}
        onClose={() => setRotating(null)}
        title="Rotar clave de firma"
        subtitle={rotating ? APPS[rotating].name : undefined}
        danger
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRotating(null)}>Cancelar</Button>
            <Button
              variant="warning"
              icon={<RefreshCw className="h-4 w-4" />}
              onClick={async () => {
                if (!rotating) return;
                try {
                  const res = await api.post<SigningKey>(`/api/keys/${rotating}/rotate`, { confirm: true, notes: 'Rotación desde la consola web' });
                  toast.success(`Nueva clave activa: ${res.kid}`);
                  setRotating(null);
                  await load();
                } catch (err) {
                  toast.error(err);
                }
              }}
            >
              Rotar ahora
            </Button>
          </>
        }
      >
        <div className="space-y-3 text-sm text-slate-300">
          <p>Se generará un <strong>nuevo par RSA-4096</strong> y se marcará como activo.</p>
          <ul className="list-disc space-y-1 pl-5 text-xs text-slate-400">
            <li>Las licencias nuevas se firmarán con la clave nueva.</li>
            <li>Las licencias ya emitidas siguen siendo válidas (guardan su <code className="font-mono">issuerKey</code>).</li>
            <li>Si la app descarga la clave desde <code className="font-mono">/.well-known/</code>, conviene publicar la nueva.</li>
          </ul>
          <div className="rounded-lg border border-amber-800/40 bg-amber-950/30 p-2.5 text-xs text-amber-200">
            La rotación queda registrada en la auditoría como evento crítico.
          </div>
        </div>
      </Modal>

      {/* Importación de llave privada */}
      {importing ? (
        <ImportKeyModal
          appId={importing}
          onClose={() => setImporting(null)}
          onImported={async () => {
            setImporting(null);
            await load();
          }}
        />
      ) : null}

      {/* Exportación privada */}
      {exporting ? <ExportPrivateKeyModal appId={exporting} onClose={() => setExporting(null)} /> : null}

      {/* PEM */}
      <Modal open={Boolean(viewing)} onClose={() => setViewing(null)} title={`Clave pública ${viewing?.kid ?? ''}`} size="md"
        footer={<Button variant="primary" onClick={() => setViewing(null)}>Cerrar</Button>}>
        {viewing ? (
          <div className="space-y-3">
            <CodeBlock>{viewing.publicKeyPem}</CodeBlock>
            <p className="text-[11px] text-slate-500">
              Formato X.509 SubjectPublicKeyInfo — el mismo que <code className="font-mono">X509EncodedKeySpec</code> de Android.
            </p>
          </div>
        ) : null}
      </Modal>

      {viewport}
    </>
  );
}

function ImportKeyModal({ appId, onClose, onImported }: { appId: AppId; onClose: () => void; onImported: () => void }) {
  const { toast, viewport } = useToast();
  const [value, setValue] = React.useState('');
  const [notes, setNotes] = React.useState('Importada desde el keygen Android');
  const [saving, setSaving] = React.useState(false);

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title="Importar llave privada existente"
        subtitle={`${APPS[appId].name} — PKCS#8 (Base64 o PEM)`}
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={onClose}>Cancelar</Button>
            <Button
              variant="primary"
              loading={saving}
              icon={<Upload className="h-4 w-4" />}
              onClick={async () => {
                setSaving(true);
                try {
                  const res = await api.post<SigningKey>(`/api/keys/${appId}/import`, { privateKey: value.trim(), notes });
                  toast.success(`Llave importada y activa: ${res.kid}`);
                  onImported();
                } catch (err) {
                  toast.error(err, 'No se pudo importar la llave');
                } finally {
                  setSaving(false);
                }
              }}
            >
              Importar y activar
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <div className="rounded-lg border border-sky-800/40 bg-sky-950/30 p-3 text-xs text-sky-200">
            Usa esto para que la web firme con la <strong>misma llave</strong> que ya generó la app Keygen Pro: así las
            licencias emitidas desde el navegador se verifican en los móviles que ya tienen esa clave pública.
          </div>
          <Field label="Llave privada (PKCS#8)" required hint="Se cifra con AES-256-GCM antes de guardarse. Nunca viaja a terceros.">
            <Textarea
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={'-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----'}
              className="min-h-[180px] font-mono text-[11px]"
            />
          </Field>
          <Field label="Notas">
            <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} className="min-h-[52px]" />
          </Field>
        </div>
      </Modal>
      {viewport}
    </>
  );
}

function ExportPrivateKeyModal({ appId, onClose }: { appId: AppId; onClose: () => void }) {
  const { toast, viewport } = useToast();
  const [reason, setReason] = React.useState('');
  const [confirmed, setConfirmed] = React.useState(false);
  const [result, setResult] = React.useState<{ pem: string; pkcs8Base64: string; kid: string } | null>(null);
  const [loading, setLoading] = React.useState(false);

  const run = async () => {
    setLoading(true);
    try {
      const res = await api.post<{ pem: string; pkcs8Base64: string; kid: string }>(`/api/keys/${appId}/export-private`, {
        confirm: true,
        reason,
      });
      setResult(res);
      toast.success('Llave privada exportada (evento crítico registrado)');
    } catch (err) {
      toast.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title="Exportar llave privada"
        subtitle={`${APPS[appId].name} — operación crítica`}
        danger
        size="md"
        footer={
          result ? (
            <Button variant="primary" onClick={onClose}>Cerrar</Button>
          ) : (
            <>
              <Button variant="ghost" onClick={onClose}>Cancelar</Button>
              <Button variant="danger" loading={loading} disabled={!confirmed || reason.trim().length < 4} onClick={() => void run()}>
                Exportar
              </Button>
            </>
          )
        }
      >
        {result ? (
          <div className="space-y-3">
            <div className="rounded-lg border border-rose-800/50 bg-rose-950/40 p-3 text-xs text-rose-200">
              Llave <strong>{result.kid}</strong> expuesta. Guárdala en un gestor de secretos y bórrala de este equipo en
              cuanto la transfieras.
            </div>
            <CodeBlock className="max-h-52">{result.pem}</CodeBlock>
            <Button
              size="sm"
              variant="secondary"
              icon={<Copy className="h-3.5 w-3.5" />}
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(result.pkcs8Base64);
                  toast.success('PKCS#8 Base64 copiado');
                } catch {
                  toast.warning('El navegador bloqueó el portapapeles');
                }
              }}
            >
              Copiar PKCS#8 (Base64)
            </Button>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="rounded-lg border border-rose-800/50 bg-rose-950/40 p-3 text-xs text-rose-100">
              <p className="flex items-center gap-2 font-semibold">
                <AlertTriangle className="h-4 w-4" /> Zona de máximo riesgo
              </p>
              <p className="mt-1">
                Con esta llave cualquiera puede emitir licencias válidas para {APPS[appId].name}. La operación se registra
                en la auditoría con tu usuario, IP y motivo.
              </p>
            </div>
            <Field label="Motivo de la exportación" required hint="Mínimo 4 caracteres; queda en el log de auditoría">
              <Textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} className="min-h-[64px]" />
            </Field>
            <label className="flex cursor-pointer items-start gap-2 text-xs text-slate-300">
              <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} className="mt-0.5 h-4 w-4 rounded border-slate-600 bg-slate-950 text-rose-600 focus:ring-rose-500" />
              Entiendo el riesgo y autorizo la exportación de la llave privada.
            </label>
          </div>
        )}
      </Modal>
      {viewport}
    </>
  );
}
