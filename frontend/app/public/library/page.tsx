import type { Metadata } from "next";
import { PublicLibraryPageClient } from "@/components/notes/public-library-page-client";
import { buildPageMetadata } from "@/lib/site-metadata";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Public Library – Free Study Notes, Summaries, and Quizzes",
  description: "Browse public study notes, summaries, and practice quizzes shared by the NoteLib community.",
  path: "/public/library",
});

export default function PublicLibrarySeoIndexPage() {
  return <PublicLibraryPageClient />;
}
