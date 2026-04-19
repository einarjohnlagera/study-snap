"use client";

import { cn } from "@/lib/utils";

type SkeletonProps = {
  className?: string;
};

export function Skeleton({ className }: Readonly<SkeletonProps>) {
  return <div className={cn("animate-pulse rounded bg-foreground/10", className)} aria-hidden="true" />;
}
