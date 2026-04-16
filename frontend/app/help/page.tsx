"use client";

import Link from "next/link";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";

type HelpSection = {
  id: string;
  title: string;
  items: Array<{ question: string; answer: string }>;
};

const HELP_SECTIONS: HelpSection[] = [
  {
    id: "getting-started",
    title: "Getting Started",
    items: [
      {
        question: "What is NoteLib?",
        answer:
          "NoteLib turns your notes into structured study outputs. You create a note, generate a Study Pack, then practice with Quick Review, Challenge Quiz, and Adaptive Practice.",
      },
      {
        question: "How do I create a note?",
        answer:
          "Go to Library and click \"New Note\", or click \"Create Note\" from the Dashboard. You can type, paste, or upload content. Save the note first, then generate a Study Pack.",
      },
      {
        question: "What is a Study Pack?",
        answer:
          "A Study Pack is AI-generated content attached to your note: a summary, key concepts, and a practice quiz. Click \"Generate Study Pack\" on any saved note to create one.",
      },
    ],
  },
  {
    id: "creating-notes",
    title: "Creating Notes",
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
          "Note content is locked after generation to preserve the Study Pack. You can still update the title, subject, course/program, and tags. To improve the note content, use \"Make a Copy\", edit the copy, and generate a new Study Pack.",
      },
    ],
  },
  {
    id: "study-packs",
    title: "Study Packs",
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
        question: "What is the Quiz tab?",
        answer:
          "The Quiz tab shows the practice questions generated from your note. Use Quick Review or Challenge Quiz buttons to start a session.",
      },
    ],
  },
  {
    id: "quiz-types",
    title: "Quiz Types",
    items: [
      {
        question: "What is Quick Review?",
        answer:
          "Quick Review uses your saved practice questions in a low-pressure session. It helps reinforce recall after studying. Questions you miss are shown again in a retry round.",
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
    title: "Performance Tracking",
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
          "Strongest Notes shows the top 3 notes by your best quiz score. Click any note to review the session, or click \"View all\" to see your full performance breakdown on the Profile page.",
      },
    ],
  },
  {
    id: "exporting",
    title: "Exporting Quizzes",
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
    ],
  },
];

export default function HelpPage() {
  const router = useRouter();

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
  }, [router]);

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="HELP"
        title="Help Center"
        description="Quick answers to common questions about NoteLib."
      />

      <div className="space-y-4">
        {HELP_SECTIONS.map((section) => (
          <Card key={section.id} id={section.id} className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">{section.title}</h2>
            <div className="space-y-4">
              {section.items.map((item) => (
                <div key={item.question} className="space-y-1">
                  <p className="text-sm font-medium text-foreground">{item.question}</p>
                  <p className="text-sm text-foreground/70">{item.answer}</p>
                </div>
              ))}
            </div>
          </Card>
        ))}
      </div>

      <Card className="p-4 sm:p-6">
        <p className="text-sm text-foreground/70">
          Still have questions?{" "}
          <Link
            href="/settings"
            className="font-medium text-blue-600 hover:underline dark:text-blue-400"
          >
            Visit Settings
          </Link>{" "}
          to manage your account, or explore the{" "}
          <Link
            href="/public/library"
            className="font-medium text-blue-600 hover:underline dark:text-blue-400"
          >
            Public Library
          </Link>{" "}
          to see notes shared by other students.
        </p>
      </Card>
    </main>
  );
}
