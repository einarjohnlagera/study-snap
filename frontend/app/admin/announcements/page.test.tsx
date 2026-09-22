import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AdminAnnouncementsPage from "./page";
import { listAnnouncements, publishAnnouncement } from "@/lib/api";

const routerMock = {
  replace: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAdminUser: jest.fn(() => true),
}));

jest.mock("@/lib/api", () => ({
  listAnnouncements: jest.fn(),
  publishAnnouncement: jest.fn(),
  createAnnouncement: jest.fn(),
  updateAnnouncement: jest.fn(),
  endAnnouncement: jest.fn(),
  ApiRequestError: class ApiRequestError extends Error {
    status: number;

    constructor(message: string, options: { status: number }) {
      super(message);
      this.status = options.status;
    }
  },
}));

const draft = {
  id: "announcement-1",
  title: "Board Exam Mode is here",
  body: "Practice with a full timed board exam.",
  ctaLabel: "Try it",
  ctaPath: "/dashboard?tab=exams",
  audience: "EVERYONE" as const,
  audienceValue: null,
  status: "DRAFT" as const,
  publishedAt: null,
  expiresAt: null,
  createdAt: "2026-09-09T00:00:00Z",
  updatedAt: "2026-09-09T00:00:00Z",
  editable: true,
  expired: false,
};

async function publishFromPage() {
  render(<AdminAnnouncementsPage />);
  await screen.findByText(draft.title);
  fireEvent.click(screen.getByRole("button", { name: "Publish" }));
  fireEvent.click(screen.getByRole("button", { name: "Publish now" }));
  await waitFor(() => expect(publishAnnouncement).toHaveBeenCalledWith(draft.id));
}

describe("AdminAnnouncementsPage publish feedback", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (listAnnouncements as jest.Mock).mockResolvedValue([draft]);
  });

  it("says accepted delivery is running in the background", async () => {
    (publishAnnouncement as jest.Mock).mockResolvedValue({
      announcement: { ...draft, status: "PUBLISHED", editable: false },
      recipientCount: 12,
      queued: 12,
    });

    await publishFromPage();

    expect(await screen.findByText("Publishing to 12 people — delivery runs in the background."))
      .toBeInTheDocument();
  });

  it("preserves the retry affordance when delivery cannot be queued", async () => {
    (publishAnnouncement as jest.Mock).mockResolvedValue({
      announcement: { ...draft, status: "PUBLISHED", editable: false },
      recipientCount: 12,
      queued: 0,
    });

    await publishFromPage();

    expect(await screen.findByText("Could not queue delivery. Press Publish again to retry."))
      .toBeInTheDocument();
  });
});

describe("AdminAnnouncementsPage body character counter", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (listAnnouncements as jest.Mock).mockResolvedValue([]);
  });

  function bodyTextarea() {
    // ⚠️ The label wraps the textarea AND its helper/counter text, so the implicit accessible name
    // includes all of it — match on the leading "Body", not an exact string.
    return screen.getByRole("textbox", { name: /^Body/ });
  }

  // ⚠️ The count/limit/soft-target are separate JSX expressions inside one <span>, so the text is
  // split across sibling text nodes — a plain string matcher can't find it. Match by textContent.
  function bodyCounter(expected: string) {
    return screen.getByText((_, element) => element?.tagName === "SPAN" && element.textContent === expected);
  }

  it("shows a neutral count under the ~160-char soft target", async () => {
    render(<AdminAnnouncementsPage />);
    await screen.findByRole("heading", { name: "New announcement" });

    fireEvent.change(bodyTextarea(), { target: { value: "Short body." } });

    const counter = bodyCounter("11 / 1000 (~160 recommended)");
    expect(counter).not.toHaveClass("text-amber-600");
    expect(counter).not.toHaveClass("text-red-600");
  });

  it("warns, but does not block, once the body passes the ~160-char soft target", async () => {
    render(<AdminAnnouncementsPage />);
    await screen.findByRole("heading", { name: "New announcement" });

    fireEvent.change(bodyTextarea(), { target: { value: "x".repeat(200) } });

    const counter = bodyCounter("200 / 1000 (~160 recommended)");
    expect(counter).toHaveClass("text-amber-600");
    expect(bodyTextarea()).toHaveValue("x".repeat(200));
  });

  it("cannot exceed the 1000-char hard limit — the column and API validator are unchanged", async () => {
    render(<AdminAnnouncementsPage />);
    await screen.findByRole("heading", { name: "New announcement" });

    expect(bodyTextarea()).toHaveAttribute("maxLength", "1000");
  });
});
