import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { HashScrollListener } from "@/components/navigation/hash-scroll-listener";
import { PublicFooter } from "@/components/public/public-footer";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { buildPageMetadata } from "@/lib/site-metadata";
import {
  getLearnGuidesByCategory,
  LEARN_CATEGORY_ANCHORS,
  learnGuideCategories,
} from "@/lib/learn-guides";

export const metadata: Metadata = buildPageMetadata({
  title: "Study Guides — Turn Notes Into Quizzes | NoteLib",
  description: "Study guides for students, board exam reviewees, teachers, and professionals who want to turn notes into reviewers, practice questions, and better exam prep.",
  path: "/learn",
});

export default function LearnPage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-10 px-4 py-8 sm:px-6 sm:py-12">
      <HashScrollListener allowedIds={Object.values(LEARN_CATEGORY_ANCHORS)} />
      <section className="space-y-4 rounded-[2rem] border border-border bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.16),_transparent_35%),linear-gradient(135deg,_rgba(255,255,255,0.98),_rgba(239,246,255,0.92))] p-6 shadow-sm dark:bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.2),_transparent_35%),linear-gradient(135deg,_rgba(2,6,23,0.94),_rgba(15,23,42,0.94))] sm:p-8">
        <BrandFullLogo width={208} height={44} priority />
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Learn
        </p>
        <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
          Learn How to Turn Notes Into Quizzes and Interview Prep
        </h1>
        <p className="max-w-3xl text-base leading-relaxed text-foreground/75">
          Study guides and onboarding content for students, board exam reviewees, teachers, and professionals preparing for job interviews. Learn how to turn notes into reviewers, practice questions, weak-concept review, and better study habits.
        </p>
      </section>

      {learnGuideCategories.map((category) => {
        const guides = getLearnGuidesByCategory(category.key);

        return (
          <section id={LEARN_CATEGORY_ANCHORS[category.key]} key={category.key} className="space-y-5">
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                {category.title}
              </p>
              <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
                {category.description}
              </p>
            </div>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {guides.map((guide) => (
                <Card key={guide.slug} className="flex h-full flex-col gap-4 p-5 sm:p-6">
                  <div className="space-y-2">
                    <CardTitle>{guide.title}</CardTitle>
                    <CardDescription className="text-sm">{guide.description}</CardDescription>
                  </div>
                  <div className="mt-auto">
                    <Link
                      href={`/learn/${guide.slug}`}
                      className="inline-flex items-center gap-2 text-sm font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      Read Guide
                      <ArrowRight className="h-4 w-4" />
                    </Link>
                  </div>
                </Card>
              ))}
            </div>
          </section>
        );
      })}

      <PublicFooter />
    </main>
  );
}
