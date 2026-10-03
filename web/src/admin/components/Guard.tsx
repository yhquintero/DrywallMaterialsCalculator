import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { ShieldAlert, ShieldX } from 'lucide-react';
import { useAuth } from '../AuthContext';
import { Button, Card } from './ui';

/** Bloquea el acceso a rutas que requieren sesión iniciada. */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950">
        <div className="flex flex-col items-center gap-3 text-slate-400">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-700 border-t-brand-500" />
          <p className="text-sm">Verificando sesión segura…</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/admin/login" replace state={{ from: location.pathname + location.search }} />;
  }

  return <>{children}</>;
}

/** Bloquea por permisos granulares; muestra el motivo exacto si se deniega. */
export function RequirePermission({
  permission,
  children,
  fallback,
}: {
  permission: string | string[];
  children: React.ReactNode;
  fallback?: React.ReactNode;
}) {
  const { hasPermission, user } = useAuth();
  const required = Array.isArray(permission) ? permission : [permission];

  if (hasPermission(required)) return <>{children}</>;

  if (fallback) return <>{fallback}</>;

  return (
    <Card className="mx-auto max-w-lg">
      <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
        <ShieldX className="h-10 w-10 text-rose-400" />
        <h2 className="text-base font-semibold text-slate-100">Acceso denegado</h2>
        <p className="text-sm text-slate-400">
          Tu rol <strong className="text-slate-200">{user?.roleName}</strong> no incluye el permiso{' '}
          <code className="rounded bg-slate-800 px-1.5 py-0.5 font-mono text-xs text-amber-300">{required.join(', ')}</code>.
        </p>
        <p className="text-xs text-slate-500">
          El intento quedó registrado en la auditoría. Solicita el permiso a un administrador.
        </p>
      </div>
    </Card>
  );
}

/** Oculta elementos de la UI si falta el permiso (botones, columnas, acciones). */
export function Can({
  permission,
  children,
  else: elseNode = null,
}: {
  permission: string | string[];
  children: React.ReactNode;
  else?: React.ReactNode;
}) {
  const { hasPermission } = useAuth();
  return <>{hasPermission(permission) ? children : elseNode}</>;
}

/** Aviso visible cuando la consola no está sirviéndose por HTTPS. */
export function InsecureConnectionBanner() {
  const { secureConnection } = useAuth();
  const [dismissed, setDismissed] = React.useState(false);
  if (secureConnection || dismissed) return null;

  const isLocal = ['localhost', '127.0.0.1'].includes(window.location.hostname) || window.location.hostname.endsWith('.e2b.app');

  return (
    <div className="flex items-start gap-3 border-b border-amber-600/40 bg-amber-950/70 px-4 py-2.5 text-amber-100">
      <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="flex-1 text-xs">
        <p className="font-semibold">Conexión sin cifrar (HTTP)</p>
        <p className="mt-0.5 text-amber-200/80">
          {isLocal
            ? 'Estás en un entorno local de desarrollo. Activa TLS con `npm run dev:https` para probar el flujo completo.'
            : 'La consola debe servirse SOLO por HTTPS: las credenciales y las llaves de licencia viajan sin cifrar. Configura el certificado TLS en el reverse proxy.'}
        </p>
      </div>
      <Button size="sm" variant="ghost" onClick={() => setDismissed(true)} className="text-amber-200 hover:bg-amber-900/50">
        Ocultar
      </Button>
    </div>
  );
}
