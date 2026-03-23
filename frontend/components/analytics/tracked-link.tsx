"use client";

import type { MouseEventHandler, ReactNode } from "react";
import Link, { type LinkProps } from "next/link";
import { trackAnalyticsEvent, type AnalyticsEventType } from "@/lib/api";

type TrackedLinkProps = LinkProps & {
  children: ReactNode;
  className?: string;
  onClick?: MouseEventHandler<HTMLAnchorElement>;
  eventType: AnalyticsEventType;
  entityId?: string | null;
  eventMetadata?: Record<string, unknown>;
};

export function TrackedLink({
  children,
  className,
  onClick,
  eventType,
  entityId = null,
  eventMetadata,
  ...linkProps
}: TrackedLinkProps) {
  const handleClick: MouseEventHandler<HTMLAnchorElement> = (event) => {
    onClick?.(event);
    void trackAnalyticsEvent({
      eventType,
      entityId,
      metadata: eventMetadata,
    });
  };

  return (
    <Link {...linkProps} className={className} onClick={handleClick}>
      {children}
    </Link>
  );
}
