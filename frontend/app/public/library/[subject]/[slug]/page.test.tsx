import { render, screen } from "@testing-library/react";
import PublicLibrarySeoPage, { generateMetadata } from "./page";
import { getServerPublicNoteBySeoPath } from "@/lib/server-public-notes";

const notFoundMock = jest.fn(() => {
  throw new Error("NEXT_NOT_FOUND");
});

jest.mock("next/navigation", () => ({
  notFound: () => notFoundMock(),
}));

jest.mock("@/lib/server-public-notes", () => ({
  getServerPublicNoteBySeoPath: jest.fn(),
}));

jest.mock("@/components/notes/public-note-ownership-actions", () => ({
  PublicNoteAuthorLine: ({
    ownerUserId,
    official,
    subject,
  }: {
    ownerUserId: string | null;
    official: boolean;
    subject?: string | null;
  }) => (
    <div>
      Author line for {ownerUserId ?? "none"} / {official ? "official" : "community"} / {subject ?? "none"}
    </div>
  ),
  PublicNoteOwnershipActions: ({
    noteId,
    ownerUserId,
  }: {
    noteId: string;
    ownerUserId: string | null;
  }) => (
    <div>
      Ownership actions for {noteId} / {ownerUserId ?? "none"}
    </div>
  ),
}));

const baseNote = {
  id: "note-1",
  ownerUserId: "user-2",
  title: "Cell Structure",
  subject: "Science",
  tags: ["biology", "cells"],
  contentPreview: "Cells are the basic unit of life.",
  studyPackStatus: "STUDY_PACK_READY",
  summary: "Cell structure summary",
  keyConcepts: ["Cell membrane", "Nucleus"],
  quiz: [
    {
      question: "What controls the cell?",
      choices: ["Nucleus", "Cytoplasm", "Membrane", "Ribosome"],
      answer: "Nucleus",
      explanation: "The nucleus controls cell activity.",
    },
  ],
  official: false,
  authorDisplayName: "studybuddy",
  updatedAt: "2026-03-23T09:00:00Z",
};

describe("PublicLibrarySeoPage", () => {
  beforeEach(() => {
    notFoundMock.mockClear();
    (getServerPublicNoteBySeoPath as jest.Mock).mockReset();
  });

  it("renders public note content without requiring auth", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    const { container } = render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.getByText("Public Library")).toBeInTheDocument();
    expect(screen.getByText("Generated with NoteLib")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Cell Structure" })).toBeInTheDocument();
    expect(screen.getByText("Author line for user-2 / community / Science")).toBeInTheDocument();
    expect(screen.getByText("Ownership actions for note-1 / user-2")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Summary" })).toBeInTheDocument();
    expect(screen.getByText("Cell structure summary")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Key Concepts" })).toBeInTheDocument();
    expect(screen.getByText("Cell membrane")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Practice Questions Preview" })).toBeInTheDocument();
    expect(
      screen.getByText("Copy this note or open your own version to try the interactive quiz, see answers, and track your score."),
    ).toBeInTheDocument();
    expect(screen.getByText(/What controls the cell\?/i)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "What you can do with this note in NoteLib" })).toBeInTheDocument();
    expect(screen.getByText("Take Quick Review quiz")).toBeInTheDocument();
    expect(
      screen.getByText(/Preview only\. The full quiz experience, answer reveal, and score tracking are available/i),
    ).toBeInTheDocument();

    const structuredData = container.querySelector("#public-note-structured-data");
    expect(structuredData).not.toBeNull();
    expect(structuredData?.textContent).toContain('"@type":"Article"');
    expect(structuredData?.textContent).toContain('"headline":"Cell Structure"');
    expect(structuredData?.textContent).toContain('"articleSection":"Science"');
    expect(structuredData?.textContent).toContain('"keywords":"biology, cells"');
  });

  it("returns SEO metadata for a public note", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
    });

    expect(metadata.title).toBe("Cell Structure | NoteLib");
    expect(metadata.description).toBe("Cell structure summary");
    expect(metadata.alternates).toEqual({
      canonical: "https://notelib.app/public/library/science/cell-structure",
    });
    expect(metadata.openGraph).toMatchObject({
      title: "Cell Structure | NoteLib",
      type: "article",
      url: "https://notelib.app/public/library/science/cell-structure",
      siteName: "NoteLib",
      images: expect.arrayContaining([
        expect.objectContaining({ url: "https://notelib.app/og-image.png" }),
      ]),
    });
    expect(metadata.twitter).toMatchObject({
      card: "summary_large_image",
      title: "Cell Structure | NoteLib",
      description: "Cell structure summary",
      images: ["https://notelib.app/og-image.png"],
    });
  });

  it("falls back to generated description when the note summary is missing", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue({
      ...baseNote,
      summary: "",
    });

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
    });

    expect(metadata.description).toBe(
      "Study Cell Structure with summaries, key concepts, and practice questions on NoteLib.",
    );
  });

  it("does not render private or missing notes publicly", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(null);

    await expect(
      PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "private-note" }),
      }),
    ).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("marks missing pages as noindex in metadata", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(null);

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "science", slug: "private-note" }),
    });

    expect(metadata.title).toBe("Public Note Not Found | NoteLib");
    expect(metadata.robots).toEqual({ index: false, follow: false });
  });
});
