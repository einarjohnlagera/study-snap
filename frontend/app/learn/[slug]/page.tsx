import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicFooter } from "@/components/public/public-footer";
import { LearnArticleLayout } from "@/components/public/learn-article-layout";
import { getLearnGuideBySlug, learnGuides } from "@/lib/learn-guides";
import { buildPageMetadata } from "@/lib/site-metadata";

type LearnArticlePageProps = {
  params: Promise<{
    slug: string;
  }>;
};

export async function generateStaticParams() {
  return learnGuides.map((guide) => ({
    slug: guide.slug,
  }));
}

export async function generateMetadata({ params }: LearnArticlePageProps): Promise<Metadata> {
  const { slug } = await params;
  const guide = getLearnGuideBySlug(slug);

  if (!guide) {
    return buildPageMetadata({
      title: "Learn",
      description: "Study guides and tips for students, board exam reviewees, and teachers.",
      path: "/learn",
    });
  }

  return buildPageMetadata({
    title: `${guide.title} | NoteLib Learn`,
    description: guide.description,
    path: `/learn/${guide.slug}`,
  });
}

export default async function LearnArticlePage({ params }: Readonly<LearnArticlePageProps>) {
  const { slug } = await params;
  const guide = getLearnGuideBySlug(slug);

  if (!guide) {
    notFound();
  }

  return (
    <>
      <LearnArticleLayout guide={guide} />
      <div className="mx-auto w-full max-w-4xl px-4 pb-10 sm:px-6 sm:pb-12">
        <PublicFooter />
      </div>
    </>
  );
}
