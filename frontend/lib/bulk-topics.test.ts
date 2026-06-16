import { MAX_TOPIC_LENGTH, parsePastedTopics } from "./bulk-topics";

describe("parsePastedTopics", () => {
  it("splits a multi-line block into one topic per line", () => {
    expect(parsePastedTopics("Adjusting Entries\nCash Management")).toEqual([
      "Adjusting Entries",
      "Cash Management",
    ]);
  });

  it("handles CRLF line endings from Windows / Google Docs", () => {
    expect(parsePastedTopics("Topic A\r\nTopic B")).toEqual(["Topic A", "Topic B"]);
  });

  it("strips leading bullet markers", () => {
    expect(
      parsePastedTopics("* Adjusting Entries\n- Cash Management\n• Accrual Basis"),
    ).toEqual(["Adjusting Entries", "Cash Management", "Accrual Basis"]);
  });

  it("strips leading numbered markers", () => {
    expect(parsePastedTopics("1. First\n2) Second\n(3) Third")).toEqual([
      "First",
      "Second",
      "Third",
    ]);
  });

  it("preserves source order", () => {
    expect(parsePastedTopics("One\nTwo\nThree")).toEqual(["One", "Two", "Three"]);
  });

  it("drops empty and whitespace-only lines", () => {
    expect(parsePastedTopics("Topic A\n\n   \nTopic B")).toEqual(["Topic A", "Topic B"]);
  });

  it("never splits on commas (topics legitimately contain commas)", () => {
    expect(parsePastedTopics("Cash, Cash Equivalents, and Reporting")).toEqual([
      "Cash, Cash Equivalents, and Reporting",
    ]);
  });

  it("does not strip a leading dot from real topics like .NET", () => {
    expect(parsePastedTopics(".NET framework basics")).toEqual([".NET framework basics"]);
  });

  it("does not treat a decimal without trailing space as a marker", () => {
    expect(parsePastedTopics("1.5 inch standards")).toEqual(["1.5 inch standards"]);
  });

  it("does not strip leading symbols that are not list markers", () => {
    expect(parsePastedTopics('"To be" analysis\n$100 budgeting\n@decorator patterns')).toEqual([
      '"To be" analysis',
      "$100 budgeting",
      "@decorator patterns",
    ]);
  });

  it("only strips one marker, not nested ones", () => {
    expect(parsePastedTopics("- * Double marked")).toEqual(["* Double marked"]);
  });

  it("clamps each line to the max topic length", () => {
    const long = "a".repeat(MAX_TOPIC_LENGTH + 50);
    const [result] = parsePastedTopics(long);
    expect(result).toHaveLength(MAX_TOPIC_LENGTH);
  });

  it("returns an empty array for empty or marker-only input", () => {
    expect(parsePastedTopics("")).toEqual([]);
    expect(parsePastedTopics("   \n  \n")).toEqual([]);
  });
});
