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

jest.mock("@/components/notes/public-seo-copy-cta", () => ({
  PublicSeoCopyCta: ({ noteId }: { noteId: string }) => (
    <button type="button">Make a Copy and Generate Your Own Quiz ({noteId})</button>
  ),
}));

const baseNote = {
  id: "note-1",
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

    render(
      await PublicLibrarySeoPage({
        params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
      }),
    );

    expect(screen.getByText("Public Library")).toBeInTheDocument();
    expect(screen.getByText("Generated with NoteLib")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Cell Structure" })).toBeInTheDocument();
    expect(screen.getByText("Subject: Science")).toBeInTheDocument();
    expect(screen.getByText("By studybuddy")).toBeInTheDocument();
    expect(screen.getByText("Cell structure summary")).toBeInTheDocument();
    expect(screen.getByText("Cell membrane")).toBeInTheDocument();
    expect(screen.getByText(/What controls the cell\?/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Make a Copy and Generate Your Own Quiz/i })).toBeInTheDocument();
  });

  it("returns SEO metadata for a public note", async () => {
    (getServerPublicNoteBySeoPath as jest.Mock).mockResolvedValue(baseNote);

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "science", slug: "cell-structure" }),
    });

    expect(metadata.title).toBe("Cell Structure Summary and Reviewer | NoteLib");
    expect(metadata.description).toBe(
      "Study Cell Structure with summary, key concepts, and quiz reviewer. Free study pack from NoteLib.",
    );
    expect(metadata.openGraph).toMatchObject({
      title: "Cell Structure Summary and Reviewer | NoteLib",
      type: "article",
      url: "http://localhost:3000/public/library/science/cell-structure",
    });
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
