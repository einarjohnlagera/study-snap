import { BookOpen, LineChart, Lock } from "lucide-react";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { buttonVariants } from "@/components/ui/button";

/**
 * ⚠️ This section replaced a "Coming Soon" teaser that outlived the feature by three releases.
 *
 * <p>It advertised a waitlist for supporter progress — which had already shipped in `v0.89.0` — so the only
 * public surface mentioning this capability was telling visitors it did not exist, and collecting an
 * interest click instead of a signup. Every claim below is live today; do not add one that is not.
 *
 * <p>⚠️ Keep it RELATIONSHIP-NEUTRAL. Marketing may lead with the parent case because it is the clearest
 * real-world pain point, but the product gates nothing on who someone is to you — there is no guardian
 * profile, no supporter profile type, and none is coming. Naming several relationships is deliberate.
 *
 * <p>⚠️ Outcome before feature. No "social learning", and no permissions/sharing vocabulary in the copy.
 */
const supportCards = [
  {
    icon: BookOpen,
    title: "Give them something to study",
    description:
      "Send a note and its Study Pack to someone you choose. They open it and study it the same way you would — summary, key ideas, then practice.",
  },
  {
    icon: LineChart,
    title: "See whether it is working",
    description:
      "Once they accept, you can see how ready they are, how often they study, and how their practice is going.",
  },
  {
    icon: Lock,
    title: "Their notes stay theirs",
    description:
      "You never see what they wrote — only how their preparation is moving. Nothing is shared until someone chooses to share it.",
  },
] as const;

export function LearningConnectionsSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
          Learning doesn&apos;t always happen alone
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">
          Help someone you care about learn
        </h2>
        <p className="max-w-3xl text-sm text-foreground/75">
          Parents, tutors, older siblings and study partners can share material and follow along — without
          having to become anyone&apos;s teacher, and without building a single quiz by hand.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {supportCards.map((item) => (
          <Card key={item.title} className="space-y-4 p-5">
            <item.icon className="h-5 w-5 text-sky-600 dark:text-sky-400" aria-hidden="true" />
            <div className="space-y-2">
              <CardTitle>{item.title}</CardTitle>
              <CardDescription className="text-sm">{item.description}</CardDescription>
            </div>
          </Card>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <TrackedLink
          href="/signup"
          className={buttonVariants({ variant: "outline" })}
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "learning_connections_section", destination: "/signup" }}
        >
          Start for Free
        </TrackedLink>
        <p className="text-xs text-foreground/60">
          Sharing a quiz needs nothing from them — no account, no sign-up.
        </p>
      </div>
    </section>
  );
}
