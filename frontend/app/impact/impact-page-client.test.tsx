import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ImpactPageClient } from "./impact-page-client";
import { getCreatorImpact, getCreatorImpactSummary } from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
};
const requireAuthenticatedOnboardedUserMock = jest.fn((_router: unknown) => true);

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({
    id: "creator-1",
    emailVerifiedAt: "2026-09-01T00:00:00Z",
    onboardingCompletedAt: "2026-09-01T00:00:00Z",
    profileType: "TEACHER",
  }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: (router: unknown) => requireAuthenticatedOnboardedUserMock(router),
}));

jest.mock("@/lib/api", () => {
  const actual = jest.requireActual("@/lib/api");
  return {
    ...actual,
    getCreatorImpact: jest.fn(),
    getCreatorImpactSummary: jest.fn(),
    trackAnalyticsEvent: jest.fn().mockResolvedValue(undefined),
  };
});

const impactedPage = {
  notes: [{
    noteId: "note-1",
    title: "Plant Cells",
    distinctLearnersHelped: 2,
    viewCount: 9,
    copyCount: 5,
  }],
  page: 0,
  size: 20,
  totalImpacted: 1,
  totalZeroImpact: 1,
};

describe("ImpactPageClient", () => {
  beforeEach(() => {
    requireAuthenticatedOnboardedUserMock.mockReset();
    requireAuthenticatedOnboardedUserMock.mockReturnValue(true);
    (getCreatorImpact as jest.Mock).mockReset();
    (getCreatorImpactSummary as jest.Mock).mockReset();
    (getCreatorImpactSummary as jest.Mock).mockResolvedValue({
      distinctLearnersHelped: 2,
      publicNoteCount: 2,
    });
    (getCreatorImpact as jest.Mock).mockImplementation((impacted: boolean) => Promise.resolve(
      impacted
        ? impactedPage
        : {
            notes: [{
              noteId: "note-2",
              title: "Cell Division",
              distinctLearnersHelped: 0,
              viewCount: 1,
              copyCount: 0,
            }],
            page: 0,
            size: 20,
            totalImpacted: 1,
            totalZeroImpact: 1,
          },
    ));
  });

  it("loads the headline from summary and lazily loads the collapsed zero-impact section", async () => {
    render(<ImpactPageClient />);

    expect(await screen.findByText("distinct learners helped")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Notes helping learners · 1" })).toBeInTheDocument();
    expect(screen.getByText("9 page views · 5 copies")).toBeInTheDocument();
    const otherNotes = screen.getByRole("button", { name: /Other published notes/ });
    expect(otherNotes).toHaveAttribute("aria-expanded", "false");
    expect(getCreatorImpact).toHaveBeenCalledTimes(1);
    expect(getCreatorImpact).toHaveBeenCalledWith(true);
    expect(screen.queryByText("Cell Division")).not.toBeInTheDocument();

    fireEvent.click(otherNotes);

    expect(await screen.findByText("Cell Division")).toBeInTheDocument();
    expect(otherNotes).toHaveAttribute("aria-expanded", "true");
    expect(getCreatorImpact).toHaveBeenLastCalledWith(false, 0);
    expect(screen.queryByText(/Awaiting first study/)).not.toBeInTheDocument();
  });

  it("shows the published-note zero state without claiming a learner was helped", async () => {
    (getCreatorImpactSummary as jest.Mock).mockResolvedValue({
      distinctLearnersHelped: 0,
      publicNoteCount: 1,
    });
    (getCreatorImpact as jest.Mock).mockResolvedValue({
      notes: [],
      page: 0,
      size: 20,
      totalImpacted: 0,
      totalZeroImpact: 1,
    });

    render(<ImpactPageClient />);

    expect(await screen.findByText("Your shared notes are ready to help someone learn.")).toBeInTheDocument();
    expect(screen.queryByText("learner helped")).not.toBeInTheDocument();
    expect(screen.queryByText("No learners yet")).not.toBeInTheDocument();
  });

  it("shows the no-public-notes state and existing Library flow", async () => {
    (getCreatorImpactSummary as jest.Mock).mockResolvedValue({
      distinctLearnersHelped: 0,
      publicNoteCount: 0,
    });
    (getCreatorImpact as jest.Mock).mockResolvedValue({
      notes: [],
      page: 0,
      size: 20,
      totalImpacted: 0,
      totalZeroImpact: 0,
    });

    render(<ImpactPageClient />);

    expect(await screen.findByRole("heading", { name: "Share what you know." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Go to Library →" })).toHaveAttribute("href", "/library");
    expect(screen.queryByText("learner helped")).not.toBeInTheDocument();
  });

  it("keeps impacted notes visible when the expanded zero-impact request fails", async () => {
    (getCreatorImpact as jest.Mock)
      .mockResolvedValueOnce(impactedPage)
      .mockRejectedValueOnce(new Error("Network error"));

    render(<ImpactPageClient />);
    expect(await screen.findByText("Plant Cells")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Other published notes/ }));

    expect(await screen.findByText("Could not load your other published notes.")).toBeInTheDocument();
    expect(screen.getByText("Plant Cells")).toBeInTheDocument();
  });

  it("retries the failed zero-impact page instead of appending the prior page again", async () => {
    let secondPageAttempts = 0;
    (getCreatorImpact as jest.Mock).mockImplementation((isImpacted: boolean, page = 0) => {
      if (isImpacted) {
        return Promise.resolve({ ...impactedPage, totalZeroImpact: 2 });
      }
      if (page === 0) {
        return Promise.resolve({
          notes: [{ noteId: "zero-1", title: "First zero", distinctLearnersHelped: 0, viewCount: 1, copyCount: 0 }],
          page: 0,
          size: 20,
          totalImpacted: 1,
          totalZeroImpact: 2,
        });
      }
      secondPageAttempts += 1;
      return secondPageAttempts === 1
        ? Promise.reject(new Error("Network error"))
        : Promise.resolve({
            notes: [{ noteId: "zero-2", title: "Second zero", distinctLearnersHelped: 0, viewCount: 0, copyCount: 0 }],
            page: 1,
            size: 20,
            totalImpacted: 1,
            totalZeroImpact: 2,
          });
    });

    render(<ImpactPageClient />);
    fireEvent.click(await screen.findByRole("button", { name: /Other published notes/ }));
    expect(await screen.findByText("First zero")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Load more" }));
    expect(await screen.findByText("Could not load your other published notes.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("Second zero")).toBeInTheDocument();
    expect(screen.getAllByText("First zero")).toHaveLength(1);
    expect(getCreatorImpact).toHaveBeenCalledWith(false, 1);
  });

  it("surfaces a retryable failure when a further page of impacted notes fails", async () => {
    let impactedAttempts = 0;
    (getCreatorImpact as jest.Mock).mockImplementation((isImpacted: boolean, page = 0) => {
      if (!isImpacted) {
        return Promise.resolve({ notes: [], page: 0, size: 20, totalImpacted: 2, totalZeroImpact: 0 });
      }
      if (page === 0) {
        return Promise.resolve({ ...impactedPage, totalImpacted: 2, totalZeroImpact: 0 });
      }
      impactedAttempts += 1;
      return impactedAttempts === 1
        ? Promise.reject(new Error("Network error"))
        : Promise.resolve({
            notes: [{ noteId: "note-9", title: "Photosynthesis", distinctLearnersHelped: 1, viewCount: 2, copyCount: 1 }],
            page: 1,
            size: 20,
            totalImpacted: 2,
            totalZeroImpact: 0,
          });
    });

    render(<ImpactPageClient />);
    expect(await screen.findByText("Plant Cells")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Load more" }));

    expect(await screen.findByText("Could not load more notes helping learners.")).toBeInTheDocument();
    expect(screen.getByText("Plant Cells")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("Photosynthesis")).toBeInTheDocument();
    expect(screen.queryByText("Could not load more notes helping learners.")).not.toBeInTheDocument();
    expect(screen.getAllByText("Plant Cells")).toHaveLength(1);
  });

  it("stops offering more zero-impact notes when a later page reports a shrunken total", async () => {
    // A note can cross from zero-impact to impacted while the page is open. totalZeroImpact is
    // captured at initial load, so without refreshing it from each page response the "Load more"
    // button compares against a stale, too-high total and keeps requesting pages that come back
    // empty. Every page response already carries fresh totals — this asserts they are not discarded.
    (getCreatorImpact as jest.Mock).mockImplementation((isImpacted: boolean, page = 0) => {
      if (isImpacted) {
        return Promise.resolve({ ...impactedPage, totalImpacted: 1, totalZeroImpact: 3 });
      }
      return Promise.resolve({
        notes: [{ noteId: `zero-${page}`, title: `Zero note ${page}`, distinctLearnersHelped: 0, viewCount: 0, copyCount: 0 }],
        page,
        size: 20,
        totalImpacted: 1,
        totalZeroImpact: 1,
      });
    });

    render(<ImpactPageClient />);
    fireEvent.click(await screen.findByRole("button", { name: /Other published notes/ }));

    expect(await screen.findByText("Zero note 0")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "Load more" })).not.toBeInTheDocument());
  });

  it("offers a retry when the initial impact load fails", async () => {
    (getCreatorImpactSummary as jest.Mock)
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce({ distinctLearnersHelped: 2, publicNoteCount: 2 });

    render(<ImpactPageClient />);
    expect(await screen.findByText("Could not load your impact.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    await waitFor(() => expect(screen.getByText("Plant Cells")).toBeInTheDocument());
  });

  it("does not request private impact data when the authenticated route guard rejects entry", async () => {
    requireAuthenticatedOnboardedUserMock.mockReturnValue(false);

    render(<ImpactPageClient />);

    await waitFor(() => expect(requireAuthenticatedOnboardedUserMock).toHaveBeenCalledWith(routerMock));
    expect(getCreatorImpactSummary).not.toHaveBeenCalled();
    expect(getCreatorImpact).not.toHaveBeenCalled();
  });
});
