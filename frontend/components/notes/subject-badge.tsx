import { getSubjectDisplayLabel, hasExplicitSubject } from "@/lib/subjects";
import { cn } from "@/lib/utils";

type SubjectBadgeProps = {
  subject: string | null | undefined;
  className?: string;
};

export function SubjectBadge({ subject, className }: Readonly<SubjectBadgeProps>) {
  const isExplicit = hasExplicitSubject(subject);

  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium",
        isExplicit
          ? "border-blue-500/35 bg-blue-500/10 text-blue-700 dark:text-blue-300"
          : "border-border bg-muted/40 text-foreground/65",
        className,
      )}
    >
      {getSubjectDisplayLabel(subject)}
    </span>
  );
}
