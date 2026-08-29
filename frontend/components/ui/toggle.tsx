"use client";

type ToggleProps = {
  id?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  ariaLabel: string;
  disabled?: boolean;
};

export function Toggle({
  id,
  checked,
  onChange,
  ariaLabel,
  disabled = false,
}: Readonly<ToggleProps>) {
  return (
    <button
      id={id}
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={ariaLabel}
      // ⚠️ aria-disabled, NOT the native `disabled` attribute. A disabled <button> is removed from
      // the tab order entirely, so a keyboard or screen-reader user cannot reach the control, hear
      // that it exists, or find out why it is unavailable — on a sharing switch that is exactly the
      // information they need. Staying focusable keeps it discoverable; the click is ignored below.
      aria-disabled={disabled || undefined}
      onClick={() => {
        if (disabled) return;
        onChange(!checked);
      }}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 aria-disabled:cursor-not-allowed aria-disabled:opacity-50 ${
        checked
          ? "border-blue-600 bg-blue-600"
          : "border-border bg-foreground/15"
      }`}
    >
      <span
        aria-hidden="true"
        className={`h-5 w-5 rounded-full bg-white shadow-sm transition-transform ${
          checked ? "translate-x-5" : "translate-x-0"
        }`}
      />
    </button>
  );
}
