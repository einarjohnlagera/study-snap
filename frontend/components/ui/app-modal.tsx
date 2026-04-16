"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { X } from "lucide-react";

type AppModalProps = {
  isOpen: boolean;
  title: ReactNode;
  description?: string;
  onClose: () => void;
  children?: ReactNode;
  actions?: ReactNode;
  panelClassName?: string;
  headerClassName?: string;
  contentClassName?: string;
  actionsClassName?: string;
  descriptionClassName?: string;
  headerActions?: ReactNode;
  enableSwipeToClose?: boolean;
  titleClassName?: string;
  titleIcon?: ReactNode;
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
  headerActions,
  enableSwipeToClose = false,
  titleClassName,
  titleIcon,
}: Readonly<AppModalProps>) {
  const titleId = useId();
  const descriptionId = useId();
  const panelRef = useRef<HTMLDivElement | null>(null);
  const touchStartYRef = useRef<number | null>(null);
  const touchStartXRef = useRef<number | null>(null);
  const [touchOffsetY, setTouchOffsetY] = useState(0);

  useEffect(() => {
    if (!isOpen) {
      setTouchOffsetY(0);
      touchStartYRef.current = null;
      touchStartXRef.current = null;
    }
  }, [isOpen]);

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
      className="motion-fade-enter fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4"
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
        className={`motion-modal-enter flex w-[90%] max-w-[420px] flex-col overflow-hidden max-h-[90dvh] rounded-xl border border-border bg-background p-4 shadow-xl transition-transform duration-200 dark:bg-zinc-900 sm:p-5 ${panelClassName ?? ""}`}
        style={touchOffsetY > 0 ? { transform: `translateY(${touchOffsetY}px)` } : undefined}
        onMouseDown={(event) => {
          event.stopPropagation();
        }}
        onClick={(event) => {
          event.stopPropagation();
        }}
        onTouchStart={(event) => {
          if (!enableSwipeToClose) {
            return;
          }
          touchStartYRef.current = event.touches[0]?.clientY ?? null;
          touchStartXRef.current = event.touches[0]?.clientX ?? null;
        }}
        onTouchMove={(event) => {
          if (!enableSwipeToClose || touchStartYRef.current === null || touchStartXRef.current === null) {
            return;
          }
          const currentY = event.touches[0]?.clientY ?? touchStartYRef.current;
          const currentX = event.touches[0]?.clientX ?? touchStartXRef.current;
          const deltaY = currentY - touchStartYRef.current;
          const deltaX = currentX - touchStartXRef.current;
          if (deltaY <= 0 || Math.abs(deltaX) > deltaY) {
            setTouchOffsetY(0);
            return;
          }
          setTouchOffsetY(deltaY);
        }}
        onTouchEnd={() => {
          if (!enableSwipeToClose) {
            return;
          }
          if (touchOffsetY > 80) {
            onClose();
          }
          setTouchOffsetY(0);
          touchStartYRef.current = null;
          touchStartXRef.current = null;
        }}
      >
        <div className={`flex shrink-0 items-start justify-between gap-4 ${headerClassName ?? ""}`}>
          <div className="min-w-0 space-y-2">
            <div className="flex items-start gap-3">
              {titleIcon ? (
                <div className="shrink-0 pt-0.5">
                  {titleIcon}
                </div>
              ) : null}
              <h2 id={titleId} className={`text-lg font-semibold text-foreground ${titleClassName ?? ""}`}>
                {title}
              </h2>
            </div>
            {description ? (
              <p
                id={descriptionId}
                className={`text-sm leading-relaxed text-foreground/80 ${descriptionClassName ?? ""}`}
              >
                {description}
              </p>
            ) : null}
          </div>
          <div className="flex shrink-0 items-center gap-1">
            {headerActions}
            <button
              type="button"
              onClick={onClose}
              aria-label="Close"
              className="rounded p-1 text-foreground/50 transition-colors hover:bg-highlight hover:text-foreground"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </div>
        {children ? <div className={`mt-4 flex-1 overflow-y-auto min-h-0 ${contentClassName ?? ""}`}>{children}</div> : null}
        {actions ? <div className={`mt-5 shrink-0 ${actionsClassName ?? ""}`}>{actions}</div> : null}
      </div>
    </div>
  );
}
