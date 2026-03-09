import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type DashboardErrorProps = {
  message: string;
  onRetry: () => Promise<void>;
};

export function DashboardError({ message, onRetry }: DashboardErrorProps) {
  return (
    <Card className="space-y-4">
      <h2 className="text-xl font-semibold">We could not load your Study Library</h2>
      <p className="text-sm text-foreground/75">{message}</p>
      <Button type="button" variant="outline" onClick={() => void onRetry()}>
        Retry
      </Button>
    </Card>
  );
}
