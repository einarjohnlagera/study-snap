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
    authorDisplayName,
    isOfficialAuthor,
    isCurrentUser,
    subject,
  }: {
    ownerUserId: string | null;
    authorDisplayName: string;
    isOfficialAuthor: boolean;
    isCurrentUser: boolean;
    subject?: string | null;
  }) => (
    <div>
      Author line for {ownerUserId ?? "none"} / {authorDisplayName} / {isOfficialAuthor ? "official" : "regular"} / {isCurrentUser ? "current" : "other"} / {subject ?? "none"}
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

jest.mock("@/components/notes/public-mini-quiz-preview", () => ({
  PublicMiniQuizPreview: ({ quiz, noteId }: { quiz: { question: string }[]; noteId: string }) => (
    <div data-testid="mini-quiz-preview">
      Mini quiz for {noteId}: {quiz[0]?.question ?? "no question"}
    </div>
  ),
}));

const baseNote = {
  id: "note-1",
  ownerUserId: "user-2",
  title: "Cell Structure",
  subject: "Science",
  tags: ["biology", "cells"],
  content: "Cells are the basic unit of life.\n\nThey contain organelles that support cell function.",
  contentPreview: "Cells are the basic unit of life.",
  studyPackStatus: "STUDY_PACK_READY",
  summary: "Cell structure is fundamental to biology. Every living organism is made of cells. Cells vary widely in size and function.",
  keyConcepts: ["Cell membrane", "Nucleus"],
  quiz: [
    {
      question: "What controls the cell?",
      choices: ["Nucleus", "Cytoplasm", "Membrane", "Ribosome"],
      correctIndex: 0,
      answer: "Nucleus",
      explanation: "The nucleus controls cell activity.",
    },
  ],
  authorDisplayName: "studybuddy",
  isOfficialAuthor: false,
  isCurrentUser: false,
  updatedAt: "2026-03-23T09:00:00Z",
};

describe("PublicLibrarySeoPage", () => {
  beforeEach(() => {
    notFoundMock.mockClear();
    (getServerPublicNoteBySeoPath as jest.Mock).mockReset();
  });

  it("renders title, author, and summary without requiring any interaction", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.getByRole("heading", { name: "Cell Structure" })).toBeInTheDocument();
    expect(screen.getByText("Author line for user-2 / studybuddy / regular / other / Science")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Summary" })).toBeInTheDocument();
    expect(screen.getAllByText(/Cell structure is fundamental to biology/i).length).toBeGreaterThanOrEqual(1);
  });

  it("renders hook derived from the first two sentences of the summary", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    // Hook is the first 2 sentences and is an exact match — does not include the third sentence
    const hookEl = screen.getByText(
      "Cell structure is fundamental to biology. Every living organism is made of cells.",
      { exact: true },
    );
    expect(hookEl).toBeInTheDocument();
    expect(hookEl.textContent).not.toMatch(/Cells vary widely/i);
  });

  it("renders key concepts immediately without a tab click", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.getByRole("heading", { name: "🧠 Key Concepts" })).toBeInTheDocument();
    expect(screen.getByText("Cell membrane")).toBeInTheDocument();
    expect(screen.getByText("Nucleus")).toBeInTheDocument();
  });

  it("renders the mini quiz preview for a study-pack-ready note with quiz", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.getByTestId("mini-quiz-preview")).toBeInTheDocument();
    expect(screen.getByText(/Mini quiz for note-1: What controls the cell\?/i)).toBeInTheDocument();
  });

  it("does not render the mini quiz preview for a draft note", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "DRAFT",
    });

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.queryByTestId("mini-quiz-preview")).not.toBeInTheDocument();
  });

  it("does not render the mini quiz preview when the note has no quiz", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue({
      ...baseNote,
      quiz: [],
    });

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.queryByTestId("mini-quiz-preview")).not.toBeInTheDocument();
  });

  it("renders the soft conversion CTA", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.getByRole("heading", { name: "Struggling with a topic?" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Create your own Study Pack/i })).toBeInTheDocument();
  });

  it("renders full notes section", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.getByRole("heading", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.getByText(/Cells are the basic unit of life\./i)).toBeInTheDocument();
  });

  it("renders ownership actions after content (not in the header)", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    const { container } = render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    const ownershipEl = screen.getByText("Ownership actions for note-1 / user-2");
    const summaryEl = screen.getByRole("heading", { name: "Summary" });
    const headerEl = container.querySelector("header");

    expect(ownershipEl).toBeInTheDocument();
    expect(summaryEl).toBeInTheDocument();
    // Ownership actions must not be inside the header element
    expect(headerEl?.contains(ownershipEl)).toBe(false);
  });

  it("does not render a hook when the summary is empty", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue({
      ...baseNote,
      summary: "",
    });

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    // Title still renders; no hook since summary is empty
    expect(screen.getByRole("heading", { name: "Cell Structure" })).toBeInTheDocument();
    // Summary section shows the fallback text
    expect(screen.getByText("No summary available yet.")).toBeInTheDocument();
  });

  it("emits Article structured data", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    const { container } = render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

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
    expect(metadata.description).toContain("Cell structure is fundamental");
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
