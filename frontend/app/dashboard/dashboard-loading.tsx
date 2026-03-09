import { Card } from "@/components/ui/card";

export function DashboardLoading() {
  return (
    <div className="space-y-6">
      <Card className="space-y-3">
        <div className="h-7 w-48 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-80 animate-pulse rounded bg-foreground/10" />
        <div className="h-10 w-36 animate-pulse rounded bg-foreground/10" />
      </Card>

      <Card className="space-y-3">
        <div className="h-5 w-44 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
      </Card>

      <div className="grid gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <Card key={`stat-skeleton-${index}`} className="space-y-2">
            <div className="h-3 w-24 animate-pulse rounded bg-foreground/10" />
            <div className="h-8 w-12 animate-pulse rounded bg-foreground/10" />
          </Card>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {Array.from({ length: 4 }).map((_, index) => (
          <Card key={`grid-skeleton-${index}`} className="space-y-3">
            <div className="h-5 w-2/3 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
            <div className="h-9 w-24 animate-pulse rounded bg-foreground/10" />
          </Card>
        ))}
      </div>
    </div>
  );
}
