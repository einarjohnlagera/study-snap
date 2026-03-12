import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type DashboardErrorProps = {
  message: string;
  onRetry: () => Promise<void>;
};

export function DashboardError({ message, onRetry }: DashboardErrorProps) {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">We could not load your Study Library</h2>
      <p className="text-sm text-foreground/75">{message}</p>
      <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void onRetry()}>
        Retry
      </Button>
    </Card>
  );
}
