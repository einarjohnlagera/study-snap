import Link from "next/link";
import { Button } from "@/components/ui/button";

export function DashboardHero() {
  return (
    <section className="space-y-4 rounded-xl border border-border bg-gray-50 p-4 shadow-sm dark:bg-gray-950/40 sm:p-6">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Dashboard</h1>
        <p className="max-w-2xl text-sm text-foreground/75">
          Your Study Library workspace. Revisit saved Study Packs, continue studying, and stay organized.
        </p>
      </div>
      <Link href="/study">
        <Button type="button" className="w-full sm:w-auto">New Study Pack</Button>
      </Link>
    </section>
  );
}
