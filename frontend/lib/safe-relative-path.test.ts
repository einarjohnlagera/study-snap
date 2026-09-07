import { isSafeRelativePath, toSafeRelativePath } from "./safe-relative-path";

/**
 * ⚠️ THE RENDER-SIDE HALF OF A SECURITY CONTROL. The backend validates on write; this exists because
 * `next/link` renders an absolute URL as a live external anchor, so a stored value must be re-checked
 * before it reaches an `href`. The cases mirror the backend's table deliberately — if one side gains a
 * case, the other should too.
 */
describe("safe relative path", () => {
  it.each([
    "https://evil.example",
    "http://evil.example/path",
    "//evil.example",
    "//evil.example/dashboard",
    "javascript:alert(1)",
    "data:text/html,<script>alert(1)</script>",
    "mailto:someone@evil.example",
    "dashboard",
    "\\\\evil.example",
    "/dashboard#fragment",
    "/dash board",
  ])("rejects %s", (value) => {
    expect(isSafeRelativePath(value)).toBe(false);
    expect(toSafeRelativePath(value)).toBeNull();
  });

  it.each([
    "/",
    "/dashboard",
    "/valid/path",
    "/valid/path?x=1",
    "/notes/9f3c-1234?tab=quiz&mode=board",
  ])("accepts %s", (value) => {
    expect(isSafeRelativePath(value)).toBe(true);
    expect(toSafeRelativePath(value)).toBe(value);
  });

  it("treats absent and blank as no link at all", () => {
    expect(toSafeRelativePath(null)).toBeNull();
    expect(toSafeRelativePath(undefined)).toBeNull();
    expect(toSafeRelativePath("   ")).toBeNull();
  });

  it("trims surrounding whitespace rather than stripping characters from the middle", () => {
    expect(toSafeRelativePath("  /valid/path?x=1  ")).toBe("/valid/path?x=1");
  });
});
