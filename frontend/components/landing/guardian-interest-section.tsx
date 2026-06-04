"use client";

import { useState } from "react";
import { Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { trackAnalyticsEvent } from "@/lib/api";

export function GuardianInterestSection() {
  const [interested, setInterested] = useState(false);

  const handleInterest = () => {
    void trackAnalyticsEvent({ eventType: "GUARDIAN_INTEREST" });
    setInterested(true);
  };

  return (
    <section className="rounded-2xl border border-violet-500/20 bg-violet-500/5 p-5 dark:bg-violet-500/8 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-3">
          <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-violet-500/30 bg-background/80 text-violet-700 dark:text-violet-300">
            <Users className="h-4 w-4" />
          </span>
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-sm font-semibold text-foreground">For Parents &amp; Guardians</p>
              <span className="inline-flex items-center rounded-full border border-violet-500/30 bg-background/80 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-violet-700 dark:text-violet-300">
                Coming Soon
              </span>
            </div>
            <p className="text-sm text-foreground/70">
              Help your child stay on top of their study schedule — track progress, see weak areas, and keep the review loop going between sessions.
            </p>
          </div>
        </div>
        <div className="shrink-0 sm:pl-4">
          {interested ? (
            <p className="text-sm font-medium text-violet-700 dark:text-violet-300">
              Thanks — we&apos;ll reach out when it&apos;s ready.
            </p>
          ) : (
            <Button
              type="button"
              variant="outline"
              className="w-full border-violet-500/40 text-violet-700 hover:bg-violet-500/10 dark:text-violet-300 sm:w-auto"
              onClick={handleInterest}
            >
              I&apos;m interested
            </Button>
          )}
        </div>
      </div>
    </section>
  );
}
