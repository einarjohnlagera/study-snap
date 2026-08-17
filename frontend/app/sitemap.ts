import type { MetadataRoute } from "next";
import { buildPublicLibraryNotePath, buildPublicLibrarySubjectPath } from "@/lib/public-note-path";
import { EXAM_HUB_SLUGS } from "@/lib/exam-hub-config";
import { learnGuides } from "@/lib/learn-guides";
import { getPublicSubjectEntries, getServerPublicNotes, SUBJECT_PAGE_INDEX_THRESHOLD } from "@/lib/server-public-notes";
import { absoluteUrl } from "@/lib/site-metadata";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  let publicNotes: Awaited<ReturnType<typeof getServerPublicNotes>>;
  try {
    publicNotes = await getServerPublicNotes();
  } catch {
    publicNotes = [];
  }
  const publicSubjects = getPublicSubjectEntries(publicNotes);

  return [
    {
      url: absoluteUrl("/"),
      changeFrequency: "weekly" as const,
      priority: 1,
    },
    {
      url: absoluteUrl("/pricing"),
      changeFrequency: "weekly" as const,
      priority: 0.9,
    },
    {
      url: absoluteUrl("/how-it-works"),
      changeFrequency: "weekly" as const,
      priority: 0.9,
    },
    {
      url: absoluteUrl("/learn"),
      changeFrequency: "weekly" as const,
      priority: 0.9,
    },
    ...learnGuides.map((guide) => ({
      url: absoluteUrl(`/learn/${guide.slug}`),
      changeFrequency: "monthly" as const,
      priority: 0.8,
    })),
    {
      url: absoluteUrl("/privacy"),
      changeFrequency: "monthly" as const,
      priority: 0.3,
    },
    {
      url: absoluteUrl("/terms"),
      changeFrequency: "monthly" as const,
      priority: 0.3,
    },
    {
      url: absoluteUrl("/refund"),
      changeFrequency: "monthly" as const,
      priority: 0.3,
    },
    {
      url: absoluteUrl("/public/library"),
      changeFrequency: "daily" as const,
      priority: 0.9,
    },
    {
      // Added in v0.84.0, when /explore became anonymous. It was previously omitted because a
      // signed-out visitor was redirected to /login, so submitting it would have advertised a
      // dead end. It is now the public discovery destination and is self-canonical with its own
      // CollectionPage identity, so it belongs here alongside the sources it composites.
      url: absoluteUrl("/explore"),
      changeFrequency: "daily" as const,
      priority: 0.9,
    },
    {
      url: absoluteUrl("/exam"),
      changeFrequency: "weekly" as const,
      priority: 0.9,
    },
    ...EXAM_HUB_SLUGS.map((slug) => ({
      url: absoluteUrl(`/exam/${slug}`),
      changeFrequency: "daily" as const,
      priority: 0.9,
    })),
    ...publicSubjects
      .filter((subject) => subject.noteCount >= SUBJECT_PAGE_INDEX_THRESHOLD)
      .map((subject) => ({
        url: absoluteUrl(buildPublicLibrarySubjectPath(subject.label)),
        lastModified: subject.lastModified ?? undefined,
        changeFrequency: "daily" as const,
        priority: 0.8,
      })),
    ...publicNotes.map((note) => ({
      url: absoluteUrl(buildPublicLibraryNotePath({ subject: note.subject, title: note.title })),
      lastModified: note.updatedAt ?? undefined,
      changeFrequency: "monthly" as const,
      priority: 0.7,
    })),
  ];
}
