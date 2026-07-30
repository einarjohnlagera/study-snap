import type { ReactNode } from "react";

// Deliberately not <Card>: Card hardcodes p-6, and this codebase's cn() is a plain string
// join (no tailwind-merge), so passing p-0 via className does not override it — both classes
// land in the DOM and Tailwind's compiled stylesheet order decides the winner, not DOM order.
// Replicating Card's other base styles here, without the padding, sidesteps that entirely.
export function ResultGuidanceGroup({
  children,
  label,
  testId,
}: Readonly<{
  children: ReactNode;
  label: string;
  testId: string;
}>) {
  return (
    <div
      aria-label={label}
      data-testid={testId}
      className="motion-surface overflow-hidden rounded-xl border border-blue-500/25 bg-blue-500/5 shadow-sm"
    >
      <div className="divide-y divide-blue-500/15">{children}</div>
    </div>
  );
}
