import { buildCollectionPageStructuredData } from "./structured-data";

describe("buildCollectionPageStructuredData", () => {
  it("omits mainEntity when no items are provided", () => {
    const result = buildCollectionPageStructuredData({
      name: "PNLE Exam Hub",
      url: "https://notelib.app/exam/pnle",
      description: "PNLE notes and quizzes.",
    });

    expect(result.mainEntity).toBeUndefined();
    expect(JSON.stringify(result)).not.toContain("mainEntity");
  });

  it("omits mainEntity when items is an empty array", () => {
    const result = buildCollectionPageStructuredData({
      name: "PNLE Exam Hub",
      url: "https://notelib.app/exam/pnle",
      description: "PNLE notes and quizzes.",
      items: [],
    });

    expect(result.mainEntity).toBeUndefined();
  });

  it("asserts an ItemList of the collection's actual member notes when items are provided", () => {
    const result = buildCollectionPageStructuredData({
      name: "PNLE Exam Hub",
      url: "https://notelib.app/exam/pnle",
      description: "PNLE notes and quizzes.",
      items: [
        { name: "Fundamentals of Nursing", url: "https://notelib.app/public/library/nursing/fundamentals" },
        { name: "Med-Surg Notes", url: "https://notelib.app/public/library/nursing/med-surg" },
      ],
    });

    expect(result.mainEntity).toEqual({
      "@type": "ItemList",
      itemListElement: [
        {
          "@type": "ListItem",
          position: 1,
          name: "Fundamentals of Nursing",
          item: "https://notelib.app/public/library/nursing/fundamentals",
        },
        {
          "@type": "ListItem",
          position: 2,
          name: "Med-Surg Notes",
          item: "https://notelib.app/public/library/nursing/med-surg",
        },
      ],
    });
  });
});
