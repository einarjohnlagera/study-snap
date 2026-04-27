import { ResponsiveActionButton } from "@/components/ui/action-button";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type DashboardPersonalizationPromptProps = {
  onDismiss: () => void;
  onOpenPreferences: () => void;
};

export function DashboardPersonalizationPrompt({
  onDismiss,
  onOpenPreferences,
}: Readonly<DashboardPersonalizationPromptProps>) {
  return (
    <Card className="motion-fade-enter space-y-4 p-4 sm:p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1.5">
          <h2 className="text-lg font-semibold sm:text-xl">Make NoteLib work better for you</h2>
          <p className="text-sm text-foreground/75">
            Set your learning style and reminders in seconds.
          </p>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          aria-label="Dismiss personalization prompt"
          onClick={onDismiss}
        >
          Dismiss
        </Button>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:justify-start">
        <ResponsiveActionButton
          type="button"
          action="settings"
          label="Set Preferences"
          className="w-full sm:w-auto"
          onClick={onOpenPreferences}
        />
      </div>
    </Card>
  );
}
