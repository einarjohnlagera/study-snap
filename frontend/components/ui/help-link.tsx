"use client";

import Link from "next/link";
import { ArrowRight, HelpCircle } from "lucide-react";

type HelpLinkProps = Readonly<{
  /** The Help guide card id to deep-link into (e.g. "progress-focus"). Opens `/help#{guideId}`. */
  guideId: string;
  /** Link text. Defaults to "How this works". */
  label?: string;
  /** Show a small help icon before the label. */
  withIcon?: boolean;
  className?: string;
}>;

/**
 * A small, persistent reference link co-located with a complex feature.
 * Deep-links into a specific Help guide via URL hash (`/help#{guideId}`).
 *
 * This is the reference-grade counterpart to {@link GuidanceTip}: it never
 * disappears, so users can re-read how a feature works (e.g. next term).
 * Pair it with a one-sentence inline gist on the surface itself — the link is
 * the depth path, not the only explanation.
 */
export function HelpLink({ guideId, label = "How this works", withIcon = false, className }: HelpLinkProps) {
  return (
    <Link
      href={`/help#${guideId}`}
      className={[
        "inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline dark:text-blue-400",
        className ?? "",
      ]
        .join(" ")
        .trim()}
    >
      {withIcon ? <HelpCircle className="h-3.5 w-3.5" aria-hidden="true" /> : null}
      {label}
      <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
    </Link>
  );
}
