import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import OnboardingPage from "./page";
import {
  getCourseProgramCatalog,
  getMe,
  trackAnalyticsEvent,
  updateLearningProfileContext,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { ONBOARDING_DRAFT_SCHEMA_VERSION } from "@/lib/onboarding-v2";
import { COURSE_PROGRAM_SUGGESTIONS } from "@/lib/learning-profile";

/**
 * Guard (c) for `v0.128.0`, and the first test `app/onboarding` has ever had.
 *
 * <p>⚠️ The defect this exists to catch is NOT "the screen is broken" — it is the silent no-op that
 * bit `v0.116.0` and `v0.117.0`. `useCourseProgramCatalogNames` swallows every failure to `null`, and
 * `buildCatalogFirstCourseProgramSuggestions(null, …)` falls straight back to
 * `COURSE_PROGRAM_SUGGESTIONS` — so a change that never receives a catalog renders IDENTICALLY to the
 * pre-change screen and passes any test that merely asserts the step renders.
 *
 * <p>So the catalog assertion below compares against `COURSE_PROGRAM_SUGGESTIONS` itself: the listed
 * names must be ones the hardcoded constant does not contain, in a position the fallback cannot
 * produce. A fixture reusing a constant entry (e.g. "Nursing") would pass under the no-op.
 *
 * <p>The free-text case is not decoration either: `v0.79.0` shipped the counter-proposal, so free text
 * STAYS ALLOWED and the `ADR-001` field-locking amendment remains unratified. A test covering only the
 * catalog path could not catch a regression that locks the field.
 */

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn(), refresh: jest.fn() }),
  usePathname: () => "/onboarding",
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: () => ({ usageSummary: null, refreshUsageSummary: jest.fn() }),
}));

jest.mock("@/lib/api", () => ({
  completeOnboarding: jest.fn(),
  completeOnboardingProfileType: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  generateNoteFromTopic: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  getOfficialStudyPlanWishlistStatus: jest.fn(),
  getCourseProgramCatalog: jest.fn(),
  isNoteGenerationLimitReachedError: jest.fn(() => false),
  requestOfficialStudyPlan: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateExamDate: jest.fn(),
  updateLearningProfileContext: jest.fn(),
}));

const USER_ID = "user-onboarding-1";

// ⚠️ Deliberately NOT members of COURSE_PROGRAM_SUGGESTIONS. See the note above: a catalog fixture
// that overlaps the hardcoded constant cannot distinguish a working catalog from the fallback.
const CATALOG_ONLY_PROGRAMS = ["Doctor of Veterinary Medicine", "BS Marine Transportation"];

const onboardingUser = {
  id: USER_ID,
  displayName: "Note",
  profileType: "STUDENT",
  emailVerifiedAt: "2026-09-01T00:00:00Z",
  onboardingCompletedAt: null,
  productOnboardingCompletedAt: null,
  planType: "FREE",
  learnerLevel: null,
  courseProgram: null,
  examDate: null,
};

function seedDraftOnCourseProgramStep() {
  globalThis.localStorage.setItem(
    `notelib.onboarding-v2:${USER_ID}`,
    JSON.stringify({
      schemaVersion: ONBOARDING_DRAFT_SCHEMA_VERSION,
      startedAtMs: Date.now(),
      currentStep: 2,
      profileType: "STUDENT",
      learnerLevel: null,
      courseProgram: "",
      examDate: "",
      intent: null,
      reviewSetAvailable: null,
      inputMethod: null,
      topic: "",
      noteContent: "",
      generatedNoteReady: false,
      noteId: null,
      studyPackId: null,
    }),
  );
}

async function renderOnCourseProgramStep() {
  const result = render(<OnboardingPage />);
  expect(await screen.findByLabelText("Course / Program")).toBeInTheDocument();
  return result;
}

/**
 * Advances past Course / Program and picks a learner level.
 *
 * <p>⚠️ The Course / Program screen does NOT commit on its own -- `updateLearningProfileContext` is
 * awaited inside `selectLearnerLevel` on the NEXT step, which is where both the program and the level
 * are persisted together. Going through Continue rather than reaching into state is the point: it is
 * the path a learner actually takes, and a fixture that hand-built the committed state would prove
 * nothing about the screen.
 */
async function continueAndSelectCollege() {
  fireEvent.click(screen.getByRole("button", { name: "Continue" }));
  const learnerLevel = await screen.findByLabelText("Learner Level");
  fireEvent.change(learnerLevel, { target: { value: "COLLEGE" } });
  fireEvent.click(screen.getByRole("button", { name: "Continue" }));
}

describe("onboarding Course / Program step", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    globalThis.localStorage.clear();
    (getAuthUser as jest.Mock).mockReturnValue(onboardingUser);
    (getMe as jest.Mock).mockResolvedValue(onboardingUser);
    (updateLearningProfileContext as jest.Mock).mockResolvedValue(onboardingUser);
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue(
      CATALOG_ONLY_PROGRAMS.map((name, index) => ({
        id: `program-${index}`,
        name,
        programFamilyId: null,
        programFamilyName: null,
      })),
    );
    seedDraftOnCourseProgramStep();
  });

  it("fetches the catalog and lists catalog names ahead of the hardcoded suggestions", async () => {
    await renderOnCourseProgramStep();

    await waitFor(() => expect(getCourseProgramCatalog).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));

    const listbox = screen.getByRole("listbox");
    await within(listbox).findByRole("option", { name: CATALOG_ONLY_PROGRAMS[0] });
    const options = within(listbox).getAllByRole("option").map((option) => option.textContent);

    // The load-bearing assertion: these two names are absent from the hardcoded constant, so this
    // ordering is unreachable if the catalog never arrived and the helper fell back.
    expect(options.slice(0, 2)).toEqual(CATALOG_ONLY_PROGRAMS);
    CATALOG_ONLY_PROGRAMS.forEach((name) => expect(COURSE_PROGRAM_SUGGESTIONS).not.toContain(name));
    // The hardcoded list is appended, not replaced -- catalog-FIRST, not catalog-only.
    expect(options).toContain(COURSE_PROGRAM_SUGGESTIONS[0]);
  });

  it("completes the step with a catalog-sourced program and records it as matching the catalog", async () => {
    await renderOnCourseProgramStep();
    await waitFor(() => expect(getCourseProgramCatalog).toHaveBeenCalled());

    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    const listbox = screen.getByRole("listbox");
    fireEvent.click(await within(listbox).findByRole("option", { name: CATALOG_ONLY_PROGRAMS[0] }));

    expect(screen.getByLabelText("Course / Program")).toHaveValue(CATALOG_ONLY_PROGRAMS[0]);

    await continueAndSelectCollege();

    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", CATALOG_ONLY_PROGRAMS[0]);
    });
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith(
        expect.objectContaining({
          eventType: "COURSE_PROGRAM_VALUE_SELECTED",
          metadata: expect.objectContaining({ surface: "onboarding", matchedCatalog: true }),
        }),
      );
    });
  });

  it("still completes the step with free text, which the counter-proposal keeps allowed", async () => {
    await renderOnCourseProgramStep();
    await waitFor(() => expect(getCourseProgramCatalog).toHaveBeenCalled());

    const input = screen.getByLabelText("Course / Program");
    fireEvent.change(input, { target: { value: "Underwater Basket Weaving" } });
    expect(input).toHaveValue("Underwater Basket Weaving");

    await continueAndSelectCollege();

    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", "Underwater Basket Weaving");
    });
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith(
        expect.objectContaining({
          eventType: "COURSE_PROGRAM_VALUE_SELECTED",
          metadata: expect.objectContaining({ surface: "onboarding", matchedCatalog: false }),
        }),
      );
    });
  });

  it("does not re-report a program the learner already committed in an earlier session", async () => {
    // Resumed onboarding: the program is already persisted on the account, and the learner returns
    // and steps through the learner-level screen again without touching it. The tracking ref is
    // per-page-load, so it is seeded from the stored value -- otherwise this fires a second
    // selection for a value that never changed.
    (getMe as jest.Mock).mockResolvedValue({
      ...onboardingUser,
      courseProgram: CATALOG_ONLY_PROGRAMS[0],
    });

    await renderOnCourseProgramStep();
    await waitFor(() => expect(getCourseProgramCatalog).toHaveBeenCalled());
    expect(screen.getByLabelText("Course / Program")).toHaveValue(CATALOG_ONLY_PROGRAMS[0]);

    await continueAndSelectCollege();

    // The save still happens -- suppression is of the ANALYTICS event, not of the write.
    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", CATALOG_ONLY_PROGRAMS[0]);
    });
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({ eventType: "COURSE_PROGRAM_VALUE_SELECTED" }),
    );
  });

  it("does not block the step when the catalog fetch fails", async () => {
    (getCourseProgramCatalog as jest.Mock).mockRejectedValue(new Error("offline"));
    await renderOnCourseProgramStep();
    await waitFor(() => expect(getCourseProgramCatalog).toHaveBeenCalled());

    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    const listbox = screen.getByRole("listbox");
    // Falls back to the hardcoded constant rather than rendering an empty picker.
    expect(await within(listbox).findByRole("option", { name: COURSE_PROGRAM_SUGGESTIONS[0] })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "High School" } });
    await continueAndSelectCollege();

    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", "High School");
    });
    // ⚠️ The hook returns null on failure and trackCourseProgramValueSelected suppresses the event
    // when it has no catalog to compare against -- an unclassifiable selection is worse than none.
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({ eventType: "COURSE_PROGRAM_VALUE_SELECTED" }),
    );
  });
});
