import { Card } from "@/components/ui/card";
import { ResponsiveActionLink, type ActionIconName } from "@/components/ui/action-button";

type DashboardActionCardProps = {
  title: string;
  description: string;
  actionLabel: string;
  actionHref: string;
  actionIcon?: ActionIconName;
  secondaryActionLabel?: string;
  secondaryActionHref?: string;
  secondaryActionIcon?: ActionIconName;
};

export function DashboardActionCard({
  title,
  description,
  actionLabel,
  actionHref,
  actionIcon = "studyPack",
  secondaryActionLabel,
  secondaryActionHref,
  secondaryActionIcon = "open",
}: Readonly<DashboardActionCardProps>) {
  return (
    <Card className="space-y-3 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">{title}</h2>
      <p className="text-sm text-foreground/75">{description}</p>
      <div className="flex flex-col gap-2 sm:flex-row">
        <ResponsiveActionLink href={actionHref} action={actionIcon} label={actionLabel} className="w-full sm:w-auto" />
        {secondaryActionLabel && secondaryActionHref ? (
          <ResponsiveActionLink
            href={secondaryActionHref}
            action={secondaryActionIcon}
            label={secondaryActionLabel}
            variant="outline"
            className="w-full sm:w-auto"
          />
        ) : null}
      </div>
    </Card>
  );
}
