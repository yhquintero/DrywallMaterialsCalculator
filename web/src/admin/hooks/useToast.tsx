import React, { useCallback, useRef, useState } from 'react';
import { Toast, type ToastKind } from '../components/ui';

interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

/** Notificaciones flotantes. `notify.error(err)` entiende ApiError del cliente API. */
export function useToast() {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const counter = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback((kind: ToastKind, message: string) => {
    counter.current += 1;
    const id = counter.current;
    setToasts((prev) => [...prev.slice(-3), { id, kind, message }]);
    return id;
  }, []);

  const fromError = useCallback(
    (err: unknown, fallback = 'Ocurrió un error') => {
      const message = err instanceof Error && err.message ? err.message : fallback;
      return push('error', message);
    },
    [push]
  );

  const viewport = (
    <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-full max-w-sm flex-col gap-2">
      {toasts.map((t) => (
        <Toast key={t.id} kind={t.kind} message={t.message} onClose={() => dismiss(t.id)} />
      ))}
    </div>
  );

  return {
    toast: { success: (m: string) => push('success', m), error: fromError, info: (m: string) => push('info', m), warning: (m: string) => push('warning', m) },
    viewport,
  };
}
