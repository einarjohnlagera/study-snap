import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { PublicFooter } from "@/components/public/public-footer";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { buildPageMetadata } from "@/lib/site-metadata";
import { learnGuides } from "@/lib/learn-guides";

export const metadata: Metadata = buildPageMetadata({
  title: "Learn How to Study Smarter",
  description: "Study guides and tips for students, board exam reviewees, and teachers.",
  path: "/learn",
});

export default function LearnPage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-4 py-8 sm:px-6 sm:py-12">
      <section className="space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Learn
        </p>
        <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
          Learn How to Study Smarter
        </h1>
        <p className="max-w-3xl text-base leading-relaxed text-foreground/75">
          Study guides and tips for students, board exam reviewees, and teachers.
        </p>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        {learnGuides.map((guide) => (
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
      </section>

      <PublicFooter />
    </main>
  );
}
