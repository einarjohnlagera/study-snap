import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CollectionReadinessPageClient } from "./readiness-page-client";
import { ApiRequestError, getPlanReadiness, trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => {
  class ApiRequestError extends Error {
    status: number;
    code: string | null;

    constructor(message: string, options: { status: number; code?: string | null }) {
      super(message);
      this.status = options.status;
      this.code = options.code ?? null;
    }
  }

  return {
    ApiRequestError,
    getPlanReadiness: jest.fn(),
    trackAnalyticsEvent: jest.fn(),
  };
});

function readiness(overrides: Record<string, unknown> = {}) {
  return {
    collectionId: "collection-1",
    totalNotes: 3,
    notesWithStudyPack: 2,
    overallReadinessPercentage: 50,
    totalConcepts: 4,
    masteredConcepts: 2,
    dueConcepts: 1,
    notPracticedConcepts: 1,
    subjects: [
      {
        subject: "Biology",
        totalConcepts: 4,
        masteredConcepts: 2,
        dueConcepts: 1,
        notPracticedConcepts: 1,
        masteryPercentage: 50,
      },
    ],
    ...overrides,
  };
}

describe("CollectionReadinessPageClient", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    (getPlanReadiness as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT" });
    (getPlanReadiness as jest.Mock).mockResolvedValue(readiness());
  });

  it("renders plan readiness, back link, progress cross-link, and analytics once", async () => {
    render(<CollectionReadinessPageClient collectionId="collection-1" />);

    expect(await screen.findByText("2 of 3 notes have Study Packs.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Study Plan readiness" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Study Plan" })).toHaveAttribute("href", "/collections/collection-1");
    expect(screen.getByRole("link", { name: /View full Progress/ })).toHaveAttribute("href", "/progress");
    expect(screen.getByRole("progressbar", { name: "Biology readiness" })).toHaveAttribute("aria-valuenow", "50");
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
    });
    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
      eventType: "PLAN_READINESS_VIEWED",
      entityId: "collection-1",
    }));
  });

  it("renders the empty plan readiness state as guidance", async () => {
    (getPlanReadiness as jest.Mock).mockResolvedValue(readiness({
      totalNotes: 0,
      notesWithStudyPack: 0,
      overallReadinessPercentage: 0,
      totalConcepts: 0,
      masteredConcepts: 0,
      dueConcepts: 0,
      notPracticedConcepts: 0,
      subjects: [],
    }));

    render(<CollectionReadinessPageClient collectionId="collection-1" />);

    expect(await screen.findByText("0 of 0 notes have Study Packs.")).toBeInTheDocument();
    expect(screen.getByText("No readiness yet")).toBeInTheDocument();
    expect(screen.getAllByText("Generate Study Packs and practice to see readiness.")).toHaveLength(2);
  });

  it("renders never-practiced packs as not started", async () => {
    (getPlanReadiness as jest.Mock).mockResolvedValue(readiness({
      overallReadinessPercentage: 0,
      masteredConcepts: 0,
      dueConcepts: 0,
      notPracticedConcepts: 4,
      subjects: [
        {
          subject: "Biology",
          totalConcepts: 4,
          masteredConcepts: 0,
          dueConcepts: 0,
          notPracticedConcepts: 4,
          masteryPercentage: 0,
        },
      ],
    }));

    render(<CollectionReadinessPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Not started")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Biology readiness" })).toHaveAttribute("aria-valuenow", "0");
  });

  it("renders transient errors with retry", async () => {
    (getPlanReadiness as jest.Mock)
      .mockRejectedValueOnce(new Error("Network failed"))
      .mockResolvedValueOnce(readiness());

    render(<CollectionReadinessPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Could not load readiness")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("2 of 3 notes have Study Packs.")).toBeInTheDocument();
    expect(getPlanReadiness).toHaveBeenCalledTimes(2);
  });

  it("renders not found for missing or non-owned plans", async () => {
    (getPlanReadiness as jest.Mock).mockRejectedValue(new ApiRequestError("Missing", { status: 404 }));

    render(<CollectionReadinessPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Study Plan not found")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Study Plans" })[0]).toHaveAttribute("href", "/collections");
    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });
});
