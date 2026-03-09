import { Card } from "@/components/ui/card";

export function DashboardLoading() {
  return (
    <div className="space-y-6">
      <Card className="space-y-3">
        <div className="h-4 w-32 animate-pulse rounded bg-foreground/10" />
        <div className="h-6 w-56 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
        <div className="flex gap-2">
          <div className="h-9 w-32 animate-pulse rounded bg-foreground/10" />
          <div className="h-9 w-36 animate-pulse rounded bg-foreground/10" />
        </div>
      </Card>

      <Card className="space-y-3">
        <div className="h-5 w-44 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-40 animate-pulse rounded bg-foreground/10" />
        <div className="flex gap-2">
          <div className="h-9 w-24 animate-pulse rounded bg-foreground/10" />
          <div className="h-9 w-40 animate-pulse rounded bg-foreground/10" />
        </div>
      </Card>

      <div className="grid gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <Card key={`stat-skeleton-${index}`} className="space-y-2">
            <div className="h-3 w-24 animate-pulse rounded bg-foreground/10" />
            <div className="h-8 w-12 animate-pulse rounded bg-foreground/10" />
          </Card>
        ))}
      </div>

      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="h-6 w-40 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-14 animate-pulse rounded bg-foreground/10" />
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={`grid-skeleton-${index}`} className="space-y-3">
              <div className="h-6 w-2/3 animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-1/2 animate-pulse rounded bg-foreground/10" />
              <div className="flex gap-2">
                <div className="h-6 w-14 animate-pulse rounded-full bg-foreground/10" />
                <div className="h-6 w-20 animate-pulse rounded-full bg-foreground/10" />
              </div>
              <div className="flex gap-2">
                <div className="h-9 w-20 animate-pulse rounded bg-foreground/10" />
                <div className="h-9 w-20 animate-pulse rounded bg-foreground/10" />
              </div>
            </Card>
          ))}
        </div>
      </section>
    </div>
  );
}
