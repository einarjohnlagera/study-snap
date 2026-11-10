import type { Metadata } from "next";

/**
 * Metadata for a creator or profile page whose upstream read failed (an error state the visitor sees from `error.tsx`).
 * Static text only: it carries no profile data and nothing is cached. `noindex` matches the policy every other profile state
 * already has; `follow` is left at its default. The HTTP status of the response is not changed by this.
 */
export const PUBLIC_PROFILE_UNAVAILABLE_METADATA: Metadata = {
  title: "Profile temporarily unavailable | NoteLib",
  robots: { index: false },
};
