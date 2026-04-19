import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export function DashboardLoading() {
  return (
    <div className="space-y-6">
      <Card className="space-y-3">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-6 w-56" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
        <div className="flex gap-2">
          <Skeleton className="h-9 w-32" />
          <Skeleton className="h-9 w-36" />
        </div>
      </Card>

      <Card className="space-y-3">
        <Skeleton className="h-5 w-44" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-4 w-40" />
        <div className="flex gap-2">
          <Skeleton className="h-9 w-24" />
          <Skeleton className="h-9 w-40" />
        </div>
      </Card>

      <div className="grid gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <Card key={`stat-skeleton-${index}`} className="space-y-2">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-8 w-12" />
          </Card>
        ))}
      </div>

      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-4 w-14" />
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={`grid-skeleton-${index}`} className="space-y-3">
              <Skeleton className="h-6 w-2/3" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
              <div className="flex gap-2">
                <Skeleton className="h-6 w-14 rounded-full" />
                <Skeleton className="h-6 w-20 rounded-full" />
              </div>
              <div className="flex gap-2">
                <Skeleton className="h-9 w-20" />
                <Skeleton className="h-9 w-20" />
              </div>
            </Card>
          ))}
        </div>
      </section>
    </div>
  );
}
