import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export default function BillingFailedPage() {
  return (
    <main className="mx-auto flex min-h-[calc(100dvh-4rem)] w-full max-w-3xl items-center px-4 py-8 sm:px-6">
      <Card className="w-full space-y-4 p-6 sm:p-8">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-amber-600 dark:text-amber-400">
            Payment not completed
          </p>
          <h1 className="text-2xl font-semibold sm:text-3xl">Payment failed or was cancelled</h1>
          <p className="text-sm leading-relaxed text-foreground/70 sm:text-base">
            No Premium changes were applied. You can try the upgrade flow again whenever you&apos;re ready.
          </p>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Link href="/settings#plan-billing" className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              Try again
            </Button>
          </Link>
        </div>
      </Card>
    </main>
  );
}
