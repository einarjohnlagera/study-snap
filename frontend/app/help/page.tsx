"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { ArrowRight, BookOpen, BarChart2, Download, FileText, GraduationCap, Lightbulb, Settings, User } from "lucide-react";
import { useRouter } from "next/navigation";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { PageHeader } from "@/components/page-header";
import { GettingStartedGuide } from "@/components/help/getting-started-guide";
import { StudentGuide } from "@/components/help/student-guide";
import { TeacherGuide } from "@/components/help/teacher-guide";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import type { LucideIcon } from "lucide-react";

type HelpQA = { question: string; answer: string };

type HelpCard = {
  id: string;
  icon: LucideIcon;
  title: string;
  description: string;
  items: HelpQA[];
};

const HELP_CARDS: HelpCard[] = [
  {
    id: "getting-started",
    icon: Lightbulb,
    title: "Getting Started",
    description: "What NoteLib is and how to begin your first study session.",
    items: [
      {
        question: "What is NoteLib?",
        answer:
          "NoteLib turns your notes into structured study outputs. You create a note, generate a Study Pack, then practice with Quick Review, Challenge Quiz, and Adaptive Practice.",
      },
      {
        question: "How do I create a note?",
        answer:
          'Go to Library and click "New Note", or click "Create Note" from the Dashboard. You can type, paste, or upload content. Save the note first, then generate a Study Pack.',
      },
      {
        question: "What is a Study Pack?",
        answer:
          'A Study Pack is AI-generated content attached to your note: a summary, key concepts, and a practice quiz. Click "Generate Study Pack" on any saved note to create one.',
      },
    ],
  },
  {
    id: "creating-notes",
    icon: FileText,
    title: "Creating Notes",
    description: "How to write, organize, and manage notes effectively.",
    items: [
      {
        question: "What should I put in a note?",
        answer:
          "Paste or type your study material — lecture notes, textbook excerpts, reviewer content, or anything you want to study from. The more complete your note, the better the generated quiz and summary.",
      },
      {
        question: "What do Subject and Course / Program do?",
        answer:
          "Subject helps you organize and filter notes in your Library. Course / Program helps NoteLib tailor content and recommendations to your field of study.",
      },
      {
        question: "Can I edit a note after generating a Study Pack?",
        answer:
          'Note content is locked after generation to preserve the Study Pack. You can still update the title, subject, course/program, and tags. To improve the note content, use "Make a Copy", edit the copy, and generate a new Study Pack.',
      },
    ],
  },
  {
    id: "study-packs-quizzes",
    icon: BookOpen,
    title: "Study Packs & Quizzes",
    description: "Summaries, key concepts, and the three quiz modes explained.",
    items: [
      {
        question: "What is the Summary tab?",
        answer:
          "The Summary is an AI-generated overview of your note, condensed into key points to help you recall the main ideas at a glance.",
      },
      {
        question: "What are Key Concepts?",
        answer:
          "Key Concepts are the most important ideas extracted from your note, organized for quick review and reference.",
      },
      {
        question: "What is Quick Review?",
        answer:
          "Quick Review uses your saved practice questions in a low-pressure session. Questions you miss are shown again in a retry round.",
      },
      {
        question: "What is Challenge Quiz?",
        answer:
          "Challenge Quiz generates new timed questions to simulate test conditions. It has difficulty levels (Easy, Medium, Hard) and tracks your best score per note.",
      },
      {
        question: "What is Adaptive Practice?",
        answer:
          "Adaptive Practice focuses on concepts you missed in Challenge Quiz. It targets your weak areas with questions built from your recent performance. Available on the Premium plan.",
      },
    ],
  },
  {
    id: "performance",
    icon: BarChart2,
    title: "Performance & Insights",
    description: "Tracking scores, weak concepts, and your progress over time.",
    items: [
      {
        question: "Where can I see my quiz results?",
        answer:
          "Each note's detail page shows a Performance Overview with attempt counts and scores for Quick Review and Challenge Quiz. The Profile page shows your top-performing notes grouped by best score.",
      },
      {
        question: "What are Weak Concepts?",
        answer:
          "Weak Concepts are topics where your Challenge Quiz accuracy was below 60%. They appear on your Dashboard and note detail page to guide your next study session.",
      },
      {
        question: "What is the Strongest Notes section on the Dashboard?",
        answer:
          'Strongest Notes shows the top 3 notes by your best quiz score. Click any note to review the session, or click "View all" to see your full performance breakdown on the Profile page.',
      },
    ],
  },
  {
    id: "export-sharing",
    icon: Download,
    title: "Export & Sharing",
    description: "Download quiz sessions as PDFs and share notes publicly.",
    items: [
      {
        question: "Can I export a quiz session?",
        answer:
          "Yes. On any Session Review page, use the Export button to download a PDF. You can export the Full Review (all questions), Mistakes Only (incorrect answers), or Weak Concepts (questions from identified weak areas).",
      },
      {
        question: "What format are exported files?",
        answer:
          "Exports are PDF files named with the note title and date, such as notelib-quiz-biology-2026-04-16.pdf. They include question text, answers, explanations, and a score summary.",
      },
      {
        question: "How do I share a note publicly?",
        answer:
          "On the note detail page, use the visibility toggle to set the note to Public. It will then appear in the Public Library for other students to view and copy.",
      },
    ],
  },
  {
    id: "profile-settings",
    icon: Settings,
    title: "Profile & Settings",
    description: "Account preferences, learner level, theme, and plan details.",
    items: [
      {
        question: "What does Learner Level do?",
        answer:
          "Learner Level adjusts quiz difficulty and content recommendations based on your current study stage. You can update it anytime from your Profile page.",
      },
      {
        question: "Where do I change my theme?",
        answer:
          'Go to Settings > Preferences. You can choose Light, Dark, or System. System follows your device setting automatically.',
      },
      {
        question: "Where can I see my plan and usage?",
        answer:
          "Settings > Plan & Billing shows your current plan, Study Pack usage for the month, and reset date. The Dashboard also shows a usage summary card.",
      },
    ],
  },
  {
    id: "student-guide",
    icon: User,
    title: "Student Guide",
    description: "A step-by-step study workflow to get the most out of NoteLib.",
    items: [],
  },
  {
    id: "teacher-guide",
    icon: GraduationCap,
    title: "Teacher Guide",
    description: "Turn your lesson materials into study packs and exportable quiz content.",
    items: [],
  },
];

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
          description={
            openCard.id === "student-guide"
              ? "Study smarter using notes."
              : openCard.id === "teacher-guide"
                ? "Use your lesson materials to build review-ready study content faster."
                : undefined
          }
          onClose={() => setOpenCardId(null)}
          panelClassName="sm:max-w-lg"
        >
          {openCard.id === "getting-started" ? (
            <GettingStartedGuide />
          ) : openCard.id === "student-guide" ? (
            <StudentGuide />
          ) : openCard.id === "teacher-guide" ? (
            <TeacherGuide />
          ) : (
            <div className="space-y-5">
              {openCard.items.map((item) => (
                <div key={item.question} className="space-y-1">
                  <p className="text-sm font-medium text-foreground">{item.question}</p>
                  <p className="text-sm text-foreground/70">{item.answer}</p>
                </div>
              ))}
            </div>
          )}
        </AppModal>
      ) : null}
    </main>
  );
}
