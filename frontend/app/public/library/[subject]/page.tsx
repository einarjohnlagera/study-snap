import type { Metadata } from "next";
import { Suspense } from "react";
import { PublicListingSkeleton } from "@/components/public-page-skeleton";
import { buildPublicLibrarySubjectPath } from "@/lib/public-note-path";
import {
  getServerPublicNotesBySubjectSlug,
  SUBJECT_PAGE_INDEX_THRESHOLD,
} from "@/lib/server-public-notes";
import { buildPageMetadata } from "@/lib/site-metadata";
import {
  buildSubjectDescription,
  resolveSubjectLabel,
  SubjectListingContent,
  type SubjectNote,
} from "./subject-listing-content";

export const dynamic = "force-dynamic";

type SubjectLandingPageProps = {
  params: Promise<{
    subject: string;
  }>;
};

export async function generateMetadata({ params }: SubjectLandingPageProps): Promise<Metadata> {
  const { subject } = await params;
  const notes = await getServerPublicNotesBySubjectSlug(subject) as SubjectNote[];
  const subjectLabel = resolveSubjectLabel(subject, notes);
  const description = buildSubjectDescription(subjectLabel);

  return buildPageMetadata({
    title: `Free ${subjectLabel} Reviewer Notes & Practice Quizzes | NoteLib`,
    description,
    path: buildPublicLibrarySubjectPath(subject),
    noIndex: notes.length < SUBJECT_PAGE_INDEX_THRESHOLD,
  });
}

/**
 * This route has no `loading.tsx`; its Suspense child shows the skeleton. Next.js 16.1.6 runs
 * `generateMetadata` on prefetch of a dynamic route with or without `loading.tsx` (v0.179.0 B4,
 * real browser). Every subject-listing `<Link>` must set `prefetch={false}`; see the rule above
 * `buildPublicLibrarySubjectPath` in `frontend/lib/public-note-path.ts`.
 */
export default async function SubjectLandingPage({ params }: Readonly<SubjectLandingPageProps>) {
  const { subject } = await params;
  return (
    <Suspense fallback={<PublicListingSkeleton />}>
      <SubjectListingContent subject={subject} />
    </Suspense>
  );
}
