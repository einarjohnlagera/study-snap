"use client";

import { useEffect, useState } from "react";
import {
  getCourseProgramCatalog,
  trackAnalyticsEvent,
  type AnalyticsEventRequest,
} from "@/lib/api";
import {
  isCatalogCourseProgram,
  mergeCourseProgramSuggestionsInOrder,
  normalizeCourseProgram,
} from "@/lib/learning-profile";

export type CourseProgramSelectionSurface =
  | "profile"
  | "note-editor"
  | "note-detail"
  | "dashboard-prompt";

export function useCourseProgramCatalogNames(): string[] | null {
  const [catalogNames, setCatalogNames] = useState<string[] | null>(null);

  useEffect(() => {
    let active = true;
    try {
      void Promise.resolve(getCourseProgramCatalog())
        .then((catalog) => {
          if (!active) {
            return;
          }
          const names = mergeCourseProgramSuggestionsInOrder(catalog.map((item) => item.name));
          setCatalogNames(names.length > 0 ? names : null);
        })
        .catch(() => {
          if (active) {
            setCatalogNames(null);
          }
        });
    } catch {
      // The initial null state already keeps the hardcoded fallback active.
    }
    return () => {
      active = false;
    };
  }, []);

  return catalogNames;
}

/**
 * Records a Course / Program *selection*, not a save.
 *
 * `previousValue` is required and the event is suppressed when the value did not change. Every call
 * site sits in a save handler that also persists other fields — the profile form saves learner level
 * beside the program, and both note surfaces save the whole note — so firing on every save would fill
 * the metric with re-saves of pre-existing values. Those are overwhelmingly the legacy off-catalog
 * strings this release exists to reduce, so the off-catalog rate would look flat and high regardless
 * of whether catalog-first changed anything. The measurement has to count what learners *pick*.
 */
export function trackCourseProgramValueSelected(
  surface: CourseProgramSelectionSurface,
  value: string,
  previousValue: string | null | undefined,
  catalogNames: string[] | null,
): void {
  if (!catalogNames) {
    return;
  }
  const normalizedNext = normalizeCourseProgram(value)?.toLocaleLowerCase("en") ?? "";
  const normalizedPrevious = normalizeCourseProgram(previousValue)?.toLocaleLowerCase("en") ?? "";
  if (normalizedNext === normalizedPrevious) {
    return;
  }
  const request: AnalyticsEventRequest = {
    eventType: "COURSE_PROGRAM_VALUE_SELECTED",
    metadata: {
      surface,
      matchedCatalog: isCatalogCourseProgram(value, catalogNames),
    },
  };
  try {
    void Promise.resolve(trackAnalyticsEvent(request)).catch(() => undefined);
  } catch {
    // Analytics must never interrupt the save that committed the Course / Program value.
  }
}
