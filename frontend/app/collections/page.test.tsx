import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import CollectionsPage, { metadata } from "./page";
import { CollectionsPageClient } from "./collections-page-client";
import { createCollection, listCollections } from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};

let searchParamsMock = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ profileType: "STUDENT" }),
}));

jest.mock("@/lib/api", () => ({
  createCollection: jest.fn(),
  listCollections: jest.fn(),
}));

describe("CollectionsPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (createCollection as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    searchParamsMock = new URLSearchParams();
  });

  it("auto-opens the create modal when arriving with ?new=1", async () => {
    searchParamsMock = new URLSearchParams("new=1");
    (listCollections as jest.Mock).mockResolvedValue([]);

    render(<CollectionsPage />);

    expect(await screen.findByRole("heading", { name: "New Study Plan" })).toBeInTheDocument();
  });

  it("exports page metadata", () => {
    expect(metadata).toMatchObject({ title: "Collections | NoteLib" });
  });

  it("renders collection cards returned by the API", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "collection-1",
        title: "Midterm Plan",
        description: "Weeks 1-4",
        visibility: "PRIVATE",
        courseProgram: null,
        sourcePlanId: null,
        itemCount: 2,
        createdAt: "2026-06-01T00:00:00Z",
        updatedAt: "2026-06-02T00:00:00Z",
      },
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByRole("heading", { name: "Your Study Plans" })).toBeInTheDocument();
    const title = await screen.findByText("Midterm Plan");
    expect(title.closest("a")).toHaveAttribute("href", "/collections/collection-1");
    expect(screen.getByText("2 notes")).toBeInTheDocument();
  });

  it("shows profile-aware empty state when there are no collections", async () => {
    (listCollections as jest.Mock).mockResolvedValue([]);

    render(<CollectionsPage />);

    expect(await screen.findByText("No study plans yet")).toBeInTheDocument();
    expect(screen.getByText(/Group notes for a unit/)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "New Study Plan" }).length).toBeGreaterThan(0);
  });

  it("shows retry on load error", async () => {
    (listCollections as jest.Mock).mockRejectedValueOnce(new Error("Network down"));
    (listCollections as jest.Mock).mockResolvedValueOnce([]);

    render(<CollectionsPage />);

    expect(await screen.findByRole("heading", { name: "Could not load study plans" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    await waitFor(() => {
      expect(listCollections).toHaveBeenCalledTimes(2);
    });
  });

  it("creates a collection and navigates to detail", async () => {
    (listCollections as jest.Mock).mockResolvedValue([]);
    (createCollection as jest.Mock).mockResolvedValue({
      id: "created-1",
      title: "Finals",
      description: null,
      visibility: "PRIVATE",
      courseProgram: null,
      sourcePlanId: null,
      createdAt: "2026-06-01T00:00:00Z",
      updatedAt: "2026-06-01T00:00:00Z",
      items: [],
    });

    render(<CollectionsPageClient />);

    await screen.findByText("No study plans yet");
    fireEvent.click(screen.getAllByRole("button", { name: "New Study Plan" })[0]);
    fireEvent.change(screen.getByPlaceholderText("Study Plan title"), { target: { value: "Finals" } });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Finals", description: null });
      expect(pushMock).toHaveBeenCalledWith("/collections/created-1");
    });
  });
});
