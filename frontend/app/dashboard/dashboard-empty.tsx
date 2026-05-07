import { Card } from "@/components/ui/card";
import { ResponsiveActionLink } from "@/components/ui/action-button";

export function DashboardEmpty() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">Start studying smarter</h2>
      <p className="max-w-2xl text-sm text-foreground/75">
        Add your first note, generate a Study Pack, and start quizzing in minutes.
      </p>
      <ResponsiveActionLink href="/notes/new" action="create" label="Create Your First Note" className="w-full sm:w-auto" />
    </Card>
  );
}
