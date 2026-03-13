import Link from "next/link";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/page-header";

export function DashboardHero() {
  return (
    <section className="space-y-4">
      <PageHeader
        eyebrow="DASHBOARD"
        title="Dashboard"
        description="Your Study Library workspace. Revisit saved Study Packs, continue studying, and stay organized."
      />
      <Link href="/study" className="w-full sm:w-auto">
        <Button type="button" className="w-full sm:w-auto">New Study Pack</Button>
      </Link>
    </section>
  );
}
