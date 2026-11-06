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
 * The shell returns immediately; the catalog walk happens inside the Suspense child. There is deliberately NO `loading.tsx`
 * for this route: a route with one makes Next run `generateMetadata` (which walks the catalog) when a `<Link>` is merely
 * prefetched, so a link scrolling into view would start a walk (v0.179.0 B4 audit, real-browser run). Without it a prefetch
 * of this dynamic route renders nothing.
 */
export default async function SubjectLandingPage({ params }: Readonly<SubjectLandingPageProps>) {
  const { subject } = await params;
  return (
    <Suspense fallback={<PublicListingSkeleton />}>
      <SubjectListingContent subject={subject} />
    </Suspense>
  );
}
