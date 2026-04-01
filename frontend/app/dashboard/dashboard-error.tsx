import { Card } from "@/components/ui/card";
import { ResponsiveActionButton } from "@/components/ui/action-button";

type DashboardErrorProps = {
  message: string;
  onRetry: () => Promise<void>;
};

export function DashboardError({ message, onRetry }: Readonly<DashboardErrorProps>) {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">We could not load your notes</h2>
      <p className="text-sm text-foreground/75">{message}</p>
      <ResponsiveActionButton
        type="button"
        variant="outline"
        className="w-full sm:w-auto"
        onClick={() => void onRetry()}
        action="retry"
        label="Retry"
      />
    </Card>
  );
}
