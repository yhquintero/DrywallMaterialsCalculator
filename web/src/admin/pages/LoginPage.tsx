import React from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, ArrowRight, KeyRound, Lock, ShieldCheck, Eye, EyeOff, Wand2 } from 'lucide-react';
import { useAuth } from '../AuthContext';
import { Button, Field, Input, cn } from '../components/ui';

const DEV_DEFAULT_USER = 'admin';
const DEV_DEFAULT_PASS = 'Admin.Drywall2026!';

export function LoginPage() {
  const { user, loading, login, error, clearError, secureConnection, session } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [localError, setLocalError] = React.useState<string | null>(null);

  const redirectTo = (location.state as { from?: string } | null)?.from || '/admin';

  React.useEffect(() => {
    clearError();
  }, [username, password, clearError]);

  if (loading) {
    return (
      <div className="grid min-h-screen place-items-center bg-slate-950 text-slate-400">
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-700 border-t-brand-500" />
          <p className="text-sm">Verificando sesión…</p>
        </div>
      </div>
    );
  }

  if (user) return <Navigate to={redirectTo} replace />;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (!username.trim() || !password) {
      setLocalError('Introduce tu usuario y contraseña');
      return;
    }
    setSubmitting(true);
    try {
      const logged = await login(username.trim(), password);
      if (logged.mustChangePassword) {
        navigate('/admin/settings?cambiar=1', { replace: true });
      } else {
        navigate(redirectTo, { replace: true });
      }
    } catch {
      /* el mensaje llega por `error` del contexto */
    } finally {
      setSubmitting(false);
    }
  };

  const message = localError || error;
  const isDevHost =
    typeof window !== 'undefined' &&
    (['localhost', '127.0.0.1'].includes(window.location.hostname) ||
      window.location.hostname.endsWith('.e2b.app') ||
      /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(window.location.hostname));

  return (
    <div className="relative grid min-h-screen place-items-center overflow-hidden bg-slate-950 px-4 py-10">
      <div className="blueprint-grid pointer-events-none absolute inset-0 opacity-60" />
      <div className="pointer-events-none absolute -top-32 left-1/2 h-72 w-72 -translate-x-1/2 rounded-full bg-brand-600/20 blur-3xl" />

      <div className="relative w-full max-w-md">
        <div className="mb-4 flex justify-between items-center text-xs">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-slate-400 hover:text-brand-300 transition"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>Volver a la Calculadora</span>
          </Link>
          <span className="font-mono text-[11px] text-slate-500">/admin/licenses · Keygen RSA-4096</span>
        </div>

        <div className="mb-6 flex flex-col items-center text-center">
          <span className="grid h-14 w-14 place-items-center rounded-2xl bg-brand-600/15 text-brand-400 ring-1 ring-inset ring-brand-500/40">
            <KeyRound className="h-7 w-7" />
          </span>
          <h1 className="mt-4 text-2xl font-bold tracking-tight text-white">Consola de Licencias & Keygen</h1>
          <p className="mt-1 text-sm text-slate-400">
            Emisión y firma de licencias para <strong className="text-slate-200">DrywallPro Master</strong> y{' '}
            <strong className="text-slate-200">Keygen Pro</strong>
          </p>
        </div>

        <form
          onSubmit={submit}
          className="rounded-2xl border border-slate-800 bg-slate-900/80 p-6 shadow-2xl shadow-black/40 backdrop-blur"
        >
          <div className="space-y-4">
            <Field label="Usuario o correo" required>
              <Input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
                autoComplete="username"
                autoFocus
                spellCheck={false}
                maxLength={120}
              />
            </Field>

            <Field label="Contraseña" required>
              <div className="relative">
                <Input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  autoComplete="current-password"
                  maxLength={200}
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1.5 text-slate-500 transition hover:text-slate-300"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </Field>
          </div>

          {message ? (
            <div className="mt-4 flex items-start gap-2 rounded-lg border border-rose-700/40 bg-rose-950/60 px-3 py-2.5 text-xs text-rose-200">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <p>{message}</p>
            </div>
          ) : null}

          <Button type="submit" variant="primary" size="lg" loading={submitting} className="mt-5 w-full">
            Iniciar sesión
            {!submitting ? <ArrowRight className="h-4 w-4" /> : null}
          </Button>

          {isDevHost ? (
            <div className="mt-4 rounded-xl border border-brand-500/30 bg-brand-950/30 p-3 text-xs text-slate-300">
              <div className="flex items-center justify-between gap-2">
                <span className="font-semibold text-brand-300 flex items-center gap-1.5">
                  <Wand2 className="h-3.5 w-3.5" /> Acceso inicial de desarrollo
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setUsername(DEV_DEFAULT_USER);
                    setPassword(DEV_DEFAULT_PASS);
                    setLocalError(null);
                  }}
                  className="rounded-md bg-brand-600/30 px-2 py-1 text-[11px] font-semibold text-brand-200 ring-1 ring-inset ring-brand-500/40 transition hover:bg-brand-600/50 hover:text-white"
                >
                  Autocompletar
                </button>
              </div>
              <p className="mt-1.5 font-mono text-[11px] text-slate-400">
                usuario: <strong className="text-slate-200">{DEV_DEFAULT_USER}</strong> · contraseña:{' '}
                <strong className="text-slate-200">{DEV_DEFAULT_PASS}</strong>
              </p>
            </div>
          ) : null}

          <div className="mt-5 space-y-1.5 border-t border-slate-800 pt-4 text-[11px] text-slate-500">
            <p className="flex items-center gap-1.5">
              <ShieldCheck className={cn('h-3.5 w-3.5', secureConnection ? 'text-emerald-400' : 'text-amber-400')} />
              {secureConnection
                ? 'Conexión cifrada (HTTPS) — las credenciales viajan seguras.'
                : 'Conexión HTTP sin cifrar. En producción esta consola exige HTTPS.'}
            </p>
            <p className="flex items-center gap-1.5">
              <Lock className="h-3.5 w-3.5" />
              {session
                ? `Bloqueo tras ${session.maxFailedLogins} intentos fallidos durante ${session.lockMinutes} min · sesión de ${session.accessTtlMinutes} min`
                : 'Acceso por roles y permisos con auditoría completa.'}
            </p>
          </div>
        </form>

        <p className="mt-5 text-center text-[11px] text-slate-600">
          ¿Primera vez? El administrador inicial se crea al arrancar el servidor (`npm --prefix server run bootstrap`).
        </p>
      </div>
    </div>
  );
}
