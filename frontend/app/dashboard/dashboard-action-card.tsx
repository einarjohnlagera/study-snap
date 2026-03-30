import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type DashboardActionCardProps = {
  title: string;
  description: string;
  actionLabel: string;
  actionHref: string;
  secondaryActionLabel?: string;
  secondaryActionHref?: string;
};

export function DashboardActionCard({
  title,
  description,
  actionLabel,
  actionHref,
  secondaryActionLabel,
  secondaryActionHref,
}: Readonly<DashboardActionCardProps>) {
  return (
    <Card className="space-y-3 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">{title}</h2>
      <p className="text-sm text-foreground/75">{description}</p>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Link href={actionHref} className="w-full sm:w-auto">
          <Button type="button" className="w-full sm:w-auto">
            {actionLabel}
          </Button>
        </Link>
        {secondaryActionLabel && secondaryActionHref ? (
          <Link href={secondaryActionHref} className="w-full sm:w-auto">
            <Button type="button" variant="outline" className="w-full sm:w-auto">
              {secondaryActionLabel}
            </Button>
          </Link>
        ) : null}
      </div>
    </Card>
  );
}
