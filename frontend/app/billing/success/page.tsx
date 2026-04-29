import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export default function BillingSuccessPage() {
  return (
    <main className="mx-auto flex min-h-[calc(100dvh-4rem)] w-full max-w-3xl items-center px-4 py-8 sm:px-6">
      <Card className="w-full space-y-4 p-6 sm:p-8">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-emerald-600 dark:text-emerald-400">
            Payment successful
          </p>
          <h1 className="text-2xl font-semibold sm:text-3xl">Your Premium access is now active</h1>
          <p className="text-sm leading-relaxed text-foreground/70 sm:text-base">
            You can go back to your dashboard and continue studying with Premium features unlocked.
          </p>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Link href="/dashboard" className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              Go to Dashboard
            </Button>
          </Link>
        </div>
      </Card>
    </main>
  );
}
