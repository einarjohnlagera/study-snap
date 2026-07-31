import { render, screen, waitFor } from "@testing-library/react";
import { ExamHubOfficialReviewSets } from "./exam-hub-official-review-sets";
import { getAccessToken, getAuthUser } from "@/lib/auth";
import type { ExamHubConfig } from "@/lib/exam-hub-config";
import type { NoteCollectionSummary } from "@/lib/api";

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn() }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  getAccessToken: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  API_BASE_URL: "http://localhost:8080/api",
  adoptGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
  getPublicStudyPlanDetail: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("@/lib/exam-intent", () => ({
  setExamIntentCookie: jest.fn(),
}));

const exam: ExamHubConfig = {
  slug: "ple" as ExamHubConfig["slug"],
  shortName: "PLE",
  fullName: "Physician Licensure Examination (PLE)",
  description: "Free PLE reviewer notes",
  coursePrograms: ["Medicine"],
};

const plan: NoteCollectionSummary = {
  id: "public-plan-1",
  title: "PLE Official Review Set",
  description: null,
  visibility: "PUBLIC",
  courseProgram: "Medicine",
  sourcePlanId: null,
  parentCollectionId: null,
  itemCount: 10,
  readyCount: null,
  childCount: 0,
  notesPracticed: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("ExamHubOfficialReviewSets", () => {
  const fetchMock = jest.fn();

  beforeEach(() => {
    (getAuthUser as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue(null);
    (getAccessToken as jest.Mock).mockReset();
    (getAccessToken as jest.Mock).mockReturnValue(null);
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, json: async () => [] });
    globalThis.fetch = fetchMock as unknown as typeof fetch;
  });

  it("shows a sign-in prompt for a signed-out visitor and never calls the collections lookup", async () => {
    render(<ExamHubOfficialReviewSets exam={exam} plans={[plan]} />);

    expect(await screen.findByRole("button", { name: /sign in to adopt/i })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows Start for a signed-in visitor who has not adopted the set", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });
    (getAccessToken as jest.Mock).mockReturnValue("valid-token");
    fetchMock.mockResolvedValue({ ok: true, json: async () => [] });

    render(<ExamHubOfficialReviewSets exam={exam} plans={[plan]} />);

    expect(await screen.findByRole("button", { name: /start this/i })).toBeInTheDocument();
  });

  it("resolves the real adopted collection and shows Continue for an already-adopted signed-in visitor", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });
    (getAccessToken as jest.Mock).mockReturnValue("valid-token");
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => [{ ...plan, id: "owned-copy-1", sourcePlanId: plan.id }],
    });

    render(<ExamHubOfficialReviewSets exam={exam} plans={[plan]} />);

    expect(await screen.findByRole("button", { name: /continue this/i })).toBeInTheDocument();
  });

  it("fails open to Start when the personal-collections lookup rejects", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });
    (getAccessToken as jest.Mock).mockReturnValue("valid-token");
    fetchMock.mockRejectedValue(new Error("network error"));

    render(<ExamHubOfficialReviewSets exam={exam} plans={[plan]} />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(await screen.findByRole("button", { name: /start this/i })).toBeInTheDocument();
  });

  it("fails open to Start on a 401, without following the app's session-expiry redirect path", async () => {
    // A stale localStorage auth user with a dead refresh token is exactly the case this raw
    // fetch exists for: `listCollections()` would have routed this 401 through `fetchWithAuth`
    // and hard-redirected the visitor to `/login` via `handleUnauthorizedSession`. This
    // component must never do that — it's the anonymous, SEO-relevant Exam Hub page.
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });
    (getAccessToken as jest.Mock).mockReturnValue("stale-token");
    fetchMock.mockResolvedValue({ ok: false, status: 401, json: async () => ({}) });

    render(<ExamHubOfficialReviewSets exam={exam} plans={[plan]} />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(await screen.findByRole("button", { name: /start this/i })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/collections",
      expect.objectContaining({ headers: { Authorization: "Bearer stale-token" } }),
    );
  });

  it("does not call the collections lookup when there is no access token, even if isAuthenticated", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });
    (getAccessToken as jest.Mock).mockReturnValue(null);

    render(<ExamHubOfficialReviewSets exam={exam} plans={[plan]} />);

    expect(await screen.findByRole("button", { name: /start this/i })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
