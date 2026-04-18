import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";

type DashboardHeroProps = {
  greetingName: string;
  description: string;
  supportingText?: string;
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

export function DashboardHero({
  greetingName,
  description,
  supportingText = "Ready to continue your studies?",
}: Readonly<DashboardHeroProps>) {
  const greetingLabel = resolveGreetingLabel(new Date().getHours());

  return (
    <section className="space-y-4">
      <Card className="space-y-1 p-4 sm:p-6">
        <h2 className="text-xl font-semibold sm:text-2xl">
          {greetingLabel}, {greetingName}
        </h2>
        <p className="text-sm text-foreground/75">{supportingText}</p>
      </Card>
      <PageHeader
        eyebrow="DASHBOARD"
        title="Dashboard"
        description={description}
      />
    </section>
  );
}
