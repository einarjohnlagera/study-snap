"use client";

import Link from "next/link";
import { trackAnalyticsEvent } from "@/lib/api";

export function PublicNoteOfficialSetLink({ id }: Readonly<{ id: string }>) {
  return (
    <Link
      href={`/collections/published?highlight=${encodeURIComponent(id)}`}
      className="shrink-0 text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
      onClick={() => void trackAnalyticsEvent({
        eventType: "PUBLIC_NOTE_OFFICIAL_SET_LINK_CLICKED",
        entityId: id,
      })}
    >
      Browse this Review Set →
    </Link>
  );
}
