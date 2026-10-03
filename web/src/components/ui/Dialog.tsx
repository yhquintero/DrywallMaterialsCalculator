import React, { useCallback, useEffect, useRef } from 'react';

/**
 * Contenedor de diálogo accesible reutilizable.
 *
 * Resuelve de una sola vez los requisitos de accesibilidad que la revisión
 * técnica detectó como ausentes en los modales de la calculadora:
 *
 *   • Semántica correcta: `role="dialog"` + `aria-modal="true"` + nombre accesible.
 *   • Cierre con la tecla `Escape`.
 *   • Trampa de foco: `Tab`/`Shift+Tab` no salen del diálogo.
 *   • Foco inicial en el primer control y devolución del foco al elemento que
 *     abrió el diálogo al cerrarlo.
 *   • Bloqueo del desplazamiento del documento de fondo.
 *   • Cierre al pulsar el fondo (configurable).
 *
 * Sólo se monta cuando `isOpen` es verdadero, por lo que el ciclo de vida del
 * foco queda ligado a la apertura real del modal.
 */

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',');

interface DialogProps {
  isOpen: boolean;
  onClose: () => void;
  /** Nombre accesible del diálogo (se aplica como `aria-label`). */
  label: string;
  /** Identificador del elemento que describe el contenido (`aria-describedby`). */
  describedBy?: string;
  backdropClassName?: string;
  panelClassName?: string;
  /** Cerrar al pulsar fuera del panel (por defecto sí). */
  closeOnBackdrop?: boolean;
  children: React.ReactNode;
}

export const Dialog: React.FC<DialogProps> = ({
  isOpen,
  onClose,
  label,
  describedBy,
  backdropClassName = 'fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm',
  panelClassName = 'bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-3xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden',
  closeOnBackdrop = true,
  children
}) => {
  const panelRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);
  /** Evita que una pulsación iniciada dentro del panel cierre el diálogo. */
  const pointerDownInside = useRef(false);

  const focusFirstControl = useCallback(() => {
    const panel = panelRef.current;
    if (!panel) return;
    const target =
      panel.querySelector<HTMLElement>('[data-autofocus]') ||
      panel.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
    (target || panel).focus({ preventScroll: true });
  }, []);

  // Foco inicial + restauración al cerrar + bloqueo del scroll de fondo.
  useEffect(() => {
    if (!isOpen) return;
    previouslyFocused.current = document.activeElement as HTMLElement | null;

    const { overflow, paddingRight } = document.body.style;
    const scrollbar = window.innerWidth - document.documentElement.clientWidth;
    document.body.style.overflow = 'hidden';
    if (scrollbar > 0) document.body.style.paddingRight = `${scrollbar}px`;

    // El foco se aplica tras el primer pintado para asegurar que el contenido
    // (incluido el cargado en diferido) ya está en el DOM.
    const raf = window.requestAnimationFrame(focusFirstControl);

    return () => {
      window.cancelAnimationFrame(raf);
      document.body.style.overflow = overflow;
      document.body.style.paddingRight = paddingRight;
      previouslyFocused.current?.focus?.({ preventScroll: true });
    };
  }, [isOpen, focusFirstControl]);

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.stopPropagation();
      onClose();
      return;
    }
    if (event.key !== 'Tab') return;

    const panel = panelRef.current;
    if (!panel) return;
    const focusables = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
      (element) => element.offsetParent !== null || element === document.activeElement
    );
    if (!focusables.length) {
      event.preventDefault();
      panel.focus({ preventScroll: true });
      return;
    }
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement as HTMLElement | null;

    if (event.shiftKey && (active === first || !panel.contains(active))) {
      event.preventDefault();
      last.focus({ preventScroll: true });
    } else if (!event.shiftKey && (active === last || !panel.contains(active))) {
      event.preventDefault();
      first.focus({ preventScroll: true });
    }
  };

  if (!isOpen) return null;

  return (
    <div
      className={backdropClassName}
      onKeyDown={handleKeyDown}
      onPointerDown={(event) => {
        pointerDownInside.current = panelRef.current?.contains(event.target as Node) ?? false;
      }}
      onMouseDown={(event) => {
        if (!closeOnBackdrop) return;
        if (panelRef.current?.contains(event.target as Node)) return;
        if (pointerDownInside.current) return;
        onClose();
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        aria-describedby={describedBy}
        tabIndex={-1}
        className={`${panelClassName} focus:outline-none`}
      >
        {children}
      </div>
    </div>
  );
};
