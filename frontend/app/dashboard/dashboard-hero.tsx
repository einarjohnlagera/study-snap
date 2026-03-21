import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";

type DashboardHeroProps = {
  greetingName: string;
};

function resolveGreetingLabel(hour: number): string {
  if (hour >= 5 && hour <= 11) {
    return "Good morning";
  }
  if (hour >= 12 && hour <= 17) {
    return "Good afternoon";
  }
  return "Good evening";
}

export function DashboardHero({ greetingName }: DashboardHeroProps) {
  const greetingLabel = resolveGreetingLabel(new Date().getHours());

  return (
    <section className="space-y-4">
      <Card className="space-y-1 p-4 sm:p-6">
        <h2 className="text-xl font-semibold sm:text-2xl">
          {greetingLabel}, {greetingName}
        </h2>
        <p className="text-sm text-foreground/75">Ready to continue your studies?</p>
      </Card>
      <PageHeader
        eyebrow="DASHBOARD"
        title="Dashboard"
        description="Your note workspace. Revisit saved notes, continue studying, and stay organized."
      />
      <Card className="space-y-3 p-4 sm:p-6">
        <h2 className="text-lg font-semibold sm:text-xl">New Note</h2>
        <p className="text-sm text-foreground/75">
          Save your notes, then generate a Study Pack with summaries and quizzes.
        </p>
        <Link href="/study" className="w-full sm:w-auto">
          <Button type="button" className="w-full sm:w-auto">New Note</Button>
        </Link>
      </Card>
    </section>
  );
}
