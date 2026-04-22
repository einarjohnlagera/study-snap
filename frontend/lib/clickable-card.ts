import { cn } from "@/lib/utils";

const CARD_INTERACTION_TRANSITION =
  "transform-gpu transition-[transform,box-shadow,border-color,background-color] duration-150 ease-out";
const CARD_INTERACTION_FOCUS_RING =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background";

export function getSelectionCardClassName(options?: {
  selected?: boolean;
  disabled?: boolean;
  className?: string;
}) {
  const { selected = false, disabled = false, className } = options ?? {};

  return cn(
    "rounded-xl border bg-background text-left shadow-sm",
    CARD_INTERACTION_TRANSITION,
    CARD_INTERACTION_FOCUS_RING,
    disabled
      ? "cursor-not-allowed border-border bg-muted/10 opacity-60"
      : "cursor-pointer active:scale-[0.985]",
    disabled
      ? null
      : selected
        ? "border-blue-500 bg-blue-50/70 shadow-md dark:border-blue-400 dark:bg-blue-950/20"
        : "border-border hover:border-blue-300/80 hover:bg-blue-50/35 hover:shadow-md dark:hover:border-blue-700/70 dark:hover:bg-blue-950/15",
    className,
  );
}

export function getBrowsingCardClassName(className?: string) {
  return cn(
    "rounded-2xl border border-border bg-background shadow-sm",
    CARD_INTERACTION_TRANSITION,
    CARD_INTERACTION_FOCUS_RING,
    "cursor-pointer active:scale-[0.99] hover:border-border/90 hover:bg-highlight hover:shadow-md",
    className,
  );
}
