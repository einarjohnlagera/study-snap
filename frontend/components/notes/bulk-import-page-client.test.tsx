import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { BulkImportPageClient, MAX_BATCH_IMPORT_FILES } from "./bulk-import-page-client";
import {
  addCollectionItems,
  createCollection,
  getMyPlan,
  importNotesBatch,
  listCollections,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const replaceMock = jest.fn();

let bulkSearchParams = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
  useSearchParams: () => bulkSearchParams,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  addCollectionItems: jest.fn(),
  createCollection: jest.fn(),
  getMyPlan: jest.fn(),
  importNotesBatch: jest.fn(),
  listCollections: jest.fn(),
}));

const partialResult = {
  created: [
    {
      noteId: "note-1",
      title: "Cell Biology",
      fileName: "cells.pdf",
      lowConfidence: false,
    },
    {
      noteId: "note-2",
      title: "Lecture Scan",
      fileName: "lecture-scan.jpg",
      lowConfidence: true,
    },
  ],
  failed: [
    {
      fileName: "blank.txt",
      errorCode: "EMPTY_TEXT",
      message: "No readable text was found in this file.",
    },
  ],
};

function selectFiles(files: File[]) {
  fireEvent.change(screen.getByLabelText("Files"), { target: { files } });
}

async function importFiles(files: File[], result = partialResult) {
  (importNotesBatch as jest.Mock).mockResolvedValueOnce(result);
  selectFiles(files);
  fireEvent.click(screen.getByRole("button", { name: `Import ${files.length} files` }));
  await screen.findByRole("heading", { name: "Import results" });
}

function buildFiles(count: number): File[] {
  return Array.from({ length: count }, (_, index) => (
    new File([`file-${index}`], `file-${index}.txt`, { type: "text/plain" })
  ));
}

describe("BulkImportPageClient", () => {
  beforeAll(() => {
    Object.defineProperty(globalThis, "requestAnimationFrame", {
      configurable: true,
      value: (callback: FrameRequestCallback) => {
        callback(0);
        return 0;
      },
    });
    Object.defineProperty(globalThis, "cancelAnimationFrame", {
      configurable: true,
      value: jest.fn(),
    });
  });

  beforeEach(() => {
    replaceMock.mockReset();
    (addCollectionItems as jest.Mock).mockReset();
    (createCollection as jest.Mock).mockReset();
    (importNotesBatch as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      limits: { ocrPerMonth: 20 },
      remaining: { ocrRemaining: 20 },
      usageCycle: { startsAt: "2026-06-01T00:00:00Z", endsAt: "2026-07-01T00:00:00Z" },
    });
    (listCollections as jest.Mock).mockResolvedValue([]);
    (createCollection as jest.Mock).mockResolvedValue({ id: "collection-new", title: "Unit One" });
    (addCollectionItems as jest.Mock).mockResolvedValue({ id: "collection-1", title: "Existing Plan" });
    bulkSearchParams = new URLSearchParams();
  });

  it("points the back link to Library by default and to New Note when from=new", () => {
    render(<BulkImportPageClient />);
    expect(screen.getByRole("link", { name: "Library" })).toHaveAttribute("href", "/library");

    bulkSearchParams = new URLSearchParams("from=new");
    render(<BulkImportPageClient />);
    expect(screen.getByRole("link", { name: "New Note" })).toHaveAttribute("href", "/notes/new");
  });

  it("renders partial success, low-confidence guidance, failures, and draft links", async () => {
    render(<BulkImportPageClient />);

    await importFiles([
      new File(["cells"], "cells.pdf", { type: "application/pdf" }),
      new File(["scan"], "lecture-scan.jpg", { type: "image/jpeg" }),
      new File([""], "blank.txt", { type: "text/plain" }),
    ]);

    expect(screen.getByText("Imported 2 of 3 files.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Created (2)" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Failed (1)" })).toBeInTheDocument();
    expect(screen.getByText("Low confidence — review recommended")).toBeInTheDocument();
    expect(screen.getByText("No readable text was found in this file.")).toBeInTheDocument();
    const reviewLinks = screen.getAllByRole("link", { name: "Review draft" });
    expect(reviewLinks[0]).toHaveAttribute("href", "/notes/note-1");
    expect(reviewLinks[1]).toHaveAttribute("href", "/notes/note-2");
    expect(screen.queryByRole("button", { name: /generate|quiz/i })).not.toBeInTheDocument();
  });

  it("blocks selections above the batch cap", () => {
    render(<BulkImportPageClient />);

    selectFiles(buildFiles(MAX_BATCH_IMPORT_FILES + 1));

    expect(screen.getByText(`You can import up to ${MAX_BATCH_IMPORT_FILES} files at once.`)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: `Import ${MAX_BATCH_IMPORT_FILES + 1} files` })).toBeDisabled();
    expect(importNotesBatch).not.toHaveBeenCalled();
  });

  it("renders an all-failed valid batch with an import-again path", async () => {
    render(<BulkImportPageClient />);
    const allFailed = {
      created: [],
      failed: [
        { fileName: "blank.txt", errorCode: "EMPTY_TEXT", message: "No readable text was found." },
      ],
    };

    (importNotesBatch as jest.Mock).mockResolvedValueOnce(allFailed);
    selectFiles([new File([""], "blank.txt", { type: "text/plain" })]);
    fireEvent.click(screen.getByRole("button", { name: "Import 1 file" }));

    expect(await screen.findByText("No files were imported. 1 file failed.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Failed (1)" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Import more files" })).toBeInTheDocument();
    expect(screen.queryByText(/Add these .* drafts to a/i)).not.toBeInTheDocument();
  });

  it("preserves selected files and offers retry after a request failure", async () => {
    (importNotesBatch as jest.Mock).mockRejectedValueOnce(new Error("Upload connection failed."));
    render(<BulkImportPageClient />);
    const files = buildFiles(2);

    selectFiles(files);
    fireEvent.click(screen.getByRole("button", { name: "Import 2 files" }));

    expect(await screen.findByText("Upload connection failed.")).toBeInTheDocument();
    expect(screen.getByText("file-0.txt")).toBeInTheDocument();
    expect(screen.getByText("file-1.txt")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry import" })).toBeInTheDocument();
  });

  it("creates a new profile-labeled collection with the created note ids", async () => {
    render(<BulkImportPageClient />);
    await importFiles(buildFiles(3));

    fireEvent.click(screen.getByRole("button", { name: "Add these 2 drafts to a Study Plan" }));
    const dialog = await screen.findByRole("dialog", { name: "Add to a Study Plan" });
    fireEvent.change(within(dialog).getByLabelText("Title"), { target: { value: "Unit One" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Create new Study Plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Unit One", noteIds: ["note-1", "note-2"] });
    });
    expect(await screen.findByRole("link", { name: "View Study Plan" })).toHaveAttribute("href", "/collections/collection-new");
  });

  it("adds all created note ids to an existing collection", async () => {
    (listCollections as jest.Mock).mockResolvedValueOnce([
      {
        id: "collection-1",
        title: "Existing Plan",
        description: null,
        itemCount: 4,
        notesPracticed: 0,
        createdAt: "2026-06-12T00:00:00Z",
        updatedAt: "2026-06-12T00:00:00Z",
      },
    ]);
    render(<BulkImportPageClient />);
    await importFiles(buildFiles(3));

    fireEvent.click(screen.getByRole("button", { name: "Add these 2 drafts to a Study Plan" }));
    const dialog = await screen.findByRole("dialog", { name: "Add to a Study Plan" });
    fireEvent.click(await within(dialog).findByRole("button", { name: "Add here" }));

    await waitFor(() => {
      expect(addCollectionItems).toHaveBeenCalledWith("collection-1", ["note-1", "note-2"]);
    });
  });

  it("shows collection load retry instead of an empty state", async () => {
    (listCollections as jest.Mock).mockRejectedValueOnce(new Error("Could not load collections."));
    render(<BulkImportPageClient />);
    await importFiles(buildFiles(3));

    fireEvent.click(screen.getByRole("button", { name: "Add these 2 drafts to a Study Plan" }));
    const dialog = await screen.findByRole("dialog", { name: "Add to a Study Plan" });

    expect(await within(dialog).findByText("Could not load collections.")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(within(dialog).queryByText(/Create your first/i)).not.toBeInTheDocument();
  });
});
