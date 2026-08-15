import { trackCourseProgramValueSelected } from "./use-course-program-catalog";
import { trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  getCourseProgramCatalog: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("trackCourseProgramValueSelected", () => {
  beforeEach(() => {
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
  });

  it.each([
    ["profile", " nursing ", true],
    ["note-editor", "NURSING", true],
    ["note-detail", "Professional / Board Exam Review", false],
    ["dashboard-prompt", "Software Engineering", false],
  ] as const)("tracks a committed %s value without including the raw value", (surface, value, matchedCatalog) => {
    trackCourseProgramValueSelected(surface, value, "Previously Something Else", ["Nursing", "Pharmacy"]);

    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "COURSE_PROGRAM_VALUE_SELECTED",
      metadata: { surface, matchedCatalog },
    });
    expect((trackAnalyticsEvent as jest.Mock).mock.calls[0][0].metadata).not.toHaveProperty("value");
  });

  it.each([
    ["identical", "Nursing", "Nursing"],
    ["case-and-whitespace-equivalent", " nursing ", "Nursing"],
    ["both empty", "", null],
  ] as const)("omits the event when the value is %s to the previous one", (_label, value, previous) => {
    // The metric counts what learners PICK. Every call site saves other fields alongside the
    // program, so firing on unchanged values would fill it with re-saves of legacy strings.
    trackCourseProgramValueSelected("profile", value, previous, ["Nursing", "Pharmacy"]);

    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });

  it("omits the event when catalog availability is unknown", () => {
    trackCourseProgramValueSelected("profile", "Nursing", "Architecture", null);

    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });

  it("swallows rejected and synchronous analytics failures", async () => {
    (trackAnalyticsEvent as jest.Mock).mockRejectedValueOnce(new Error("unavailable"));
    expect(() => trackCourseProgramValueSelected("profile", "Nursing", "Architecture", ["Nursing"])).not.toThrow();
    await Promise.resolve();

    (trackAnalyticsEvent as jest.Mock).mockImplementationOnce(() => {
      throw new Error("unavailable");
    });
    expect(() => trackCourseProgramValueSelected("profile", "Nursing", "Architecture", ["Nursing"])).not.toThrow();
  });
});
