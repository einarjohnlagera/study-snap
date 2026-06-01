"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { NoteQualityBadges } from "@/components/notes/note-quality-badge";
import { NoteStateBadge } from "@/components/notes/note-state-badge";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  listPublicNotes,
  type NoteListItemResponse,
} from "@/lib/api";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { PROFILE_LEARNING_PROFILE_SECTION_ID } from "@/lib/profile-sections";
import { buildPublicLibraryUrl } from "@/lib/public-library-url";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";

type DashboardCommunityNotesSectionProps = {
  courseProgram: string | null;
  viewerUserId: string | null;
};

const COMMUNITY_NOTES_LIMIT = 4;
const PROFILE_LEARNING_PROFILE_PATH = `/profile#${PROFILE_LEARNING_PROFILE_SECTION_ID}`;

function CommunityNotesSkeleton() {
  return (
    <section className="space-y-3 sm:space-y-4">
      <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
        <Skeleton className="h-7 w-44" />
        <Skeleton className="h-4 w-24" />
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {Array.from({ length: COMMUNITY_NOTES_LIMIT }).map((_, index) => (
          <Card key={index} className="space-y-4 p-4 sm:p-5">
            <Skeleton className="h-5 w-28" />
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-4 w-36" />
          </Card>
        ))}
      </div>
    </section>
  );
}

export function DashboardCommunityNotesSection({
  courseProgram,
  viewerUserId,
}: Readonly<DashboardCommunityNotesSectionProps>) {
  const router = useRouter();
  const normalizedCourseProgram = useMemo(() => normalizeCourseProgram(courseProgram), [courseProgram]);
  const [result, setResult] = useState<{
    courseProgram: string | null;
    hidden: boolean;
    notes: NoteListItemResponse[];
  }>({
    courseProgram: null,
    hidden: false,
    notes: [],
  });
  const [showCourseProgramModal, setShowCourseProgramModal] = useState(false);

  useEffect(() => {
    if (!normalizedCourseProgram) {
      return;
    }

    let cancelled = false;

    void listPublicNotes({
      courseProgram: normalizedCourseProgram,
      size: COMMUNITY_NOTES_LIMIT,
    })
      .then((result) => {
        if (cancelled) {
          return;
        }
        setResult({
          courseProgram: normalizedCourseProgram,
          hidden: result.length === 0,
          notes: result,
        });
      })
      .catch(() => {
        if (!cancelled) {
          setResult({
            courseProgram: normalizedCourseProgram,
            hidden: true,
            notes: [],
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [normalizedCourseProgram, viewerUserId]);

  if (!normalizedCourseProgram) {
    return (
      <>
        <section className="space-y-3 sm:space-y-4">
          <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
            <h2 className="text-lg font-semibold sm:text-xl">Notes for your review track</h2>
          </div>
          <Card className="space-y-3 p-4 sm:p-6">
            <h3 className="text-base font-semibold sm:text-lg">Set your Course/Program</h3>
            <p className="text-sm text-foreground/75">
              Set your Course/Program to see public notes tailored for your review track.
            </p>
            <Button type="button" onClick={() => setShowCourseProgramModal(true)}>
              Set your Course/Program
            </Button>
          </Card>
        </section>

        <AppModal
          isOpen={showCourseProgramModal}
          title="Set your Course/Program"
          description="Set your Course/Program to see public notes tailored for your review track."
          onClose={() => setShowCourseProgramModal(false)}
          actions={(
            <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
              <Button type="button" variant="outline" onClick={() => setShowCourseProgramModal(false)}>
                Cancel
              </Button>
              <Button
                type="button"
                onClick={() => {
                  setShowCourseProgramModal(false);
                  router.push(PROFILE_LEARNING_PROFILE_PATH);
                }}
              >
                Go to Learner Profile
              </Button>
            </div>
          )}
        />
      </>
    );
  }

  const loading = result.courseProgram !== normalizedCourseProgram;
  const notes = result.courseProgram === normalizedCourseProgram ? result.notes : [];
  const hidden = result.courseProgram === normalizedCourseProgram && result.hidden;

  if (hidden) {
    return null;
  }

  if (loading) {
    return <CommunityNotesSkeleton />;
  }

  const publicLibraryUrl = buildPublicLibraryUrl({ courseProgram: normalizedCourseProgram });

  return (
    <section className="space-y-3 sm:space-y-4">
      <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">Notes for {normalizedCourseProgram}</h2>
        <p className="text-xs text-foreground/65">{notes.length} public notes</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {notes.map((note) => (
          <Link
            key={note.id}
            href={buildPublicLibraryNotePath({ subject: note.subject, title: note.title })}
            className="block"
          >
            <Card className="h-full space-y-3 p-4 transition-colors hover:bg-highlight hover:shadow-md sm:p-5">
              <SharedNoteCard
                title={note.title}
                courseProgram={normalizeCourseProgram(note.courseProgram)}
                subject={note.subject}
                tags={note.tags}
                tagDisplayLimit={3}
                contentPreview={note.contentPreview}
                summaryPreview={note.summaryPreview}
                copyCount={typeof note.copyCount === "number" && note.copyCount > 0 ? note.copyCount : null}
                viewCount={typeof note.viewCount === "number" && note.viewCount > 0 ? note.viewCount : null}
                stateBadge={<NoteStateBadge status={note.studyPackStatus} />}
                metadataBadges={(
                  <NoteQualityBadges
                    copyCount={note.copyCount}
                    likeCount={note.likeCount}
                    viewCount={note.viewCount}
                  />
                )}
              />
            </Card>
          </Link>
        ))}
      </div>

      <div className="flex justify-end pt-0.5">
        <Link href={publicLibraryUrl} className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          See all in Public Library &rarr;
        </Link>
      </div>
    </section>
  );
}
