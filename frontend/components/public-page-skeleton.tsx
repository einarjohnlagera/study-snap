const pulse = "animate-pulse rounded bg-foreground/10 motion-reduce:animate-none";

export function PublicListingSkeleton() {
  return (
    <main aria-busy="true" data-public-page-skeleton="listing" className="mx-auto w-full max-w-6xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <header className="space-y-3 rounded-3xl border border-blue-500/20 bg-linear-to-br from-blue-500/10 via-background to-amber-500/10 p-6 shadow-sm sm:p-8">
        <div aria-hidden="true" className={`${pulse} h-4 w-32`} />
        <div aria-hidden="true" className={`${pulse} h-8 w-2/3`} />
      </header>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }, (_, index) => (
          <div key={index} aria-hidden="true" className="space-y-4 rounded-2xl border border-border p-4 shadow-sm sm:p-5">
            <div aria-hidden="true" className={`${pulse} h-5 w-3/4`} />
            <div aria-hidden="true" className={`${pulse} h-4 w-full`} />
            <div aria-hidden="true" className={`${pulse} h-4 w-2/3`} />
          </div>
        ))}
      </div>
    </main>
  );
}
