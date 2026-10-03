/**
 * Primitivas de UI de la consola (tema oscuro profesional coherente con la app).
 * Componentes pequeños y sin dependencias para mantener el bundle ligero.
 */
import React from 'react';
import clsx from 'clsx';
import { twMerge } from 'tailwind-merge';
import { AlertTriangle, CheckCircle2, Info, Loader2, X, XCircle } from 'lucide-react';

export const cn = (...inputs: (string | undefined | null | false)[]) => twMerge(clsx(inputs));

/* ─────────────────────────────── Card ─────────────────────────────── */
export function Card({
  className,
  children,
  ...rest
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      {...rest}
      className={cn(
        'rounded-xl border border-slate-800 bg-slate-900/60 shadow-lg shadow-black/20 backdrop-blur',
        className
      )}
    >
      {children}
    </div>
  );
}

export function CardHeader({
  title,
  subtitle,
  icon,
  actions,
  className,
}: {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  icon?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex items-start justify-between gap-4 border-b border-slate-800 px-5 py-4', className)}>
      <div className="flex min-w-0 items-start gap-3">
        {icon ? <span className="mt-0.5 text-brand-400">{icon}</span> : null}
        <div className="min-w-0">
          <h2 className="truncate text-sm font-semibold tracking-tight text-slate-100">{title}</h2>
          {subtitle ? <p className="mt-0.5 text-xs text-slate-400">{subtitle}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function CardBody({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn('px-5 py-4', className)}>{children}</div>;
}

/* ─────────────────────────────── Button ─────────────────────────────── */
type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success' | 'warning';

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-500 ring-1 ring-inset ring-brand-500/40 disabled:hover:bg-brand-600',
  secondary: 'bg-slate-800 text-slate-100 hover:bg-slate-700 ring-1 ring-inset ring-slate-700',
  ghost: 'bg-transparent text-slate-300 hover:bg-slate-800 hover:text-white',
  danger: 'bg-rose-600/90 text-white hover:bg-rose-500 ring-1 ring-inset ring-rose-500/40',
  success: 'bg-emerald-600 text-white hover:bg-emerald-500 ring-1 ring-inset ring-emerald-500/40',
  warning: 'bg-amber-500 text-slate-950 hover:bg-amber-400 ring-1 ring-inset ring-amber-400/40',
};

export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  icon,
  className,
  children,
  disabled,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  icon?: React.ReactNode;
}) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-950',
        'disabled:cursor-not-allowed disabled:opacity-50',
        size === 'sm' && 'px-2.5 py-1.5 text-xs',
        size === 'md' && 'px-3.5 py-2 text-sm',
        size === 'lg' && 'px-5 py-2.5 text-base',
        BUTTON_STYLES[variant],
        className
      )}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : icon}
      {children}
    </button>
  );
}

/* ─────────────────────────────── Badge ─────────────────────────────── */
export function Badge({
  className,
  children,
  tone = 'slate',
}: {
  className?: string;
  children: React.ReactNode;
  tone?: 'slate' | 'green' | 'red' | 'amber' | 'sky' | 'violet';
}) {
  const tones: Record<string, string> = {
    slate: 'bg-slate-500/15 text-slate-300 ring-slate-500/30',
    green: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
    red: 'bg-rose-500/15 text-rose-300 ring-rose-500/30',
    amber: 'bg-amber-500/15 text-amber-300 ring-amber-500/30',
    sky: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
    violet: 'bg-violet-500/15 text-violet-300 ring-violet-500/30',
  };
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset',
        tones[tone],
        className
      )}
    >
      {children}
    </span>
  );
}

/* ─────────────────────────────── Inputs ─────────────────────────────── */
export function Field({
  label,
  hint,
  error,
  required,
  className,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <label className={cn('block', className)}>
      <span className="mb-1.5 flex items-center gap-1 text-xs font-medium text-slate-300">
        {label}
        {required ? <span className="text-rose-400">*</span> : null}
      </span>
      {children}
      {hint && !error ? <span className="mt-1 block text-[11px] text-slate-500">{hint}</span> : null}
      {error ? <span className="mt-1 block text-[11px] font-medium text-rose-400">{error}</span> : null}
    </label>
  );
}

export const inputClass =
  'w-full rounded-lg border border-slate-700 bg-slate-950/70 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 ' +
  'focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 disabled:opacity-50';

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={cn(inputClass, props.className)} />;
}

export function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={cn(inputClass, 'cursor-pointer', props.className)} />;
}

export function Textarea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={cn(inputClass, 'min-h-[80px]', props.className)} />;
}

export function Checkbox({
  label,
  className,
  ...rest
}: React.InputHTMLAttributes<HTMLInputElement> & { label: React.ReactNode }) {
  return (
    <label className={cn('flex cursor-pointer items-center gap-2 text-sm text-slate-300', className)}>
      <input
        {...rest}
        type="checkbox"
        className="h-4 w-4 rounded border-slate-600 bg-slate-950 text-brand-600 focus:ring-brand-500"
      />
      {label}
    </label>
  );
}

/* ─────────────────────────────── Modal ─────────────────────────────── */
export function Modal({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  size = 'md',
  danger = false,
}: {
  open: boolean;
  onClose: () => void;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  danger?: boolean;
}) {
  React.useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;
  const widths = { sm: 'max-w-md', md: 'max-w-2xl', lg: 'max-w-4xl', xl: 'max-w-6xl' };

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-950/80 p-4 backdrop-blur-sm sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        className={cn(
          'my-auto w-full rounded-2xl border border-slate-800 bg-slate-900 shadow-2xl shadow-black/60',
          widths[size]
        )}
      >
        <div className="flex items-start justify-between gap-4 border-b border-slate-800 px-5 py-4">
          <div className="min-w-0">
            <h3 className={cn('text-base font-semibold', danger ? 'text-rose-300' : 'text-slate-100')}>{title}</h3>
            {subtitle ? <p className="mt-0.5 text-xs text-slate-400">{subtitle}</p> : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-white"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="max-h-[70vh] overflow-y-auto px-5 py-4">{children}</div>
        {footer ? (
          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-800 px-5 py-3">{footer}</div>
        ) : null}
      </div>
    </div>
  );
}

/* ─────────────────────────────── Feedback ─────────────────────────────── */
export type ToastKind = 'success' | 'error' | 'info' | 'warning';

export function Toast({ kind, message, onClose }: { kind: ToastKind; message: string; onClose: () => void }) {
  React.useEffect(() => {
    const t = setTimeout(onClose, kind === 'error' ? 7000 : 4200);
    return () => clearTimeout(t);
  }, [kind, message, onClose]);

  const styles: Record<ToastKind, { wrap: string; Icon: typeof Info }> = {
    success: { wrap: 'border-emerald-600/40 bg-emerald-950/90 text-emerald-100', Icon: CheckCircle2 },
    error: { wrap: 'border-rose-600/40 bg-rose-950/90 text-rose-100', Icon: XCircle },
    info: { wrap: 'border-sky-600/40 bg-sky-950/90 text-sky-100', Icon: Info },
    warning: { wrap: 'border-amber-600/40 bg-amber-950/90 text-amber-100', Icon: AlertTriangle },
  };
  const { wrap, Icon } = styles[kind];

  return (
    <div className={cn('pointer-events-auto flex items-start gap-3 rounded-xl border px-4 py-3 shadow-xl', wrap)}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" />
      <p className="flex-1 text-sm">{message}</p>
      <button type="button" onClick={onClose} aria-label="Cerrar aviso" className="opacity-70 transition hover:opacity-100">
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

export function EmptyState({ icon, title, description, action }: { icon?: React.ReactNode; title: string; description?: string; action?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-14 text-center">
      {icon ? <div className="text-slate-600">{icon}</div> : null}
      <p className="text-sm font-medium text-slate-300">{title}</p>
      {description ? <p className="max-w-md text-xs text-slate-500">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-400">
      <Loader2 className="h-4 w-4 animate-spin" />
      {label ?? 'Cargando…'}
    </div>
  );
}

/* ─────────────────────────────── Tabla ─────────────────────────────── */
export function Table({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn('overflow-x-auto', className)}>
      <table className="w-full border-collapse text-left text-sm">{children}</table>
    </div>
  );
}

export function Th({ children, className }: { children?: React.ReactNode; className?: string }) {
  return (
    <th
      className={cn(
        'sticky top-0 z-10 whitespace-nowrap border-b border-slate-800 bg-slate-900/95 px-3 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400',
        className
      )}
    >
      {children}
    </th>
  );
}

export function Td({ children, className }: { children?: React.ReactNode; className?: string }) {
  return <td className={cn('border-b border-slate-800/60 px-3 py-2.5 align-middle text-slate-300', className)}>{children}</td>;
}

export function Tr({ children, className, ...rest }: React.HTMLAttributes<HTMLTableRowElement>) {
  return <tr {...rest} className={cn('transition hover:bg-slate-800/40', className)}>{children}</tr>;
}

/* ─────────────────────────────── Varios ─────────────────────────────── */
export function Stat({
  label,
  value,
  hint,
  icon,
  tone = 'slate',
}: {
  label: string;
  value: React.ReactNode;
  hint?: string;
  icon?: React.ReactNode;
  tone?: 'slate' | 'green' | 'red' | 'amber' | 'sky';
}) {
  const tones: Record<string, string> = {
    slate: 'text-slate-100',
    green: 'text-emerald-300',
    red: 'text-rose-300',
    amber: 'text-amber-300',
    sky: 'text-sky-300',
  };
  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[11px] font-medium uppercase tracking-wider text-slate-500">{label}</p>
          <p className={cn('mt-1 truncate text-2xl font-bold tabular-nums', tones[tone])}>{value}</p>
          {hint ? <p className="mt-1 truncate text-[11px] text-slate-500">{hint}</p> : null}
        </div>
        {icon ? <div className="shrink-0 rounded-lg bg-slate-800/70 p-2 text-slate-400">{icon}</div> : null}
      </div>
    </Card>
  );
}

export function KeyValue({ label, value, mono = false }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5 py-1.5 sm:flex-row sm:items-baseline sm:gap-3">
      <dt className="w-44 shrink-0 text-xs font-medium text-slate-500">{label}</dt>
      <dd className={cn('min-w-0 flex-1 break-words text-sm text-slate-200', mono && 'font-mono text-xs')}>{value}</dd>
    </div>
  );
}

export function CodeBlock({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <pre
      className={cn(
        'max-h-64 overflow-auto rounded-lg border border-slate-800 bg-slate-950 p-3 font-mono text-[11px] leading-relaxed text-emerald-200',
        className
      )}
    >
      {children}
    </pre>
  );
}
