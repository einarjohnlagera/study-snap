import { render, screen } from "@testing-library/react";
import PublicLibrarySeoIndexPage, { metadata } from "./page";

jest.mock("@/components/notes/public-library-page-client", () => ({
  PublicLibraryPageClient: () => <div>Public Library Client</div>,
}));

describe("PublicLibrarySeoIndexPage", () => {
  it("renders the public library page client", () => {
    render(<PublicLibrarySeoIndexPage />);

    expect(screen.getByText("Public Library Client")).toBeInTheDocument();
  });

  it("exports public library metadata with canonical and social preview fields", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib Public Library – Free Study Notes, Summaries, and Quizzes",
      description: "Browse public study notes, summaries, and practice quizzes shared by the NoteLib community.",
      alternates: {
        canonical: "https://www.notelib.app/public/library",
      },
      openGraph: expect.objectContaining({
        type: "website",
        url: "https://www.notelib.app/public/library",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({ url: "https://www.notelib.app/og-image.png" }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        images: ["https://www.notelib.app/og-image.png"],
      }),
    });
  });
});
