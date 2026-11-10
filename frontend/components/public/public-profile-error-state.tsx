"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type PublicProfileErrorStateProps = {
  reset: () => void;
};

/**
 * Shown by the creator and profile routes' `error.tsx` when the profile could not be loaded (for example the upstream failed
 * after the 120 s cache entry expired). It never shows the error's message or digest, and it does not say the profile is
 * private, deleted or missing. A plain `reset()` only re-renders the client boundary and would not re-fetch a failed server
 * render, so Try again refreshes the route first and then resets the boundary. A retry inside the 15 s failure backoff can
 * fail again, so the copy promises nothing about timing.
 */
export function PublicProfileErrorState({ reset }: Readonly<PublicProfileErrorStateProps>) {
  const router = useRouter();
  const [isRetrying, startTransition] = useTransition();

  const handleRetry = () => {
    startTransition(() => {
      router.refresh();
      reset();
    });
  };

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href="/public/library" label="Public Library" />
      <Card className="space-y-4 p-4 sm:p-6">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">We couldn&apos;t load this profile</h1>
          <p className="text-sm text-foreground/75">
            Something went wrong while loading this profile. Please try again in a little while.
          </p>
        </div>
        <div>
          <Button type="button" variant="outline" onClick={handleRetry} disabled={isRetrying}>
            Try again
          </Button>
        </div>
      </Card>
    </main>
  );
}
