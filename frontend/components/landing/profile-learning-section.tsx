"use client";

import { useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  BarChart3,
  BookOpen,
  Briefcase,
  ClipboardList,
  Download,
  FileText,
  GraduationCap,
  Library,
  MessageSquare,
  Share2,
  Sparkles,
  Trophy,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { ProductScreenshotFrame } from "@/components/public/product-screenshot-frame";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { buildLearnCategoryPath, type LearnGuideCategory } from "@/lib/learn-guides";
import { cn } from "@/lib/utils";

type ProfileKey = "students" | "exam-reviewers" | "teachers" | "professionals";

type ModeChip = {
  label: string;
  tier: "free" | "paid" | "soon";
  tierLabel: string;
};

type LearningStep = {
  title: string;
  description: string;
  icon: LucideIcon;
};

type ProfileLearningLoop = {
  key: ProfileKey;
  label: string;
  tagline: string;
  description: string;
  icon: LucideIcon;
  learnCategory: LearnGuideCategory;
  learnLinkLabel: string;
  examHubLinkLabel?: string;
  steps: LearningStep[];
  modeChips: ModeChip[];
  screenshot: {
    src: string;
    darkSrc: string;
    alt: string;
  };
};

const profileLearningLoops: ProfileLearningLoop[] = [
  {
    key: "students",
    label: "Students",
    tagline: "Create · Understand · Practice · Challenge · Improve",
    description: "Build a reusable notes library, then turn each note into summaries, quizzes, and review sessions that keep improving with you.",
    icon: GraduationCap,
    learnCategory: "students",
    learnLinkLabel: "See guides for students",
    steps: [
      { title: "Create", description: "write or paste notes", icon: FileText },
      { title: "Understand", description: "summaries + key concepts", icon: BookOpen },
      { title: "Practice", description: "Quick Review", icon: Sparkles },
      { title: "Challenge", description: "Challenge Quiz / Long Exam", icon: Trophy },
      { title: "Improve", description: "Adaptive Practice", icon: ArrowRight },
    ],
    modeChips: [
      { label: "Quick Review", tier: "free", tierLabel: "Free" },
      { label: "Challenge Quiz", tier: "free", tierLabel: "Free" },
      { label: "Long Exam", tier: "paid", tierLabel: "Pro" },
      { label: "Adaptive Practice", tier: "free", tierLabel: "Free taste" },
    ],
    screenshot: {
      src: "/landing/profile-student-light.png",
      darkSrc: "/landing/profile-student-dark.png",
      alt: "Long Exam Mastery Report with domain breakdown",
    },
  },
  {
    key: "exam-reviewers",
    label: "Exam Reviewers",
    tagline: "Create · Understand · Practice · Simulate · Improve",
    description: "Turn reviewer notes into structured practice for board, licensure, certification, and other high-stakes exam prep.",
    icon: ClipboardList,
    learnCategory: "board-exams",
    learnLinkLabel: "See guides for board exams",
    examHubLinkLabel: "Explore Exam Hubs",
    steps: [
      { title: "Create", description: "reviewer notes", icon: FileText },
      { title: "Understand", description: "summaries + key concepts", icon: BookOpen },
      { title: "Practice", description: "Quick Review / Challenge Quiz", icon: Sparkles },
      { title: "Simulate", description: "Board Exam", icon: Trophy },
      { title: "Improve", description: "Adaptive Practice", icon: ArrowRight },
    ],
    modeChips: [
      { label: "Quick Review", tier: "free", tierLabel: "Free" },
      { label: "Challenge Quiz", tier: "free", tierLabel: "Free" },
      { label: "Board Exam", tier: "paid", tierLabel: "Pro" },
      { label: "Adaptive Practice", tier: "free", tierLabel: "Free taste" },
    ],
    screenshot: {
      src: "/landing/profile-reviewer-light.png",
      darkSrc: "/landing/profile-reviewer-dark.png",
      alt: "Board Exam Score Report",
    },
  },
  {
    key: "teachers",
    label: "Teachers",
    tagline: "Create · Generate · Preview · Export · Share",
    description: "Move from lesson notes to classroom-ready quiz drafts, with the sharing flow previewed for this release.",
    icon: Library,
    learnCategory: "teachers",
    learnLinkLabel: "See guides for teachers",
    steps: [
      { title: "Create", description: "lesson note", icon: FileText },
      { title: "Generate", description: "quiz from notes", icon: Sparkles },
      { title: "Preview", description: "Preview & refine questions", icon: MessageSquare },
      { title: "Export", description: "DOCX", icon: Download },
      { title: "Share", description: "Share quiz link to students (coming soon in this release)", icon: Share2 },
    ],
    modeChips: [
      { label: "Generate", tier: "free", tierLabel: "Free" },
      { label: "Preview", tier: "free", tierLabel: "Free" },
      { label: "Export DOCX", tier: "free", tierLabel: "Free" },
      { label: "Share link", tier: "soon", tierLabel: "Coming soon" },
    ],
    screenshot: {
      src: "/landing/profile-teacher-light.png",
      darkSrc: "/landing/profile-teacher-dark.png",
      alt: "Quiz preview and DOCX export",
    },
  },
  {
    key: "professionals",
    label: "Professionals",
    tagline: "Create · Understand · Practice · Critique · Report",
    description: "Practice domain and interview scenarios from your own notes, then use critique and reporting to sharpen readiness.",
    icon: Briefcase,
    learnCategory: "professionals",
    learnLinkLabel: "See guides for professionals",
    steps: [
      { title: "Create", description: "domain notes", icon: FileText },
      { title: "Understand", description: "summaries", icon: BookOpen },
      { title: "Practice", description: "scenario MCQ via Interview Practice", icon: ClipboardList },
      { title: "Critique", description: "per-answer feedback on your reasoning", icon: MessageSquare },
      { title: "Report", description: "Interview Readiness Report", icon: BarChart3 },
    ],
    modeChips: [
      { label: "Interview Practice", tier: "paid", tierLabel: "Pro" },
      { label: "AI Critique", tier: "paid", tierLabel: "Pro" },
      { label: "Readiness Report", tier: "paid", tierLabel: "Pro" },
    ],
    screenshot: {
      src: "/landing/profile-professional-light.png",
      darkSrc: "/landing/profile-professional-dark.png",
      alt: "Interview Readiness Report",
    },
  },
];

function getChipClassName(tier: ModeChip["tier"]) {
  if (tier === "paid") {
    return "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300";
  }

  if (tier === "soon") {
    return "border-sky-500/25 bg-sky-500/10 text-sky-700 dark:text-sky-300";
  }

  return "border-border bg-muted/40 text-foreground/75";
}

export function ProfileLearningSection() {
  const [selectedProfileKey, setSelectedProfileKey] = useState<ProfileKey>("students");
  const selectedProfile = profileLearningLoops.find((profile) => profile.key === selectedProfileKey) ?? profileLearningLoops[0];

  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Who It&apos;s For</p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Built for your study workflow</h2>
        <p className="max-w-3xl text-sm text-foreground/75">
          Choose your role and see how NoteLib moves your notes into summaries, quizzes, review, and next-step improvement.
        </p>
      </div>

      <div className="flex gap-2 overflow-x-auto rounded-2xl border border-border/80 bg-muted/20 p-1">
        {profileLearningLoops.map((profile) => {
          const isSelected = profile.key === selectedProfileKey;

          return (
            <button
              key={profile.key}
              type="button"
              aria-pressed={isSelected}
              onClick={() => setSelectedProfileKey(profile.key)}
              className={cn(
                "inline-flex min-h-11 shrink-0 items-center gap-2 rounded-xl px-3 py-2 text-sm font-semibold text-foreground/70 transition hover:bg-background/80 hover:text-foreground sm:px-4",
                isSelected && "bg-background text-foreground shadow-sm ring-1 ring-border",
              )}
            >
              <profile.icon className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              {profile.label}
            </button>
          );
        })}
      </div>

      <div className="rounded-[2rem] border border-border/80 bg-[linear-gradient(135deg,_rgba(248,250,252,0.98),_rgba(241,245,249,0.9))] p-5 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(15,23,42,0.92),_rgba(15,23,42,0.82))] sm:p-6 lg:p-8">
        <div key={selectedProfileKey} className="motion-fade-enter space-y-6">
          <div className="grid gap-8 lg:grid-cols-[1.05fr_0.95fr] lg:items-start">
            <div className="space-y-3">
              <div className="inline-flex items-center gap-2 rounded-full border border-sky-500/20 bg-background/80 px-3 py-1 text-xs font-semibold text-sky-700 dark:text-sky-300">
                <selectedProfile.icon className="h-3.5 w-3.5" />
                {selectedProfile.label}
              </div>
              <div className="space-y-2">
                <h3 className="text-xl font-semibold sm:text-2xl">{selectedProfile.tagline}</h3>
                <p className="max-w-2xl text-sm leading-relaxed text-foreground/75">
                  {selectedProfile.description}
                </p>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {selectedProfile.modeChips.map((chip) => (
                  <span
                    key={chip.label}
                    className={cn(
                      "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold",
                      getChipClassName(chip.tier),
                    )}
                  >
                    {chip.label}
                    <span className="text-[10px] font-bold uppercase tracking-wide">{chip.tierLabel}</span>
                  </span>
                ))}
              </div>
              <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm font-medium">
                <Link
                  href="/how-it-works"
                  className="inline-flex items-center gap-1.5 text-sky-700 transition hover:text-sky-800 hover:underline dark:text-sky-300 dark:hover:text-sky-200"
                >
                  See the full walkthrough
                  <ArrowRight className="h-4 w-4" />
                </Link>
                <Link
                  href={buildLearnCategoryPath(selectedProfile.learnCategory)}
                  className="inline-flex items-center gap-1.5 text-sky-700 transition hover:text-sky-800 hover:underline dark:text-sky-300 dark:hover:text-sky-200"
                >
                  {selectedProfile.learnLinkLabel}
                  <ArrowRight className="h-4 w-4" />
                </Link>
                {selectedProfile.examHubLinkLabel ? (
                  <Link
                    href="/exam"
                    className="inline-flex items-center gap-1.5 text-sky-700 transition hover:text-sky-800 hover:underline dark:text-sky-300 dark:hover:text-sky-200"
                  >
                    {selectedProfile.examHubLinkLabel}
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                ) : null}
              </div>
            </div>

            <div className="w-full lg:sticky lg:top-24">
              <ProductScreenshotFrame
                src={selectedProfile.screenshot.src}
                darkSrc={selectedProfile.screenshot.darkSrc}
                alt={selectedProfile.screenshot.alt}
                className="rounded-[1.5rem] border border-border/80 bg-background/85 shadow-[0_18px_40px_rgba(15,23,42,0.1)]"
                imageClassName="max-h-[360px] object-contain object-top sm:max-h-[420px] lg:max-h-[480px]"
                sizes="(min-width: 1280px) 440px, (min-width: 1024px) 42vw, (min-width: 768px) 88vw, 100vw"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {selectedProfile.steps.map((step, index) => (
              <Card key={step.title} className="space-y-4 p-5">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
                    Step {index + 1}
                  </span>
                  <step.icon className="h-5 w-5 shrink-0 text-sky-600 dark:text-sky-400" />
                </div>
                <div className="space-y-2">
                  <CardTitle>{step.title}</CardTitle>
                  <CardDescription className="text-sm">{step.description}</CardDescription>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
