import * as React from "react";
import { cn } from "@/lib/utils";

function Card({ className, ...props }: Readonly<React.HTMLAttributes<HTMLDivElement>>) {
  return (
    <div
      className={cn(
        "motion-surface rounded-xl border border-border bg-surface-alt p-6 shadow-sm",
        className,
      )}
      {...props}
    />
  );
}

function CardTitle({
  className,
  ...props
}: Readonly<React.HTMLAttributes<HTMLHeadingElement>>) {
  return (
    <h3 className={cn("text-lg font-semibold text-foreground", className)} {...props} />
  );
}

function CardDescription({
  className,
  ...props
}: Readonly<React.HTMLAttributes<HTMLParagraphElement>>) {
  return (
    <p
      className={cn("text-base leading-relaxed text-foreground/75", className)}
      {...props}
    />
  );
}

export { Card, CardDescription, CardTitle };
