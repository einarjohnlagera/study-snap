type ToastTone = "success" | "error" | "info" | "warning";

type ToastMessageProps = {
  message: string;
  tone?: ToastTone;
};

function toneClasses(tone: ToastTone): string {
  if (tone === "success") {
    return "border-emerald-400/70 bg-emerald-50 text-emerald-900 dark:border-emerald-700/70 dark:bg-emerald-950/40 dark:text-emerald-200";
  }
  if (tone === "error") {
    return "border-red-400/70 bg-red-50 text-red-900 dark:border-red-700/70 dark:bg-red-950/40 dark:text-red-200";
  }
  if (tone === "warning") {
    return "border-amber-400/70 bg-amber-50 text-amber-900 dark:border-amber-500/70 dark:bg-amber-950/50 dark:text-amber-200";
  }
  return "border-blue-400/70 bg-blue-50 text-blue-900 dark:border-blue-700/70 dark:bg-blue-950/40 dark:text-blue-200";
}

export function ToastMessage({ message, tone = "info" }: ToastMessageProps) {
  return (
    <div
      className={`fixed bottom-4 right-4 z-50 max-w-sm rounded-md border px-4 py-3 text-sm shadow-sm ${toneClasses(tone)}`}
      role="status"
      aria-live="polite"
    >
      {message}
    </div>
  );
}
