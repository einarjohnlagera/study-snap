"use client";

import { PublicProfileErrorState } from "@/components/public/public-profile-error-state";

export default function PublicProfileError({ reset }: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  return <PublicProfileErrorState reset={reset} />;
}
