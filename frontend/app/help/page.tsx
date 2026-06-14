"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { ArrowRight, Award, BarChart2, BookOpen, Brain, Briefcase, Compass, Download, FileText, GraduationCap, Layers, Lightbulb, User } from "lucide-react";
import { useRouter } from "next/navigation";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { PageHeader } from "@/components/page-header";
import { GettingStartedGuide } from "@/components/help/getting-started-guide";
import { CreatingNotesGuide } from "@/components/help/creating-notes-guide";
import { StudyPlansGuide } from "@/components/help/study-plans-guide";
import { StudyPacksGuide } from "@/components/help/study-packs-guide";
import { ExportSharingGuide } from "@/components/help/export-sharing-guide";
import { BoardExamGuide } from "@/components/help/board-exam-guide";
import { QuizModesGuide } from "@/components/help/quiz-modes-guide";
import { StudentGuide } from "@/components/help/student-guide";
import { TeacherGuide } from "@/components/help/teacher-guide";
import { ProfessionalGuide } from "@/components/help/professional-guide";
import { ProgressFocusGuide } from "@/components/help/progress-focus-guide";
import { ExamHubsGuide } from "@/components/help/exam-hubs-guide";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { getAuthUser } from "@/lib/auth";
import type { ProfileType } from "@/lib/api";
import type { LucideIcon } from "lucide-react";

type HelpCard = {
  id: string;
  icon: LucideIcon;
  title: string;
  description: string;
  modalDescription?: string;
};

const HELP_CARDS: HelpCard[] = [
  {
    id: "getting-started",
    icon: Lightbulb,
    title: "Getting Started",
    description: "What NoteLib is and how to begin your first study session.",
  },
  {
    id: "creating-notes",
    icon: FileText,
    title: "Creating Notes",
    description: "How to write, organize, and manage notes effectively.",
  },
  {
    id: "study-plans",
    icon: Layers,
    title: "Study Plans & Collections",
    description: "Group related notes into a saved, ordered set you can study, review, or teach from.",
    modalDescription: "Organize your notes into reusable, ordered sets.",
  },
  {
    id: "study-packs",
    icon: BookOpen,
    title: "Study Packs",
    description: "What gets generated from your note — summary and key concepts.",
  },
  {
    id: "quiz-modes",
    icon: Brain,
    title: "Quiz Modes",
    description: "All five quiz modes explained — from Quick Review to Board Exam.",
  },
  {
    id: "progress-focus",
    icon: BarChart2,
    title: "Progress & Study Focus",
    description: "How mastery, goals, and milestones work — and what to do at the start of a new term.",
    modalDescription: "Track what you know and what to study next.",
  },
  {
    id: "exam-hubs",
    icon: Compass,
    title: "Exam Hubs",
    description: "Curated note collections for the ALE, PNLE, and LET licensure exams.",
    modalDescription: "Find review material for major licensure exams.",
  },
  {
    id: "export-sharing",
    icon: Download,
    title: "Export & Sharing",
    description: "Download quiz sessions as PDFs and share notes publicly.",
  },
  {
    id: "student-guide",
    icon: User,
    title: "Student Guide",
    description: "A step-by-step study workflow to get the most out of NoteLib.",
    modalDescription: "Study smarter using notes.",
  },
  {
    id: "board-exam-guide",
    icon: Award,
    title: "Board Exam Guide",
    description: "Challenge Quiz, Board Exam Mode, and a study workflow for high-stakes exam preparation.",
    modalDescription: "Study smarter for licensure and board exams.",
  },
  {
    id: "teacher-guide",
    icon: GraduationCap,
    title: "Teacher Guide",
    description: "Turn your lesson materials into study packs and exportable quiz content.",
    modalDescription: "Use your lesson materials to build review-ready study content faster.",
  },
  {
    id: "professional-guide",
    icon: Briefcase,
    title: "Professional Guide",
    description: "Use Interview Practice to prepare for job interviews using your own notes.",
    modalDescription: "Practice smarter for job interviews using your notes.",
  },
];

const HELP_CARD_IDS = new Set(HELP_CARDS.map((card) => card.id));

const GUIDE_PROFILE_TYPES: Partial<Record<string, ProfileType>> = {
  "student-guide": "STUDENT",
  "board-exam-guide": "BOARD_EXAM",
  "teacher-guide": "TEACHER",
  "professional-guide": "PROFESSIONAL",
};

function shouldShowSwitchProfileCta(
  currentProfileType: ProfileType | null | undefined,
  guideProfileType: ProfileType | null | undefined,
) {
  return Boolean(guideProfileType && currentProfileType !== guideProfileType);
}

function GuideContent({
  cardId,
  currentProfileType,
}: {
  cardId: string;
  currentProfileType: ProfileType | null;
}) {
  const showSwitchProfileCta = shouldShowSwitchProfileCta(currentProfileType, GUIDE_PROFILE_TYPES[cardId]);
  switch (cardId) {
    case "getting-started":
      return <GettingStartedGuide />;
    case "creating-notes":
      return <CreatingNotesGuide />;
    case "study-plans":
      return <StudyPlansGuide profileType={currentProfileType} />;
    case "study-packs":
      return <StudyPacksGuide />;
    case "quiz-modes":
      return <QuizModesGuide />;
    case "progress-focus":
      return <ProgressFocusGuide />;
    case "exam-hubs":
      return <ExamHubsGuide />;
    case "export-sharing":
      return <ExportSharingGuide />;
    case "board-exam-guide":
      return <BoardExamGuide showSwitchProfileCta={showSwitchProfileCta} />;
    case "student-guide":
      return <StudentGuide showSwitchProfileCta={showSwitchProfileCta} />;
    case "teacher-guide":
      return <TeacherGuide showSwitchProfileCta={showSwitchProfileCta} />;
    case "professional-guide":
      return <ProfessionalGuide showSwitchProfileCta={showSwitchProfileCta} />;
    default:
      return null;
  }
}

export default function HelpPage() {
  const router = useRouter();
  const [openCardId, setOpenCardId] = useState<string | null>(null);
  const [currentProfileType, setCurrentProfileType] = useState<ProfileType | null>(() => getAuthUser()?.profileType ?? null);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
  }, [router]);

  useEffect(() => {
    const syncAuth = () => {
      setCurrentProfileType(getAuthUser()?.profileType ?? null);
    };
    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  // Deep-link support: open a specific guide from the URL hash (e.g. /help#progress-focus).
  // Handles both fresh navigations (mount) and hash changes while already on the page.
  useEffect(() => {
    const openFromHash = () => {
      const hashId = globalThis.location?.hash?.replace(/^#/, "") ?? "";
      if (hashId && HELP_CARD_IDS.has(hashId)) {
        setOpenCardId(hashId);
      }
    };
    openFromHash();
    globalThis.addEventListener("hashchange", openFromHash);
    return () => {
      globalThis.removeEventListener("hashchange", openFromHash);
    };
  }, []);

  const handleOpenCard = (cardId: string) => {
    setOpenCardId(cardId);
    // replaceState (not location.hash) keeps the deep link shareable without
    // firing hashchange or scroll-jumping to an anchor.
    globalThis.history.replaceState(null, "", `#${cardId}`);
  };

  const handleCloseCard = () => {
    setOpenCardId(null);
    globalThis.history.replaceState(null, "", globalThis.location.pathname + globalThis.location.search);
  };

  const openCard = HELP_CARDS.find((c) => c.id === openCardId) ?? null;

  return (
    <main className="mx-auto w-full max-w-4xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="HELP"
        title="Help Center"
        description="Browse guides or search for answers. Click any topic to read more."
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {HELP_CARDS.map((card) => {
          const Icon = card.icon;
          return (
            <button
              key={card.id}
              type="button"
              onClick={() => handleOpenCard(card.id)}
              className="group w-full text-left"
            >
              <Card className="flex h-full flex-col gap-4 p-5 transition-colors hover:bg-highlight sm:p-6">
                <div className="flex items-start gap-3">
                  <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border bg-muted/40">
                    <Icon className="h-4 w-4 text-foreground/70" aria-hidden="true" />
                  </span>
                  <div className="min-w-0 space-y-1">
                    <CardTitle className="text-sm">{card.title}</CardTitle>
                    <CardDescription className="text-xs leading-relaxed">{card.description}</CardDescription>
                  </div>
                </div>
                <div className="mt-auto flex items-center gap-1 text-xs font-medium text-blue-600 dark:text-blue-400">
                  View guide
                  <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                </div>
              </Card>
            </button>
          );
        })}
      </div>

      {/* Contact / support footer */}
      <Card className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6">
        <div className="space-y-1">
          <p className="text-sm font-medium text-foreground">Need more help?</p>
          <p className="text-xs text-foreground/60">
            Browse the{" "}
            <Link href="/public/library" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
              Public Library
            </Link>{" "}
            for notes shared by other students, or visit{" "}
            <Link href="/settings" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
              Settings
            </Link>{" "}
            to manage your account.
          </p>
        </div>
      </Card>

      {/* Guide detail modal */}
      {openCard ? (
        <AppModal
          isOpen={openCardId !== null}
          title={openCard.title}
          description={openCard.modalDescription}
          onClose={handleCloseCard}
          panelClassName="sm:max-w-lg"
        >
          <GuideContent cardId={openCard.id} currentProfileType={currentProfileType} />
        </AppModal>
      ) : null}
    </main>
  );
}
