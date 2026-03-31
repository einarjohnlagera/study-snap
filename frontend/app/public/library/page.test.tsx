import { render, screen } from "@testing-library/react";
import PublicLibrarySeoIndexPage, { metadata } from "./page";

jest.mock("@/components/notes/public-library-page-client", () => ({
  PublicLibraryPageClient: () => <div>Public Library Client</div>,
}));

describe("PublicLibrarySeoIndexPage", () => {
  it("renders the public library page client", () => {
    const { container } = render(<PublicLibrarySeoIndexPage />);

    expect(screen.getByText("Public Library Client")).toBeInTheDocument();
    const structuredData = container.querySelector("#public-library-structured-data");
    expect(structuredData).not.toBeNull();
    expect(structuredData?.textContent).toContain('"@type":"CollectionPage"');
    expect(structuredData?.textContent).toContain('"name":"NoteLib Public Library"');
  });

  it("exports public library metadata with canonical and social preview fields", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib Public Library – Free Study Notes, Summaries, and Quizzes",
      description: "Browse public study notes, summaries, and practice quizzes shared by the NoteLib community.",
      alternates: {
        canonical: "https://notelib.app/public/library",
      },
      openGraph: expect.objectContaining({
        type: "website",
        url: "https://notelib.app/public/library",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({ url: "https://notelib.app/og-image.png" }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        images: ["https://notelib.app/og-image.png"],
      }),
    });
  });
});
