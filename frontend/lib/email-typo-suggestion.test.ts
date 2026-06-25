import { suggestEmailCorrection } from "./email-typo-suggestion";

describe("suggestEmailCorrection", () => {
  it("suggests the corrected domain for near-miss typos", () => {
    expect(suggestEmailCorrection("lagurasriza@0gmail.com")).toBe("lagurasriza@gmail.com");
    expect(suggestEmailCorrection("a@gmial.com")).toBe("a@gmail.com");
    expect(suggestEmailCorrection("a@gmail.con")).toBe("a@gmail.com");
    expect(suggestEmailCorrection("a@yahooo.com")).toBe("a@yahoo.com");
    expect(suggestEmailCorrection("a@hotmial.com")).toBe("a@hotmail.com");
  });

  it("preserves the original local part (case and dots)", () => {
    expect(suggestEmailCorrection("First.Last@gmial.com")).toBe("First.Last@gmail.com");
  });

  it("returns null for valid popular domains and unrelated domains", () => {
    expect(suggestEmailCorrection("a@gmail.com")).toBeNull();
    expect(suggestEmailCorrection("a@company.com")).toBeNull();
    expect(suggestEmailCorrection("student@university.edu.ph")).toBeNull();
  });

  it("returns null for malformed or empty input", () => {
    expect(suggestEmailCorrection("")).toBeNull();
    expect(suggestEmailCorrection("noatsign")).toBeNull();
    expect(suggestEmailCorrection("@gmail.com")).toBeNull();
    expect(suggestEmailCorrection("a@")).toBeNull();
    expect(suggestEmailCorrection("a@localhost")).toBeNull();
  });
});
