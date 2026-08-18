import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { BulkGenerationPageClient } from "./bulk-generation-page-client";
import { bulkGenerateNotes, getCourseProgramCatalog, getMe, getMyPlan, listCollections, listCoursePrograms, listSubjects } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import {
  consumeBulkQueuedFlash,
  setBulkGenerationRetryStash,
} from "@/lib/bulk-generation-flash";

const replaceMock = jest.fn();
const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock, push: pushMock }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    status: number;

    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  bulkGenerateNotes: jest.fn(),
  getCourseProgramCatalog: jest.fn(),
  listCollections: jest.fn(),
  listSubjects: jest.fn(),
  listCoursePrograms: jest.fn(),
  getMe: jest.fn(),
  getMyPlan: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

async function fillAdminForm(topicValues: string[]) {
  fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
  const courseProgramInput = screen.getByLabelText("Add a course or program");
  fireEvent.change(courseProgramInput, { target: { value: "Nursing" } });
  fireEvent.click(await screen.findByRole("option", { name: "Nursing" }));
  topicValues.forEach((value, index) => {
    if (index > 0) {
      fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));
    }
    fireEvent.change(screen.getByLabelText(new RegExp(`^Topic ${index + 1}$`)), { target: { value } });
  });
  fireEvent.click(screen.getByRole("switch", { name: /public/i }));
}

describe("BulkGenerationPageClient", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    replaceMock.mockReset();
    pushMock.mockReset();
    globalThis.sessionStorage?.clear();
    (bulkGenerateNotes as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue([]);
    (listCoursePrograms as jest.Mock).mockResolvedValue([]);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([
      { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null },
    ]);
    (listCollections as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      limits: { noteGenerationsPerMonth: 10, studyPacksPerMonth: 10, ocrPerMonth: 20 },
      remaining: { noteGenerationsRemaining: 7, studyPacksRemaining: 7, ocrRemaining: 20 },
      usageCycle: { startsAt: "2026-06-01T00:00:00Z", endsAt: "2026-07-01T00:00:00Z" },
    });
    (getAuthUser as jest.Mock).mockReturnValue({ id: "admin-1", role: "ADMIN", profileType: null });
  });

  it("shows the admin authoring metadata fields", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(screen.getByText("Library tool")).toBeInTheDocument();
    expect(screen.getByLabelText(/^Subject/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Course \/ Program/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Target Audience/)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^Domain Context/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Authored Depth/)).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toBeInTheDocument();
    expect(screen.getByTestId("bulk-metadata-grid")).toHaveClass("sm:grid-cols-2");
    // ADMIN bypasses the quota gate, so no remaining-cap hint should render. Asserts the
    // string the component actually emits — the previous form ("...remaining this cycle")
    // never existed in any state, so this assertion passed vacuously both before and after
    // the v0.68.0 "topic note" rename.
    expect(screen.queryByText(/topic notes? left this cycle/i)).not.toBeInTheDocument();
  });

  it("keeps the compact grid profile-aware for teacher and non-teacher views", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "teacher-1", role: "USER", profileType: "TEACHER" });
    const { unmount } = render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(screen.getByLabelText(/^Course \/ Program/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Target Audience/)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^Domain Context/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Authored Depth/)).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toBeInTheDocument();

    unmount();
    (getAuthUser as jest.Mock).mockReturnValue({ id: "student-1", role: "USER", profileType: "STUDENT" });
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalledTimes(2));

    expect(screen.getByLabelText(/^Subject/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Course \/ Program/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Target Audience/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Domain Context/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Authored Depth/)).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toBeInTheDocument();
    expect(await screen.findByText(/Capped by your 7 topic notes left this cycle/i)).toBeInTheDocument();
  });

  it.each(["BOARD_EXAM", "PROFESSIONAL"])(
    "keeps authoring metadata hidden for %s profiles",
    async (profileType) => {
      (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1", role: "USER", profileType });

      render(<BulkGenerationPageClient />);
      await waitFor(() => expect(getMe).toHaveBeenCalled());

      expect(screen.queryByLabelText(/^Domain Context/)).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/^Authored Depth/)).not.toBeInTheDocument();
    },
  );

  it("submits the resolved payload, flashes the queued count, and redirects to Library", async () => {
    (bulkGenerateNotes as jest.Mock).mockResolvedValueOnce({
      resultId: "result-1",
      acceptedTopics: 2,
      queuedTopics: 2,
      rejectedTopics: 0,
    });
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    await fillAdminForm(["Prenatal Care", "Stages of Labor"]);
    fireEvent.change(screen.getByLabelText(/^Domain Context/), {
      target: { value: "NURSING" },
    });
    fireEvent.change(screen.getByLabelText(/^Authored Depth/), {
      target: { value: "BOARD_EXAM_REVIEW" },
    });

    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    await waitFor(() => {
      expect(bulkGenerateNotes).toHaveBeenCalledWith({
        subject: "Maternal Health",
        topics: ["Prenatal Care", "Stages of Labor"],
        makePublic: true,
        courseProgramIds: ["program-nursing"],
        domainContext: "NURSING",
        learnerLevel: "BOARD_EXAM_REVIEW",
      });
    });
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/library"));
    expect(consumeBulkQueuedFlash()).toEqual({ queuedCount: 2, resultId: "result-1" });
  });

  it("prefills an empty authored depth from the selected Review Set", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "subject-plan-1",
        title: "Civil Engineering Mathematics",
        resolvedLearnerLevel: "BOARD_EXAM_REVIEW",
      },
    ]);
    render(<BulkGenerationPageClient />);

    const selector = await screen.findByLabelText(/^Collection \(optional\)/);
    fireEvent.change(selector, { target: { value: "subject-plan-1" } });

    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("BOARD_EXAM_REVIEW");
  });

  it("does not overwrite an authored depth the curator already selected", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "subject-plan-1",
        title: "Civil Engineering Mathematics",
        resolvedLearnerLevel: "BOARD_EXAM_REVIEW",
      },
    ]);
    render(<BulkGenerationPageClient />);
    await screen.findByLabelText(/^Collection \(optional\)/);
    fireEvent.change(screen.getByLabelText(/^Authored Depth/), { target: { value: "COLLEGE" } });

    fireEvent.change(screen.getByLabelText(/^Collection \(optional\)/), { target: { value: "subject-plan-1" } });

    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("COLLEGE");
  });

  it("leaves authored depth empty when the Review Set has no resolved level", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "subject-plan-1",
        title: "Unclassified Review Set",
        resolvedLearnerLevel: null,
      },
    ]);
    render(<BulkGenerationPageClient />);

    fireEvent.change(await screen.findByLabelText(/^Collection \(optional\)/), {
      target: { value: "subject-plan-1" },
    });

    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("");
  });

  it("prefills authored depth from the author's own profile level", async () => {
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "", learnerLevel: "COLLEGE" });
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    await waitFor(() => expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("COLLEGE"));
  });

  it("lets a Review Set outrank a profile prefill but never an explicit choice", async () => {
    // ADR-001's chain is Review Set -> author profile -> explicit override. The profile
    // prefill lands on mount, so without provenance tracking it would leave the control
    // non-empty and the Review Set would be silently ignored — inverting the precedence.
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "", learnerLevel: "COLLEGE" });
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "subject-plan-1",
        title: "Civil Engineering Mathematics",
        resolvedLearnerLevel: "BOARD_EXAM_REVIEW",
      },
    ]);
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("COLLEGE"));

    fireEvent.change(await screen.findByLabelText(/^Collection \(optional\)/), {
      target: { value: "subject-plan-1" },
    });
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("BOARD_EXAM_REVIEW");

    // An explicit choice is protected from a later Review Set change.
    fireEvent.change(screen.getByLabelText(/^Authored Depth/), { target: { value: "SENIOR_HIGH" } });
    fireEvent.change(screen.getByLabelText(/^Collection \(optional\)/), { target: { value: "" } });
    fireEvent.change(screen.getByLabelText(/^Collection \(optional\)/), { target: { value: "subject-plan-1" } });
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("SENIOR_HIGH");
  });

  it("clears a Review Set's depth when switching to a set that has none", async () => {
    // Regression: handleCollectionChange only acted when the NEW set carried a level, so
    // switching or clearing left the PREVIOUS set's depth displayed and submitted. The
    // shipped test that looked like it covered this started from an empty control and
    // never exercised a switch, so it asserted strictly less than its name claimed.
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "" });
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "with-depth", title: "CE Board Review", resolvedLearnerLevel: "BOARD_EXAM_REVIEW" },
      { id: "no-depth", title: "Unclassified Set", resolvedLearnerLevel: null },
    ]);
    render(<BulkGenerationPageClient />);

    const selector = await screen.findByLabelText(/^Collection \(optional\)/);
    fireEvent.change(selector, { target: { value: "with-depth" } });
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("BOARD_EXAM_REVIEW");

    fireEvent.change(selector, { target: { value: "no-depth" } });
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("");

    fireEvent.change(selector, { target: { value: "with-depth" } });
    fireEvent.change(selector, { target: { value: "" } });
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("");
  });

  it("falls back to the profile level when the selected Review Set has no depth", async () => {
    // ADR-001's chain is Review Set -> author profile -> explicit override, so deselecting
    // a set drops to the profile leg rather than clearing to nothing.
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "", learnerLevel: "COLLEGE" });
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "with-depth", title: "CE Board Review", resolvedLearnerLevel: "BOARD_EXAM_REVIEW" },
      { id: "no-depth", title: "Unclassified Set", resolvedLearnerLevel: null },
    ]);
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("COLLEGE"));

    const selector = await screen.findByLabelText(/^Collection \(optional\)/);
    fireEvent.change(selector, { target: { value: "with-depth" } });
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("BOARD_EXAM_REVIEW");

    fireEvent.change(selector, { target: { value: "no-depth" } });
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("COLLEGE");
  });

  it("keeps a retried batch's blank depth instead of injecting the profile level", async () => {
    // Regression: the stash marked provenance only for a NON-EMPTY level, so a deliberate
    // "no depth" read as untouched and the profile pre-fill overwrote it — meaning the
    // retried notes were authored at a different depth than the batch they replaced.
    // The stash effect is synchronous on mount and getMe is async, so this ordering is the
    // load-bearing assumption; asserting it here rather than relying on it.
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "", learnerLevel: "SENIOR_HIGH" });
    setBulkGenerationRetryStash({
      subject: "Maternal Health",
      courseProgram: "Nursing",
      domainContext: "NURSING",
      learnerLevel: null,
      makePublic: false,
      topics: ["Prenatal Care"],
    });

    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("");
  });

  it("restores the Review Set from a retry stash", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "subject-plan-1", title: "Civil Engineering Mathematics", resolvedLearnerLevel: null },
    ]);
    setBulkGenerationRetryStash({
      subject: "Algebra",
      courseProgram: "Civil Engineering",
      domainContext: null,
      learnerLevel: null,
      makePublic: false,
      topics: ["Quadratic Equations"],
      collectionId: "subject-plan-1",
    });

    render(<BulkGenerationPageClient />);

    await waitFor(() => {
      expect(screen.getByLabelText(/^Collection \(optional\)/)).toHaveValue("subject-plan-1");
    });
  });

  it("includes the selected Review Set when queueing the batch", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "subject-plan-1",
        title: "Civil Engineering Mathematics",
        resolvedLearnerLevel: "BOARD_EXAM_REVIEW",
      },
    ]);
    (bulkGenerateNotes as jest.Mock).mockResolvedValue({
      resultId: "result-1",
      acceptedTopics: 1,
      queuedTopics: 1,
      rejectedTopics: 0,
    });
    render(<BulkGenerationPageClient />);
    fireEvent.change(await screen.findByLabelText(/^Collection \(optional\)/), {
      target: { value: "subject-plan-1" },
    });
    await fillAdminForm(["Structural Analysis"]);

    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    await waitFor(() => expect(bulkGenerateNotes).toHaveBeenCalledWith(
      expect.objectContaining({
        collectionId: "subject-plan-1",
        learnerLevel: "BOARD_EXAM_REVIEW",
      }),
    ));
  });

  it("prefills an editable section from subject only while it is untouched", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "subject-plan-1", title: "Civil Engineering Mathematics", resolvedLearnerLevel: null },
    ]);
    render(<BulkGenerationPageClient />);

    expect(screen.queryByLabelText(/^Section \(optional\)/)).not.toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText(/^Collection \(optional\)/), {
      target: { value: "subject-plan-1" },
    });
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Algebra" } });
    const sectionInput = screen.getByLabelText(/^Section \(optional\)/);
    expect(sectionInput).toHaveValue("Algebra");
    expect(sectionInput).toHaveAttribute("maxlength", "120");

    fireEvent.change(sectionInput, { target: { value: "Foundations" } });
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Engineering Mathematics" } });
    expect(sectionInput).toHaveValue("Foundations");
  });

  it("submits the edited section only with a selected Review Set", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "subject-plan-1", title: "Civil Engineering Mathematics", resolvedLearnerLevel: null },
    ]);
    (bulkGenerateNotes as jest.Mock).mockResolvedValue({
      resultId: "result-1",
      acceptedTopics: 1,
      queuedTopics: 1,
      rejectedTopics: 0,
    });
    render(<BulkGenerationPageClient />);
    fireEvent.change(await screen.findByLabelText(/^Collection \(optional\)/), {
      target: { value: "subject-plan-1" },
    });
    await fillAdminForm(["Quadratic Equations"]);
    fireEvent.change(screen.getByLabelText(/^Section \(optional\)/), { target: { value: "Core Algebra" } });

    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    await waitFor(() => expect(bulkGenerateNotes).toHaveBeenCalledWith(
      expect.objectContaining({ collectionId: "subject-plan-1", sectionLabel: "Core Algebra" }),
    ));
  });

  it("keeps generation usable when Review Sets fail to load", async () => {
    (listCollections as jest.Mock).mockRejectedValue(new Error("Could not load Collections."));
    (bulkGenerateNotes as jest.Mock).mockResolvedValue({
      resultId: "result-1",
      acceptedTopics: 1,
      queuedTopics: 1,
      rejectedTopics: 0,
    });
    render(<BulkGenerationPageClient />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load Collections.");
    await fillAdminForm(["Prenatal Care"]);

    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    await waitFor(() => expect(bulkGenerateNotes).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: "Retry Collections" })).toBeInTheDocument();
  });

  it("prefills from retry stash and clears it", async () => {
    setBulkGenerationRetryStash({
      subject: "Maternal Health",
      courseProgram: "Nursing",
      domainContext: "NURSING",
      learnerLevel: "BOARD_EXAM_REVIEW",
      makePublic: true,
      topics: ["Prenatal Care", "Labor Stages"],
    });

    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(screen.getByLabelText(/^Subject/)).toHaveValue("Maternal Health");
    expect(await screen.findByRole("button", { name: "Remove Nursing" })).toBeInTheDocument();
    expect(screen.getByLabelText(/^Domain Context/)).toHaveValue("NURSING");
    expect(screen.getByText(/nursing-framed Pharmacology/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("BOARD_EXAM_REVIEW");
    expect(screen.queryByLabelText(/^Target Audience/)).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByLabelText(/^Topic 1$/)).toHaveValue("Prenatal Care");
    expect(screen.getByLabelText(/^Topic 2$/)).toHaveValue("Labor Stages");
    expect(globalThis.sessionStorage.getItem("notelib.bulk.retryTopics")).toBeNull();
  });

  it("restores a stashed section without resuming subject tracking", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "subject-plan-1", title: "Civil Engineering Mathematics", resolvedLearnerLevel: null },
    ]);
    setBulkGenerationRetryStash({
      subject: "Algebra",
      courseProgram: "Civil Engineering",
      domainContext: null,
      learnerLevel: null,
      makePublic: false,
      topics: ["Quadratic Equations"],
      collectionId: "subject-plan-1",
      sectionLabel: "Foundations",
    });
    render(<BulkGenerationPageClient />);

    const sectionInput = await screen.findByLabelText(/^Section \(optional\)/);
    expect(sectionInput).toHaveValue("Foundations");
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Calculus" } });
    expect(sectionInput).toHaveValue("Foundations");
  });

  it("does not invent a section when the retry stash carries none", async () => {
    // The real retry path: retryBulkFailures cannot write sectionLabel, because
    // bulk_generation_result has no column for it. Falling back to the subject here would
    // section every retried batch by subject -- including one whose curator deliberately left
    // the section blank -- which is an assignment they never made.
    (listCollections as jest.Mock).mockResolvedValue([
      { id: "subject-plan-1", title: "Civil Engineering Mathematics", resolvedLearnerLevel: null },
    ]);
    setBulkGenerationRetryStash({
      subject: "Algebra",
      courseProgram: "Civil Engineering",
      domainContext: null,
      learnerLevel: null,
      makePublic: false,
      topics: ["Quadratic Equations"],
      collectionId: "subject-plan-1",
    });
    render(<BulkGenerationPageClient />);

    const sectionInput = await screen.findByLabelText(/^Section \(optional\)/);
    expect(sectionInput).toHaveValue("");
    expect(screen.getByLabelText(/^Subject/)).toHaveValue("Algebra");
  });

  it("splits a multi-line paste into separate topic rows and strips list markers", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    fireEvent.paste(screen.getByLabelText(/^Topic 1$/), {
      clipboardData: {
        getData: () => "* Adjusting Entries and the Accrual Basis of Accounting\n* Cash Management and Cash Equivalents in Financial Reporting",
      },
    });

    await waitFor(() => {
      expect(screen.getByLabelText(/^Topic 1$/)).toHaveValue(
        "Adjusting Entries and the Accrual Basis of Accounting",
      );
    });
    expect(screen.getByLabelText(/^Topic 2$/)).toHaveValue(
      "Cash Management and Cash Equivalents in Financial Reporting",
    );
  });

  it("preserves form state when submission fails", async () => {
    (bulkGenerateNotes as jest.Mock).mockRejectedValueOnce(new Error("Connection lost."));
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    await fillAdminForm(["Prenatal Care"]);
    fireEvent.change(screen.getByLabelText(/^Domain Context/), {
      target: { value: "NURSING" },
    });
    fireEvent.change(screen.getByLabelText(/^Authored Depth/), {
      target: { value: "BOARD_EXAM_REVIEW" },
    });

    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Connection lost.");
    expect(screen.getByLabelText(/^Subject/)).toHaveValue("Maternal Health");
    expect(screen.getByLabelText(/^Topic 1$/)).toHaveValue("Prenatal Care");
    expect(screen.getByLabelText(/^Domain Context/)).toHaveValue("NURSING");
    expect(screen.getByLabelText(/^Authored Depth/)).toHaveValue("BOARD_EXAM_REVIEW");
  });

  it("adds and removes topic rows", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));

    expect(screen.getByLabelText(/^Topic 1$/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Topic 2$/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Remove topic 2" }));

    expect(screen.queryByLabelText(/^Topic 2$/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove topic 1" })).toBeDisabled();
  });

  it("disables the queue button with no topics", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
    fireEvent.change(screen.getByLabelText(/^Course \/ Program/), { target: { value: "Nursing" } });

    expect(screen.getByRole("button", { name: "Generate" })).toBeDisabled();
    expect(bulkGenerateNotes).not.toHaveBeenCalled();
  });

  it("disables adding topics once the 50 batch cap is reached", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText(/^Topic 1$/), { target: { value: "Topic 1" } });

    for (let index = 2; index <= 50; index += 1) {
      fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));
      fireEvent.change(screen.getByLabelText(new RegExp(`^Topic ${index}$`)), {
        target: { value: `Topic ${index}` },
      });
    }

    expect(screen.getByRole("button", { name: "+ Add topic" })).toBeDisabled();
    expect(screen.queryByLabelText(/^Topic 51$/)).not.toBeInTheDocument();
    expect(screen.getByText("50 / 50")).toBeInTheDocument();
  });

  it("caps topics at the remaining note generations for limited plans", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "student-1", role: "USER", profileType: "STUDENT" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      limits: { noteGenerationsPerMonth: 10, studyPacksPerMonth: 10, ocrPerMonth: 20 },
      remaining: { noteGenerationsRemaining: 2, studyPacksRemaining: 5, ocrRemaining: 20 },
      usageCycle: { startsAt: "2026-06-01T00:00:00Z", endsAt: "2026-07-01T00:00:00Z" },
    });

    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    // Near-limit (<= 2) shows the amber note-generation banner.
    expect(
      await screen.findByText(/2 topic notes still ready to use/i),
    ).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/^Topic 1$/), { target: { value: "Topic 1" } });
    fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));
    fireEvent.change(screen.getByLabelText(/^Topic 2$/), { target: { value: "Topic 2" } });

    expect(screen.getByText("2 / 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "+ Add topic" })).toBeDisabled();
  });

  it("soft-confirms when topics exceed the Study Pack quota, then submits", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "student-1", role: "USER", profileType: "STUDENT" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      limits: { noteGenerationsPerMonth: 25, studyPacksPerMonth: 10, ocrPerMonth: 20 },
      remaining: { noteGenerationsRemaining: 10, studyPacksRemaining: 1, ocrRemaining: 20 },
      usageCycle: { startsAt: "2026-06-01T00:00:00Z", endsAt: "2026-07-01T00:00:00Z" },
    });
    (bulkGenerateNotes as jest.Mock).mockResolvedValueOnce({ resultId: "r1", queuedTopics: 2 });

    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
    fireEvent.change(screen.getByLabelText(/^Course \/ Program/), { target: { value: "Nursing" } });
    fireEvent.change(screen.getByLabelText(/^Topic 1$/), { target: { value: "Topic 1" } });
    fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));
    fireEvent.change(screen.getByLabelText(/^Topic 2$/), { target: { value: "Topic 2" } });

    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    expect(await screen.findByText(/won’t get Study Packs/i)).toBeInTheDocument();
    expect(bulkGenerateNotes).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Generate anyway" }));

    await waitFor(() => expect(bulkGenerateNotes).toHaveBeenCalled());
  });
});
