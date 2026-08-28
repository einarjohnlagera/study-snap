import { ResponsiveActionLink } from "@/components/ui/action-button";
import { Card } from "@/components/ui/card";
import type { LinkedLearnerResponse } from "@/lib/api";
import { describeSupportedLearnerStatus } from "@/lib/linked-learner-status";

export function SupportedLearnersCard({
  links,
}: Readonly<{
  links: LinkedLearnerResponse[];
}>) {
  if (links.length === 0) return null;

  return (
    <section className="space-y-3" aria-labelledby="supported-learners-heading">
      <div>
        <h2 id="supported-learners-heading" className="text-lg font-semibold sm:text-xl">People you support</h2>
        <p className="mt-1 text-sm text-foreground/70">See readiness and study activity without access to personal notes or study material.</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {links.map((link) => {
          const status = describeSupportedLearnerStatus(link);
          return (
            <Card key={link.id} className="space-y-3 p-4 sm:p-5">
              <div>
                <h3 className="font-semibold">{link.counterpartyDisplayName}</h3>
                <p className="mt-1 text-sm text-foreground/65">{status.headline}</p>
              </div>
              {link.progressSharedWithMe ? (
                <ResponsiveActionLink href={`/linked-learners/${link.id}/progress`} action="progress" label="View progress" />
              ) : (
                <p className="text-sm text-foreground/70">
                  {link.status === "ACCEPTED"
                    ? `${link.counterpartyDisplayName} is not sharing progress with you.`
                    : status.detail}
                </p>
              )}
            </Card>
          );
        })}
      </div>
      <ResponsiveActionLink href="/linked-learners" action="share" label="Manage learning connections" variant="outline" />
    </section>
  );
}
