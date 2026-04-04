"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

type AppModalProps = {
  isOpen: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children?: ReactNode;
  actions?: ReactNode;
  panelClassName?: string;
  headerClassName?: string;
  contentClassName?: string;
  actionsClassName?: string;
  descriptionClassName?: string;
};

function getFocusableElements(container: HTMLDivElement | null): HTMLElement[] {
  if (!container) {
    return [];
  }
  const selector = [
    "button:not([disabled])",
    "a[href]",
    "input:not([disabled])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    "[tabindex]:not([tabindex='-1'])",
  ].join(",");
  return Array.from(container.querySelectorAll<HTMLElement>(selector)).filter(
    (element) => !element.hasAttribute("disabled") && element.tabIndex !== -1,
  );
}

export function AppModal({
  isOpen,
  title,
  description,
  onClose,
  children,
  actions,
  panelClassName,
  headerClassName,
  contentClassName,
  actionsClassName,
  descriptionClassName,
}: Readonly<AppModalProps>) {
  const titleId = useId();
  const descriptionId = useId();
  const panelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const previousActiveElement = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const focusFirst = () => {
      const focusable = getFocusableElements(panelRef.current);
      if (focusable.length > 0) {
        focusable[0].focus();
      } else {
        panelRef.current?.focus();
      }
    };
    const frameId = globalThis.requestAnimationFrame(focusFirst);

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== "Tab") {
        return;
      }

      const focusable = getFocusableElements(panelRef.current);
      if (focusable.length === 0) {
        event.preventDefault();
        panelRef.current?.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1]!;
      const active = document.activeElement as HTMLElement | null;
      const isInside = Boolean(active && panelRef.current?.contains(active));

      if (event.shiftKey) {
        if (!isInside || active === first) {
          event.preventDefault();
          last.focus();
        }
        return;
      }

      if (!isInside || active === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      globalThis.cancelAnimationFrame(frameId);
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousActiveElement?.focus();
    };
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4"
      onMouseDown={(event) => {
        event.stopPropagation();
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
      onClick={(event) => {
        event.stopPropagation();
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className={`w-[90%] max-w-[420px] rounded-xl border border-border bg-background p-4 shadow-xl dark:bg-zinc-900 sm:p-5 ${panelClassName ?? ""}`}
        onMouseDown={(event) => {
          event.stopPropagation();
        }}
        onClick={(event) => {
          event.stopPropagation();
        }}
      >
        <div className={`space-y-2 ${headerClassName ?? ""}`}>
          <h2 id={titleId} className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          {description ? (
            <p
              id={descriptionId}
              className={`text-sm leading-relaxed text-foreground/80 ${descriptionClassName ?? ""}`}
            >
              {description}
            </p>
          ) : null}
        </div>
        {children ? <div className={`mt-4 ${contentClassName ?? ""}`}>{children}</div> : null}
        {actions ? <div className={`mt-5 ${actionsClassName ?? ""}`}>{actions}</div> : null}
      </div>
    </div>
  );
}
