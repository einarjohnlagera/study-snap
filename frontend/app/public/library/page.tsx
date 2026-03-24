import type { Metadata } from "next";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { PublicLibraryPageClient } from "@/components/notes/public-library-page-client";
import { absoluteUrl, buildPageMetadata } from "@/lib/site-metadata";
import { buildCollectionPageStructuredData } from "@/lib/structured-data";

const publicLibraryDescription =
  "Browse public study notes, summaries, and practice quizzes shared by the NoteLib community.";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Public Library – Free Study Notes, Summaries, and Quizzes",
  description: publicLibraryDescription,
  path: "/public/library",
});

export default function PublicLibrarySeoIndexPage() {
  return (
    <>
      <StructuredDataScript
        id="public-library-structured-data"
        data={buildCollectionPageStructuredData({
          name: "NoteLib Public Library",
          url: absoluteUrl("/public/library"),
          description: publicLibraryDescription,
        })}
      />
      <PublicLibraryPageClient />
    </>
  );
}
