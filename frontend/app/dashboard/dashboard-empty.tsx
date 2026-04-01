import { Card } from "@/components/ui/card";
import { ResponsiveActionLink } from "@/components/ui/action-button";

export function DashboardEmpty() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">You don&apos;t have any Study Packs yet</h2>
      <p className="max-w-2xl text-sm text-foreground/75">
        Create a note and generate your first quiz in a few minutes.
      </p>
      <ResponsiveActionLink href="/notes/new" action="create" label="Create Your First Note" className="w-full sm:w-auto" />
    </Card>
  );
}
