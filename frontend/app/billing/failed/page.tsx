import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { getSafeRedirectPath } from "@/lib/auth";

type BillingFailedPageProps = {
  searchParams?: Promise<{
    returnUrl?: string | string[];
  }>;
};

function resolveReturnUrl(rawReturnUrl: string | string[] | undefined): string | null {
  const value = Array.isArray(rawReturnUrl) ? rawReturnUrl[0] : rawReturnUrl;
  return getSafeRedirectPath(value);
}

export default async function BillingFailedPage({ searchParams }: Readonly<BillingFailedPageProps>) {
  const resolvedSearchParams = searchParams ? await searchParams : {};
  const returnUrl = resolveReturnUrl(resolvedSearchParams.returnUrl);

  return (
    <main className="mx-auto flex min-h-[calc(100dvh-4rem)] w-full max-w-3xl items-center px-4 py-8 sm:px-6">
      <Card className="w-full space-y-6 p-6 sm:p-8">
        <div className="space-y-3">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-amber-600 dark:text-amber-400">
            Payment not completed
          </p>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Payment was cancelled or did not complete
          </h1>
          <p className="text-sm leading-relaxed text-foreground/75 sm:text-base">
            No charges were applied. You can try again anytime.
          </p>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Link href={returnUrl ?? "/pricing"} className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              {returnUrl ? "Try Again" : "Back to Pricing"}
            </Button>
          </Link>
          <Link href="/dashboard" className="w-full sm:w-auto">
            <Button type="button" variant="outline" className="w-full sm:w-auto">
              Go to Dashboard
            </Button>
          </Link>
        </div>
      </Card>
    </main>
  );
}
