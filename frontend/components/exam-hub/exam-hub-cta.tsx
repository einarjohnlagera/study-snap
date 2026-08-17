"use client";

import { useMemo, useSyncExternalStore } from "react";
import Link from "next/link";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { setExamIntentCookie } from "@/lib/exam-intent";
import type { ExamHubConfig } from "@/lib/exam-hub-config";
import { EXPLORE_PATH } from "@/lib/explore-url";
import { buildPublicLibraryUrl, slugifyPublicLibraryFilterValue } from "@/lib/public-library-url";

type ExamHubCtaProps = {
  exam: ExamHubConfig;
};

export function buildExamAuthPath(slug: string, redirectTo?: string) {
  const params = new URLSearchParams({
    mode: "signup",
    intent: "exam",
    exam: slug,
  });
  if (redirectTo) {
    params.set("redirect", redirectTo);
  }
  return `/auth?${params.toString()}`;
}

function subscribeToAuthChanges(onStoreChange: () => void) {
  if (globalThis.window === undefined) {
    return () => {};
  }
  globalThis.addEventListener("studysnap-auth-change", onStoreChange);
  globalThis.addEventListener("storage", onStoreChange);
  return () => {
    globalThis.removeEventListener("studysnap-auth-change", onStoreChange);
    globalThis.removeEventListener("storage", onStoreChange);
  };
}

function getAuthenticatedSnapshot() {
  return getAuthUser() !== null;
}

function getServerAuthenticatedSnapshot() {
  return false;
}

// Stage 1 (v0.84.0): the filtered destination moved from /public/library to /explore. The builder
// already takes a base path, and /explore's Notes tab renders the same component, so the same
// courseProgram filter resolves identically — only the surrounding surface changes.
export function buildExamPublicLibraryPath(exam: ExamHubConfig) {
  return buildPublicLibraryUrl(
    { courseProgram: slugifyPublicLibraryFilterValue(exam.coursePrograms[0] ?? "") },
    undefined,
    EXPLORE_PATH,
  );
}

export function ExamHubCta({ exam }: Readonly<ExamHubCtaProps>) {
  const isAuthenticated = useSyncExternalStore(
    subscribeToAuthChanges,
    getAuthenticatedSnapshot,
    getServerAuthenticatedSnapshot,
  );
  const authPath = useMemo(() => buildExamAuthPath(exam.slug), [exam.slug]);
  const publicLibraryPath = useMemo(() => buildExamPublicLibraryPath(exam), [exam]);

  if (isAuthenticated) {
    return (
      <TrackedLink
        href={publicLibraryPath}
        eventType="EXAM_HUB_CTA_CLICKED"
        eventMetadata={{ slug: exam.slug, destination: "explore" }}
        className="inline-flex min-h-11 items-center justify-center rounded-full bg-foreground px-5 py-2 text-sm font-semibold text-background transition hover:bg-foreground/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      >
        Browse {exam.coursePrograms[0]} Notes
      </TrackedLink>
    );
  }

  return (
    <Link
      href={authPath}
      className="inline-flex min-h-11 items-center justify-center rounded-full bg-primary px-5 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      onClick={() => {
        setExamIntentCookie(exam.slug);
        void trackAnalyticsEvent({
          eventType: "EXAM_HUB_CTA_CLICKED",
          metadata: { slug: exam.slug, destination: "signup" },
        });
      }}
    >
      Start preparing for the {exam.shortName}
    </Link>
  );
}
