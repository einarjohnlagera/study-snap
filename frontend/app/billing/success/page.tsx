import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type BillingSuccessPageProps = {
  searchParams?: Promise<{
    returnUrl?: string | string[];
    plan?: string | string[];
  }>;
};

function resolveReturnUrl(rawReturnUrl: string | string[] | undefined): string | null {
  const value = Array.isArray(rawReturnUrl) ? rawReturnUrl[0] : rawReturnUrl;
  if (!value || !value.startsWith("/") || value.startsWith("//")) return null;
  return value;
}

function shouldReturnToDashboard(returnUrl: string | null): boolean {
  if (!returnUrl) {
    return true;
  }
  const pathname = returnUrl.split("?")[0]?.split("#")[0] ?? returnUrl;
  return pathname === "/settings"
    || pathname.startsWith("/settings/")
    || pathname === "/billing"
    || pathname.startsWith("/billing/");
}

function resolvePlanLabel(rawPlan: string | string[] | undefined): "Plus" | "Pro" {
  const value = Array.isArray(rawPlan) ? rawPlan[0] : rawPlan;
  return value === "PLUS" ? "Plus" : "Pro";
}

export default async function BillingSuccessPage({ searchParams }: Readonly<BillingSuccessPageProps>) {
  const resolvedSearchParams = searchParams ? await searchParams : {};
  const returnUrl = resolveReturnUrl(resolvedSearchParams.returnUrl);
  const planLabel = resolvePlanLabel(resolvedSearchParams.plan);
  const useDashboardPrimary = shouldReturnToDashboard(returnUrl);
  const primaryHref = useDashboardPrimary ? "/dashboard" : returnUrl ?? "/dashboard";
  const primaryLabel = useDashboardPrimary ? "Go to Dashboard" : "Continue where you left off";

  return (
    <main className="mx-auto flex min-h-[calc(100dvh-4rem)] w-full max-w-3xl items-center px-4 py-8 sm:px-6">
      <Card className="w-full space-y-6 p-6 sm:p-8">
        <div className="space-y-3">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-emerald-600 dark:text-emerald-400">
            Payment successful
          </p>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Your NoteLib {planLabel} access is now active
          </h1>
          <p className="text-sm leading-relaxed text-foreground/75 sm:text-base">
            {planLabel === "Pro"
              ? "You now have the highest limits, Adaptive Practice, difficulty selection, Board Exam Mode, and unlimited exports."
              : "You now have higher monthly limits, more exports, and more room to keep studying without hitting Free plan caps."}
          </p>
          <p className="text-xs text-foreground/60 sm:text-sm">
            Paid access is activated after payment confirmation. If your access does not update immediately, refresh after a few seconds.
          </p>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Link href={primaryHref} className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              {primaryLabel}
            </Button>
          </Link>
          {!useDashboardPrimary && returnUrl ? (
            <Link href="/dashboard" className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Go to Dashboard
              </Button>
            </Link>
          ) : null}
        </div>
      </Card>
    </main>
  );
}
