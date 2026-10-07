import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Activity,
  BadgeCheck,
  Calculator,
  ChevronsUpDown,
  FileKey2,
  KeyRound,
  LayoutDashboard,
  Lock,
  LogOut,
  Menu,
  Radio,
  ScrollText,
  Settings2,
  Shield,
  ShieldCheck,
  Smartphone,
  UserCog,
  Users,
  Wallet,
  Wand2,
  X,
} from 'lucide-react';
import { useAuth } from '../AuthContext';
import { cn } from './ui';
import { Can, InsecureConnectionBanner } from './Guard';
import { APPS } from '../permissions';

interface NavItem {
  to: string;
  label: string;
  icon: React.ReactNode;
  permission: string | string[];
  end?: boolean;
}

const NAV_SECTIONS: { title: string; items: NavItem[] }[] = [
  {
    title: 'General',
    items: [
      { to: '/admin', label: 'Panel', icon: <LayoutDashboard className="h-4 w-4" />, permission: 'dashboard.view', end: true },
    ],
  },
  {
    title: 'Keygen',
    items: [
      { to: '/admin/licenses', label: 'Licencias', icon: <BadgeCheck className="h-4 w-4" />, permission: 'licenses.view' },
      { to: '/admin/devices', label: 'Dispositivos', icon: <Smartphone className="h-4 w-4" />, permission: 'licenses.view' },
      { to: '/admin/keys', label: 'Claves de firma', icon: <KeyRound className="h-4 w-4" />, permission: 'keys.view' },
      { to: '/admin/plans', label: 'Planes y precios', icon: <Wallet className="h-4 w-4" />, permission: 'licenses.view' },
      { to: '/admin/rates', label: 'Tasas de cambio', icon: <Radio className="h-4 w-4" />, permission: 'rates.view' },
    ],
  },
  {
    title: 'Seguridad',
    items: [
      { to: '/admin/users', label: 'Usuarios', icon: <Users className="h-4 w-4" />, permission: 'users.view' },
      { to: '/admin/roles', label: 'Roles y permisos', icon: <UserCog className="h-4 w-4" />, permission: 'roles.view' },
      { to: '/admin/audit', label: 'Auditoría', icon: <ScrollText className="h-4 w-4" />, permission: 'audit.view' },
      { to: '/admin/settings', label: 'Configuración', icon: <Settings2 className="h-4 w-4" />, permission: ['system.health'] },
    ],
  },
];

export function AdminLayout() {
  const { user, logout, hasPermission, secureConnection } = useAuth();
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const [userMenu, setUserMenu] = React.useState(false);
  const navigate = useNavigate();

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => hasPermission(item.permission)),
  })).filter((section) => section.items.length > 0);

  const handleLogout = async () => {
    await logout();
    navigate('/admin/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <InsecureConnectionBanner />

      {/* ── Barra superior ─────────────────────────────────────────── */}
      <header className="sticky top-0 z-40 border-b border-slate-800 bg-slate-950/90 backdrop-blur">
        <div className="flex h-14 items-center gap-3 px-4">
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-800 hover:text-white lg:hidden"
            onClick={() => setSidebarOpen((v) => !v)}
            aria-label="Abrir menú"
          >
            {sidebarOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>

          <div className="flex items-center gap-2.5">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-600/20 text-brand-400 ring-1 ring-inset ring-brand-500/40">
              <Shield className="h-4.5 w-4.5" />
            </span>
            <div className="leading-tight">
              <p className="text-sm font-semibold tracking-tight">Consola de Licencias</p>
              <p className="text-[11px] text-slate-500">DrywallPro Master · Control de Keygen</p>
            </div>
          </div>

          <div className="ml-auto flex items-center gap-2">
            <span
              className={cn(
                'hidden items-center gap-1.5 rounded-md px-2 py-1 text-[11px] font-medium ring-1 ring-inset sm:inline-flex',
                secureConnection
                  ? 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/30'
                  : 'bg-amber-500/10 text-amber-300 ring-amber-500/30'
              )}
              title={secureConnection ? 'Conexión cifrada con TLS' : 'Conexión sin cifrar'}
            >
              {secureConnection ? <Lock className="h-3 w-3" /> : <ShieldCheck className="h-3 w-3" />}
              {secureConnection ? 'HTTPS' : 'HTTP'}
            </span>

            <Can permission="licenses.issue">
              <NavLink
                to="/admin/licenses?emitir=1"
                className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-brand-500"
                title="Crear y firmar una nueva licencia con Keygen"
              >
                <Wand2 className="h-3.5 w-3.5" />
                <span>Emitir licencia</span>
              </NavLink>
            </Can>

            <NavLink
              to="/"
              className="hidden items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-300 transition hover:bg-slate-800 hover:text-white md:inline-flex"
            >
              <Calculator className="h-4 w-4" />
              Calculadora
            </NavLink>

            <div className="relative">
              <button
                type="button"
                onClick={() => setUserMenu((v) => !v)}
                className="flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-2.5 py-1.5 text-left transition hover:border-slate-700"
              >
                <span className="grid h-7 w-7 place-items-center rounded-full bg-brand-600/25 text-xs font-bold text-brand-300">
                  {(user?.fullName || user?.username || '?').slice(0, 2).toUpperCase()}
                </span>
                <span className="hidden leading-tight sm:block">
                  <span className="block text-xs font-medium">{user?.fullName || user?.username}</span>
                  <span className="block text-[10px] text-slate-500">{user?.roleName}</span>
                </span>
                <ChevronsUpDown className="h-3.5 w-3.5 text-slate-500" />
              </button>

              {userMenu ? (
                <>
                  <div className="fixed inset-0 z-10" onClick={() => setUserMenu(false)} aria-hidden />
                  <div className="absolute right-0 z-20 mt-2 w-64 rounded-xl border border-slate-800 bg-slate-900 p-2 shadow-2xl">
                    <div className="border-b border-slate-800 px-2 pb-2">
                      <p className="text-xs font-semibold">{user?.fullName || user?.username}</p>
                      <p className="truncate text-[11px] text-slate-500">{user?.email || '@' + user?.username}</p>
                      <p className="mt-1 inline-flex items-center gap-1 rounded bg-slate-800 px-1.5 py-0.5 text-[10px] text-brand-300">
                        <Activity className="h-3 w-3" />
                        {user?.roleCode} · nivel {user?.roleLevel}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setUserMenu(false);
                        navigate('/admin/settings');
                      }}
                      className="mt-1 flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white"
                    >
                      <Settings2 className="h-3.5 w-3.5" /> Configuración y contraseña
                    </button>
                    <button
                      type="button"
                      onClick={handleLogout}
                      className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-xs text-rose-300 transition hover:bg-rose-950/60"
                    >
                      <LogOut className="h-3.5 w-3.5" /> Cerrar sesión
                    </button>
                  </div>
                </>
              ) : null}
            </div>
          </div>
        </div>
      </header>

      <div className="flex">
        {/* ── Sidebar ─────────────────────────────────────────────── */}
        <aside
          className={cn(
            'fixed inset-y-0 left-0 z-30 w-64 shrink-0 border-r border-slate-800 bg-slate-950 pt-14 transition-transform lg:sticky lg:top-14 lg:h-[calc(100vh-3.5rem)] lg:translate-x-0 lg:pt-0',
            sidebarOpen ? 'translate-x-0' : '-translate-x-full'
          )}
        >
          <nav className="h-full overflow-y-auto px-3 py-4">
            <div className="mb-4 rounded-lg border border-slate-800 bg-slate-900/60 p-3">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Apps controladas</p>
              <ul className="mt-2 space-y-1.5">
                <li className="flex items-center gap-2 text-xs text-slate-300">
                  <FileKey2 className="h-3.5 w-3.5 text-brand-400" />
                  {APPS.drywall_calculator.name}
                </li>
                <li className="flex items-center gap-2 text-xs text-slate-300">
                  <KeyRound className="h-3.5 w-3.5 text-sky-400" />
                  {APPS.keygen_pro.name}
                </li>
              </ul>
              <Can permission="licenses.issue">
                <NavLink
                  to="/admin/licenses?emitir=1"
                  onClick={() => setSidebarOpen(false)}
                  className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-lg bg-brand-600/20 px-2.5 py-1.5 text-xs font-semibold text-brand-300 ring-1 ring-inset ring-brand-500/40 transition hover:bg-brand-600/30 hover:text-white"
                >
                  <Wand2 className="h-3.5 w-3.5" />
                  Crear licencia (Keygen)
                </NavLink>
              </Can>
            </div>

            {sections.map((section) => (
              <div key={section.title} className="mb-5">
                <p className="mb-1.5 px-2 text-[10px] font-semibold uppercase tracking-wider text-slate-600">
                  {section.title}
                </p>
                <ul className="space-y-0.5">
                  {section.items.map((item) => (
                    <li key={item.to}>
                      <NavLink
                        to={item.to}
                        end={item.end}
                        onClick={() => setSidebarOpen(false)}
                        className={({ isActive }) =>
                          cn(
                            'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition',
                            isActive
                              ? 'bg-brand-600/15 font-medium text-brand-300 ring-1 ring-inset ring-brand-500/30'
                              : 'text-slate-400 hover:bg-slate-800/70 hover:text-slate-100'
                          )
                        }
                      >
                        {item.icon}
                        {item.label}
                      </NavLink>
                    </li>
                  ))}
                </ul>
              </div>
            ))}

            <div className="mt-6 rounded-lg border border-slate-800/70 bg-slate-900/40 p-3 text-[11px] leading-relaxed text-slate-500">
              <p className="flex items-center gap-1.5 font-medium text-slate-400">
                <ShieldCheck className="h-3.5 w-3.5 text-brand-400" /> Sesión protegida
              </p>
              <p className="mt-1">
                Tokens JWT de corta duración, refresh en cookie httpOnly y auditoría de cada acción.
              </p>
            </div>
          </nav>
        </aside>

        {sidebarOpen ? (
          <div className="fixed inset-0 z-20 bg-slate-950/70 lg:hidden" onClick={() => setSidebarOpen(false)} aria-hidden />
        ) : null}

        {/* ── Contenido ───────────────────────────────────────────── */}
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-xl font-bold tracking-tight text-slate-50">{title}</h1>
        {description ? <p className="mt-1 max-w-3xl text-sm text-slate-400">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}
