import * as React from "react";
import { LoadingSpinner } from "@/components/ui/loading-spinner";
import { cn } from "@/lib/utils";

type ButtonVariant = "default" | "secondary" | "outline" | "ghost" | "destructive" | "destructiveOutline";
type ButtonSize = "default" | "sm";

// `wrap` exists because `cn` is a plain join, NOT tailwind-merge -- passing `whitespace-normal` through
// `className` would leave BOTH it and the base `whitespace-nowrap` in the class list and let stylesheet
// order decide, which is not a contract we can rely on. So a wrapping button must not EMIT the conflicting
// class in the first place. Defaults keep every existing caller byte-identical.
const buttonVariants = ({
  variant = "default",
  size = "default",
  wrap = false,
  className,
}: {
  variant?: ButtonVariant;
  size?: ButtonSize;
  wrap?: boolean;
  className?: string;
}) =>
  cn(
    "motion-pressable inline-flex items-center justify-center rounded-lg text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50",
    wrap ? "whitespace-normal text-center" : "whitespace-nowrap",
    variant === "default" &&
      "bg-primary text-white shadow-sm transition-colors hover:bg-primary-hover active:bg-primary-active",
    variant === "secondary" &&
      "bg-muted text-foreground transition-colors hover:bg-muted/70 active:bg-muted/50",
    variant === "outline" &&
      "border border-border bg-background text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong dark:border-gray-600 dark:text-gray-200",
    variant === "ghost" &&
      "bg-transparent text-foreground/75 transition-colors hover:bg-highlight hover:text-foreground active:bg-highlight-strong dark:text-gray-300 dark:hover:text-gray-100",
    variant === "destructive" &&
      "bg-red-600 text-white shadow-sm transition-colors hover:bg-red-700 active:bg-red-800",
    variant === "destructiveOutline" &&
      "border border-red-500/50 bg-background text-red-600 transition-colors hover:border-red-500 hover:bg-red-50 active:bg-red-100 dark:border-red-500/40 dark:text-red-400 dark:hover:bg-red-950/40",
    size === "default" && (wrap ? "min-h-10 px-4 py-2" : "h-10 px-4 py-2"),
    size === "sm" && (wrap ? "min-h-9 px-3 py-1.5" : "h-9 px-3"),
    className,
  );

function Button({
  className,
  variant = "default",
  size = "default",
  wrap = false,
  loading = false,
  loadingText,
  disabled,
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  wrap?: boolean;
  loading?: boolean;
  loadingText?: string;
}) {
  return (
    <button
      className={buttonVariants({ variant, size, wrap, className })}
      disabled={loading || disabled}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? (
        <span className="inline-flex items-center justify-center gap-2">
          <LoadingSpinner />
          <span>{loadingText ?? children}</span>
        </span>
      ) : children}
    </button>
  );
}

export { Button, buttonVariants };
