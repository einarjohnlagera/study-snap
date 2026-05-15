"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { ArrowRight, Award, BookOpen, Brain, Download, FileText, GraduationCap, Lightbulb, User } from "lucide-react";
import { useRouter } from "next/navigation";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { PageHeader } from "@/components/page-header";
import { GettingStartedGuide } from "@/components/help/getting-started-guide";
import { CreatingNotesGuide } from "@/components/help/creating-notes-guide";
import { StudyPacksGuide } from "@/components/help/study-packs-guide";
import { ExportSharingGuide } from "@/components/help/export-sharing-guide";
import { BoardExamGuide } from "@/components/help/board-exam-guide";
import { QuizModesGuide } from "@/components/help/quiz-modes-guide";
import { StudentGuide } from "@/components/help/student-guide";
import { TeacherGuide } from "@/components/help/teacher-guide";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
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
    description: "Long Exam, Board Exam Mode, and a study workflow for high-stakes exam preparation.",
    modalDescription: "Study smarter for licensure and board exams.",
  },
  {
    id: "teacher-guide",
    icon: GraduationCap,
    title: "Teacher Guide",
    description: "Turn your lesson materials into study packs and exportable quiz content.",
    modalDescription: "Use your lesson materials to build review-ready study content faster.",
  },
];

function GuideContent({ cardId }: { cardId: string }) {
  switch (cardId) {
    case "getting-started":
      return <GettingStartedGuide />;
    case "creating-notes":
      return <CreatingNotesGuide />;
    case "study-packs":
      return <StudyPacksGuide />;
    case "quiz-modes":
      return <QuizModesGuide />;
    case "export-sharing":
      return <ExportSharingGuide />;
    case "board-exam-guide":
      return <BoardExamGuide />;
    case "student-guide":
      return <StudentGuide />;
    case "teacher-guide":
      return <TeacherGuide />;
    default:
      return null;
  }
}

export default function HelpPage() {
  const router = useRouter();
  const [openCardId, setOpenCardId] = useState<string | null>(null);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
  }, [router]);

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
              onClick={() => setOpenCardId(card.id)}
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
          onClose={() => setOpenCardId(null)}
          panelClassName="sm:max-w-lg"
        >
          <GuideContent cardId={openCard.id} />
        </AppModal>
      ) : null}
    </main>
  );
}
