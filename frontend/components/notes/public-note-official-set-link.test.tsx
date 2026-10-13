import { fireEvent, render, screen } from "@testing-library/react";
import { trackAnalyticsEvent } from "@/lib/api";
import { PublicNoteOfficialSetLink } from "./public-note-official-set-link";

jest.mock("@/lib/api", () => ({ trackAnalyticsEvent: jest.fn() }));

it("tracks a public-note Official set link only when clicked", () => {
  render(<PublicNoteOfficialSetLink id="set-1" />);
  const link = screen.getByRole("link", { name: "Browse this Review Set →" });
  expect(link).toHaveAttribute("href", "/collections/published?highlight=set-1");
  expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  fireEvent.click(link);
  expect(trackAnalyticsEvent).toHaveBeenCalledWith({
    eventType: "PUBLIC_NOTE_OFFICIAL_SET_LINK_CLICKED", entityId: "set-1",
  });
});
